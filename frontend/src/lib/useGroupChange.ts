"use client";

import { type QueryClient, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";

/** Where the time of the last change of a group is kept: next to the group's data, in the same cache. */
export function lastChangedKey(groupId: string): string[] {
  return ["groups", groupId, "lastChangedAt"];
}

/**
 * Something in the group changed (an own write, an answer in the bell): remember when, and ask all of the group's
 * queries again. The time starts the bounded look-again of whichever tab is open or opened next, because the ledger
 * books the change a moment after it was saved (eventual consistency).
 */
export function markGroupChanged(client: QueryClient, groupId: string): void {
  client.setQueryData(lastChangedKey(groupId), Date.now());
  void client.invalidateQueries({ queryKey: ["groups", groupId] });
}

/**
 * The time (ms since 1970) of the last change made on this page, or `undefined`, and a stable function that marks the
 * group changed. The time is only ever set, never fetched, so the query is disabled: invalidating the group leaves it
 * alone.
 */
export function useGroupChange(groupId: string): { lastChangedAt: number | undefined; markChanged: () => void } {
  const client = useQueryClient();
  const { data } = useQuery<number>({
    queryKey: lastChangedKey(groupId),
    queryFn: () => Date.now(),
    enabled: false,
  });
  const markChanged = useCallback(() => markGroupChanged(client, groupId), [client, groupId]);
  return { lastChangedAt: data, markChanged };
}
