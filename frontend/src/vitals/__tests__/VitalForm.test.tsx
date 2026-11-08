import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "../../api/client";
import type { Vital } from "../types";
import VitalForm from "../VitalForm";

const WHEN = "2026-01-15T08:30";
const WHEN_ISO = new Date(WHEN).toISOString();

function stored(overrides: Partial<Vital> = {}): Vital {
  return {
    id: "abc",
    type: "HEART_RATE",
    value: 72,
    secondaryValue: null,
    unit: "/min",
    measuredAt: "2026-01-10T08:00:00Z",
    note: "resting",
    createdAt: "2026-01-10T08:00:00Z",
    updatedAt: "2026-01-10T08:00:00Z",
    ...overrides,
  };
}

describe("VitalForm", () => {
  it("sends a blood pressure reading with both parts and the default unit", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<VitalForm onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText("Systolic"), "120");
    await user.type(screen.getByLabelText("Diastolic"), "80");
    fireEvent.change(screen.getByLabelText("Date and time"), { target: { value: WHEN } });
    await user.type(screen.getByLabelText("Note"), "  after a walk  ");
    await user.click(screen.getByRole("button", { name: "Save reading" }));

    expect(onSubmit).toHaveBeenCalledWith({
      type: "BLOOD_PRESSURE",
      value: 120,
      secondaryValue: 80,
      unit: "mm[Hg]",
      measuredAt: WHEN_ISO,
      note: "after a walk",
    });
  });

  it("only asks for the second number when the type is blood pressure", async () => {
    const user = userEvent.setup();
    render(<VitalForm onSubmit={vi.fn()} />);
    expect(screen.getByLabelText("Diastolic")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Type"), "HEART_RATE");

    expect(screen.queryByLabelText("Diastolic")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Value")).toBeInTheDocument();
    expect(screen.getByText("Unit: beats/min")).toBeInTheDocument();
  });

  it("sends a heart rate without a second value, and leaves out an empty note", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<VitalForm onSubmit={onSubmit} />);

    await user.selectOptions(screen.getByLabelText("Type"), "HEART_RATE");
    await user.type(screen.getByLabelText("Value"), "68");
    fireEvent.change(screen.getByLabelText("Date and time"), { target: { value: WHEN } });
    await user.click(screen.getByRole("button", { name: "Save reading" }));

    expect(onSubmit).toHaveBeenCalledWith({ type: "HEART_RATE", value: 68, unit: "/min", measuredAt: WHEN_ISO });
  });

  it("makes the person choose a glucose unit and then sends it", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<VitalForm onSubmit={onSubmit} />);

    await user.selectOptions(screen.getByLabelText("Type"), "BLOOD_GLUCOSE");

    const unitSelect = screen.getByLabelText("Unit");
    expect(unitSelect).toHaveValue("");
    expect(unitSelect).toBeRequired();

    await user.type(screen.getByLabelText("Value"), "5.5");
    await user.selectOptions(unitSelect, "mmol/L");
    fireEvent.change(screen.getByLabelText("Date and time"), { target: { value: WHEN } });
    await user.click(screen.getByRole("button", { name: "Save reading" }));

    expect(onSubmit).toHaveBeenCalledWith({ type: "BLOOD_GLUCOSE", value: 5.5, unit: "mmol/L", measuredAt: WHEN_ISO });
  });

  it("clears the second value when the type changes away from blood pressure", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<VitalForm onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText("Systolic"), "120");
    await user.type(screen.getByLabelText("Diastolic"), "80");
    await user.selectOptions(screen.getByLabelText("Type"), "BODY_WEIGHT");
    await user.clear(screen.getByLabelText("Value"));
    await user.type(screen.getByLabelText("Value"), "70.5");
    fireEvent.change(screen.getByLabelText("Date and time"), { target: { value: WHEN } });
    await user.click(screen.getByRole("button", { name: "Save reading" }));

    expect(onSubmit).toHaveBeenCalledWith({ type: "BODY_WEIGHT", value: 70.5, unit: "kg", measuredAt: WHEN_ISO });
  });

  it("shows the server's field messages when the reading is refused", async () => {
    const user = userEvent.setup();
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(400, "Validation failed", { value: "value must be between 20 and 250" }));
    render(<VitalForm onSubmit={onSubmit} />);

    await user.selectOptions(screen.getByLabelText("Type"), "HEART_RATE");
    await user.type(screen.getByLabelText("Value"), "5");
    fireEvent.change(screen.getByLabelText("Date and time"), { target: { value: WHEN } });
    await user.click(screen.getByRole("button", { name: "Save reading" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("value must be between 20 and 250");
  });

  it("pre-fills an existing reading when editing and can be cancelled", async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    render(<VitalForm initial={stored()} onSubmit={vi.fn()} onCancel={onCancel} />);

    expect(screen.getByRole("heading", { name: "Edit reading" })).toBeInTheDocument();
    expect(screen.getByLabelText("Type")).toHaveValue("HEART_RATE");
    expect(screen.getByLabelText("Value")).toHaveValue(72);
    expect(screen.getByLabelText("Note")).toHaveValue("resting");

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("has no cancel button when adding", () => {
    render(<VitalForm onSubmit={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.queryByRole("button", { name: "Cancel" })).not.toBeInTheDocument();
  });
});