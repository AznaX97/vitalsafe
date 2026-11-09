import { describe, expect, it } from "vitest";
import { fmt, scale, ticks, xFor, yDomain } from "../chartMath";

describe("yDomain", () => {
  it("gives a small default range when there is no data", () => {
    expect(yDomain([])).toEqual({ min: 0, max: 1 });
  });

  it("never returns a flat range for a single value", () => {
    expect(yDomain([70])).toEqual({ min: 69, max: 71 });
    expect(yDomain([70, 70, 70])).toEqual({ min: 69, max: 71 });
  });

  it("adds eight percent of room above and below", () => {
    const domain = yDomain([60, 80]);

    expect(domain.min).toBeCloseTo(58.4);
    expect(domain.max).toBeCloseTo(81.6);
  });
});

describe("scale", () => {
  it("maps the ends and the middle of the domain onto the pixel range", () => {
    const domain = { min: 0, max: 100 };

    expect(scale(0, domain, 0, 200)).toBe(0);
    expect(scale(100, domain, 0, 200)).toBe(200);
    expect(scale(50, domain, 0, 200)).toBe(100);
  });

  it("flips the axis when the start is larger than the end, as in an SVG", () => {
    const domain = { min: 0, max: 100 };

    expect(scale(0, domain, 250, 10)).toBe(250);
    expect(scale(100, domain, 250, 10)).toBe(10);
  });
});

describe("xFor", () => {
  it("keeps uneven gaps uneven", () => {
    const first = Date.parse("2026-01-01T00:00:00Z");
    const last = Date.parse("2026-01-11T00:00:00Z");
    const day3 = Date.parse("2026-01-04T00:00:00Z");

    expect(xFor(first, first, last, 0, 100)).toBe(0);
    expect(xFor(last, first, last, 0, 100)).toBe(100);
    expect(xFor(day3, first, last, 0, 100)).toBeCloseTo(30);
  });

  it("centres a single point", () => {
    expect(xFor(5, 5, 5, 0, 100)).toBe(50);
  });
});

describe("ticks and fmt", () => {
  it("spreads ticks evenly across the domain", () => {
    expect(ticks({ min: 0, max: 4 })).toEqual([0, 1, 2, 3, 4]);
    expect(ticks({ min: 0, max: 10 }, 2)).toEqual([0, 5, 10]);
  });

  it("formats to one decimal place without a trailing zero", () => {
    expect(fmt(70)).toBe("70");
    expect(fmt(72.45)).toBe("72.5");
    expect(fmt(36.64)).toBe("36.6");
    expect(fmt(0.04)).toBe("0");
  });
});