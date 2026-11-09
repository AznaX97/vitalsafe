import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiError } from "../client";

const fetchMock = vi.fn();

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

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

describe("raw bodies", () => {
  it("sends text as it is, with the content type it is given", async () => {
    const { api } = await loadClient();
    fetchMock.mockResolvedValueOnce(json(200, { imported: 2 }));

    const result = await api("/vitals/import", { method: "POST", body: "a,b\n1,2\n", contentType: "text/csv" });

    expect(result).toEqual({ imported: 2 });
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.body).toBe("a,b\n1,2\n");
    expect((init.headers as Record<string, string>)["Content-Type"]).toBe("text/csv");
  });

  it("keeps the parsed error body so callers can show details such as row errors", async () => {
    const { api } = await loadClient();
    const sent = { error: "Import failed: nothing was imported", rows: [{ row: 2, fields: { value: "too low" } }] };
    fetchMock.mockResolvedValueOnce(json(400, sent));

    const error = await failureOf(api("/vitals/import", { method: "POST", body: "x", contentType: "text/csv" }));

    expect(error.status).toBe(400);
    expect(error.message).toBe("Import failed: nothing was imported");
    expect(error.body).toEqual(sent);
  });
});

describe("apiDownload", () => {
  it("returns the file and the name the server suggested", async () => {
    const { apiDownload } = await loadClient();
    fetchMock.mockResolvedValueOnce(
      new Response("type,value\n", {
        status: 200,
        headers: { "Content-Disposition": 'attachment; filename="vitalsafe-readings.csv"' },
      }),
    );

    const { blob, filename } = await apiDownload("/vitals/export");

    expect(filename).toBe("vitalsafe-readings.csv");
    expect(await blob.text()).toBe("type,value\n");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/vitals/export");
  });

  it("falls back to a generic name", async () => {
    const { apiDownload } = await loadClient();
    fetchMock.mockResolvedValueOnce(new Response("x", { status: 200 }));

    expect((await apiDownload("/vitals/export")).filename).toBe("download");
  });

  it("refreshes once when the access token has expired, then retries", async () => {
    const { apiDownload, setToken } = await loadClient();
    setToken("old");
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(json(200, { token: "new", email: "a@b.c" }))
      .mockResolvedValueOnce(
        new Response("ok", { status: 200, headers: { "Content-Disposition": 'attachment; filename="f.json"' } }),
      );

    const { filename } = await apiDownload("/fhir/export");

    expect(filename).toBe("f.json");
    expect(fetchMock).toHaveBeenCalledTimes(3);
    const retryHeaders = (fetchMock.mock.calls[2][1] as RequestInit).headers as Record<string, string>;
    expect(retryHeaders["Authorization"]).toBe("Bearer new");
  });

  it("throws a normal ApiError when the server refuses", async () => {
    const { apiDownload } = await loadClient();
    fetchMock.mockResolvedValueOnce(json(413, { error: "Too many readings for one export." }));

    const error = await failureOf(apiDownload("/vitals/export"));

    expect(error.status).toBe(413);
    expect(error.message).toContain("Too many readings");
  });
});