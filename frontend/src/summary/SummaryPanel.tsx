import { useMemo, useState } from "react";
import { errorMessage } from "../api/client";
import { TYPE_LABELS, UNITS, VITAL_TYPES, formatUnit, type VitalType } from "../vitals/types";
import { describeRange, formatPair } from "./describe";
import TrendChart from "./TrendChart";
import type { Bucket } from "./types";
import { useSummary } from "./useSummary";

const PERIODS = [
  { days: 7, label: "Last 7 days" },
  { days: 30, label: "Last 30 days" },
  { days: 90, label: "Last 90 days" },
  { days: 365, label: "Last year" },
];

const BUCKETS: { value: Bucket; label: string }[] = [
  { value: "DAY", label: "Day" },
  { value: "WEEK", label: "Week" },
  { value: "MONTH", label: "Month" },
];

function browserZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  } catch {
    return "UTC";
  }
}

export default function SummaryPanel() {
  const [type, setType] = useState<VitalType>("BLOOD_PRESSURE");
  const [days, setDays] = useState(30);
  const [bucket, setBucket] = useState<Bucket>("DAY");
  const [unit, setUnit] = useState("");
  // const zone = useMemo(browserZone, []);
  const zone = useMemo(() => browserZone(), []);

  const query = useSummary({ type, unit, days, bucket, zone });
  const summary = query.data;
  const choices = UNITS[type];

  function changeType(next: VitalType) {
    setType(next);
    setUnit("");
  }

  return (
    <section className="card">
      <h2>Summary</h2>

      <div className="row">
        <label>
          Summary of
          <select value={type} onChange={(e) => changeType(e.target.value as VitalType)}>
            {VITAL_TYPES.map((option) => (
              <option key={option} value={option}>
                {TYPE_LABELS[option]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Period
          <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {PERIODS.map((option) => (
              <option key={option.days} value={option.days}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Group by
          <select value={bucket} onChange={(e) => setBucket(e.target.value as Bucket)}>
            {BUCKETS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        {choices.length > 1 && (
          <label>
            Summary unit
            <select value={unit} onChange={(e) => setUnit(e.target.value)}>
              <option value="">Any</option>
              {choices.map((option) => (
                <option key={option} value={option}>
                  {formatUnit(option)}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {query.isPending && <p className="muted">Loading...</p>}
      {query.isError && (
        <p className="error" role="alert">
          {errorMessage(query.error)}
        </p>
      )}

      {summary && summary.count === 0 && <p className="muted">No readings of this kind in this period.</p>}

      {summary && summary.count > 0 && summary.primary && (
        <>
          <dl className="facts">
            <div>
              <dt>Readings</dt>
              <dd>{summary.count}</dd>
            </div>
            {summary.latest && (
              <div>
                <dt>Latest</dt>
                <dd>
                  {formatPair(type, summary.latest.value, summary.latest.secondaryValue, summary.unit)} on{" "}
                  {new Date(summary.latest.measuredAt).toLocaleDateString()}
                </dd>
              </div>
            )}
            <div>
              <dt>Average</dt>
              <dd>{formatPair(type, summary.primary.average, summary.secondary?.average ?? null, summary.unit)}</dd>
            </div>
            <div>
              <dt>Lowest</dt>
              <dd>{formatPair(type, summary.primary.min, summary.secondary?.min ?? null, summary.unit)}</dd>
            </div>
            <div>
              <dt>Highest</dt>
              <dd>{formatPair(type, summary.primary.max, summary.secondary?.max ?? null, summary.unit)}</dd>
            </div>
            {summary.target && (
              <>
                <div>
                  <dt>Range you set</dt>
                  <dd>{describeRange(type, summary.unit ?? "", summary.target)}</dd>
                </div>
                <div>
                  <dt>Within your range</dt>
                  <dd>{summary.target.within}</dd>
                </div>
                <div>
                  <dt>Above your range</dt>
                  <dd>{summary.target.above}</dd>
                </div>
                <div>
                  <dt>Below your range</dt>
                  <dd>{summary.target.below}</dd>
                </div>
                {summary.target.mixed > 0 && (
                  <div>
                    <dt>Mixed (one part above, another below)</dt>
                    <dd>{summary.target.mixed}</dd>
                  </div>
                )}
              </>
            )}
          </dl>

          {type === "BLOOD_PRESSURE" && (
            <p className="hint">Blood pressure statistics are worked out separately for the top and the bottom number.</p>
          )}
          {!summary.target && (
            <p className="hint">No range is set for this kind of reading. You can set your own under Target ranges.</p>
          )}

          <TrendChart type={type} unit={summary.unit} bucket={bucket} buckets={summary.buckets} target={summary.target} />
        </>
      )}

      <p className="hint">Statistics of the readings you logged. Not medical advice and not a diagnosis.</p>
    </section>
  );
}