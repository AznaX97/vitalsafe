import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useAuth, type AuthState } from "../../auth/useAuth";
import { clearFlash, peekFlash, setFlash } from "../../lib/flash";
import AuthPage from "../AuthPage";

vi.mock("../../auth/useAuth", () => ({ useAuth: vi.fn() }));

function renderLogin() {
  vi.mocked(useAuth).mockReturnValue({
    email: null,
    authenticated: false,
    loading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    endSession: vi.fn(),
  } as unknown as AuthState);
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <Routes>
        <Route path="/login" element={<AuthPage mode="login" />} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => clearFlash());

describe("AuthPage flash notice", () => {
  it("shows a notice that was set before the person arrived", () => {
    setFlash("Your account and all its data were deleted.");

    renderLogin();

    expect(screen.getByRole("status")).toHaveTextContent("Your account and all its data were deleted.");
  });

  it("shows it once, then forgets it", () => {
    setFlash("Your password was changed. Log in again with the new one.");

    const first = renderLogin();
    expect(screen.getByRole("status")).toBeInTheDocument();
    first.unmount();

    expect(peekFlash()).toBeNull();
    renderLogin();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});