"use client";

import Link from "next/link";
import { useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { api } from "@/lib/api";
import {
  centsToAmount,
  equalShares,
  eurosToCents,
  formatEuro,
  formatPercent,
  percentageShares,
  percentToHundredths,
} from "@/lib/money";

type Member = components["schemas"]["Member"];
type SplitStrategy = "EQUAL" | "EXACT" | "PERCENTAGE";

const SPLIT_OPTIONS: [SplitStrategy, string][] = [
  ["EQUAL", "Equally"],
  ["EXACT", "Exact amounts"],
  ["PERCENTAGE", "Percentages"],
];

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

  const [splitStrategy, setSplitStrategy] = useState<SplitStrategy>("EQUAL");
  // What was typed per member, keyed by userId; only the ticked members count (a box of an unticked member is hidden).
  const [typed, setTyped] = useState<Record<string, string>>({});

  const participants = members.filter((m) => included.includes(m.userId));
  const cents = eurosToCents(amount);
  const parse = splitStrategy === "PERCENTAGE" ? percentToHundredths : eurosToCents;
  const parsed = participants.map((m) => parse(typed[m.userId] ?? ""));
  const typedSum = parsed.reduce<number>((sum, v) => sum + (v ?? 0), 0);
  // percentageShares is only valid for a sum of exactly 100 % (10000 hundredths), so the preview waits for it
  const shares =
    cents === null
      ? []
      : splitStrategy === "EQUAL"
        ? equalShares(cents, participants.length)
        : splitStrategy === "PERCENTAGE" && typedSum === 10000
          ? percentageShares(cents, parsed.map((v) => v ?? 0))
          : [];

  function toggle(userId: string) {
    setIncluded((ids) => (ids.includes(userId) ? ids.filter((id) => id !== userId) : [...ids, userId]));
  }

  function chooseStrategy(strategy: SplitStrategy) {
    setSplitStrategy(strategy);
    setTyped({});
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    if (!title.trim()) return setError("Please enter a title.");
    if (cents === null) return setError("Please enter a valid amount, e.g. 19,99.");
    if (splitStrategy !== "EQUAL") {
      const unit = splitStrategy === "EXACT" ? "amount" : "percentage";
      const bad = participants.find((_, i) => parsed[i] === null);
      if (bad) return setError(`Please enter a valid ${unit} for ${bad.displayName}.`);
      if (splitStrategy === "EXACT" && typedSum !== cents) {
        return setError(`The amounts must add up to ${formatEuro(cents)}.`);
      }
      if (splitStrategy === "PERCENTAGE" && typedSum !== 10000) {
        return setError("The percentages must add up to 100 %.");
      }
    }
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
          participants: participants.map((m, i) =>
            splitStrategy === "EQUAL" ? { userId: m.userId } : { userId: m.userId, value: centsToAmount(parsed[i] ?? 0) },
          ),
          splitStrategy,
        }),
      });
      setTitle("");
      setAmount("");
      setTyped({});
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
      <fieldset className="flex flex-wrap gap-x-4 gap-y-1">
        <legend className="pb-2 font-bold">Split</legend>
        {SPLIT_OPTIONS.map(([value, label]) => (
          <label key={value} className="flex min-h-12 items-center gap-2">
            <input type="radio" name="splitStrategy" checked={splitStrategy === value} onChange={() => chooseStrategy(value)} />
            {label}
          </label>
        ))}
      </fieldset>
      <fieldset className="flex flex-col gap-2">
        <legend className="pb-2 font-bold">Split between</legend>
        <button
          type="button"
          onClick={() => setIncluded(members.map((m) => m.userId))}
          className="btn-secondary self-start"
        >
          Select everyone
        </button>
        {members.map((m) => (
          <div key={m.userId} className="flex items-center justify-between gap-2">
            <label className="flex min-h-12 items-center gap-3">
              <input
                type="checkbox"
                checked={included.includes(m.userId)}
                disabled={m.userId === currentUserId} // the recorder always takes part (R18)
                onChange={() => toggle(m.userId)}
              />
              {m.displayName}
            </label>
            {splitStrategy !== "EQUAL" && included.includes(m.userId) && (
              <input
                className="w-28 text-right"
                aria-label={`${splitStrategy === "EXACT" ? "Amount" : "Percentage"} for ${m.displayName}`}
                inputMode="decimal"
                required
                value={typed[m.userId] ?? ""}
                onChange={(e) => setTyped((t) => ({ ...t, [m.userId]: e.target.value }))}
              />
            )}
          </div>
        ))}
        <p className="text-sm text-muted">You stay in the split because you record it.</p>
      </fieldset>
      {splitStrategy === "EXACT" && cents !== null && (
        <p className="text-sm text-foreground/80">Remaining: {formatEuro(cents - typedSum)}</p>
      )}
      {splitStrategy === "PERCENTAGE" && <p className="text-sm text-foreground/80">Sum: {formatPercent(typedSum)}</p>}
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
