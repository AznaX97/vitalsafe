import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SummaryPanel from "../SummaryPanel";
import type { Summary } from "../types";

const fetchMock = vi.fn();

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function summary(overrides: Partial<Summary> = {}): Summary {
  return {
    type: "HEART_RATE",
    unit: "/min",
    from: "2026-01-01T00:00:00Z",
    to: "2026-02-01T00:00:00Z",
    zone: "UTC",
    bucket: "DAY",
    count: 3,
    primary: { min: 60, max: 80, average: 70 },
    secondary: null,
    latest: { measuredAt: "2026-01-14T08:00:00Z", value: 80, secondaryValue: null },
    target: null,
    buckets: [
      { start: "2026-01-12", count: 1, primary: { min: 60, max: 60, average: 60 }, secondary: null },
      { start: "2026-01-14", count: 2, primary: { min: 70, max: 80, average: 75 }, secondary: null },
    ],
    disclaimer: "Statistics of the readings you logged. Not medical advice and not a diagnosis.",
    ...overrides,
  };
}

function lastRequest(): URL {
  const calls = fetchMock.mock.calls;
  return new URL(String(calls[calls.length - 1][0]), "http://localhost");
}

function renderPanel() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SummaryPanel />
    </QueryClientProvider>,
  );
}

const value = (label: string) => screen.getByText(label, { selector: "dt" }).nextElementSibling;

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("SummaryPanel", () => {
  it("asks for the last 30 days of blood pressure, grouped by day, in the browser's time zone", async () => {
    fetchMock.mockResolvedValue(json(200, summary({ type: "BLOOD_PRESSURE" })));

    renderPanel();

    // await screen.findByText("Readings");
    await screen.findByText("Readings", { selector: "dt" });
    const url = lastRequest();
    expect(url.pathname).toBe("/api/v1/vitals/summary");
    expect(url.searchParams.get("type")).toBe("BLOOD_PRESSURE");
    expect(url.searchParams.get("bucket")).toBe("DAY");
    expect(url.searchParams.get("zone")).toBeTruthy();
    const days = (Date.parse(url.searchParams.get("to") ?? "") - Date.parse(url.searchParams.get("from") ?? "")) / 86_400_000;
    expect(days).toBeCloseTo(30, 2);
  });

  it("shows the statistics and the chart", async () => {
    const user = userEvent.setup();
    // fetchMock.mockResolvedValue(json(200, summary()));
    fetchMock.mockImplementation(async () => json(200, summary()));
    renderPanel();
    // await screen.findByText("Readings");
    await screen.findByText("Readings", { selector: "dt" });

    await user.selectOptions(screen.getByLabelText("Summary of"), "HEART_RATE");

    expect(await screen.findByRole("img", { name: /average heart rate per day/i })).toBeInTheDocument();
    expect(value("Readings")).toHaveTextContent("3");
    expect(value("Average")).toHaveTextContent("70 beats/min");
    expect(value("Lowest")).toHaveTextContent("60 beats/min");
    expect(value("Highest")).toHaveTextContent("80 beats/min");
    expect(value("Latest")).toHaveTextContent("80 beats/min");
    expect(screen.getByText(/Not medical advice and not a diagnosis/)).toBeInTheDocument();
  });

  it("says so when there are no readings in the period", async () => {
    fetchMock.mockResolvedValue(json(200, summary({ count: 0, primary: null, latest: null, buckets: [] })));

    renderPanel();

    expect(await screen.findByText("No readings of this kind in this period.")).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("asks again when the kind, period or grouping changes", async () => {
    const user = userEvent.setup();
    // fetchMock.mockResolvedValue(json(200, summary()));
    fetchMock.mockImplementation(async () => json(200, summary()));
    renderPanel();
    // await screen.findByText("Readings");
    await screen.findByText("Readings", { selector: "dt" });

    await user.selectOptions(screen.getByLabelText("Summary of"), "BODY_WEIGHT");
    // await screen.findByText("Readings");
    await screen.findByText("Readings", { selector: "dt" });
    expect(lastRequest().searchParams.get("type")).toBe("BODY_WEIGHT");

    await user.selectOptions(screen.getByLabelText("Period"), "90");
    // await screen.findByText("Readings");
    await screen.findByText("Readings", { selector: "dt" });
    const url = lastRequest();
    const days = (Date.parse(url.searchParams.get("to") ?? "") - Date.parse(url.searchParams.get("from") ?? "")) / 86_400_000;
    expect(days).toBeCloseTo(90, 2);

    await user.selectOptions(screen.getByLabelText("Group by"), "WEEK");
    // await screen.findByText("Readings");
    await screen.findByText("Readings", { selector: "dt" });
    expect(lastRequest().searchParams.get("bucket")).toBe("WEEK");
  });

  it("offers a unit choice only for glucose, and sends it", async () => {
    const user = userEvent.setup();
    // fetchMock.mockResolvedValue(json(200, summary()));
    fetchMock.mockImplementation(async () => json(200, summary()));
    renderPanel();
    // await screen.findByText("Readings");
    await screen.findByText("Readings", { selector: "dt" });
    expect(screen.queryByLabelText("Summary unit")).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Summary of"), "BLOOD_GLUCOSE");
    await user.selectOptions(await screen.findByLabelText("Summary unit"), "mmol/L");

    // await screen.findByText("Readings");
    await screen.findByText("Readings", { selector: "dt" });
    expect(lastRequest().searchParams.get("unit")).toBe("mmol/L");
  });

  it("shows the range the person set and how many readings fell within, above and below it", async () => {
    fetchMock.mockResolvedValue(
      json(
        200,
        summary({
          target: { lowValue: 50, highValue: 100, lowSecondaryValue: null, highSecondaryValue: null, within: 5, above: 2, below: 1, mixed: 0 },
        }),
      ),
    );

    renderPanel();

    // await screen.findByText("Readings");
    await screen.findByText("Readings", { selector: "dt" });
    expect(value("Range you set")).toHaveTextContent("50 to 100 beats/min");
    expect(value("Within your range")).toHaveTextContent("5");
    expect(value("Above your range")).toHaveTextContent("2");
    expect(value("Below your range")).toHaveTextContent("1");
    expect(screen.queryByText(/Mixed/)).not.toBeInTheDocument();
  });

  it("only mentions mixed readings when there are some", async () => {
    fetchMock.mockResolvedValue(
      json(
        200,
        summary({
          target: { lowValue: 90, highValue: 130, lowSecondaryValue: 60, highSecondaryValue: 85, within: 0, above: 0, below: 0, mixed: 2 },
        }),
      ),
    );

    renderPanel();

    // await screen.findByText("Readings");
    await screen.findByText("Readings", { selector: "dt" });
    expect(value("Mixed (one part above, another below)")).toHaveTextContent("2");
  });

  it("suggests setting a range when there is none", async () => {
    fetchMock.mockResolvedValue(json(200, summary({ target: null })));

    renderPanel();

    expect(await screen.findByText(/No range is set for this kind of reading/)).toBeInTheDocument();
  });

  it("explains blood pressure statistics", async () => {
    fetchMock.mockResolvedValue(
      json(200, summary({ type: "BLOOD_PRESSURE", unit: "mm[Hg]", secondary: { min: 78, max: 90, average: 84 } })),
    );

    renderPanel();

    // await screen.findByText("Readings");
    await screen.findByText("Readings", { selector: "dt" });
    expect(value("Average")).toHaveTextContent("70/84 mmHg");
    expect(screen.getByText(/worked out separately for the top and the bottom number/)).toBeInTheDocument();
  });

  it("shows the server's message when readings use more than one unit", async () => {
    fetchMock.mockResolvedValue(
      json(400, { error: "Validation failed", fields: { unit: "Readings use more than one unit (mg/dL, mmol/L). Choose one with the unit parameter." } }),
    );

    renderPanel();

    expect(await screen.findByRole("alert")).toHaveTextContent("more than one unit");
  });

  it("shows the server's message when the period holds too many readings", async () => {
    fetchMock.mockResolvedValue(json(413, { error: "Too many readings in this range. Use a shorter range." }));

    renderPanel();

    expect(await screen.findByRole("alert")).toHaveTextContent("Use a shorter range");
  });
});