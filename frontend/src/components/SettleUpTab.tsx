"use client";

import { useCallback, useRef, useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Toast } from "@/components/Toast";
import { api } from "@/lib/api";
import { useCurrentUser } from "@/lib/currentUser";
import { messageFor } from "@/lib/errors";
import { amountToCents, centsToAmount, eurosToCents, formatEuro } from "@/lib/money";
import { useApi } from "@/lib/useApi";
import { useLookAgain } from "@/lib/useLookAgain";

type PlanEdge = components["schemas"]["SettlementPlanEdge"];

function openCents(edge: PlanEdge): number {
  return amountToCents(edge.amount) - amountToCents(edge.pendingAmount);
}

function Pending({ edge }: { edge: PlanEdge }) {
  const pending = amountToCents(edge.pendingAmount);
  if (pending <= 0) return null;
  return <p className="text-sm text-gray-600">{formatEuro(pending)} awaiting confirmation</p>;
}

/** The inline form of one "You owe" row. The amount starts at what is still open and can only go down. */
function PaymentForm({ groupId, edge, onPaid }: { groupId: string; edge: PlanEdge; onPaid: () => void }) {
  const open = openCents(edge);
  const [amount, setAmount] = useState(centsToAmount(open));
  const [note, setNote] = useState("");
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  // State updates are not visible to a second submit in the same tick; the ref is.
  const sending = useRef(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (sending.current) return;
    const cents = eurosToCents(amount);
    if (cents === null) return setError("Please enter a valid amount, e.g. 19,99.");
    if (cents > open) return setError(`You can only pay up to ${formatEuro(open)} here.`);
    sending.current = true;
    setBusy(true);
    try {
      await api(`/groups/${groupId}/settlement-payments`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          toCreditor: edge.toCreditor,
          amount: centsToAmount(cents),
          ...(note.trim() ? { note: note.trim() } : {}),
        }),
      });
      setError(undefined);
      onPaid();
    } catch (err) {
      setError(messageFor(err));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="mt-2 flex flex-col gap-2 rounded border p-3">
      <label className="flex flex-col gap-1">
        Amount (€)
        <input
          className="rounded border px-2 py-2"
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
      </label>
      <label className="flex flex-col gap-1">
        Note (optional)
        <input className="rounded border px-2 py-2" value={note} maxLength={200} onChange={(e) => setNote(e.target.value)} />
      </label>
      {error && (
        <p role="alert" className="text-red-700">
          {error}
        </p>
      )}
      <button type="submit" disabled={busy} className="rounded bg-black px-3 py-2 text-white disabled:opacity-50">
        Send payment
      </button>
    </form>
  );
}

function OweRow({ groupId, edge, onPaid }: { groupId: string; edge: PlanEdge; onPaid: () => void }) {
  const [formOpen, setFormOpen] = useState(false);
  return (
    <li className="px-3 py-2">
      <div className="flex items-center justify-between gap-2">
        <span>{`${formatEuro(amountToCents(edge.amount))} to ${edge.toCreditorName}`}</span>
        {openCents(edge) > 0 && !formOpen && (
          <button type="button" onClick={() => setFormOpen(true)} className="rounded border px-3 py-1">
            I paid
          </button>
        )}
      </div>
      <Pending edge={edge} />
      {formOpen && (
        <PaymentForm
          groupId={groupId}
          edge={edge}
          onPaid={() => {
            setFormOpen(false);
            onPaid();
          }}
        />
      )}
    </li>
  );
}

function Section({ title, quiet, children }: { title: string; quiet?: boolean; children: React.ReactNode }) {
  return (
    <section aria-label={title} className={`flex flex-col gap-1 ${quiet ? "text-sm text-gray-600" : ""}`}>
      <h2 className="font-medium">{title}</h2>
      <ul className="divide-y rounded border">{children}</ul>
    </section>
  );
}

/**
 * The settle-up plan of a group (R10 to R12): who pays whom how much. Everybody sees the whole plan; the rows that
 * involve the current user come first. "I paid" reports a payment, which the creditor still has to confirm.
 */
export function SettleUpTab({ groupId }: { groupId: string }) {
  const me = useCurrentUser();
  const { data: plan, error, reload } = useApi<PlanEdge[]>(`/groups/${groupId}/settlement-plan`);
  const [paidAt, setPaidAt] = useState<number>();
  const [notice, setNotice] = useState<string>();
  const dismissNotice = useCallback(() => setNotice(undefined), []);

  useLookAgain(paidAt, reload);

  if (error && !plan) return <ErrorBanner error={error} />;
  if (!plan || !me) return <p>Loading…</p>;

  const owe = plan.filter((e) => e.fromDebtor === me.userId);
  const owed = plan.filter((e) => e.toCreditor === me.userId);
  const others = plan.filter((e) => e.fromDebtor !== me.userId && e.toCreditor !== me.userId);

  return (
    <div className="flex flex-col gap-4">
      <ErrorBanner error={error} />
      {plan.length === 0 && <p>Everybody is settled.</p>}
      {owe.length > 0 && (
        <Section title="You owe">
          {owe.map((e) => (
            <OweRow
              key={e.toCreditor}
              groupId={groupId}
              edge={e}
              onPaid={() => {
                void reload();
                setPaidAt(Date.now());
                setNotice("Payment reported. It counts once it is confirmed.");
              }}
            />
          ))}
        </Section>
      )}
      {owed.length > 0 && (
        <Section title="Owed to you">
          {owed.map((e) => (
            <li key={e.fromDebtor} className="px-3 py-2">
              <span>{`${e.fromDebtorName} owes ${formatEuro(amountToCents(e.amount))}`}</span>
              <Pending edge={e} />
            </li>
          ))}
        </Section>
      )}
      {others.length > 0 && (
        <Section title="Others" quiet>
          {others.map((e) => (
            <li key={`${e.fromDebtor}-${e.toCreditor}`} className="px-3 py-2">
              {`${e.fromDebtorName} owes ${e.toCreditorName} ${formatEuro(amountToCents(e.amount))}`}
            </li>
          ))}
        </Section>
      )}
      {notice && <Toast message={notice} onDismiss={dismissNotice} />}
    </div>
  );
}
