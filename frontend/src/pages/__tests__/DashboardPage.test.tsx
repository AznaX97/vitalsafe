import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth, type AuthState } from "../../auth/useAuth";
import type { Vital } from "../../vitals/types";
import DashboardPage from "../DashboardPage";

vi.mock("../../auth/useAuth", () => ({ useAuth: vi.fn() }));
vi.mock("../../summary/SummaryPanel", () => ({ default: () => null }));vi.mock("../../targets/TargetsPanel", () => ({ default: () => null }));vi.mock("../../data/DataToolsPanel", () => ({ default: () => null }));

const fetchMock = vi.fn();

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function reading(id: number, overrides: Partial<Vital> = {}): Vital {
  return {
    id: `id-${id}`,
    type: "HEART_RATE",
    value: 50 + id,
    secondaryValue: null,
    unit: "/min",
    measuredAt: `2026-01-${String(id).padStart(2, "0")}T08:00:00Z`,
    note: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

// A tiny fake of the backend's vitals endpoints, so the real client and hooks run against it
function installFakeBackend(initial: Vital[]) {
  const store = [...initial];
  const requests: string[] = [];
  fetchMock.mockImplementation(async (input: string, init?: RequestInit) => {
    const url = new URL(input, "http://localhost");
    const method = init?.method ?? "GET";
    requests.push(`${method} ${url.pathname}${url.search}`);

    if (url.pathname === "/api/v1/vitals" && method === "GET") {
      const type = url.searchParams.get("type");
      const page = Number(url.searchParams.get("page") ?? "0");
      const size = Number(url.searchParams.get("size") ?? "20");
      const matching = store
        .filter((vital) => !type || vital.type === type)
        .sort((a, b) => b.measuredAt.localeCompare(a.measuredAt));
      return json(200, {
        items: matching.slice(page * size, page * size + size),
        page,
        size,
        totalItems: matching.length,
        totalPages: Math.ceil(matching.length / size),
      });
    }
    if (url.pathname === "/api/v1/vitals" && method === "POST") {
      const body = JSON.parse(String(init?.body)) as Partial<Vital>;
      const created = reading(900 + store.length, {
        type: body.type,
        value: body.value,
        secondaryValue: body.secondaryValue ?? null,
        unit: body.unit ?? "/min",
        measuredAt: body.measuredAt,
        note: body.note ?? null,
      });
      store.push(created);
      return json(201, created);
    }
    const match = url.pathname.match(/^\/api\/v1\/vitals\/(.+)$/);
    if (match && method === "DELETE") {
      const index = store.findIndex((vital) => vital.id === match[1]);
      if (index < 0) {
        return json(404, { error: "Reading not found" });
      }
      store.splice(index, 1);
      return new Response(null, { status: 204 });
    }
    if (match && method === "PUT") {
      const index = store.findIndex((vital) => vital.id === match[1]);
      const body = JSON.parse(String(init?.body)) as Partial<Vital>;
      store[index] = { ...store[index], ...body, secondaryValue: body.secondaryValue ?? null, note: body.note ?? null };
      return json(200, store[index]);
    }
    return json(404, { error: "Not found" });
  });
  return { store, requests };
}

function renderDashboard() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <DashboardPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  vi.mocked(useAuth).mockReturnValue({
    email: "me@example.com",
    logout: vi.fn().mockResolvedValue(undefined),
  } as unknown as AuthState);
});

describe("DashboardPage", () => {
  it("loads and shows the person's readings, newest first", async () => {
    installFakeBackend([reading(3), reading(5), reading(4)]);

    renderDashboard();

    expect(await screen.findByText("55 beats/min")).toBeInTheDocument();
    const values = screen.getAllByText(/beats\/min$/).map((node) => node.textContent);
    expect(values).toEqual(["55 beats/min", "54 beats/min", "53 beats/min"]);
    expect(screen.getByText("me@example.com")).toBeInTheDocument();
  });

  it("shows an empty message when there are no readings", async () => {
    installFakeBackend([]);

    renderDashboard();

    expect(await screen.findByText("No readings yet.")).toBeInTheDocument();
  });

  it("shows an error when loading fails", async () => {
    fetchMock.mockResolvedValue(json(500, { error: "Something went wrong" }));

    renderDashboard();

    expect(await screen.findByRole("alert")).toHaveTextContent("Something went wrong");
  });

  it("filters by type and goes back to the first page", async () => {
    const user = userEvent.setup();
    const { requests } = installFakeBackend([
      reading(1),
      reading(2, { type: "BODY_WEIGHT", value: 70, unit: "kg" }),
    ]);
    renderDashboard();
    await screen.findByText("70 kg");

    await user.selectOptions(screen.getByLabelText("Show"), "BODY_WEIGHT");

    await waitFor(() => expect(screen.queryByText("51 beats/min")).not.toBeInTheDocument());
    expect(screen.getByText("70 kg")).toBeInTheDocument();
    expect(requests.some((request) => request.includes("type=BODY_WEIGHT") && request.includes("page=0"))).toBe(true);
  });

  it("pages through a long list", async () => {
    const user = userEvent.setup();
    const many = Array.from({ length: 12 }, (_, index) => reading(index + 1));
    installFakeBackend(many);
    renderDashboard();

    expect(await screen.findByText("Page 1 of 2")).toBeInTheDocument();
    expect(screen.getAllByText(/beats\/min$/)).toHaveLength(10);

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText("Page 2 of 2")).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText(/beats\/min$/)).toHaveLength(2));
  });

  it("adds a reading and shows it in the list", async () => {
    const user = userEvent.setup();
    const { requests } = installFakeBackend([]);
    renderDashboard();
    await screen.findByText("No readings yet.");

    await user.selectOptions(screen.getByLabelText("Type"), "HEART_RATE");
    await user.type(screen.getByLabelText("Value"), "70");
    fireEvent.change(screen.getByLabelText("Date and time"), { target: { value: "2026-01-15T08:30" } });
    await user.click(screen.getByRole("button", { name: "Save reading" }));

    expect(await screen.findByText("70 beats/min")).toBeInTheDocument();
    expect(requests).toContain("POST /api/v1/vitals");
    // The form starts empty again for the next reading
    expect(screen.getByLabelText("Systolic")).toHaveValue(null);
    expect(screen.getByLabelText("Diastolic")).toHaveValue(null);
  });

  it("edits a reading", async () => {
    const user = userEvent.setup();
    const { requests } = installFakeBackend([reading(1)]);
    renderDashboard();
    const item = (await screen.findByText("51 beats/min")).closest("li") as HTMLElement;

    await user.click(within(item).getByRole("button", { name: "Edit" }));
    expect(screen.getByRole("heading", { name: "Edit reading" })).toBeInTheDocument();
    await user.clear(screen.getByLabelText("Value"));
    await user.type(screen.getByLabelText("Value"), "77");
    await user.click(screen.getByRole("button", { name: "Save reading" }));

    expect(await screen.findByText("77 beats/min")).toBeInTheDocument();
    expect(requests).toContain("PUT /api/v1/vitals/id-1");
    expect(screen.getByRole("heading", { name: "Add a reading" })).toBeInTheDocument();
  });

  it("deletes a reading after confirmation", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const { requests } = installFakeBackend([reading(1), reading(2)]);
    renderDashboard();
    const item = (await screen.findByText("51 beats/min")).closest("li") as HTMLElement;

    await user.click(within(item).getByRole("button", { name: "Delete" }));

    await waitFor(() => expect(screen.queryByText("51 beats/min")).not.toBeInTheDocument());
    expect(requests).toContain("DELETE /api/v1/vitals/id-1");
    expect(screen.getByText("52 beats/min")).toBeInTheDocument();
  });

  it("does nothing when the delete confirmation is declined", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const { requests } = installFakeBackend([reading(1)]);
    renderDashboard();
    const item = (await screen.findByText("51 beats/min")).closest("li") as HTMLElement;

    await user.click(within(item).getByRole("button", { name: "Delete" }));

    expect(requests.some((request) => request.startsWith("DELETE"))).toBe(false);
    expect(screen.getByText("51 beats/min")).toBeInTheDocument();
  });
});