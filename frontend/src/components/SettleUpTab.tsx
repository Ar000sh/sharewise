"use client";

import { useCallback, useRef, useState } from "react";
import type { components } from "@/api/schema";
import { Avatar } from "@/components/Avatar";
import { Dialog } from "@/components/Dialog";
import { ErrorBanner } from "@/components/ErrorBanner";
import { PaymentHistory } from "@/components/PaymentHistory";
import { Toast } from "@/components/Toast";
import { WaiveDebtForm, payableCents } from "@/components/WaiveDebtForm";
import { api } from "@/lib/api";
import { useCurrentUser } from "@/lib/currentUser";
import { messageFor } from "@/lib/errors";
import { amountToCents, centsToAmount, eurosToCents, formatEuro } from "@/lib/money";
import { useApi } from "@/lib/useApi";
import { useGroupChange } from "@/lib/useGroupChange";
import { useAnswerPayment, useInbox } from "@/lib/useInbox";
import { useLookAgain } from "@/lib/useLookAgain";

type PlanEdge = components["schemas"]["SettlementPlanEdge"];
type SettlementPayment = components["schemas"]["SettlementPayment"];

/** Which dialog is open: the person it is about, never a plan entry (the entry is read fresh from the polled plan). */
type OpenDialog =
  | { kind: "report"; creditor: string; creditorName: string }
  | { kind: "waive"; debtor: string; debtorName: string }
  | { kind: "reject"; payment: SettlementPayment };

const euro = (amount: string) => formatEuro(amountToCents(amount));

/** "27 Jul, 18:40" from an API timestamp, in the viewer's time zone. */
function reportedAt(timestamp: string): string {
  return new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }).format(
    new Date(timestamp),
  );
}

/**
 * Report a payment to one creditor (R16, board B-12). Like the waiver, it is about one person: `edge` is their current
 * plan entry or `undefined` once the debt is gone (design 2026-11-23, F2). The amount starts at what the server
 * accepts right now (`payableAmount`) and can only go down.
 */
function ReportPaymentForm({
  groupId,
  creditorName,
  edge,
  onPaid,
  onCancel,
}: {
  groupId: string;
  creditorName: string;
  edge: PlanEdge | undefined;
  onPaid: () => void;
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
        <p>Nothing is open with {creditorName} any more.</p>
        <button type="button" onClick={onCancel} className="btn-secondary self-start">
          Close
        </button>
      </div>
    );
  }

  const payableNow = payableCents(edge);
  const amount = typedAmount ?? centsToAmount(payableNow);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (sending.current || !edge) return;
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
      onPaid();
    } catch (err) {
      setError(messageFor(err));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <p>Tell {creditorName} you paid. It stays pending until they answer.</p>
      <div className="flex flex-col gap-1">
        <label htmlFor="report-amount" className="font-bold">
          Amount (EUR)
        </label>
        <input
          id="report-amount"
          aria-describedby="report-amount-hint"
          inputMode="decimal"
          value={amount}
          onChange={(e) => setTypedAmount(e.target.value)}
        />
        <p id="report-amount-hint" className="text-sm text-muted">
          Up to {formatEuro(payableNow)}, what you owe {creditorName}.
        </p>
      </div>
      <label className="flex flex-col gap-1 font-bold">
        Note (optional)
        <input className="font-normal" value={note} maxLength={200} onChange={(e) => setNote(e.target.value)} />
      </label>
      {error && (
        <p role="alert" className="font-bold text-negative">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-3">
        <button type="submit" disabled={busy} className="btn-primary">
          Report payment
        </button>
        <button type="button" onClick={onCancel} className="btn-secondary">
          Cancel
        </button>
      </div>
    </form>
  );
}

/** Reject a reported payment with a reason, so the debtor can fix it (R16). */
function RejectPaymentForm({ payment, onDone, onCancel }: { payment: SettlementPayment; onDone: () => void; onCancel: () => void }) {
  const { reject } = useAnswerPayment();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy || !reason.trim()) return;
    setBusy(true);
    try {
      await reject(payment, reason.trim());
      onDone();
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <p>
        {payment.fromDebtorName} reported {euro(payment.amount)} on {reportedAt(payment.createdAt)}.
        {payment.note && ` Note: “${payment.note}”`}
      </p>
      <label className="flex flex-col gap-1 font-bold">
        Reason
        <input className="font-normal" value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} />
      </label>
      <p className="-mt-3 text-sm text-muted">Say why you reject it, so {payment.fromDebtorName} can fix it.</p>
      <ErrorBanner error={error} />
      <div className="flex flex-wrap gap-3">
        <button type="submit" disabled={busy || !reason.trim()} className="btn-primary">
          Reject payment
        </button>
        <button type="button" onClick={onCancel} className="btn-secondary">
          Cancel
        </button>
      </div>
    </form>
  );
}

/** One payment that waits for the viewer's answer, in the group it belongs to. */
function WaitingPaymentRow({ payment, onReject }: { payment: SettlementPayment; onReject: () => void }) {
  const { confirm } = useAnswerPayment();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();

  async function confirmIt() {
    setBusy(true);
    setError(undefined);
    try {
      await confirm(payment);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-3 px-5 py-4">
      <div className="flex items-start gap-4">
        <Avatar userId={payment.fromDebtor} name={payment.fromDebtorName} />
        <div className="flex flex-col">
          <span className="font-bold">
            {payment.fromDebtorName} says they paid you {euro(payment.amount)}
          </span>
          <span className="text-sm">
            Reported {reportedAt(payment.createdAt)}
            {payment.note && ` · Note: “${payment.note}”`}
          </span>
        </div>
      </div>
      <ErrorBanner error={error} />
      <div className="flex flex-wrap gap-3">
        <button
          type="button"
          disabled={busy}
          onClick={() => void confirmIt()}
          aria-label={`Confirm payment from ${payment.fromDebtorName}`}
          className="btn-primary"
        >
          Confirm
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={onReject}
          aria-label={`Reject payment from ${payment.fromDebtorName}`}
          className="btn-secondary"
        >
          Reject with reason
        </button>
      </div>
    </li>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section aria-label={title} className="flex flex-col gap-3">
      <h2 className="text-2xl">{title}</h2>
      {children}
    </section>
  );
}

/** A plan row: who pays whom how much, with what is already reported and the row's action. */
function PlanRow({
  personId,
  personName,
  text,
  note,
  action,
}: {
  personId: string;
  personName: string;
  text: string;
  note?: string;
  action?: React.ReactNode;
}) {
  return (
    <li className="flex flex-wrap items-center gap-4 px-5 py-4">
      <Avatar userId={personId} name={personName} />
      <div className="flex min-w-0 flex-1 flex-col">
        <span className="font-bold">{text}</span>
        {note && <span className="text-sm text-muted">{note}</span>}
      </div>
      {action}
    </li>
  );
}

/**
 * Settle up (R15 to R17, boards B-07 and B-08): what needs the viewer comes first (payments waiting for their answer),
 * then what they can receive, what they owe and the rest of the plan, then the payment history. Reporting a payment,
 * rejecting one and waiving a debt open a dialog about that one person.
 */
export function SettleUpTab({ groupId }: { groupId: string }) {
  const me = useCurrentUser();
  const { data: plan, error, reload } = useApi<PlanEdge[]>(`/groups/${groupId}/settlement-plan`, { poll: true });
  const { inbox } = useInbox();
  const { lastChangedAt, markChanged } = useGroupChange(groupId);
  const [dialog, setDialog] = useState<OpenDialog>();
  const [notice, setNotice] = useState<string>();
  const dismissNotice = useCallback(() => setNotice(undefined), []);
  const closeDialog = useCallback(() => setDialog(undefined), []);

  useLookAgain(lastChangedAt, reload);

  if (error && !plan) return <ErrorBanner error={error} onRetry={reload} />;
  if (!plan || !me) return <p>Loading…</p>;

  const waiting = (inbox?.payments ?? []).filter((p) => p.groupId === groupId);
  const owedByMe = plan.filter((edge) => edge.fromDebtor === me.userId);
  const owedToMe = plan.filter((edge) => edge.toCreditor === me.userId);
  const others = plan.filter((edge) => edge.fromDebtor !== me.userId && edge.toCreditor !== me.userId);

  const afterWrite = (message: string) => {
    setDialog(undefined);
    markChanged();
    setNotice(message);
  };

  return (
    <div className="flex flex-col gap-8">
      <ErrorBanner error={error} />

      {waiting.length > 0 && (
        <Section title="Needs your answer">
          <ul className="card divide-y border-accent">
            {waiting.map((payment) => (
              <WaitingPaymentRow key={payment.id} payment={payment} onReject={() => setDialog({ kind: "reject", payment })} />
            ))}
          </ul>
        </Section>
      )}

      {plan.length === 0 ? (
        <p className="card p-6 font-display text-2xl font-bold">Everybody is settled.</p>
      ) : (
        <>
          <Section title="You can receive">
            {owedToMe.length === 0 ? (
              <p className="text-muted">Nothing. No one owes you in this group.</p>
            ) : (
              <ul className="card divide-y">
                {owedToMe.map((edge) => {
                  const pending = amountToCents(edge.pendingAmount);
                  return (
                    <PlanRow
                      key={edge.fromDebtor}
                      personId={edge.fromDebtor}
                      personName={edge.fromDebtorName}
                      text={`${edge.fromDebtorName} pays you ${euro(edge.amount)}`}
                      note={pending > 0 ? `${formatEuro(pending)} reported, waiting for your answer` : undefined}
                      action={
                        payableCents(edge) > 0 && (
                          <button
                            type="button"
                            onClick={() => setDialog({ kind: "waive", debtor: edge.fromDebtor, debtorName: edge.fromDebtorName })}
                            className="btn-secondary"
                          >
                            Waive debt
                          </button>
                        )
                      }
                    />
                  );
                })}
              </ul>
            )}
          </Section>

          <Section title="You owe">
            {owedByMe.length === 0 ? (
              <p className="text-muted">Nothing. You owe no one in this group.</p>
            ) : (
              <ul className="card divide-y">
                {owedByMe.map((edge) => {
                  const pending = amountToCents(edge.pendingAmount);
                  const payable = payableCents(edge) > 0;
                  return (
                    <PlanRow
                      key={edge.toCreditor}
                      personId={edge.toCreditor}
                      personName={edge.toCreditorName}
                      text={`Pay ${edge.toCreditorName} ${euro(edge.amount)}`}
                      note={
                        pending > 0
                          ? `${formatEuro(pending)} awaiting confirmation`
                          : payable
                            ? "Not reported yet"
                            : "Not payable right now: payments that wait for an answer use up this amount."
                      }
                      action={
                        payable && (
                          <button
                            type="button"
                            onClick={() => setDialog({ kind: "report", creditor: edge.toCreditor, creditorName: edge.toCreditorName })}
                            className="btn-primary"
                          >
                            Report a payment
                          </button>
                        )
                      }
                    />
                  );
                })}
              </ul>
            )}
            <p className="text-sm text-muted">A reported payment stays pending until the person you paid answers.</p>
          </Section>

          {others.length > 0 && (
            <Section title="Between other members">
              <ul className="card divide-y">
                {others.map((edge) => (
                  <PlanRow
                    key={`${edge.fromDebtor}-${edge.toCreditor}`}
                    personId={edge.fromDebtor}
                    personName={edge.fromDebtorName}
                    text={`${edge.fromDebtorName} pays ${edge.toCreditorName} ${euro(edge.amount)}`}
                    note={amountToCents(edge.pendingAmount) > 0 ? "Payment pending" : undefined}
                  />
                ))}
              </ul>
            </Section>
          )}

          <p className="rounded-2xl bg-surface-2 px-4 py-3 text-sm">
            The plan uses the fewest payments: {plan.length} in total. The plan never changes balances. Only confirmed
            payments do.
          </p>
        </>
      )}

      <PaymentHistory groupId={groupId} currentUserId={me.userId} />

      {dialog?.kind === "report" && (
        <Dialog title="Report a payment" onClose={closeDialog}>
          <ReportPaymentForm
            groupId={groupId}
            creditorName={dialog.creditorName}
            edge={owedByMe.find((edge) => edge.toCreditor === dialog.creditor)}
            onPaid={() => afterWrite("Payment reported. It counts once it is confirmed.")}
            onCancel={closeDialog}
          />
        </Dialog>
      )}
      {dialog?.kind === "waive" && (
        <Dialog title="Waive a debt" onClose={closeDialog}>
          <WaiveDebtForm
            groupId={groupId}
            debtorName={dialog.debtorName}
            edge={owedToMe.find((edge) => edge.fromDebtor === dialog.debtor)}
            onWaived={() => afterWrite("Debt waived.")}
            onCancel={closeDialog}
          />
        </Dialog>
      )}
      {dialog?.kind === "reject" && (
        <Dialog title="Reject payment" onClose={closeDialog}>
          <RejectPaymentForm payment={dialog.payment} onDone={closeDialog} onCancel={closeDialog} />
        </Dialog>
      )}
      {notice && <Toast message={notice} onDismiss={dismissNotice} />}
    </div>
  );
}
