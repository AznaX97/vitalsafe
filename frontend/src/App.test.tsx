import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "./App";

describe("App", () => {
  it("shows the name and the disclaimer", () => {
    render(<App />);

    expect(screen.getByRole("heading", { name: "VitalSafe" })).toBeInTheDocument();
    expect(screen.getByText(/not medical advice/i)).toBeInTheDocument();
  });
});