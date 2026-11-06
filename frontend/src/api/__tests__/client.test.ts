import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiError } from "../client";

const fetchMock = vi.fn();

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function empty(status: number): Response {
  return new Response(null, { status });
}

function headersOf(callIndex: number): Record<string, string> {
  const init = fetchMock.mock.calls[callIndex][1] as RequestInit;
  return init.headers as Record<string, string>;
}

// The client keeps its token in a module variable, so every test gets a fresh copy
async function loadClient() {
  vi.resetModules();
  return import("../client");
}

async function failureOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("Expected the promise to reject");
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("api client", () => {
  it("sends JSON to /api/v1 and no Authorization header when logged out", async () => {
    const { api } = await loadClient();
    fetchMock.mockResolvedValueOnce(json(200, { ok: true }));

    const result = await api("/things", { method: "POST", json: { a: 1 } });

    expect(result).toEqual({ ok: true });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/things");
    expect(init.method).toBe("POST");
    expect(headersOf(0)["Content-Type"]).toBe("application/json");
    expect(headersOf(0)["Authorization"]).toBeUndefined();
    expect(init.body).toBe(JSON.stringify({ a: 1 }));
  });

  it("adds the bearer token once one is set", async () => {
    const { api, setToken } = await loadClient();
    setToken("my-token");
    fetchMock.mockResolvedValueOnce(json(200, []));

    await api("/vitals");

    expect(headersOf(0)["Authorization"]).toBe("Bearer my-token");
  });

  it("returns null for a 204 response", async () => {
    const { api } = await loadClient();
    fetchMock.mockResolvedValueOnce(empty(204));

    expect(await api("/vitals/1", { method: "DELETE" })).toBeNull();
  });

  it("turns a backend error into an ApiError with status, message and fields", async () => {
    const { api, ApiError: LoadedApiError } = await loadClient();
    fetchMock.mockResolvedValueOnce(json(400, { error: "Validation failed", fields: { value: "value is required" } }));

    const error = await failureOf(api("/vitals", { method: "POST", json: {} }));

    expect(error).toBeInstanceOf(LoadedApiError);
    expect(error.status).toBe(400);
    expect(error.message).toBe("Validation failed");
    expect(error.fields).toEqual({ value: "value is required" });
  });

  it("uses a generic message when the error body is empty", async () => {
    const { api } = await loadClient();
    fetchMock.mockResolvedValueOnce(empty(500));

    const error = await failureOf(api("/vitals"));

    expect(error.status).toBe(500);
    expect(error.message).toBe("Request failed");
  });

  it("reports a network failure as status 0", async () => {
    const { api } = await loadClient();
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    const error = await failureOf(api("/vitals"));

    expect(error.status).toBe(0);
    expect(error.message).toContain("Cannot reach the server");
  });

  it("refreshes silently on 401 and retries the request once", async () => {
    const { api, setToken } = await loadClient();
    setToken("old");
    fetchMock
      .mockResolvedValueOnce(empty(401))
      .mockResolvedValueOnce(json(200, { token: "new", email: "a@b.c" }))
      .mockResolvedValueOnce(json(200, [{ id: 1 }]));

    const result = await api("/vitals");

    expect(result).toEqual([{ id: 1 }]);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(headersOf(0)["Authorization"]).toBe("Bearer old");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/auth/refresh");
    expect((fetchMock.mock.calls[1][1] as RequestInit).method).toBe("POST");
    expect(headersOf(2)["Authorization"]).toBe("Bearer new");
  });

  it("calls the unauthorized handler when the refresh fails", async () => {
    const { api, setToken, setUnauthorizedHandler } = await loadClient();
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    setToken("old");
    fetchMock.mockResolvedValueOnce(empty(401)).mockResolvedValueOnce(empty(401));

    const error = await failureOf(api("/vitals"));

    expect(handler).toHaveBeenCalledTimes(1);
    expect(error.status).toBe(401);
  });

  it("does not try to refresh when a login attempt fails", async () => {
    const { api, setUnauthorizedHandler } = await loadClient();
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    fetchMock.mockResolvedValueOnce(json(401, { error: "Invalid email or password" }));

    const error = await failureOf(api("/auth/login", { method: "POST", json: {} }));

    expect(error.message).toBe("Invalid email or password");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(handler).not.toHaveBeenCalled();
  });

  it("shares one refresh request between concurrent callers", async () => {
    const { refreshSession } = await loadClient();
    fetchMock.mockResolvedValueOnce(json(200, { token: "t", email: "a@b.c" }));

    // Refresh tokens are single use, so two parallel refreshes would look like token theft
    const [first, second] = await Promise.all([refreshSession(), refreshSession()]);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(first).toEqual({ token: "t", email: "a@b.c" });
    expect(second).toBe(first);
  });

  it("stores the token from a successful refresh", async () => {
    const { api, refreshSession } = await loadClient();
    fetchMock.mockResolvedValueOnce(json(200, { token: "fresh", email: "a@b.c" })).mockResolvedValueOnce(json(200, []));

    await refreshSession();
    await api("/vitals");

    expect(headersOf(1)["Authorization"]).toBe("Bearer fresh");
  });

  it("returns null when there is no session to restore", async () => {
    const { refreshSession } = await loadClient();
    fetchMock.mockResolvedValueOnce(empty(401));

    expect(await refreshSession()).toBeNull();
  });

  it("serverLogout never throws, even when the server is unreachable", async () => {
    const { serverLogout } = await loadClient();
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    await expect(serverLogout()).resolves.toBeUndefined();
  });

  it("errorMessage prefers field problems and never leaks unknown errors", async () => {
    const { ApiError: LoadedApiError, errorMessage } = await loadClient();

    expect(errorMessage(new LoadedApiError(400, "Validation failed", { value: "Too low.", unit: "Wrong unit." }))).toBe(
      "Too low. Wrong unit.",
    );
    expect(errorMessage(new LoadedApiError(401, "Invalid email or password"))).toBe("Invalid email or password");
    expect(errorMessage(new Error("secret internal detail"))).toBe("Something went wrong");
    expect(errorMessage("a string")).toBe("Something went wrong");
  });
});