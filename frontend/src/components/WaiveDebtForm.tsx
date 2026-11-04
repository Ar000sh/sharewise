"use client";

import { useRef, useState } from "react";
import type { components } from "@/api/schema";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { amountToCents, centsToAmount, eurosToCents, formatEuro } from "@/lib/money";

type PlanEdge = components["schemas"]["SettlementPlanEdge"];

/** What is still open on a plan entry: the amount minus what is already reported and waiting for an answer. */
export function openCents(edge: PlanEdge): number {
  return amountToCents(edge.amount) - amountToCents(edge.pendingAmount);
}

/**
 * The creditor forgives (part of) a debt. `debtors` are the plan entries toward the current user; the amount starts at
 * what is still open. A waiver needs a reason and takes effect at once, so there is no confirmation step.
 */
export function WaiveDebtForm({
  groupId,
  debtors,
  prefill,
  onWaived,
}: {
  groupId: string;
  debtors: PlanEdge[];
  prefill?: { fromDebtor: string };
  onWaived?: () => void;
}) {
  // The choice and the typed amount are only what the user did; what is shown is derived from the current `debtors`,
  // so a plan that reloads (a debtor gone, a smaller open amount) never leaves the form on stale values.
  const [chosen, setChosen] = useState(prefill?.fromDebtor);
  const [typedAmount, setTypedAmount] = useState<string>();
  const [note, setNote] = useState("");
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  // State updates are not visible to a second submit in the same tick; the ref is.
  const sending = useRef(false);

  const selected = debtors.find((d) => d.fromDebtor === chosen) ?? debtors[0];
  const fromDebtor = selected?.fromDebtor ?? "";
  const amount = typedAmount ?? (selected ? centsToAmount(openCents(selected)) : "");

  function pick(id: string) {
    setChosen(id);
    setTypedAmount(undefined);
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (sending.current) return;
    const cents = eurosToCents(amount);
    if (!selected) return setError("Please choose who owes you.");
    if (cents === null || cents <= 0) return setError("Please enter a valid amount, e.g. 19,99.");
    if (cents > openCents(selected)) return setError(`You can waive up to ${formatEuro(openCents(selected))} here.`);
    if (!note.trim()) return setError("Please give a reason for waiving.");
    sending.current = true;
    setBusy(true);
    try {
      await api(`/groups/${groupId}/debt-waivers`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ fromDebtor, amount: centsToAmount(cents), note: note.trim() }),
      });
      setError(undefined);
      setNote("");
      setTypedAmount(undefined);
      onWaived?.();
    } catch (err) {
      // The shared sentence talks about paying; here the creditor gives something up.
      setError(
        err instanceof ApiError && err.code === "PAYMENT_EXCEEDS_OPEN_DEBT"
          ? "You can only waive up to what is still open."
          : messageFor(err),
      );
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-2 rounded border p-3">
      <label className="flex flex-col gap-1">
        Debtor
        <select className="rounded border px-2 py-2" value={fromDebtor} onChange={(e) => pick(e.target.value)}>
          {debtors.map((d) => (
            <option key={d.fromDebtor} value={d.fromDebtor}>
              {d.fromDebtorName}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1">
        Waived amount (€)
        <input
          className="rounded border px-2 py-2"
          inputMode="decimal"
          value={amount}
          onChange={(e) => setTypedAmount(e.target.value)}
        />
      </label>
      <label className="flex flex-col gap-1">
        Reason
        <input className="rounded border px-2 py-2" value={note} maxLength={200} onChange={(e) => setNote(e.target.value)} />
      </label>
      {error && (
        <p role="alert" className="text-red-700 dark:text-red-400">
          {error}
        </p>
      )}
      <button type="submit" disabled={busy} className="rounded bg-foreground px-3 py-2 text-background disabled:opacity-50">
        Waive debt
      </button>
    </form>
  );
}
