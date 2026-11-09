import { fmt } from "../lib/chartMath";
import { formatUnit, type VitalType } from "../vitals/types";
import type { RangeBounds } from "./types";

/** "120/80 mmHg" for blood pressure, "72 beats/min" for the rest. */
export function formatPair(type: VitalType, primary: number, secondary: number | null, unit: string | null): string {
  const suffix = unit ? ` ${formatUnit(unit)}` : "";
  if (type === "BLOOD_PRESSURE" && secondary !== null) {
    return `${fmt(primary)}/${fmt(secondary)}${suffix}`;
  }
  return `${fmt(primary)}${suffix}`;
}

export function describeBounds(low: number | null, high: number | null, unit: string): string | null {
  const suffix = unit ? ` ${formatUnit(unit)}` : "";
  if (low !== null && high !== null) {
    return `${fmt(low)} to ${fmt(high)}${suffix}`;
  }
  if (low !== null) {
    return `at least ${fmt(low)}${suffix}`;
  }
  if (high !== null) {
    return `at most ${fmt(high)}${suffix}`;
  }
  return null;
}

/** The range as words, always the range the person set. */
export function describeRange(type: VitalType, unit: string, range: RangeBounds): string {
  const first = describeBounds(range.lowValue, range.highValue, unit);
  if (type !== "BLOOD_PRESSURE") {
    return first ?? "";
  }
  const second = describeBounds(range.lowSecondaryValue, range.highSecondaryValue, unit);
  const parts: string[] = [];
  if (first) {
    parts.push(`${first} (systolic)`);
  }
  if (second) {
    parts.push(`${second} (diastolic)`);
  }
  return parts.join(", ");
}

export function plural(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
}