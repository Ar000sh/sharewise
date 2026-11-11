"use client";

import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { amountToCents, formatEuro } from "@/lib/money";
import { useApi } from "@/lib/useApi";
import { useGroupChange } from "@/lib/useGroupChange";
import { useLookAgain } from "@/lib/useLookAgain";

type Balance = components["schemas"]["Balance"];

function signed(cents: number): string {
  return cents > 0 ? `+${formatEuro(cents)}` : formatEuro(cents);
}

/**
 * Everybody's net balance. When something in the group changed on this page a moment ago (an expense, a payment
 * answered in the bell) the tab looks again a few times and says so: the ledger books the change after it was saved.
 */
export function BalancesTab({ groupId }: { groupId: string }) {
  const { data: balances, error, reload } = useApi<Balance[]>(`/groups/${groupId}/balances`);
  const { lastChangedAt } = useGroupChange(groupId);
  const { updating } = useLookAgain(lastChangedAt, reload);

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
