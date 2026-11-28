"use client";

import Link from "next/link";
import { useState } from "react";
import type { components } from "@/api/schema";
import { Avatar } from "@/components/Avatar";
import { Dialog } from "@/components/Dialog";
import { ErrorBanner } from "@/components/ErrorBanner";
import { api } from "@/lib/api";
import { formatDay } from "@/lib/dates";
import { amountToCents, formatEuro, formatSignedEuro, yourPartCents } from "@/lib/money";

type Expense = components["schemas"]["Expense"];
type Member = components["schemas"]["Member"];

/** How the row names the split, in the words of the form's three modes. */
const SPLIT_WORDS: Record<Expense["splitStrategy"], string> = {
  EQUAL: "equally",
  EXACT: "exact amounts",
  PERCENTAGE: "by percentages",
};

/** Consecutive expenses of the same date, in the order the API gives them (newest first). */
function byDay(expenses: Expense[]): { date: string; expenses: Expense[] }[] {
  const days: { date: string; expenses: Expense[] }[] = [];
  for (const expense of expenses) {
    const last = days.at(-1);
    if (last?.date === expense.date) last.expenses.push(expense);
    else days.push({ date: expense.date, expenses: [expense] });
  }
  return days;
}

type Props = {
  expenses: Expense[];
  members: Member[];
  /** People who left or were removed: old expenses name them as "Bob (left)" (R13). */
  formerMembers?: Member[];
  currentUserId: string;
  /**
   * Set only while the group is open (R5): then the recorder, the payer and an admin get "Correct" and "Remove" on an
   * expense that is not removed (R6). Only a hint for the screen: the backend checks the same rules.
   */
  change?: { groupId: string; isAdmin: boolean; onChanged: () => void };
};

/**
 * The expenses of a group grouped by day (board B-04). Each row says who paid, how it is split and what it means for
 * the person looking: their part, plus or minus, with the sign and not only the colour telling the direction. A
 * corrected expense carries a badge and its history (R7); a removed one stays in the list, struck through, and no
 * longer counts (R8).
 */
export function ExpenseList({ expenses, members, formerMembers = [], currentUserId, change }: Props) {
  const [history, setHistory] = useState<Expense>();
  const [removing, setRemoving] = useState<Expense>();
  const [removeError, setRemoveError] = useState<unknown>();
  const [busy, setBusy] = useState(false);

  if (expenses.length === 0) {
    return (
      <div className="card flex flex-col gap-1 p-6">
        <h2 className="text-2xl">No expenses yet</h2>
        <p className="text-muted">Record the first one. Everyone in the group sees it.</p>
      </div>
    );
  }

  const nameOf = (id: string) => {
    const member = members.find((m) => m.userId === id);
    if (member) return member.displayName;
    const former = formerMembers.find((m) => m.userId === id);
    return former ? `${former.displayName} (left)` : "Former member";
  };

  const isMember = (id: string) => members.some((m) => m.userId === id);
  // An expense that involves somebody who has left cannot change (FORMER_MEMBER_INVOLVED): their balance is closed.
  const canChange = (e: Expense) =>
    change !== undefined &&
    e.status !== "REMOVED" &&
    (change.isAdmin || e.recordedBy === currentUserId || e.paidBy === currentUserId) &&
    isMember(e.paidBy) &&
    e.shares.every((s) => isMember(s.participantId));

  async function remove(expense: Expense) {
    if (!change) return;
    setBusy(true);
    try {
      await api(`/groups/${change.groupId}/expenses/${expense.id}`, { method: "DELETE" });
      closeRemove();
      change.onChanged();
    } catch (err) {
      setRemoveError(err);
    } finally {
      setBusy(false);
    }
  }

  function closeRemove() {
    setRemoving(undefined);
    setRemoveError(undefined);
  }

  return (
    <div className="flex flex-col gap-2">
      {byDay(expenses).map((day) => (
        <section key={day.date} aria-label={formatDay(day.date)}>
          <h2 className="px-1 pt-3 pb-1 font-sans text-sm font-bold tracking-normal text-muted">{formatDay(day.date)}</h2>
          <ul className="card divide-y">
            {day.expenses.map((e) => {
              const removed = e.status === "REMOVED";
              const part = yourPartCents(e, currentUserId);
              return (
                <li key={e.id} className="flex flex-wrap items-center gap-4 px-5 py-4">
                  <Avatar userId={e.paidBy} name={nameOf(e.paidBy)} />
                  <div className="flex min-w-0 flex-1 flex-col">
                    <span className="flex flex-wrap items-center gap-2">
                      <span className={`font-bold ${removed ? "text-muted line-through" : ""}`}>{e.title}</span>
                      {e.status !== "ACTIVE" && (
                        <span className="rounded-full border-2 border-muted px-2 text-xs font-bold text-muted">
                          {removed ? "Removed" : "Corrected"}
                        </span>
                      )}
                    </span>
                    <span className="text-sm text-muted">
                      {e.paidBy === currentUserId ? "You" : nameOf(e.paidBy)} paid · {SPLIT_WORDS[e.splitStrategy]} between{" "}
                      {e.shares.length}
                    </span>
                  </div>
                  <div className="flex flex-col items-end">
                    <span className={`font-bold whitespace-nowrap tabular-nums ${removed ? "text-muted line-through" : ""}`}>
                      {formatEuro(amountToCents(e.totalAmount))}
                    </span>
                    {removed ? (
                      <span className="text-sm text-muted">no longer counts</span>
                    ) : (
                      <span
                        className={`text-sm whitespace-nowrap tabular-nums ${part > 0 ? "text-positive" : part < 0 ? "text-negative" : "text-muted"}`}
                      >
                        your part {formatSignedEuro(part)}
                      </span>
                    )}
                  </div>
                  {(e.revisions.length > 0 || canChange(e)) && (
                    <div className="flex w-full flex-wrap justify-end gap-2">
                      {e.revisions.length > 0 && (
                        <button
                          type="button"
                          onClick={() => setHistory(e)}
                          aria-label={`History of ${e.title}`}
                          className="btn-secondary"
                        >
                          History
                        </button>
                      )}
                      {canChange(e) && change && (
                        <>
                          <Link
                            href={`/groups/${change.groupId}/expenses/${e.id}/correct`}
                            aria-label={`Correct ${e.title}`}
                            className="btn-secondary"
                          >
                            Correct
                          </Link>
                          <button
                            type="button"
                            onClick={() => setRemoving(e)}
                            aria-label={`Remove ${e.title}`}
                            className="btn-secondary"
                          >
                            Remove
                          </button>
                        </>
                      )}
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </section>
      ))}
      {history && (
        <Dialog title={`History of ${history.title}`} onClose={() => setHistory(undefined)}>
          <ol className="flex flex-col gap-3">
            {history.revisions.map((r, i) => (
              <li key={i} className="flex flex-col gap-1 rounded-2xl bg-surface-2 px-4 py-3 text-sm">
                <span className="font-bold">
                  {nameOf(r.correctedBy)} corrected it on {formatDay(r.correctedAt.slice(0, 10))}
                </span>
                {r.reason && <span>“{r.reason}”</span>}
                <span className="text-muted">
                  Before: {r.previous.title}, {formatEuro(amountToCents(r.previous.totalAmount))}, paid by{" "}
                  {nameOf(r.previous.paidBy)}, {SPLIT_WORDS[r.previous.splitStrategy]} between {r.previous.shares.length}
                </span>
              </li>
            ))}
          </ol>
          {history.status === "REMOVED" && <p className="text-sm text-muted">It was removed afterwards.</p>}
          <button type="button" onClick={() => setHistory(undefined)} className="btn-secondary self-start">
            Close
          </button>
        </Dialog>
      )}
      {removing && (
        <Dialog title={`Remove ${removing.title}?`} onClose={closeRemove}>
          <p>
            The expense stays in the list, struck through, but no longer counts. Balances are booked back for everybody in
            it.
          </p>
          <ErrorBanner error={removeError} />
          <div className="flex flex-wrap gap-3">
            <button type="button" onClick={() => void remove(removing)} disabled={busy} className="btn-primary">
              Remove expense
            </button>
            <button type="button" onClick={closeRemove} className="btn-secondary">
              Cancel
            </button>
          </div>
        </Dialog>
      )}
    </div>
  );
}
