"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { api } from "@/lib/api";

/** How often the open group page asks again (design 2026-11-11, L2). TanStack pauses it while the tab is hidden. */
export const POLL_MS = 10_000;

/** The cache key of an API path: its segments, so that `["groups", id]` reaches every query of one group. */
export function queryKeyOf(path: string): string[] {
  return path.split("/").filter(Boolean);
}

/**
 * Loads `path` through the API helper into the shared cache. `reload` marks it out of date and asks again (e.g. after
 * a write); `poll` also asks every {@link POLL_MS}. `reload` keeps its identity, because `useLookAgain` restarts its
 * timers when it changes.
 */
export function useApi<T>(path: string, options?: { poll?: boolean }) {
  const client = useQueryClient();
  const { data, error } = useQuery({
    queryKey: queryKeyOf(path),
    queryFn: () => api<T>(path),
    refetchInterval: options?.poll ? POLL_MS : false,
  });
  const reload = useCallback(() => client.invalidateQueries({ queryKey: queryKeyOf(path) }), [client, path]);
  return { data, error: error ?? undefined, reload };
}
