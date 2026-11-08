import { useState, type FormEvent } from "react";
import { errorMessage } from "../api/client";
import { TYPE_LABELS, UNITS, VITAL_TYPES, formatUnit, toDateTimeLocal, type Vital, type VitalInput, type VitalType } from "./types";

interface Props {
  initial?: Vital | null;
  onSubmit: (input: VitalInput) => Promise<void>;
  onCancel?: () => void;
}

// Glucose has two possible units, so the person must choose. Other types have a single default.
function defaultUnit(type: VitalType): string {
  return UNITS[type].length === 1 ? UNITS[type][0] : "";
}

export default function VitalForm({ initial, onSubmit, onCancel }: Props) {
  const [type, setType] = useState<VitalType>(initial?.type ?? "BLOOD_PRESSURE");
  const [value, setValue] = useState(initial ? String(initial.value) : "");
  const [secondary, setSecondary] = useState(initial?.secondaryValue != null ? String(initial.secondaryValue) : "");
  const [unit, setUnit] = useState(initial?.unit ?? defaultUnit("BLOOD_PRESSURE"));
  const [measuredAt, setMeasuredAt] = useState(toDateTimeLocal(initial ? new Date(initial.measuredAt) : new Date()));
  const [note, setNote] = useState(initial?.note ?? "");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const isPressure = type === "BLOOD_PRESSURE";
  const needsUnitChoice = UNITS[type].length > 1;

  function changeType(next: VitalType) {
    setType(next);
    setUnit(defaultUnit(next));
    if (next !== "BLOOD_PRESSURE") {
      setSecondary("");
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError("");

    const when = new Date(measuredAt);
    if (Number.isNaN(when.getTime())) {
      setError("Enter a valid date and time.");
      return;
    }
    const input: VitalInput = {
      type,
      value: Number(value),
      measuredAt: when.toISOString(),
    };
    if (isPressure && secondary !== "") {
      input.secondaryValue = Number(secondary);
    }
    if (unit) {
      input.unit = unit;
    }
    if (note.trim()) {
      input.note = note.trim();
    }

    setBusy(true);
    try {
      await onSubmit(input);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card" onSubmit={handleSubmit}>
      <h2>{initial ? "Edit reading" : "Add a reading"}</h2>

      <label>
        Type
        <select value={type} onChange={(e) => changeType(e.target.value as VitalType)}>
          {VITAL_TYPES.map((option) => (
            <option key={option} value={option}>
              {TYPE_LABELS[option]}
            </option>
          ))}
        </select>
      </label>

      <div className="row">
        <label>
          {isPressure ? "Systolic" : "Value"}
          <input type="number" step="any" value={value} onChange={(e) => setValue(e.target.value)} required />
        </label>
        {isPressure && (
          <label>
            Diastolic
            <input type="number" step="any" value={secondary} onChange={(e) => setSecondary(e.target.value)} required />
          </label>
        )}
      </div>

      {needsUnitChoice ? (
        <label>
          Unit
          <select value={unit} onChange={(e) => setUnit(e.target.value)} required>
            <option value="">Choose a unit</option>
            {UNITS[type].map((option) => (
              <option key={option} value={option}>
                {formatUnit(option)}
              </option>
            ))}
          </select>
        </label>
      ) : (
        <p className="hint">Unit: {formatUnit(UNITS[type][0])}</p>
      )}

      <label>
        Date and time
        <input type="datetime-local" value={measuredAt} onChange={(e) => setMeasuredAt(e.target.value)} required />
      </label>

      <label>
        Note
        <textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={1000} rows={2} />
      </label>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <div className="row">
        <button type="submit" disabled={busy}>
          {busy ? "Saving..." : "Save reading"}
        </button>
        {initial && onCancel && (
          <button type="button" className="secondary" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}