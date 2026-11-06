import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "../../api/client";
import { useAuth, type AuthState } from "../../auth/useAuth";
import AuthPage from "../AuthPage";

vi.mock("../../auth/useAuth", () => ({ useAuth: vi.fn() }));

function renderPage(mode: "login" | "register", overrides: Partial<AuthState> = {}) {
  const auth: AuthState = {
    email: null,
    authenticated: false,
    loading: false,
    login: vi.fn().mockResolvedValue(undefined),
    register: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn().mockResolvedValue(undefined),
    endSession: vi.fn(),
    ...overrides,
  };
  vi.mocked(useAuth).mockReturnValue(auth);
  const view = render(
    <MemoryRouter initialEntries={["/start"]}>
      <Routes>
        <Route path="/start" element={<AuthPage mode={mode} />} />
        <Route path="/" element={<p>Dashboard page</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return { auth, ...view };
}

describe("AuthPage in login mode", () => {
  it("has no consent checkbox", () => {
    renderPage("login");

    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create an account" })).toHaveAttribute("href", "/register");
  });

  it("logs in with the typed email and password", async () => {
    const user = userEvent.setup();
    const { auth } = renderPage("login");

    await user.type(screen.getByLabelText("Email"), "me@example.com");
    await user.type(screen.getByLabelText("Password"), "StrongPassw0rd12");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(auth.login).toHaveBeenCalledWith("me@example.com", "StrongPassw0rd12");
  });

  it("shows the server's error when login fails", async () => {
    const user = userEvent.setup();
    renderPage("login", { login: vi.fn().mockRejectedValue(new ApiError(401, "Invalid email or password")) });

    await user.type(screen.getByLabelText("Email"), "me@example.com");
    await user.type(screen.getByLabelText("Password"), "WrongPassw0rd12");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password");
  });

  it("shows the rate limit message", async () => {
    const user = userEvent.setup();
    renderPage("login", {
      login: vi.fn().mockRejectedValue(new ApiError(429, "Too many attempts. Try again in a minute.")),
    });

    await user.type(screen.getByLabelText("Email"), "me@example.com");
    await user.type(screen.getByLabelText("Password"), "WrongPassw0rd12");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Too many attempts");
  });
});

describe("AuthPage in register mode", () => {
  it("requires the privacy consent checkbox and links to the notice", () => {
    renderPage("register");

    expect(screen.getByRole("checkbox")).toBeRequired();
    const link = screen.getByRole("link", { name: "privacy notice" });
    expect(link).toHaveAttribute("href", "/privacy");
    expect(link).toHaveAttribute("target", "_blank");
    expect(screen.getByLabelText("Password")).toHaveAttribute("minlength", "12");
  });

  it("registers with the consent flag once the box is ticked", async () => {
    const user = userEvent.setup();
    const { auth } = renderPage("register");

    await user.type(screen.getByLabelText("Email"), "new@example.com");
    await user.type(screen.getByLabelText("Password"), "StrongPassw0rd12");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(auth.register).toHaveBeenCalledWith("new@example.com", "StrongPassw0rd12", true);
  });

  it("shows field problems from the server", async () => {
    const user = userEvent.setup();
    renderPage("register", {
      register: vi.fn().mockRejectedValue(new ApiError(409, "Email already registered")),
    });

    await user.type(screen.getByLabelText("Email"), "taken@example.com");
    await user.type(screen.getByLabelText("Password"), "StrongPassw0rd12");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Email already registered");
  });
});

describe("AuthPage session handling", () => {
  it("renders nothing while the session is being restored", () => {
    const { container } = renderPage("login", { loading: true });

    expect(container).toBeEmptyDOMElement();
  });

  it("redirects to the dashboard when already logged in", () => {
    renderPage("login", { authenticated: true });

    expect(screen.getByText("Dashboard page")).toBeInTheDocument();
  });
});