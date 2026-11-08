import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Page, Vital } from "../types";
import VitalList from "../VitalList";

function reading(overrides: Partial<Vital> = {}): Vital {
  return {
    id: "r1",
    type: "BLOOD_PRESSURE",
    value: 120,
    secondaryValue: 80,
    unit: "mm[Hg]",
    measuredAt: "2026-01-15T08:00:00Z",
    note: "after a walk",
    createdAt: "2026-01-15T08:00:00Z",
    updatedAt: "2026-01-15T08:00:00Z",
    ...overrides,
  };
}

function page(items: Vital[], pageNumber = 0, totalPages = 1): Page<Vital> {
  return { items, page: pageNumber, size: 10, totalItems: items.length, totalPages };
}

const noop = () => undefined;

describe("VitalList", () => {
  it("shows an empty message when there are no readings", () => {
    render(<VitalList data={page([])} onEdit={noop} onDelete={noop} onPage={noop} />);

    expect(screen.getByText("No readings yet.")).toBeInTheDocument();
  });

  it("shows the reading, its kind, its time and its note", () => {
    render(<VitalList data={page([reading()])} onEdit={noop} onDelete={noop} onPage={noop} />);

    expect(screen.getByText("120/80 mmHg")).toBeInTheDocument();
    expect(screen.getByText("Blood pressure")).toBeInTheDocument();
    expect(screen.getByText("after a walk")).toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });

  it("passes the reading to the edit and delete handlers", async () => {
    const user = userEvent.setup();
    const onEdit = vi.fn();
    const onDelete = vi.fn();
    const item = reading();
    render(<VitalList data={page([item])} onEdit={onEdit} onDelete={onDelete} onPage={noop} />);

    await user.click(screen.getByRole("button", { name: "Edit" }));
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(onEdit).toHaveBeenCalledWith(item);
    expect(onDelete).toHaveBeenCalledWith(item);
  });

  it("moves between pages and disables the buttons at the ends", async () => {
    const user = userEvent.setup();
    const onPage = vi.fn();
    const { rerender } = render(<VitalList data={page([reading()], 0, 3)} onEdit={noop} onDelete={noop} onPage={onPage} />);

    expect(screen.getByText("Page 1 of 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(onPage).toHaveBeenCalledWith(1);

    rerender(<VitalList data={page([reading()], 2, 3)} onEdit={noop} onDelete={noop} onPage={onPage} />);
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Previous" }));
    expect(onPage).toHaveBeenCalledWith(1);
  });

  it("shows a note as plain text and never as HTML", () => {
    const hostile = reading({ note: "<img src=x onerror=\"globalThis.__xss = true\">" });
    const { container } = render(<VitalList data={page([hostile])} onEdit={noop} onDelete={noop} onPage={noop} />);

    expect(screen.getByText(hostile.note as string)).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
    expect((globalThis as Record<string, unknown>).__xss).toBeUndefined();
  });
});