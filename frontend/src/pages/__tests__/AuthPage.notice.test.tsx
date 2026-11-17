import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { useAuth, type AuthState } from "../../auth/useAuth";
import AuthPage from "../AuthPage";

vi.mock("../../auth/useAuth", () => ({ useAuth: vi.fn() }));

function renderLogin(mode: "login" | "register", state?: unknown) {
  vi.mocked(useAuth).mockReturnValue({
    email: null,
    authenticated: false,
    loading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    endSession: vi.fn(),
  } as AuthState);
  render(
    <MemoryRouter initialEntries={[{ pathname: "/start", state }]}>
      <Routes>
        <Route path="/start" element={<AuthPage mode={mode} />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("AuthPage notice and recovery link", () => {
  it("shows the notice it was sent here with, for example after a password change", () => {
    renderLogin("login", { notice: "Your password was changed. Log in again with the new one." });

    expect(screen.getByRole("status")).toHaveTextContent("Your password was changed");
  });

  it("shows no notice by default", () => {
    renderLogin("login");

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("offers the password reset on the login page only", () => {
    renderLogin("login");
    expect(screen.getByRole("link", { name: "Forgot your password?" })).toHaveAttribute("href", "/forgot-password");
  });

  it("does not offer it on the register page", () => {
    renderLogin("register");
    expect(screen.queryByRole("link", { name: "Forgot your password?" })).not.toBeInTheDocument();
  });
});