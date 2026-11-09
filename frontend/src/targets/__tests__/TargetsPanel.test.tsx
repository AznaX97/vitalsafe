import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { TargetRange } from "../types";
import TargetsPanel from "../TargetsPanel";

const fetchMock = vi.fn();

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function target(overrides: Partial<TargetRange> = {}): TargetRange {
  return {
    type: "HEART_RATE",
    unit: "/min",
    lowValue: 50,
    highValue: 100,
    lowSecondaryValue: null,
    highSecondaryValue: null,
    updatedAt: "2026-01-10T08:00:00Z",
    ...overrides,
  };
}

// A tiny fake of the targets endpoints
function installFakeBackend(initial: TargetRange[]) {
  const store = [...initial];
  const requests: { method: string; url: string; body: unknown }[] = [];
  fetchMock.mockImplementation(async (input: string, init?: RequestInit) => {
    const url = new URL(input, "http://localhost");
    const method = init?.method ?? "GET";
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    requests.push({ method, url: `${url.pathname}${url.search}`, body });

    if (url.pathname === "/api/v1/targets" && method === "GET") {
      return json(200, store);
    }
    const match = url.pathname.match(/^\/api\/v1\/targets\/(.+)$/);
    if (match && method === "PUT") {
      const saved = target({
        type: match[1] as TargetRange["type"],
        unit: (body.unit as string | undefined) ?? "/min",
        lowValue: body.lowValue ?? null,
        highValue: body.highValue ?? null,
        lowSecondaryValue: body.lowSecondaryValue ?? null,
        highSecondaryValue: body.highSecondaryValue ?? null,
      });
      const index = store.findIndex((item) => item.type === saved.type && item.unit === saved.unit);
      if (index >= 0) {
        store[index] = saved;
      } else {
        store.push(saved);
      }
      return json(200, saved);
    }
    if (match && method === "DELETE") {
      const unit = url.searchParams.get("unit");
      const index = store.findIndex((item) => item.type === match[1] && item.unit === unit);
      if (index < 0) {
        return json(404, { error: "No target range is set" });
      }
      store.splice(index, 1);
      return new Response(null, { status: 204 });
    }
    return json(404, { error: "Not found" });
  });
  return { store, requests };
}

function renderPanel() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <TargetsPanel />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("TargetsPanel", () => {
  it("says the numbers are the person's own and never a judgement", async () => {
    installFakeBackend([]);

    renderPanel();

    expect(await screen.findByText("No ranges set yet.")).toBeInTheDocument();
    expect(screen.getByText(/numbers you choose, ideally together with your doctor/)).toBeInTheDocument();
    expect(screen.getByText(/never decides what is healthy/)).toBeInTheDocument();
  });

  it("lists the ranges that are set", async () => {
    installFakeBackend([
      target(),
      target({ type: "BLOOD_PRESSURE", unit: "mm[Hg]", lowValue: 90, highValue: 130, lowSecondaryValue: 60, highSecondaryValue: 85 }),
    ]);

    renderPanel();

    expect(await screen.findByText("50 to 100 beats/min")).toBeInTheDocument();
    expect(screen.getByText("90 to 130 mmHg (systolic), 60 to 85 mmHg (diastolic)")).toBeInTheDocument();
  });

  it("sets a heart rate range with the default unit", async () => {
    const user = userEvent.setup();
    const { requests } = installFakeBackend([]);
    renderPanel();
    await screen.findByText("No ranges set yet.");

    await user.selectOptions(screen.getByLabelText("Range for"), "HEART_RATE");
    await user.type(screen.getByLabelText("Low"), "50");
    await user.type(screen.getByLabelText("High"), "100");
    await user.click(screen.getByRole("button", { name: "Save range" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Range saved.");
    const put = requests.find((request) => request.method === "PUT");
    expect(put?.url).toBe("/api/v1/targets/HEART_RATE");
    expect(put?.body).toEqual({ unit: "/min", lowValue: 50, highValue: 100 });
    expect(await screen.findByText("50 to 100 beats/min")).toBeInTheDocument();
    expect(screen.getByLabelText("Low")).toHaveValue(null);
  });

  it("asks for all four numbers for blood pressure and sends only the ones filled in", async () => {
    const user = userEvent.setup();
    const { requests } = installFakeBackend([]);
    renderPanel();
    await screen.findByText("No ranges set yet.");

    await user.type(screen.getByLabelText("Systolic high"), "130");
    await user.type(screen.getByLabelText("Diastolic high"), "85");
    await user.click(screen.getByRole("button", { name: "Save range" }));

    await screen.findByRole("status");
    const put = requests.find((request) => request.method === "PUT");
    expect(put?.url).toBe("/api/v1/targets/BLOOD_PRESSURE");
    expect(put?.body).toEqual({ unit: "mm[Hg]", highValue: 130, highSecondaryValue: 85 });
  });

  it("makes the person choose a glucose unit", async () => {
    const user = userEvent.setup();
    installFakeBackend([]);
    renderPanel();
    await screen.findByText("No ranges set yet.");

    await user.selectOptions(screen.getByLabelText("Range for"), "BLOOD_GLUCOSE");

    const unit = screen.getByLabelText("Range unit");
    expect(unit).toHaveValue("");
    expect(unit).toBeRequired();
    expect(screen.queryByLabelText("Diastolic high")).not.toBeInTheDocument();
  });

  it("removes a range, naming the unit in the request", async () => {
    const user = userEvent.setup();
    const { requests } = installFakeBackend([target()]);
    renderPanel();
    await screen.findByText("50 to 100 beats/min");

    await user.click(screen.getByRole("button", { name: "Remove range for Heart rate" }));

    expect(await screen.findByText("No ranges set yet.")).toBeInTheDocument();
    expect(requests.find((request) => request.method === "DELETE")?.url).toBe("/api/v1/targets/HEART_RATE?unit=%2Fmin");
    expect(screen.getByRole("status")).toHaveTextContent("Range removed.");
  });

  it("tells two glucose ranges apart by their unit", async () => {
    installFakeBackend([
      target({ type: "BLOOD_GLUCOSE", unit: "mg/dL", lowValue: 70, highValue: 140 }),
      target({ type: "BLOOD_GLUCOSE", unit: "mmol/L", lowValue: 4, highValue: 8 }),
    ]);

    renderPanel();

    expect(await screen.findByRole("button", { name: "Remove range for Blood glucose (mg/dL)" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove range for Blood glucose (mmol/L)" })).toBeInTheDocument();
  });

  it("shows the server's message when a range is refused", async () => {
    const user = userEvent.setup();
    installFakeBackend([]);
    renderPanel();
    await screen.findByText("No ranges set yet.");
    fetchMock.mockImplementationOnce(async () => json(200, []));
    fetchMock.mockImplementationOnce(async () =>
      json(400, { error: "Validation failed", fields: { highValue: "highValue must be between 20 and 250" } }),
    );

    await user.selectOptions(screen.getByLabelText("Range for"), "HEART_RATE");
    await user.type(screen.getByLabelText("High"), "900");
    await user.click(screen.getByRole("button", { name: "Save range" }));

    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText(/between 20 and 250/)).toBeInTheDocument();
  });
});