import { describe, expect, it } from "vitest";
import { describeBounds, describeRange, formatPair, plural } from "../describe";

describe("formatPair", () => {
  it("shows blood pressure as two numbers", () => {
    expect(formatPair("BLOOD_PRESSURE", 120.4, 79.6, "mm[Hg]")).toBe("120.4/79.6 mmHg");
  });

  it("shows other readings as one number with a readable unit", () => {
    expect(formatPair("HEART_RATE", 70, null, "/min")).toBe("70 beats/min");
    expect(formatPair("BODY_WEIGHT", 72.5, null, "kg")).toBe("72.5 kg");
  });

  it("copes with a missing unit", () => {
    expect(formatPair("HEART_RATE", 70, null, null)).toBe("70");
  });
});

describe("describeBounds", () => {
  it("describes both, one, or no bounds", () => {
    expect(describeBounds(50, 100, "/min")).toBe("50 to 100 beats/min");
    expect(describeBounds(50, null, "/min")).toBe("at least 50 beats/min");
    expect(describeBounds(null, 100, "/min")).toBe("at most 100 beats/min");
    expect(describeBounds(null, null, "/min")).toBeNull();
  });
});

describe("describeRange", () => {
  it("labels both parts of a blood pressure range", () => {
    expect(
      describeRange("BLOOD_PRESSURE", "mm[Hg]", { lowValue: 90, highValue: 130, lowSecondaryValue: 60, highSecondaryValue: 85 }),
    ).toBe("90 to 130 mmHg (systolic), 60 to 85 mmHg (diastolic)");
  });

  it("leaves out the part that has no bounds", () => {
    expect(
      describeRange("BLOOD_PRESSURE", "mm[Hg]", { lowValue: null, highValue: 130, lowSecondaryValue: null, highSecondaryValue: null }),
    ).toBe("at most 130 mmHg (systolic)");
  });

  it("describes a simple range", () => {
    expect(
      describeRange("HEART_RATE", "/min", { lowValue: 50, highValue: 100, lowSecondaryValue: null, highSecondaryValue: null }),
    ).toBe("50 to 100 beats/min");
  });
});

describe("plural", () => {
  it("adds an s except for exactly one", () => {
    expect(plural(1, "reading")).toBe("1 reading");
    expect(plural(0, "reading")).toBe("0 readings");
    expect(plural(2, "duplicate")).toBe("2 duplicates");
  });
});