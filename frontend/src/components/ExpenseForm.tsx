"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Icon } from "@/components/Icon";
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
  return Array.from(
    { length: count },
    (_, i) => base + (i < remainder ? 1 : 0),
  );
}

export function ExpenseForm({
  groupId,
  members,
  currentUserId,
  onRecorded,
}: Props) {
  const [title, setTitle] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(today);
  const [paidBy, setPaidBy] = useState(
    members.find((m) => m.userId === currentUserId)?.userId ??
      members[0]?.userId ??
      "",
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
    setIncluded((ids) =>
      ids.includes(userId)
        ? ids.filter((id) => id !== userId)
        : [...ids, userId],
    );
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    if (!title.trim()) return setError("Please enter a title.");
    if (cents === null)
      return setError("Please enter a valid amount, e.g. 19,99.");
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
    <form onSubmit={submit} className="panel flex flex-col gap-5 p-6">
      <div>
        <h2 className="font-semibold">Add an expense</h2>
        <p className="mt-1 text-xs text-muted">
          Record who paid and how to share the cost.
        </p>
      </div>
      <label className="field-label">
        Title
        <input
          className="field"
          placeholder="Dinner, groceries, a place to stay…"
          value={title}
          maxLength={200}
          onChange={(e) => setTitle(e.target.value)}
        />
      </label>
      <div className="flex gap-3">
        <label className="field-label flex-1">
          Amount (€)
          <input
            className="field"
            inputMode="decimal"
            placeholder="0,00"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </label>
        <label className="field-label flex-1">
          Date
          <input
            type="date"
            className="field"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            required
          />
        </label>
      </div>
      <label className="field-label">
        Paid by
        <select
          className="field"
          value={paidBy}
          onChange={(e) => setPaidBy(e.target.value)}
        >
          {members.map((m) => (
            <option key={m.userId} value={m.userId}>
              {m.displayName}
            </option>
          ))}
        </select>
      </label>
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-3 text-sm font-medium">
          Split equally between
        </legend>
        <button
          type="button"
          onClick={() => setIncluded(members.map((m) => m.userId))}
          className="self-start rounded border px-2 py-1 text-sm"
        >
          Select everyone
        </button>
        {members.map((m) => (
          <label
            key={m.userId}
            className="flex min-h-10 cursor-pointer items-center gap-3 rounded-lg bg-background px-3 py-2 text-sm"
          >
            <input
              type="checkbox"
              className="size-4 shrink-0"
              checked={included.includes(m.userId)}
              disabled={m.userId === currentUserId}
              onChange={() => toggle(m.userId)}
            />
            {m.displayName}
          </label>
        ))}
      </fieldset>
      {shares.length > 0 && (
        <ul
          aria-label="Shares"
          className="flex flex-col gap-2 rounded-xl bg-soft p-4 text-sm text-foreground"
        >
          {participants.map((m, i) => (
            <li key={m.userId} className="flex justify-between">
              <span>{m.displayName}</span>
              <span className="font-medium tabular-nums">
                {formatEuro(shares[i])}
              </span>
            </li>
          ))}
        </ul>
      )}
      <ErrorBanner error={error} />
      <button type="submit" disabled={busy} className="button-primary">
        <Icon name="plus" />
        Add expense
      </button>
    </form>
  );
}
