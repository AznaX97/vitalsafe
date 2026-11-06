import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, refreshSession, serverLogout, setToken, setUnauthorizedHandler } from "../../api/client";
import { AuthProvider } from "../AuthProvider";
import { useAuth } from "../useAuth";

vi.mock("../../api/client", () => ({
  api: vi.fn(),
  refreshSession: vi.fn(),
  serverLogout: vi.fn(),
  setToken: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

function Probe() {
  const { email, authenticated, loading, login, register, logout } = useAuth();
  return (
    <div>
      <p>loading: {loading ? "yes" : "no"}</p>
      <p>authenticated: {authenticated ? "yes" : "no"}</p>
      <p>email: {email ?? "none"}</p>
      <button onClick={() => void login("a@b.c", "PasswordPassw0rd")}>do login</button>
      <button onClick={() => void register("new@b.c", "PasswordPassw0rd", true)}>do register</button>
      <button onClick={() => void logout()}>do logout</button>
    </div>
  );
}

function renderProvider(queryClient = new QueryClient()) {
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  );
  return queryClient;
}

beforeEach(() => {
  vi.mocked(refreshSession).mockReset();
  vi.mocked(serverLogout).mockReset().mockResolvedValue(undefined);
  vi.mocked(api).mockReset();
});

describe("AuthProvider", () => {
  it("restores the session from the refresh cookie on page load", async () => {
    vi.mocked(refreshSession).mockResolvedValue({ token: "t", email: "me@example.com" });

    renderProvider();

    expect(await screen.findByText("authenticated: yes")).toBeInTheDocument();
    expect(screen.getByText("email: me@example.com")).toBeInTheDocument();
    expect(screen.getByText("loading: no")).toBeInTheDocument();
  });

  it("stays logged out when there is no session to restore", async () => {
    vi.mocked(refreshSession).mockResolvedValue(null);

    renderProvider();

    expect(await screen.findByText("loading: no")).toBeInTheDocument();
    expect(screen.getByText("authenticated: no")).toBeInTheDocument();
  });

  it("login calls the API, stores the token and marks the user logged in", async () => {
    vi.mocked(refreshSession).mockResolvedValue(null);
    vi.mocked(api).mockResolvedValue({ token: "tok", email: "a@b.c" });
    const user = userEvent.setup();
    renderProvider();
    await screen.findByText("loading: no");

    await user.click(screen.getByRole("button", { name: "do login" }));

    expect(await screen.findByText("authenticated: yes")).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith("/auth/login", {
      method: "POST",
      json: { email: "a@b.c", password: "PasswordPassw0rd" },
    });
    expect(setToken).toHaveBeenCalledWith("tok");
  });

  it("register sends the consent flag along with the credentials", async () => {
    vi.mocked(refreshSession).mockResolvedValue(null);
    vi.mocked(api).mockResolvedValue({ token: "tok", email: "new@b.c" });
    const user = userEvent.setup();
    renderProvider();
    await screen.findByText("loading: no");

    await user.click(screen.getByRole("button", { name: "do register" }));

    expect(await screen.findByText("email: new@b.c")).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith("/auth/register", {
      method: "POST",
      json: { email: "new@b.c", password: "PasswordPassw0rd", consent: true },
    });
  });

  it("logout revokes the session on the server and clears local state", async () => {
    vi.mocked(refreshSession).mockResolvedValue({ token: "t", email: "me@example.com" });
    const user = userEvent.setup();
    renderProvider();
    await screen.findByText("authenticated: yes");

    await user.click(screen.getByRole("button", { name: "do logout" }));

    expect(await screen.findByText("authenticated: no")).toBeInTheDocument();
    expect(serverLogout).toHaveBeenCalledTimes(1);
    expect(setToken).toHaveBeenCalledWith(null);
  });

  it("clears every cached reading when the session ends", async () => {
    vi.mocked(refreshSession).mockResolvedValue({ token: "t", email: "me@example.com" });
    const queryClient = new QueryClient();
    queryClient.setQueryData(["vitals", "page-0"], { items: ["private reading"] });
    const user = userEvent.setup();
    renderProvider(queryClient);
    await screen.findByText("authenticated: yes");
    expect(queryClient.getQueryData(["vitals", "page-0"])).toBeDefined();

    await user.click(screen.getByRole("button", { name: "do logout" }));

    await screen.findByText("authenticated: no");
    expect(queryClient.getQueryData(["vitals", "page-0"])).toBeUndefined();
  });

  it("ends the session, and clears the cache, when the API client reports an expired login", async () => {
    vi.mocked(refreshSession).mockResolvedValue({ token: "t", email: "me@example.com" });
    const queryClient = new QueryClient();
    queryClient.setQueryData(["vitals"], ["private reading"]);
    renderProvider(queryClient);
    await screen.findByText("authenticated: yes");

    const handler = vi.mocked(setUnauthorizedHandler).mock.calls.at(-1)?.[0];
    act(() => handler?.());

    expect(await screen.findByText("authenticated: no")).toBeInTheDocument();
    expect(serverLogout).not.toHaveBeenCalled();
    expect(queryClient.getQueryData(["vitals"])).toBeUndefined();
  });
});