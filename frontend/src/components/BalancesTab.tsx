"use client";

import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Avatar } from "@/components/Avatar";
import { Icon } from "@/components/Icon";
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
  const { data: balances, error, reload } = useApi<Balance[]>(`/groups/${groupId}/balances`, { poll: true });
  const { lastChangedAt } = useGroupChange(groupId);
  const { updating } = useLookAgain(lastChangedAt, reload);

  if (error && !balances) return <ErrorBanner error={error} />;
  if (!balances) return <p className="loading-state">Loading…</p>;
  const sum = balances.reduce((total, b) => total + amountToCents(b.netBalance), 0);
  return (
    <div className="flex max-w-3xl flex-col gap-5">
      <div><h2 className="text-xl font-semibold">A clear picture, for everyone</h2><p className="mt-2 text-sm leading-relaxed text-muted">Positive means someone should receive money. Negative means they owe their group.</p></div>
      <ErrorBanner error={error} />
      <ul className="panel divide-y divide-line">
        {balances.map((b) => {
          const cents = amountToCents(b.netBalance);
          return (
            <li key={b.userId} className="flex items-center justify-between gap-3 px-5 py-5 sm:px-6">
              <Avatar name={b.displayName} />
              <span className="min-w-0 flex-1 break-words text-sm font-medium">{b.displayName}<span className="mt-1 block text-xs font-normal text-muted">{cents > 0 ? "Should receive" : cents < 0 ? "Owes the group" : "All settled"}</span></span>
              <span className={`shrink-0 text-sm font-semibold tabular-nums ${cents > 0 ? "text-green-700 dark:text-green-400" : cents < 0 ? "text-red-700 dark:text-red-400" : ""}`}>
                {signed(cents)}
              </span>
            </li>
          );
        })}
      </ul>
      <p className="flex items-center gap-2 text-xs text-muted">
        <Icon name="balance" />
        Sum: {formatEuro(sum)}
        {updating && " · Updating…"}
      </p>
      <p className="rounded-xl bg-soft p-4 text-xs leading-relaxed text-foreground">Balances reflect recorded expenses. After a new expense, they may take a moment to update. Payments are arranged outside ShareWise.</p>
    </div>
  );
}
