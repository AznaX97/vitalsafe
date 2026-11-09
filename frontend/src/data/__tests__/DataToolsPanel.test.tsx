import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { saveBlob } from "../../lib/download";
import DataToolsPanel from "../DataToolsPanel";

vi.mock("../../lib/download", () => ({ saveBlob: vi.fn() }));

const fetchMock = vi.fn();

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function renderPanel() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <DataToolsPanel />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("DataToolsPanel downloads", () => {
  it("downloads the CSV through the API client and saves it under the server's file name", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(
      new Response("type,value\n", { status: 200, headers: { "Content-Disposition": 'attachment; filename="vitalsafe-readings.csv"' } }),
    );
    renderPanel();

    await user.click(screen.getByRole("button", { name: "Download CSV" }));

    expect(await screen.findByRole("status")).toHaveTextContent("CSV file downloaded.");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/vitals/export");
    expect(saveBlob).toHaveBeenCalledTimes(1);
    expect(vi.mocked(saveBlob).mock.calls[0][1]).toBe("vitalsafe-readings.csv");
  });

  it("downloads the FHIR export", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(
      new Response("{}", { status: 200, headers: { "Content-Disposition": 'attachment; filename="vitalsafe-fhir-export.json"' } }),
    );
    renderPanel();

    await user.click(screen.getByRole("button", { name: "Download FHIR" }));

    await screen.findByRole("status");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/fhir/export");
    expect(vi.mocked(saveBlob).mock.calls[0][1]).toBe("vitalsafe-fhir-export.json");
  });

  it("shows the server's message when a download is refused", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(json(413, { error: "Too many readings for one export. Use from and to to export in parts." }));
    renderPanel();

    await user.click(screen.getByRole("button", { name: "Download CSV" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Too many readings");
    expect(saveBlob).not.toHaveBeenCalled();
  });
});

describe("DataToolsPanel imports", () => {
  it("reports how many readings were imported and how many duplicates were skipped", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(json(200, { imported: 3, skippedDuplicates: 2 }));
    renderPanel();

    await user.upload(screen.getByLabelText("CSV file"), new File(["x"], "r.csv", { type: "text/csv" }));
    await user.click(screen.getByRole("button", { name: "Import CSV" }));

    expect(await screen.findByText("Imported 3 readings. Skipped 2 duplicates.")).toBeInTheDocument();
  });

  it("imports a FHIR Observation as application/fhir+json", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(json(201, { id: "abc" }));
    renderPanel();

    await user.upload(
      screen.getByLabelText("FHIR file"),
      new File(['{"resourceType":"Observation"}'], "obs.json", { type: "application/json" }),
    );
    await user.click(screen.getByRole("button", { name: "Import FHIR Observation" }));

    expect(await screen.findByText("Imported 1 reading.")).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/vitals/fhir");
    expect((init.headers as Record<string, string>)["Content-Type"]).toBe("application/fhir+json");
    expect(init.body).toBe('{"resourceType":"Observation"}');
  });
});