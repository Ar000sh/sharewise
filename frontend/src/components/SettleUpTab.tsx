"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Toast } from "@/components/Toast";
import { WaiveDebtForm, payableCents } from "@/components/WaiveDebtForm";
import { api } from "@/lib/api";
import { useCurrentUser } from "@/lib/currentUser";
import { messageFor } from "@/lib/errors";
import { amountToCents, centsToAmount, eurosToCents, formatEuro } from "@/lib/money";
import { PAYMENT_ANSWERED } from "@/lib/paymentAnswered";
import { useApi } from "@/lib/useApi";
import { useLookAgain } from "@/lib/useLookAgain";

type PlanEdge = components["schemas"]["SettlementPlanEdge"];

function PendingAmountNote({ edge }: { edge: PlanEdge }) {
  const pending = amountToCents(edge.pendingAmount);
  if (pending <= 0) return null;
  return <p className="text-sm text-foreground/70">{formatEuro(pending)} awaiting confirmation</p>;
}

/** The inline form of one "You owe" row. The amount starts at what the server accepts right now (`payableAmount`) and can only go down. */
function PaymentForm({ groupId, edge, onPaid }: { groupId: string; edge: PlanEdge; onPaid: () => void }) {
  const payableNow = payableCents(edge);
  const [amount, setAmount] = useState(centsToAmount(payableNow));
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
    if (cents > payableNow) return setError(`You can only pay up to ${formatEuro(payableNow)} here.`);
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
        <p role="alert" className="text-red-700 dark:text-red-400">
          {error}
        </p>
      )}
      <button type="submit" disabled={busy} className="rounded bg-foreground px-3 py-2 text-background disabled:opacity-50">
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
        {payableCents(edge) > 0 && !formOpen && (
          <button type="button" onClick={() => setFormOpen(true)} className="rounded border px-3 py-1">
            I paid
          </button>
        )}
      </div>
      <PendingAmountNote edge={edge} />
      {payableCents(edge) <= 0 && amountToCents(edge.pendingAmount) <= 0 && (
        <p className="text-sm text-foreground/70">Not payable right now: payments that wait for an answer use up this amount.</p>
      )}
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
    <section aria-label={title} className={`flex flex-col gap-1 ${quiet ? "text-sm text-foreground/70" : ""}`}>
      <h2 className="font-medium">{title}</h2>
      <ul className="divide-y rounded border">{children}</ul>
    </section>
  );
}

/**
 * The settle-up plan of a group (R15 to R17): who pays whom how much. Everybody sees the whole plan; the rows that
 * involve the current user come first. "I paid" reports a payment, which the creditor still has to confirm.
 */
export function SettleUpTab({ groupId }: { groupId: string }) {
  const me = useCurrentUser();
  const { data: plan, error, reload } = useApi<PlanEdge[]>(`/groups/${groupId}/settlement-plan`);
  const [changedAt, setChangedAt] = useState<number>();
  const [notice, setNotice] = useState<string>();
  // `resetKey` changes with every click on a "Waive" button so the form starts over with that debtor.
  const [waivePrefill, setWaivePrefill] = useState<{ fromDebtor: string; resetKey: number }>();
  const dismissNotice = useCallback(() => setNotice(undefined), []);

  useLookAgain(changedAt, reload);

  // The bell answers payments of any group; looking again at a plan that did not change is harmless.
  useEffect(() => {
    const onAnswered = () => setChangedAt(Date.now());
    window.addEventListener(PAYMENT_ANSWERED, onAnswered);
    return () => window.removeEventListener(PAYMENT_ANSWERED, onAnswered);
  }, []);

  if (error && !plan) return <ErrorBanner error={error} />;
  if (!plan || !me) return <p>Loading…</p>;

  const owedByMe = plan.filter((edge) => edge.fromDebtor === me.userId);
  const owedToMe = plan.filter((edge) => edge.toCreditor === me.userId);
  const waivable = owedToMe.filter((edge) => payableCents(edge) > 0);
  const others = plan.filter((edge) => edge.fromDebtor !== me.userId && edge.toCreditor !== me.userId);

  return (
    <div className="flex flex-col gap-4">
      <ErrorBanner error={error} />
      {plan.length === 0 && <p>Everybody is settled.</p>}
      {owedByMe.length > 0 && (
        <Section title="You owe">
          {owedByMe.map((edge) => (
            <OweRow
              key={edge.toCreditor}
              groupId={groupId}
              edge={edge}
              onPaid={() => {
                void reload();
                setChangedAt(Date.now());
                setNotice("Payment reported. It counts once it is confirmed.");
              }}
            />
          ))}
        </Section>
      )}
      {owedToMe.length > 0 && (
        <Section title="Owed to you">
          {owedToMe.map((edge) => (
            <li key={edge.fromDebtor} className="px-3 py-2">
              <div className="flex items-center justify-between gap-2">
                <span>{`${edge.fromDebtorName} owes ${formatEuro(amountToCents(edge.amount))}`}</span>
                {payableCents(edge) > 0 && (
                  <button
                    type="button"
                    onClick={() =>
                      setWaivePrefill({ fromDebtor: edge.fromDebtor, resetKey: (waivePrefill?.resetKey ?? 0) + 1 })
                    }
                    className="rounded border px-3 py-1"
                  >
                    Waive
                  </button>
                )}
              </div>
              <PendingAmountNote edge={edge} />
            </li>
          ))}
        </Section>
      )}
      {others.length > 0 && (
        <Section title="Others" quiet>
          {others.map((edge) => (
            <li key={`${edge.fromDebtor}-${edge.toCreditor}`} className="px-3 py-2">
              {`${edge.fromDebtorName} owes ${edge.toCreditorName} ${formatEuro(amountToCents(edge.amount))}`}
            </li>
          ))}
        </Section>
      )}
      {waivable.length > 0 && (
        <section aria-label="Waive manually" className="flex flex-col gap-1">
          <h2 className="font-medium">Waive manually</h2>
          <WaiveDebtForm
            key={waivePrefill?.resetKey ?? 0}
            groupId={groupId}
            waivableEdges={waivable}
            prefill={waivePrefill}
            onWaived={() => {
              void reload();
              setChangedAt(Date.now());
              setNotice("Debt waived.");
            }}
          />
        </section>
      )}
      {notice && <Toast message={notice} onDismiss={dismissNotice} />}
    </div>
  );
}
