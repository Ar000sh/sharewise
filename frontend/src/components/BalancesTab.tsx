"use client";

import { useEffect } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { amountToCents, formatEuro } from "@/lib/money";
import { useApi } from "@/lib/useApi";

type Balance = components["schemas"]["Balance"];

const RETRY_DELAY_MS = 1000;
const MAX_RETRIES = 3;

function signed(cents: number): string {
  return cents > 0 ? `+${formatEuro(cents)}` : formatEuro(cents);
}

/**
 * Balances are eventually consistent with expenses. `refreshKey` changes after an expense is
 * recorded; a sum other than zero means an expense is not applied everywhere yet, so we re-fetch.
 */
export function BalancesTab({ groupId, refreshKey }: { groupId: string; refreshKey: number }) {
  const { data: balances, error, reload } = useApi<Balance[]>(`/groups/${groupId}/balances`);

  useEffect(() => {
    if (refreshKey > 0) void reload();
  }, [refreshKey, reload]);

  const sum = balances?.reduce((total, b) => total + amountToCents(b.netBalance), 0) ?? 0;
  const stale = sum !== 0;

  useEffect(() => {
    if (!stale) return;
    let attempts = 0;
    const timer = setInterval(() => {
      if (++attempts >= MAX_RETRIES) clearInterval(timer);
      void reload();
    }, RETRY_DELAY_MS);
    return () => clearInterval(timer);
  }, [stale, reload]);

  if (error && !balances) return <ErrorBanner error={error} />;
  if (!balances) return <p>Loading…</p>;

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
        {stale && " (updating…)"}
      </p>
    </div>
  );
}
