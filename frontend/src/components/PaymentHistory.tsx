"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { amountToCents, formatEuro } from "@/lib/money";
import { useApi } from "@/lib/useApi";

type SettlementPayment = components["schemas"]["SettlementPayment"];
type Status = SettlementPayment["status"];

const FILTERS: { label: string; status?: Status }[] = [
  { label: "All" },
  { label: "Pending", status: "PENDING" },
  { label: "Confirmed", status: "CONFIRMED" },
  { label: "Rejected", status: "REJECTED" },
  { label: "Waived", status: "WAIVED" },
];

const STATUS_LABEL: Record<Status, string> = {
  PENDING: "Pending",
  CONFIRMED: "Confirmed",
  REJECTED: "Rejected",
  WAIVED: "Waived",
};

/** "27 Jul" from an API timestamp, in the viewer's time zone (a moment, unlike an expense date). */
function shortDate(timestamp: string): string {
  return new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" }).format(new Date(timestamp));
}

/**
 * Every settlement payment of the group with its status, newest first (requirement F8, board B-07): what was reported,
 * confirmed, rejected or waived, by whom, with the note. It changes the balances only through what it shows as
 * confirmed or waived; it is a record, not an action.
 */
export function PaymentHistory({ groupId, currentUserId }: { groupId: string; currentUserId: string }) {
  const { data: payments, error } = useApi<SettlementPayment[]>(`/groups/${groupId}/settlement-payments`, { poll: true });
  const [filter, setFilter] = useState<Status>();

  const name = (id: string, displayName: string) => (id === currentUserId ? "you" : displayName);
  const shown = payments?.filter((p) => !filter || p.status === filter) ?? [];

  return (
    <section aria-label="Payment history" className="flex flex-col gap-3">
      <h2 className="text-2xl">Payment history</h2>
      <div role="group" aria-label="Show" className="flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button
            key={f.label}
            type="button"
            aria-pressed={filter === f.status}
            onClick={() => setFilter(f.status)}
            className={`min-h-11 rounded-full border-2 px-4 font-bold ${filter === f.status ? "border-foreground bg-foreground text-background" : "bg-surface"}`}
          >
            {f.label}
          </button>
        ))}
      </div>
      <ErrorBanner error={error} />
      {!payments && !error && <p>Loading…</p>}
      {payments && shown.length === 0 && (
        <p className="text-muted">No payments yet. Payments you report or receive appear here with their status.</p>
      )}
      {shown.length > 0 && (
        <ul className="card divide-y">
          {shown.map((p) => (
            <li key={p.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-5 py-4">
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="font-bold">
                  {name(p.fromDebtor, p.fromDebtorName)} → {name(p.toCreditor, p.toCreditorName)}
                </span>
                <span className="text-sm text-muted">
                  {shortDate(p.createdAt)}
                  {p.note && ` · “${p.note}”`}
                </span>
              </div>
              <span className="font-bold tabular-nums">{formatEuro(amountToCents(p.amount))}</span>
              <span
                className={`rounded-full border-2 px-3 py-0.5 text-sm font-bold ${
                  p.status === "CONFIRMED" || p.status === "WAIVED"
                    ? "border-positive text-positive"
                    : p.status === "REJECTED"
                      ? "border-negative text-negative"
                      : "border-transparent bg-warning text-on-warning"
                }`}
              >
                {STATUS_LABEL[p.status]}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
