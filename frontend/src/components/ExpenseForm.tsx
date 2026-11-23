"use client";

import Link from "next/link";
import { useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { api } from "@/lib/api";
import { centsToAmount, eurosToCents, formatEuro } from "@/lib/money";

type Member = components["schemas"]["Member"];

type Props = {
  groupId: string;
  members: Member[];
  currentUserId: string;
  onRecorded: () => void;
};

function today(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Equal split in cents; the first participants get the remainder, like the backend. */
function equalShares(totalCents: number, count: number): number[] {
  const base = Math.floor(totalCents / count);
  const remainder = totalCents - base * count;
  return Array.from({ length: count }, (_, i) => base + (i < remainder ? 1 : 0));
}

export function ExpenseForm({ groupId, members, currentUserId, onRecorded }: Props) {
  const [title, setTitle] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(today);
  const [paidBy, setPaidBy] = useState(
    members.find((m) => m.userId === currentUserId)?.userId ?? members[0]?.userId ?? "",
  );
  // Only the recorder is in the split at first (R18); everybody else is ticked on purpose. The group page hands this form
  // a new member list whenever somebody joins, and a person nobody ticked must never slip into the split (design
  // 2026-11-11, D1).
  const [included, setIncluded] = useState<string[]>([currentUserId]);
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);

  const participants = members.filter((m) => included.includes(m.userId));
  const cents = eurosToCents(amount);
  const shares = cents !== null ? equalShares(cents, participants.length) : [];

  function toggle(userId: string) {
    setIncluded((ids) => (ids.includes(userId) ? ids.filter((id) => id !== userId) : [...ids, userId]));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    if (!title.trim()) return setError("Please enter a title.");
    if (cents === null) return setError("Please enter a valid amount, e.g. 19,99.");
    setBusy(true);
    try {
      await api(`/groups/${groupId}/expenses`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          title: title.trim(),
          totalAmount: centsToAmount(cents),
          date,
          paidBy,
          participantIds: participants.map((m) => m.userId),
          splitStrategy: "EQUAL",
        }),
      });
      setTitle("");
      setAmount("");
      setError(undefined);
      onRecorded();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="card flex flex-col gap-5 p-6">
      <div className="flex flex-col gap-1">
        <label htmlFor="expense-amount" className="font-bold">
          Amount (EUR)
        </label>
        <input
          id="expense-amount"
          aria-describedby="expense-amount-hint"
          inputMode="decimal"
          className="font-display text-2xl font-bold"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        <p id="expense-amount-hint" className="text-sm text-muted">
          Comma or point, at most two decimals.
        </p>
      </div>
      <label className="flex flex-col gap-1 font-bold">
        What was it for?
        <input className="font-normal" value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
      </label>
      <div className="flex flex-col gap-5 sm:flex-row">
        <label className="flex flex-1 flex-col gap-1 font-bold">
          Paid by
          <select className="font-normal" value={paidBy} onChange={(e) => setPaidBy(e.target.value)}>
            {members.map((m) => (
              <option key={m.userId} value={m.userId}>
                {m.displayName}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-1 flex-col gap-1 font-bold">
          Date
          <input type="date" className="font-normal" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
      </div>
      <fieldset className="flex flex-col gap-2">
        <legend className="pb-2 font-bold">Split equally between</legend>
        <button
          type="button"
          onClick={() => setIncluded(members.map((m) => m.userId))}
          className="btn-secondary self-start"
        >
          Select everyone
        </button>
        {members.map((m) => (
          <label key={m.userId} className="flex min-h-12 items-center gap-3">
            <input
              type="checkbox"
              checked={included.includes(m.userId)}
              disabled={m.userId === currentUserId} // the recorder always takes part (R18)
              onChange={() => toggle(m.userId)}
            />
            {m.displayName}
          </label>
        ))}
        <p className="text-sm text-muted">You stay in the split because you record it.</p>
      </fieldset>
      {shares.length > 0 && (
        <ul aria-label="Shares" className="card divide-y bg-surface-2 text-sm">
          {participants.map((m, i) => (
            <li key={m.userId} className="flex justify-between px-4 py-2">
              <span>{m.displayName}</span>
              <span className="font-bold tabular-nums">{formatEuro(shares[i])}</span>
            </li>
          ))}
        </ul>
      )}
      <ErrorBanner error={error} />
      <div className="flex flex-wrap gap-3">
        <button type="submit" disabled={busy} className="btn-primary">
          Save expense
        </button>
        <Link href={`/groups/${groupId}`} className="btn-secondary">
          Cancel
        </Link>
      </div>
      <p className="rounded-2xl bg-surface-2 px-4 py-3 text-sm">
        <span className="font-bold">After you save,</span> it shows in the list at once. Balances show “Updating…” for a
        moment, then catch up.
      </p>
    </form>
  );
}
