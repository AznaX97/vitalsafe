import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ResetPasswordPage from "../ResetPasswordPage";

const fetchMock = vi.fn();

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function LoginProbe() {
  const notice = (useLocation().state as { notice?: string } | null)?.notice;
  return <p>Login page: {notice}</p>;
}

function renderAt(hash: string) {
  window.history.replaceState(null, "", `/reset-password${hash}`);
  render(
    <MemoryRouter initialEntries={[`/reset-password${hash}`]}>
      <Routes>
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/forgot-password" element={<p>Forgot page</p>} />
        <Route path="/login" element={<LoginProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("ResetPasswordPage", () => {
  it("sends the token from the address with the new password, then goes to the login page with a notice", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    renderAt("#token=abc-123_XYZ");

    await user.type(screen.getByLabelText("New password"), "Brand-New-Passw0rd-1");
    await user.type(screen.getByLabelText("Repeat the new password"), "Brand-New-Passw0rd-1");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByText(/Login page: Your password was changed/)).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/auth/reset-password");
    expect(JSON.parse(String(init.body))).toEqual({ token: "abc-123_XYZ", newPassword: "Brand-New-Passw0rd-1" });
  });

  it("removes the token from the address bar", () => {
    renderAt("#token=secret-token");

    expect(window.location.hash).toBe("");
    expect(window.location.href).not.toContain("secret-token");
  });

  it("does not send anything when the two passwords differ", async () => {
    const user = userEvent.setup();
    renderAt("#token=abc");

    await user.type(screen.getByLabelText("New password"), "Brand-New-Passw0rd-1");
    await user.type(screen.getByLabelText("Repeat the new password"), "Brand-New-Passw0rd-2");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("do not match");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows the server's message when the link is invalid or expired", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(json(400, { error: "This reset link is invalid or has expired. Request a new one." }));
    renderAt("#token=old");

    await user.type(screen.getByLabelText("New password"), "Brand-New-Passw0rd-1");
    await user.type(screen.getByLabelText("Repeat the new password"), "Brand-New-Passw0rd-1");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("invalid or has expired");
    expect(screen.getByRole("link", { name: "Ask for a new link" })).toHaveAttribute("href", "/forgot-password");
  });

  it("shows the password rule from the server", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(json(400, { error: "Validation failed", fields: { newPassword: "Password must be at least 12 characters" } }));
    renderAt("#token=abc");

    await user.type(screen.getByLabelText("New password"), "twelve-chars");
    await user.type(screen.getByLabelText("Repeat the new password"), "twelve-chars");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("at least 12 characters");
  });

  it("explains a link without a token and offers a new one", () => {
    renderAt("");

    expect(screen.getByRole("heading", { name: "This link is not complete" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ask for a new link" })).toHaveAttribute("href", "/forgot-password");
    expect(screen.queryByLabelText("New password")).not.toBeInTheDocument();
  });
});