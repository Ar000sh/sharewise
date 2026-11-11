"use client";

import { useRef, useState } from "react";
import type { components } from "@/api/schema";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { amountToCents, centsToAmount, eurosToCents, formatEuro } from "@/lib/money";

type PlanEdge = components["schemas"]["SettlementPlanEdge"];

/** What the server accepts as a payment or waiver on a plan entry right now (`payableAmount`). */
export function payableCents(edge: PlanEdge): number {
  return amountToCents(edge.payableAmount);
}

/**
 * The creditor forgives (part of) a debt. `waivableEdges` are the plan entries toward the current user; the amount starts at
 * what can be waived now. The reason is optional. A waiver takes effect at once, so there is no confirmation step.
 */
export function WaiveDebtForm({
  groupId,
  waivableEdges,
  onWaived,
}: {
  groupId: string;
  waivableEdges: PlanEdge[];
  onWaived?: () => void;
}) {
  // The choice and the typed amount are only what the user did; what is shown is derived from the current `waivableEdges`,
  // so a plan that reloads (a debtor gone, a smaller open amount) never leaves the form on stale values.
  const [chosenDebtorId, setChosenDebtorId] = useState<string>();
  // Pinned once the user chose a debtor or typed anything: the form then stays about that person even when the plan
  // (polled every few seconds) changes under it. Untouched, it follows the plan.
  const [pinned, setPinned] = useState(false);
  const [typedAmount, setTypedAmount] = useState<string>();
  const [note, setNote] = useState("");
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  // State updates are not visible to a second submit in the same tick; the ref is.
  const sending = useRef(false);

  // A pinned debtor who left the plan leaves nothing selected: never silently somebody else, whose debt would be waived
  // instead (design 2026-11-11, D2).
  const chosenEdge = waivableEdges.find((edge) => edge.fromDebtor === chosenDebtorId);
  const selectedEdge = chosenEdge ?? (pinned ? undefined : waivableEdges[0]);
  const fromDebtor = selectedEdge?.fromDebtor ?? "";
  const amount = typedAmount ?? (selectedEdge ? centsToAmount(payableCents(selectedEdge)) : "");

  function chooseDebtor(id: string) {
    setChosenDebtorId(id);
    setPinned(true);
    setTypedAmount(undefined);
  }

  /** The user typed: keep the debtor that is shown right now. */
  function pinShownDebtor() {
    setChosenDebtorId(fromDebtor);
    setPinned(true);
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (sending.current) return;
    const cents = eurosToCents(amount);
    if (!selectedEdge) return setError("Please choose who owes you.");
    if (cents === null || cents <= 0) return setError("Please enter a valid amount, e.g. 19,99.");
    if (cents > payableCents(selectedEdge))
      return setError(`You can waive up to ${formatEuro(payableCents(selectedEdge))} here.`);
    sending.current = true;
    setBusy(true);
    try {
      await api(`/groups/${groupId}/debt-waivers`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ fromDebtor, amount: centsToAmount(cents), ...(note.trim() ? { note: note.trim() } : {}) }),
      });
      setError(undefined);
      setNote("");
      setTypedAmount(undefined);
      // The waiver is done: the debtor stays selected while still in the plan (a partial waiver), else the form
      // follows the plan again.
      setPinned(false);
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
        <select
          className="rounded border px-2 py-2"
          value={fromDebtor}
          disabled={waivableEdges.length === 1}
          onChange={(e) => chooseDebtor(e.target.value)}
        >
          {!selectedEdge && (
            <option value="" disabled>
              Choose who owes you
            </option>
          )}
          {waivableEdges.map((edge) => (
            <option key={edge.fromDebtor} value={edge.fromDebtor}>
              {edge.fromDebtorName}
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
          onChange={(e) => {
            pinShownDebtor();
            setTypedAmount(e.target.value);
          }}
        />
      </label>
      <label className="flex flex-col gap-1">
        Reason (optional)
        <input
          className="rounded border px-2 py-2"
          value={note}
          maxLength={200}
          onChange={(e) => {
            pinShownDebtor();
            setNote(e.target.value);
          }}
        />
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
