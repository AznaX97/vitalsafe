import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import type { VitalType } from "../vitals/types";
import type { TargetRange } from "./types";

export function useTargets() {
  return useQuery({ queryKey: ["targets"], queryFn: () => api<TargetRange[]>("/targets") });
}

// A changed range changes how the summary counts readings, so both are reloaded
function useRefreshTargets() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["targets"] }),
      queryClient.invalidateQueries({ queryKey: ["summary"] }),
    ]);
}

export function useSaveTarget() {
  const refresh = useRefreshTargets();
  return useMutation({
    mutationFn: ({ type, body }: { type: VitalType; body: Record<string, string | number> }) =>
      api<TargetRange>(`/targets/${type}`, { method: "PUT", json: body }),
    onSuccess: refresh,
  });
}

export function useDeleteTarget() {
  const refresh = useRefreshTargets();
  return useMutation({
    mutationFn: ({ type, unit }: { type: VitalType; unit: string }) =>
      api<void>(`/targets/${type}?unit=${encodeURIComponent(unit)}`, { method: "DELETE" }),
    onSuccess: refresh,
  });
}