"use client";

import { useEffect, useRef } from "react";
import type { components } from "@/api/schema";
import { PENDING_FOR_ME_PATH } from "@/components/SettlementInbox";
import { useApi } from "@/lib/useApi";

type SettlementPayment = components["schemas"]["SettlementPayment"];

/** How often the count is asked for again. */
export const BADGE_POLL_MS = 30_000;

/** The number of settlement payments waiting for my answer (all groups, or one). Renders nothing at zero or on failure. */
export function PendingBadge({ groupId, refreshKey = 0 }: { groupId?: string; refreshKey?: number }) {
  const { data, reload } = useApi<SettlementPayment[]>(PENDING_FOR_ME_PATH);
  const loadedFor = useRef(refreshKey);

  // The caller bumps `refreshKey` after an answer, so the count is asked for at once and not only by the poll.
  useEffect(() => {
    if (loadedFor.current === refreshKey) return;
    loadedFor.current = refreshKey;
    void reload();
  }, [refreshKey, reload]);

  useEffect(() => {
    const timer = setInterval(() => void reload(), BADGE_POLL_MS);
    return () => clearInterval(timer);
  }, [reload]);

  // A failed poll keeps the last good count; before the first count (or after a failed first load) nothing shows.
  if (!data) return null;
  const count = data.filter((p) => groupId === undefined || p.groupId === groupId).length;
  if (count === 0) return null;
  return (
    <span
      aria-label={`${count} settlement ${count === 1 ? "payment" : "payments"} waiting for your answer`}
      className="ml-1 rounded-full bg-red-600 px-2 py-0.5 text-xs text-white"
    >
      {count}
    </span>
  );
}
