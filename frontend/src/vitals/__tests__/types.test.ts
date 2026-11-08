import { describe, expect, it } from "vitest";
import { UNITS, VITAL_TYPES, formatReading, formatUnit, toDateTimeLocal } from "../types";

describe("formatReading", () => {
  it("shows blood pressure as systolic over diastolic", () => {
    expect(formatReading({ type: "BLOOD_PRESSURE", value: 120, secondaryValue: 80, unit: "mm[Hg]" })).toBe("120/80 mmHg");
  });

  it("shows other readings with a readable unit", () => {
    expect(formatReading({ type: "HEART_RATE", value: 72, secondaryValue: null, unit: "/min" })).toBe("72 beats/min");
    expect(formatReading({ type: "BODY_TEMPERATURE", value: 36.6, secondaryValue: null, unit: "Cel" })).toBe("36.6 \u00B0C");
    expect(formatReading({ type: "BLOOD_GLUCOSE", value: 5.5, secondaryValue: null, unit: "mmol/L" })).toBe("5.5 mmol/L");
  });

  it("falls back to the plain unit code when it has no friendlier form", () => {
    expect(formatUnit("kg")).toBe("kg");
    expect(formatUnit("%")).toBe("%");
  });
});

describe("units", () => {
  it("lists a unit for every type, and only glucose offers a choice", () => {
    for (const type of VITAL_TYPES) {
      expect(UNITS[type].length).toBeGreaterThan(0);
    }
    expect(VITAL_TYPES.filter((type) => UNITS[type].length > 1)).toEqual(["BLOOD_GLUCOSE"]);
  });
});

describe("toDateTimeLocal", () => {
  it("writes local time to the minute with zero padding", () => {
    expect(toDateTimeLocal(new Date(2026, 0, 5, 7, 3, 59))).toBe("2026-01-05T07:03");
    expect(toDateTimeLocal(new Date(2026, 10, 15, 18, 45))).toBe("2026-11-15T18:45");
  });
});