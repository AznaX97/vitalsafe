export const VITAL_TYPES = [
  "BLOOD_PRESSURE",
  "HEART_RATE",
  "BODY_WEIGHT",
  "BODY_TEMPERATURE",
  "OXYGEN_SATURATION",
  "BLOOD_GLUCOSE",
] as const;

export type VitalType = (typeof VITAL_TYPES)[number];

export interface Vital {
  id: string;
  type: VitalType;
  value: number;
  secondaryValue: number | null;
  unit: string;
  measuredAt: string;
  note: string | null;
  createdAt: string;
  updatedAt: string;
}

/** What the API accepts. Leaving the unit out lets the server use the default one (except for glucose). */
export interface VitalInput {
  type: VitalType;
  value: number;
  secondaryValue?: number;
  unit?: string;
  measuredAt: string;
  note?: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export const TYPE_LABELS: Record<VitalType, string> = {
  BLOOD_PRESSURE: "Blood pressure",
  HEART_RATE: "Heart rate",
  BODY_WEIGHT: "Body weight",
  BODY_TEMPERATURE: "Body temperature",
  OXYGEN_SATURATION: "Oxygen saturation",
  BLOOD_GLUCOSE: "Blood glucose",
};

/** UCUM unit codes, exactly as the API expects them. The first one is the default. */
export const UNITS: Record<VitalType, readonly string[]> = {
  BLOOD_PRESSURE: ["mm[Hg]"],
  HEART_RATE: ["/min"],
  BODY_WEIGHT: ["kg"],
  BODY_TEMPERATURE: ["Cel"],
  OXYGEN_SATURATION: ["%"],
  BLOOD_GLUCOSE: ["mg/dL", "mmol/L"],
};

export function formatUnit(unit: string): string {
  switch (unit) {
    case "mm[Hg]":
      return "mmHg";
    case "/min":
      return "beats/min";
    case "Cel":
      return "\u00B0C";
    default:
      return unit;
  }
}

export function formatReading(reading: Pick<Vital, "type" | "value" | "secondaryValue" | "unit">): string {
  const unit = formatUnit(reading.unit);
  if (reading.type === "BLOOD_PRESSURE" && reading.secondaryValue !== null) {
    return `${reading.value}/${reading.secondaryValue} ${unit}`;
  }
  return `${reading.value} ${unit}`;
}

/** The value a datetime-local input expects: local time, to the minute. */
export function toDateTimeLocal(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}