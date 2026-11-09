import type { VitalType } from "../vitals/types";

export type Bucket = "DAY" | "WEEK" | "MONTH";

export interface Stat {
  min: number;
  max: number;
  average: number;
}

export interface BucketStat {
  start: string;
  count: number;
  primary: Stat;
  secondary: Stat | null;
}

/** A range the person chose. Any bound may be missing. */
export interface RangeBounds {
  lowValue: number | null;
  highValue: number | null;
  lowSecondaryValue: number | null;
  highSecondaryValue: number | null;
}

export interface TargetBreakdown extends RangeBounds {
  within: number;
  above: number;
  below: number;
  mixed: number;
}

export interface Latest {
  measuredAt: string;
  value: number;
  secondaryValue: number | null;
}

export interface Summary {
  type: VitalType;
  unit: string | null;
  from: string;
  to: string;
  zone: string;
  bucket: Bucket;
  count: number;
  primary: Stat | null;
  secondary: Stat | null;
  latest: Latest | null;
  target: TargetBreakdown | null;
  buckets: BucketStat[];
  disclaimer: string;
}