"use client";

import Link from "next/link";
import type { components } from "@/api/schema";
import { GroupStatusBadge } from "@/components/GroupStatusBadge";
import { amountToCents, formatSignedEuro } from "@/lib/money";
import { useApi } from "@/lib/useApi";
import { useGroupChange } from "@/lib/useGroupChange";
import { useLookAgain } from "@/lib/useLookAgain";

type GroupDetailData = components["schemas"]["GroupDetail"];
type Balance = components["schemas"]["Balance"];

/** The viewer's own balance in words: the sign and the words say the direction, the colour only repeats it. */
function OwnBalance({ cents }: { cents: number }) {
  if (cents === 0) return <p className="font-display text-3xl font-extrabold">You are settled up</p>;
  return (
    <p className="font-display text-3xl font-extrabold">
      {cents > 0 ? "You are owed " : "You owe "}
      <span className={`tabular-nums ${cents > 0 ? "text-positive" : "text-negative"}`}>{formatSignedEuro(cents)}</span>
    </p>
  );
}

/**
 * The top of a group page (board B-04): name, status, size, whether the balances are up to date, and the viewer's own
 * balance. It reads the same balances query as the Balances tab and, after a change made on this device, looks again
 * for a short time and says "Updating…" meanwhile (eventual consistency: the ledger books a change a moment later).
 */
export function GroupHero({
  group,
  currentUserId,
  waiting,
  onOpenSettleUp,
}: {
  group: GroupDetailData;
  currentUserId: string | undefined;
  /** Payments of this group that wait for the viewer's answer (from the one inbox query). */
  waiting: number;
  onOpenSettleUp: () => void;
}) {
  const { data: balances, reload } = useApi<Balance[]>(`/groups/${group.id}/balances`, { poll: true });
  const { lastChangedAt } = useGroupChange(group.id);
  const { updating } = useLookAgain(lastChangedAt, reload);
  const own = balances?.find((b) => b.userId === currentUserId);

  return (
    <section aria-label="Group" className="flex flex-col gap-4 rounded-3xl bg-surface-2 p-6 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h1 className="text-4xl sm:text-5xl">{group.name}</h1>
        {/* The wrapper decides where it shows: btn-primary sets `display` itself. */}
        <div className="hidden sm:block">
          <Link href={`/groups/${group.id}/expenses/new`} className="btn-primary">
            Add expense
          </Link>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm">
        <GroupStatusBadge status={group.status} />
        <span className="text-muted">{group.memberCount} members · EUR</span>
        <span aria-live="polite" className="rounded-full bg-surface px-3 py-0.5 font-bold text-muted">
          {updating ? "Updating…" : "Balances up to date"}
        </span>
      </div>
      {own && <OwnBalance cents={amountToCents(own.netBalance)} />}
      {waiting > 0 && (
        <button type="button" onClick={onOpenSettleUp} className="self-start font-bold underline">
          {waiting === 1 ? "1 payment waits for your answer" : `${waiting} payments wait for your answer`}
        </button>
      )}
      {/* On a phone the most frequent action stays in reach at the bottom of the screen (design U7). */}
      <div className="fixed right-4 bottom-4 z-10 sm:hidden">
        <Link href={`/groups/${group.id}/expenses/new`} className="btn-primary min-h-14 shadow-xl">
          Add expense
        </Link>
      </div>
    </section>
  );
}
