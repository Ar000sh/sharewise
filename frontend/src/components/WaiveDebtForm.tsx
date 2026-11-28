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
 * The creditor forgives (part of) one person's debt (R17, board B-12). The form is about that one person only: `edge`
 * is their current entry in the polled plan, or `undefined` once their debt is gone, and then the form says so instead
 * of offering anybody else (design 2026-11-23, F1). The amount starts at what can be waived now; the reason is
 * optional. A waiver takes effect at once, so there is no confirmation step.
 */
export function WaiveDebtForm({
  groupId,
  debtorName,
  edge,
  onWaived,
  onCancel,
}: {
  groupId: string;
  debtorName: string;
  edge: PlanEdge | undefined;
  onWaived: () => void;
  onCancel: () => void;
}) {
  const [typedAmount, setTypedAmount] = useState<string>();
  const [note, setNote] = useState("");
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  // State updates are not visible to a second submit in the same tick; the ref is.
  const sending = useRef(false);

  if (!edge || payableCents(edge) <= 0) {
    return (
      <div className="flex flex-col gap-4">
        <p>Nothing is open with {debtorName} any more.</p>
        <button type="button" onClick={onCancel} className="btn-secondary self-start">
          Close
        </button>
      </div>
    );
  }

  const open = payableCents(edge);
  const amount = typedAmount ?? centsToAmount(open);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (sending.current || !edge) return;
    const cents = eurosToCents(amount);
    if (cents === null) return setError("Please enter a valid amount, e.g. 19,99.");
    if (cents > open) return setError(`You can waive up to ${formatEuro(open)} here.`);
    sending.current = true;
    setBusy(true);
    try {
      await api(`/groups/${groupId}/debt-waivers`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ fromDebtor: edge.fromDebtor, amount: centsToAmount(cents), ...(note.trim() ? { note: note.trim() } : {}) }),
      });
      onWaived();
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
    <form onSubmit={submit} className="flex flex-col gap-4">
      <p>You give up part of what {debtorName} owes you. It takes effect at once and cannot be undone.</p>
      <p className="rounded-2xl bg-surface-2 px-4 py-3">
        <span className="font-bold">{debtorName}</span> owes you <span className="font-bold">{formatEuro(open)}</span>
      </p>
      <label className="flex flex-col gap-1 font-bold">
        Waived amount (EUR)
        <input className="font-normal" inputMode="decimal" value={amount} onChange={(e) => setTypedAmount(e.target.value)} />
      </label>
      <label className="flex flex-col gap-1 font-bold">
        Reason (optional)
        <input className="font-normal" value={note} maxLength={200} onChange={(e) => setNote(e.target.value)} />
      </label>
      {error && (
        <p role="alert" className="font-bold text-negative">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-3">
        <button type="submit" disabled={busy} className="btn-primary">
          Waive debt
        </button>
        <button type="button" onClick={onCancel} className="btn-secondary">
          Cancel
        </button>
      </div>
    </form>
  );
}
