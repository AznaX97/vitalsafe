import { useState, type FormEvent } from "react";
import { ApiError, api, errorMessage } from "../api/client";

interface Props {
  label: string;
  buttonLabel: string;
  accept: string;
  path: string;
  contentType: string;
  maxBytes: number;
  describe: (result: unknown) => string;
  onImported: () => void;
}

interface RowError {
  row: number;
  text: string;
}

function readText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error("The file could not be read"));
    reader.readAsText(file);
  });
}

// The backend lists up to 50 bad rows. Showing the first ten keeps the page readable.
function rowErrorsOf(error: unknown): RowError[] {
  if (!(error instanceof ApiError)) {
    return [];
  }
  const body = error.body as { rows?: { row: number; fields: Record<string, string> }[] } | undefined;
  return (body?.rows ?? []).slice(0, 10).map((item) => ({
    row: item.row,
    text: Object.entries(item.fields)
      .map(([field, message]) => `${field}: ${message}`)
      .join("; "),
  }));
}

export default function FileImport({ label, buttonLabel, accept, path, contentType, maxBytes, describe, onImported }: Props) {
  const [file, setFile] = useState<File | null>(null);
  const [inputKey, setInputKey] = useState(0);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [rows, setRows] = useState<RowError[]>([]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setMessage("");
    setError("");
    setRows([]);
    if (!file) {
      return;
    }
    if (file.size > maxBytes) {
      setError(`The file is too large (${Math.round(maxBytes / 1024)} KB maximum).`);
      return;
    }
    setBusy(true);
    try {
      const text = await readText(file);
      const result = await api<unknown>(path, { method: "POST", body: text, contentType });
      setMessage(describe(result));
      setFile(null);
      setInputKey((key) => key + 1);   // empties the file chooser
      onImported();
    } catch (caught) {
      setError(errorMessage(caught));
      setRows(rowErrorsOf(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="stack" onSubmit={handleSubmit}>
      <label>
        {label}
        <input key={inputKey} type="file" accept={accept} onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
      </label>
      <button type="submit" className="secondary" disabled={!file || busy}>
        {busy ? "Importing..." : buttonLabel}
      </button>

      {message && <p role="status">{message}</p>}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {rows.length > 0 && (
        <ul className="row-errors">
          {rows.map((item) => (
            <li key={item.row}>
              Row {item.row}: {item.text}
            </li>
          ))}
        </ul>
      )}
    </form>
  );
}