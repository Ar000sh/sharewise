"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { centsToAmount, eurosToCents, formatEuro } from "@/lib/money";

type Member = components["schemas"]["Member"];

type Props = {
  groupId: string;
  members: Member[];
  currentUserId?: string;
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
  const [excluded, setExcluded] = useState<string[]>([]);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  const participants = members.filter((m) => !excluded.includes(m.userId));
  const cents = eurosToCents(amount);
  const shares = cents !== null && participants.length > 0 ? equalShares(cents, participants.length) : [];

  function toggle(userId: string) {
    setExcluded((ex) => (ex.includes(userId) ? ex.filter((id) => id !== userId) : [...ex, userId]));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    if (!title.trim()) return setError("Please enter a title.");
    if (cents === null) return setError("Please enter a valid amount, e.g. 19,99.");
    if (participants.length === 0) return setError("Select at least one participant.");
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
      setError(messageFor(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3 rounded border p-3">
      <label className="flex flex-col gap-1">
        Title
        <input className="rounded border px-2 py-2" value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
      </label>
      <div className="flex gap-3">
        <label className="flex flex-1 flex-col gap-1">
          Amount (€)
          <input
            className="rounded border px-2 py-2"
            inputMode="decimal"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </label>
        <label className="flex flex-1 flex-col gap-1">
          Date
          <input type="date" className="rounded border px-2 py-2" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
      </div>
      <label className="flex flex-col gap-1">
        Paid by
        <select className="rounded border px-2 py-2" value={paidBy} onChange={(e) => setPaidBy(e.target.value)}>
          {members.map((m) => (
            <option key={m.userId} value={m.userId}>
              {m.displayName}
            </option>
          ))}
        </select>
      </label>
      <fieldset className="flex flex-col gap-1">
        <legend>Split equally between</legend>
        {members.map((m) => (
          <label key={m.userId} className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={!excluded.includes(m.userId)}
              onChange={() => toggle(m.userId)}
            />
            {m.displayName}
          </label>
        ))}
      </fieldset>
      {shares.length > 0 && (
        <ul aria-label="Shares" className="text-sm text-gray-700">
          {participants.map((m, i) => (
            <li key={m.userId} className="flex justify-between">
              <span>{m.displayName}</span>
              <span>{formatEuro(shares[i])}</span>
            </li>
          ))}
        </ul>
      )}
      {error && <p role="alert" className="text-red-700">{error}</p>}
      <button type="submit" disabled={busy} className="rounded bg-black px-3 py-2 text-white disabled:opacity-50">
        Add expense
      </button>
    </form>
  );
}
