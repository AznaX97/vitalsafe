import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth, type AuthState } from "../../auth/useAuth";
import { clearFlash, peekFlash } from "../../lib/flash";
import AccountPage from "../AccountPage";

vi.mock("../../auth/useAuth", () => ({ useAuth: vi.fn() }));

const fetchMock = vi.fn();
const endSession = vi.fn();

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function LoginProbe() {
  const notice = peekFlash();
  return <p>Login page: {notice}</p>;
}

function renderPage() {
  render(
    <MemoryRouter initialEntries={["/account"]}>
      <Routes>
        <Route path="/account" element={<AccountPage />} />
        <Route path="/login" element={<LoginProbe />} />
        <Route path="/" element={<p>Dashboard</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  fetchMock.mockReset();
  endSession.mockReset();
  clearFlash();
  vi.stubGlobal("fetch", fetchMock);
  vi.mocked(useAuth).mockReturnValue({ email: "me@example.com", endSession } as unknown as AuthState);
});

async function fillChange(user: ReturnType<typeof userEvent.setup>, current: string, next: string, repeat: string) {
  await user.type(screen.getByLabelText("Current password"), current);
  await user.type(screen.getByLabelText("New password"), next);
  await user.type(screen.getByLabelText("Repeat the new password"), repeat);
}

describe("AccountPage: changing the password", () => {
  it("sends both passwords, ends the local session and goes to the login page with a notice", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    renderPage();

    await fillChange(user, "Old-Passw0rd-12345", "Brand-New-Passw0rd-1", "Brand-New-Passw0rd-1");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByText(/Login page: Your password was changed/)).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/account/password");
    expect(JSON.parse(String(init.body))).toEqual({ currentPassword: "Old-Passw0rd-12345", newPassword: "Brand-New-Passw0rd-1" });
    expect(endSession).toHaveBeenCalledTimes(1);
  });

  it("stays on the page and shows the field message when the current password is wrong", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(json(400, { error: "Validation failed", fields: { currentPassword: "Current password is incorrect" } }));
    renderPage();

    await fillChange(user, "Wrong-Passw0rd-12345", "Brand-New-Passw0rd-1", "Brand-New-Passw0rd-1");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Current password is incorrect");
    expect(screen.getByRole("heading", { name: "Your account" })).toBeInTheDocument();
    expect(endSession).not.toHaveBeenCalled();
  });

  it("does not send anything when the new passwords differ", async () => {
    const user = userEvent.setup();
    renderPage();

    await fillChange(user, "Old-Passw0rd-12345", "Brand-New-Passw0rd-1", "Brand-New-Passw0rd-2");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("do not match");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows the account's email and a way back", () => {
    renderPage();

    expect(screen.getByText("me@example.com")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to your readings" })).toHaveAttribute("href", "/");
  });
});

describe("AccountPage: deleting the account", () => {
  it("keeps the delete button off until the password is typed and the box is ticked", async () => {
    const user = userEvent.setup();
    renderPage();
    const button = screen.getByRole("button", { name: "Delete my account" });
    expect(button).toBeDisabled();

    await user.type(screen.getByLabelText("Your password"), "Old-Passw0rd-12345");
    expect(button).toBeDisabled();

    await user.click(screen.getByRole("checkbox"));
    expect(button).toBeEnabled();
  });

  it("deletes with the password, ends the local session and says so on the login page", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    renderPage();

    await user.type(screen.getByLabelText("Your password"), "Old-Passw0rd-12345");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Delete my account" }));

    expect(await screen.findByText(/Login page: Your account and all its data were deleted/)).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/account/delete");
    expect(JSON.parse(String(init.body))).toEqual({ password: "Old-Passw0rd-12345" });
    expect(endSession).toHaveBeenCalledTimes(1);
  });

  it("shows the server's message and keeps the account when the password is wrong", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(json(400, { error: "Validation failed", fields: { password: "Password is incorrect" } }));
    renderPage();

    await user.type(screen.getByLabelText("Your password"), "Wrong-Passw0rd-12345");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Delete my account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Password is incorrect");
    expect(endSession).not.toHaveBeenCalled();
  });

  it("says what deletion removes and that it cannot be undone", () => {
    renderPage();

    expect(screen.getByText(/permanently removes your login, all your readings and your target ranges/)).toBeInTheDocument();
    expect(screen.getByText("I understand that this cannot be undone.")).toBeInTheDocument();
  });
});