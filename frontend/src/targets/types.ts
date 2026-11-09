import type { RangeBounds } from "../summary/types";
import type { VitalType } from "../vitals/types";

export interface TargetRange extends RangeBounds {
  type: VitalType;
  unit: string;
  updatedAt: string;
}