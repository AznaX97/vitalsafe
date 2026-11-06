import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import Footer from "../../components/Footer";
import { CONTACT_EMAIL, LAST_UPDATED, OPERATOR } from "../../config/legal";
import PrivacyPage from "../PrivacyPage";

function renderPrivacy() {
  render(
    <MemoryRouter>
      <PrivacyPage />
    </MemoryRouter>,
  );
}

describe("PrivacyPage", () => {
  it("shows the medical disclaimer and says the app is not for emergencies", () => {
    renderPrivacy();

    expect(screen.getByRole("heading", { name: "Privacy notice and medical disclaimer" })).toBeInTheDocument();
    expect(screen.getByText(/does not diagnose, treat or give/i)).toBeInTheDocument();
    expect(screen.getByText("VitalSafe is not for emergencies.")).toBeInTheDocument();
  });

  it("is honest that this is not end-to-end encryption and that account deletion is missing", () => {
    renderPrivacy();

    expect(screen.getByText("This is not end-to-end encryption.")).toBeInTheDocument();
    expect(screen.getByText(/Deleting your whole account is not available in the app yet/)).toBeInTheDocument();
    expect(screen.getByText(/Plain text, because the database must sort and filter them/)).toBeInTheDocument();
  });

  it("is linked from the footer", () => {
    render(
      <MemoryRouter>
        <Footer />
      </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: "Privacy notice and medical disclaimer" })).toHaveAttribute("href", "/privacy");
  });
});

describe("legal details", () => {
  // These fail until you edit frontend/src/config/legal.ts
  it("have been filled in with real details", () => {
    expect(OPERATOR).not.toBe("Your Full Name");
    expect(OPERATOR.trim().length).toBeGreaterThan(2);
    expect(CONTACT_EMAIL).toContain("@");
    expect(CONTACT_EMAIL).not.toMatch(/@example\.(com|org|net)$/);
    expect(LAST_UPDATED).toMatch(/20\d\d/);
  });
});