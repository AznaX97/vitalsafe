export interface Domain {
  min: number;
  max: number;
}

/** The vertical range of a chart: the data with a little room, and never a zero-height range. */
export function yDomain(values: number[]): Domain {
  if (values.length === 0) {
    return { min: 0, max: 1 };
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  if (min === max) {
    return { min: min - 1, max: max + 1 };
  }
  const padding = (max - min) * 0.08;
  return { min: min - padding, max: max + padding };
}

/** Maps a value inside the domain onto a pixel range. Pass start greater than end to flip the axis. */
export function scale(value: number, domain: Domain, start: number, end: number): number {
  return start + ((value - domain.min) / (domain.max - domain.min)) * (end - start);
}

/** Places a point in time between the first and last time shown. Gaps stay truthful. */
export function xFor(time: number, first: number, last: number, start: number, end: number): number {
  return last === first ? (start + end) / 2 : start + ((time - first) / (last - first)) * (end - start);
}

export function ticks(domain: Domain, intervals = 4): number[] {
  const step = (domain.max - domain.min) / intervals;
  return Array.from({ length: intervals + 1 }, (_, index) => domain.min + step * index);
}

/** One decimal place, with no trailing zero. */
export function fmt(value: number): string {
  return String(Math.round(value * 10) / 10);
}