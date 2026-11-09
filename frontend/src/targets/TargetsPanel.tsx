import { useState, type FormEvent } from "react";
import { errorMessage } from "../api/client";
import { describeRange } from "../summary/describe";
import { TYPE_LABELS, UNITS, VITAL_TYPES, formatUnit, type VitalType } from "../vitals/types";
import type { TargetRange } from "./types";
import { useDeleteTarget, useSaveTarget, useTargets } from "./useTargets";

// Glucose has two possible units, so the person must choose. Other types have a single default.
function defaultUnit(type: VitalType): string {
  return UNITS[type].length === 1 ? UNITS[type][0] : "";
}

function removeLabel(range: TargetRange): string {
  const unit = UNITS[range.type].length > 1 ? ` (${formatUnit(range.unit)})` : "";
  return `Remove range for ${TYPE_LABELS[range.type]}${unit}`;
}

export default function TargetsPanel() {
  const targets = useTargets();
  const save = useSaveTarget();
  const remove = useDeleteTarget();

  const [type, setType] = useState<VitalType>("BLOOD_PRESSURE");
  const [unit, setUnit] = useState(defaultUnit("BLOOD_PRESSURE"));
  const [low, setLow] = useState("");
  const [high, setHigh] = useState("");
  const [lowSecondary, setLowSecondary] = useState("");
  const [highSecondary, setHighSecondary] = useState("");
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");

  const isPressure = type === "BLOOD_PRESSURE";
  const needsUnitChoice = UNITS[type].length > 1;

  function changeType(next: VitalType) {
    setType(next);
    setUnit(defaultUnit(next));
    if (next !== "BLOOD_PRESSURE") {
      setLowSecondary("");
      setHighSecondary("");
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setStatus("");
    const body: Record<string, string | number> = {};
    if (unit) {
      body.unit = unit;
    }
    const bounds: [string, string][] = [
      ["lowValue", low],
      ["highValue", high],
      ["lowSecondaryValue", lowSecondary],
      ["highSecondaryValue", highSecondary],
    ];
    for (const [name, text] of bounds) {
      if (text.trim() !== "") {
        body[name] = Number(text);
      }
    }
    try {
      await save.mutateAsync({ type, body });
      setStatus("Range saved.");
      setLow("");
      setHigh("");
      setLowSecondary("");
      setHighSecondary("");
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  async function handleRemove(range: TargetRange) {
    setError("");
    setStatus("");
    try {
      await remove.mutateAsync({ type: range.type, unit: range.unit });
      setStatus("Range removed.");
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  return (
    <section className="card">
      <h2>Target ranges</h2>
      <p className="hint">
        These are numbers you choose, ideally together with your doctor. VitalSafe only reports whether a reading is within,
        above or below your range. It never decides what is healthy.
      </p>

      {targets.isPending && <p className="muted">Loading...</p>}
      {targets.isError && (
        <p className="error" role="alert">
          {errorMessage(targets.error)}
        </p>
      )}
      {targets.data && targets.data.length === 0 && <p className="muted">No ranges set yet.</p>}
      {targets.data && targets.data.length > 0 && (
        <ul className="readings">
          {targets.data.map((range) => (
            <li key={`${range.type}-${range.unit}`}>
              <div>
                <strong>{TYPE_LABELS[range.type]}</strong>
                <p>{describeRange(range.type, range.unit, range)}</p>
              </div>
              <button type="button" className="danger" aria-label={removeLabel(range)} onClick={() => void handleRemove(range)}>
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}

      <form className="stack" onSubmit={handleSubmit}>
        <label>
          Range for
          <select value={type} onChange={(e) => changeType(e.target.value as VitalType)}>
            {VITAL_TYPES.map((option) => (
              <option key={option} value={option}>
                {TYPE_LABELS[option]}
              </option>
            ))}
          </select>
        </label>

        {needsUnitChoice && (
          <label>
            Range unit
            <select value={unit} onChange={(e) => setUnit(e.target.value)} required>
              <option value="">Choose a unit</option>
              {UNITS[type].map((option) => (
                <option key={option} value={option}>
                  {formatUnit(option)}
                </option>
              ))}
            </select>
          </label>
        )}

        <div className="row">
          <label>
            {isPressure ? "Systolic low" : "Low"}
            <input type="number" step="any" value={low} onChange={(e) => setLow(e.target.value)} />
          </label>
          <label>
            {isPressure ? "Systolic high" : "High"}
            <input type="number" step="any" value={high} onChange={(e) => setHigh(e.target.value)} />
          </label>
        </div>
        {isPressure && (
          <div className="row">
            <label>
              Diastolic low
              <input type="number" step="any" value={lowSecondary} onChange={(e) => setLowSecondary(e.target.value)} />
            </label>
            <label>
              Diastolic high
              <input type="number" step="any" value={highSecondary} onChange={(e) => setHighSecondary(e.target.value)} />
            </label>
          </div>
        )}
        <p className="hint">Fill in one or both. Setting a range again replaces the old one.</p>

        {status && <p role="status">{status}</p>}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" disabled={save.isPending}>
          {save.isPending ? "Saving..." : "Save range"}
        </button>
      </form>
    </section>
  );
}