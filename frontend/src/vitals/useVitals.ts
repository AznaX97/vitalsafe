import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import type { Page, Vital, VitalInput, VitalType } from "./types";

export interface VitalFilter {
  type: VitalType | "";
  page: number;
  size: number;
}

export function useVitalsPage(filter: VitalFilter) {
  return useQuery({
    queryKey: ["vitals", filter],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(filter.page), size: String(filter.size) });
      if (filter.type) {
        params.set("type", filter.type);
      }
      return api<Page<Vital>>(`/vitals?${params.toString()}`);
    },
    // Keep showing the previous page while the next one loads, so the list does not flicker
    placeholderData: keepPreviousData,
  });
}

export function useSaveVital() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: VitalInput }) =>
      id
        ? api<Vital>(`/vitals/${id}`, { method: "PUT", json: input })
        : api<Vital>("/vitals", { method: "POST", json: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["vitals"] }),
  });
}

export function useDeleteVital() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/vitals/${id}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["vitals"] }),
  });
}