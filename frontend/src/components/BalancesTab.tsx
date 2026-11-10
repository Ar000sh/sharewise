"use client";

import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { amountToCents, formatEuro } from "@/lib/money";
import { useApi } from "@/lib/useApi";
import { useLookAgain } from "@/lib/useLookAgain";

type Balance = components["schemas"]["Balance"];

function signed(cents: number): string {
  return cents > 0 ? `+${formatEuro(cents)}` : formatEuro(cents);
}

/**
 * `lastRecordedAt` is the time (ms since 1970) an expense was recorded on this page, if one was. While that is
 * recent the tab looks again a few times and says so.
 */
export function BalancesTab({ groupId, lastRecordedAt }: { groupId: string; lastRecordedAt?: number }) {
  const { data: balances, error, reload } = useApi<Balance[]>(`/groups/${groupId}/balances`);
  const { updating } = useLookAgain(lastRecordedAt, reload);

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
              <span className={`font-medium ${cents > 0 ? "text-green-700 dark:text-green-400" : cents < 0 ? "text-red-700 dark:text-red-400" : ""}`}>
                {signed(cents)}
              </span>
            </li>
          );
        })}
      </ul>
      <p className="text-sm text-foreground/70">
        Sum: {formatEuro(sum)}
        {updating && " · Updating…"}
      </p>
    </div>
  );
}
