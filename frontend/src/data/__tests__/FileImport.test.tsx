import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FileImport from "../FileImport";

const fetchMock = vi.fn();

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function renderImport(onImported = vi.fn()) {
  render(
    <FileImport
      label="CSV file"
      buttonLabel="Import CSV"
      accept=".csv,text/csv"
      path="/vitals/import"
      contentType="text/csv"
      maxBytes={1000}
      describe={(result) => `Imported ${(result as { imported: number }).imported}.`}
      onImported={onImported}
    />,
  );
  return onImported;
}

const csvFile = (text: string) => new File([text], "readings.csv", { type: "text/csv" });

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("FileImport", () => {
  it("keeps the button off until a file is chosen", () => {
    renderImport();

    expect(screen.getByRole("button", { name: "Import CSV" })).toBeDisabled();
  });

  it("sends the file's text with the right content type and reports the result", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(json(200, { imported: 2, skippedDuplicates: 0 }));
    const onImported = renderImport();

    await user.upload(screen.getByLabelText("CSV file"), csvFile("type,value\nHEART_RATE,70\n"));
    await user.click(screen.getByRole("button", { name: "Import CSV" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Imported 2.");
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/vitals/import");
    expect(init.method).toBe("POST");
    expect(init.body).toBe("type,value\nHEART_RATE,70\n");
    expect((init.headers as Record<string, string>)["Content-Type"]).toBe("text/csv");
    expect(onImported).toHaveBeenCalledTimes(1);
  });

  it("lists the rows the server refused, and says nothing was imported", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(
      json(400, {
        error: "Import failed: nothing was imported",
        rowErrors: 2,
        rows: [
          { row: 2, fields: { value: "value must be between 20 and 250" } },
          { row: 5, fields: { type: "type is required", measuredAt: "measuredAt is required" } },
        ],
      }),
    );
    const onImported = renderImport();

    await user.upload(screen.getByLabelText("CSV file"), csvFile("type,value\nHEART_RATE,5\n"));
    await user.click(screen.getByRole("button", { name: "Import CSV" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("nothing was imported");
    expect(screen.getByText("Row 2: value: value must be between 20 and 250")).toBeInTheDocument();
    expect(screen.getByText("Row 5: type: type is required; measuredAt: measuredAt is required")).toBeInTheDocument();
    expect(onImported).not.toHaveBeenCalled();
  });

  it("shows only the first ten bad rows", async () => {
    const user = userEvent.setup();
    const rows = Array.from({ length: 25 }, (_, index) => ({ row: index + 2, fields: { value: "bad" } }));
    fetchMock.mockResolvedValue(json(400, { error: "Import failed: nothing was imported", rowErrors: 25, rows }));
    renderImport();

    await user.upload(screen.getByLabelText("CSV file"), csvFile("x"));
    await user.click(screen.getByRole("button", { name: "Import CSV" }));

    await screen.findByRole("alert");
    expect(screen.getAllByText(/^Row \d+:/)).toHaveLength(10);
  });

  it("refuses a file over the size limit without sending it", async () => {
    const user = userEvent.setup();
    renderImport();

    await user.upload(screen.getByLabelText("CSV file"), csvFile("x".repeat(1001)));
    await user.click(screen.getByRole("button", { name: "Import CSV" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The file is too large");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("empties the file chooser after a successful import", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(json(200, { imported: 1, skippedDuplicates: 0 }));
    renderImport();

    await user.upload(screen.getByLabelText("CSV file"), csvFile("x"));
    await user.click(screen.getByRole("button", { name: "Import CSV" }));

    await screen.findByRole("status");
    expect(screen.getByRole("button", { name: "Import CSV" })).toBeDisabled();
  });
});