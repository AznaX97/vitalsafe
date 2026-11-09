import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import type { VitalType } from "../vitals/types";
import type { Bucket, Summary } from "./types";

export interface SummaryParams {
  type: VitalType;
  unit: string;
  days: number;
  bucket: Bucket;
  zone: string;
}

const DAY_MS = 86_400_000;

export function useSummary(params: SummaryParams) {
  return useQuery({
    // The time range is worked out when the request is made, so it is not part of the key
    queryKey: ["summary", params],
    queryFn: () => {
      const to = new Date();
      const from = new Date(to.getTime() - params.days * DAY_MS);
      const query = new URLSearchParams({
        type: params.type,
        from: from.toISOString(),
        to: to.toISOString(),
        zone: params.zone,
        bucket: params.bucket,
      });
      if (params.unit) {
        query.set("unit", params.unit);
      }
      return api<Summary>(`/vitals/summary?${query.toString()}`);
    },
    placeholderData: keepPreviousData,
  });
}