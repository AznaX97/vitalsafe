import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { BucketStat, TargetBreakdown } from "../types";
import TrendChart from "../TrendChart";

function bucket(start: string, count: number, min: number, max: number, average: number, secondary: BucketStat["secondary"] = null): BucketStat {
  return { start, count, primary: { min, max, average }, secondary };
}

const heartRate: BucketStat[] = [bucket("2026-01-12", 2, 60, 70, 65), bucket("2026-01-14", 1, 80, 80, 80)];

function range(overrides: Partial<TargetBreakdown>): TargetBreakdown {
  return { lowValue: null, highValue: null, lowSecondaryValue: null, highSecondaryValue: null, within: 0, above: 0, below: 0, mixed: 0, ...overrides };
}

describe("TrendChart", () => {
  it("draws one labelled point per period for a single series", () => {
    const { container } = render(<TrendChart type="HEART_RATE" unit="/min" bucket="DAY" buckets={heartRate} target={null} />);

    expect(screen.getByRole("img", { name: "Chart of the average heart rate per day" })).toBeInTheDocument();
    expect(container.querySelectorAll("circle")).toHaveLength(2);
    expect(container.querySelectorAll("polyline")).toHaveLength(1);
    expect(container.querySelector("rect")).toBeNull();
  });

  it("draws two series for blood pressure", () => {
    const pressure = [
      bucket("2026-01-12", 1, 118, 122, 120, { min: 78, max: 82, average: 80 }),
      bucket("2026-01-13", 1, 124, 126, 125, { min: 82, max: 84, average: 83 }),
    ];

    const { container } = render(<TrendChart type="BLOOD_PRESSURE" unit="mm[Hg]" bucket="WEEK" buckets={pressure} target={null} />);

    expect(screen.getByRole("img", { name: "Chart of the average blood pressure per week" })).toBeInTheDocument();
    expect(container.querySelectorAll("polyline")).toHaveLength(2);
    expect(container.querySelectorAll("circle")).toHaveLength(4);
    expect(screen.getByText("Systolic")).toBeInTheDocument();
    expect(screen.getByText("Diastolic")).toBeInTheDocument();
  });

  it("shades the range the person set when both bounds exist", () => {
    const { container } = render(
      <TrendChart type="HEART_RATE" unit="/min" bucket="DAY" buckets={heartRate} target={range({ lowValue: 55, highValue: 75 })} />,
    );

    const band = container.querySelector("rect.band-a");
    expect(band).not.toBeNull();
    expect(band?.querySelector("title")?.textContent).toBe("Range you set: 55 to 75 beats/min");
    expect(screen.getByText("Shaded area or dashed line: the range you set")).toBeInTheDocument();
  });

  it("draws a dashed line when only one bound exists", () => {
    const { container } = render(
      <TrendChart type="HEART_RATE" unit="/min" bucket="DAY" buckets={heartRate} target={range({ highValue: 75 })} />,
    );

    expect(container.querySelector("rect.band-a")).toBeNull();
    expect(container.querySelector("line.band-line")).not.toBeNull();
  });

  it("copes with a single period", () => {
    const { container } = render(
      <TrendChart type="HEART_RATE" unit="/min" bucket="MONTH" buckets={[bucket("2026-01-01", 3, 60, 80, 70)]} target={null} />,
    );

    expect(container.querySelectorAll("circle")).toHaveLength(1);
    expect(container.querySelector("polyline")?.getAttribute("points")).not.toContain("NaN");
  });

  it("lists the numbers in a table for people who cannot see the chart", () => {
    render(<TrendChart type="HEART_RATE" unit="/min" bucket="DAY" buckets={heartRate} target={null} />);

    const table = screen.getByRole("table", { hidden: true });
    const rows = within(table).getAllByRole("row", { hidden: true });
    expect(rows).toHaveLength(3);

    // Columns: period, readings, average, lowest, highest. Checked by position, because a period with a
    // single reading shows the same number three times.
    const first = within(rows[1]).getAllByRole("cell", { hidden: true }).map((cell) => cell.textContent);
    expect(first.slice(1)).toEqual(["2", "65 beats/min", "60 beats/min", "70 beats/min"]);

    const second = within(rows[2]).getAllByRole("cell", { hidden: true }).map((cell) => cell.textContent);
    expect(second.slice(1)).toEqual(["1", "80 beats/min", "80 beats/min", "80 beats/min"]);
  });

  it("renders nothing when there are no periods", () => {
    const { container } = render(<TrendChart type="HEART_RATE" unit="/min" bucket="DAY" buckets={[]} target={null} />);

    expect(container).toBeEmptyDOMElement();
  });
});