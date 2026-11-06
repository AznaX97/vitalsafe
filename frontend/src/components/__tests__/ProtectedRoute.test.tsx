import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { useAuth, type AuthState } from "../../auth/useAuth";
import ProtectedRoute from "../ProtectedRoute";

vi.mock("../../auth/useAuth", () => ({ useAuth: vi.fn() }));

function signedIn(authenticated: boolean, loading: boolean): void {
  vi.mocked(useAuth).mockReturnValue({ authenticated, loading } as AuthState);
}

function renderRoute() {
  render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route path="/login" element={<p>Login page</p>} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <p>Secret dashboard</p>
            </ProtectedRoute>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  it("shows a loading message while the session is being restored", () => {
    signedIn(false, true);

    renderRoute();

    expect(screen.getByText("Loading...")).toBeInTheDocument();
    expect(screen.queryByText("Login page")).not.toBeInTheDocument();
  });

  it("redirects to the login page when not logged in", () => {
    signedIn(false, false);

    renderRoute();

    expect(screen.getByText("Login page")).toBeInTheDocument();
    expect(screen.queryByText("Secret dashboard")).not.toBeInTheDocument();
  });

  it("shows the page when logged in", () => {
    signedIn(true, false);

    renderRoute();

    expect(screen.getByText("Secret dashboard")).toBeInTheDocument();
  });
});