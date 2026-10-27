"use client";

import { useEffect, useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { amountToCents, formatEuro } from "@/lib/money";
import { useApi } from "@/lib/useApi";

type Balance = components["schemas"]["Balance"];

/**
 * Milliseconds after an expense was recorded at which the balances are fetched again. The ledger is updated by an
 * event after the expense is saved, so the first answer may still be the old one. The window is bounded: after the
 * last look the tab stops asking.
 */
const LOOK_AGAIN_AFTER_MS = [500, 1000, 2000, 4000];

function signed(cents: number): string {
  return cents > 0 ? `+${formatEuro(cents)}` : formatEuro(cents);
}

/**
 * `lastRecordedAt` is the time (ms since 1970) an expense was recorded on this page, if one was. While that is
 * recent the tab looks again a few times and says so.
 */
export function BalancesTab({ groupId, lastRecordedAt }: { groupId: string; lastRecordedAt?: number }) {
  const { data: balances, error, reload } = useApi<Balance[]>(`/groups/${groupId}/balances`);
  const [updating, setUpdating] = useState(false);

  useEffect(() => {
    if (lastRecordedAt === undefined) return;
    const age = Date.now() - lastRecordedAt;
    const remaining = LOOK_AGAIN_AFTER_MS.filter((delay) => delay > age);
    if (remaining.length === 0) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- starts a bounded series of timers
    setUpdating(true);
    const timers = remaining.map((delay, i) =>
      setTimeout(() => {
        void reload();
        if (i === remaining.length - 1) setUpdating(false);
      }, delay - age),
    );
    return () => {
      timers.forEach(clearTimeout);
      setUpdating(false);
    };
  }, [lastRecordedAt, reload]);

  if (error && !balances) return <ErrorBanner error={error} />;
  if (!balances) return <p>Loading…</p>;
  const sum = balances.reduce((total, b) => total + amountToCents(b.netBalance), 0);
  return (
    <div className="flex flex-col gap-3">
      <ErrorBanner error={error} />
      <ul className="divide-y rounded border">
        {balances.map((b) => {
          const cents = amountToCents(b.netBalance);
          return (
            <li key={b.userId} className="flex justify-between gap-2 px-3 py-2">
              <span>{b.displayName}</span>
              <span className={`font-medium ${cents > 0 ? "text-green-700" : cents < 0 ? "text-red-700" : ""}`}>
                {signed(cents)}
              </span>
            </li>
          );
        })}
      </ul>
      <p className="text-sm text-gray-600">
        Sum: {formatEuro(sum)}
        {updating && " · Updating…"}
      </p>
    </div>
  );
}
