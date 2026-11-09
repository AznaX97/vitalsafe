import { useState } from "react";
import { apiDownload, errorMessage } from "../api/client";
import { saveBlob } from "../lib/download";
import { plural } from "../summary/describe";
import { useRefreshReadings } from "../vitals/useVitals";
import FileImport from "./FileImport";

const MAX_CSV_BYTES = 512 * 1024;
const MAX_FHIR_BYTES = 100 * 1024;

export default function DataToolsPanel() {
  const refresh = useRefreshReadings();
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function download(path: string, what: string) {
    setMessage("");
    setError("");
    try {
      const { blob, filename } = await apiDownload(path);
      saveBlob(blob, filename);
      setMessage(`${what} downloaded.`);
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  return (
    <section className="card">
      <h2>Your data</h2>
      <p className="hint">
        Download your readings, or bring readings in from a file. Importing the same CSV file twice does not create
        duplicates.
      </p>

      <div className="row">
        <button type="button" className="secondary" onClick={() => void download("/vitals/export", "CSV file")}>
          Download CSV
        </button>
        <button type="button" className="secondary" onClick={() => void download("/fhir/export", "FHIR file")}>
          Download FHIR
        </button>
      </div>
      {message && <p role="status">{message}</p>}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <FileImport
        label="CSV file"
        buttonLabel="Import CSV"
        accept=".csv,text/csv"
        path="/vitals/import"
        contentType="text/csv"
        maxBytes={MAX_CSV_BYTES}
        describe={(result) => {
          const { imported, skippedDuplicates } = result as { imported: number; skippedDuplicates: number };
          const skipped = skippedDuplicates > 0 ? ` Skipped ${plural(skippedDuplicates, "duplicate")}.` : "";
          return `Imported ${plural(imported, "reading")}.${skipped}`;
        }}
        onImported={() => void refresh()}
      />

      <FileImport
        label="FHIR file"
        buttonLabel="Import FHIR Observation"
        accept=".json,application/json,application/fhir+json"
        path="/vitals/fhir"
        contentType="application/fhir+json"
        maxBytes={MAX_FHIR_BYTES}
        describe={() => "Imported 1 reading."}
        onImported={() => void refresh()}
      />
    </section>
  );
}