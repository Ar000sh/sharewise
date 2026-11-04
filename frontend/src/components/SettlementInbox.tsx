"use client";

import Link from "next/link";
import { useCallback, useRef, useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Toast } from "@/components/Toast";
import { api, ApiError } from "@/lib/api";
import { amountToCents, formatEuro } from "@/lib/money";
import { useApi } from "@/lib/useApi";

type SettlementPayment = components["schemas"]["SettlementPayment"];

/** The one request the inbox and the badge share: payments reported to me that I have not answered yet. */
export const PENDING_FOR_ME_PATH = "/me/settlement-payments?status=PENDING&role=creditor";

type Answer = "confirmed" | "rejected";

/** One reported payment with its two answers. A ref guards against two sends in the same tick. */
function InboxRow({
  payment,
  showGroup,
  onAnswer,
}: {
  payment: SettlementPayment;
  showGroup: boolean;
  onAnswer: (payment: SettlementPayment, answer: Answer, note?: string) => Promise<void>;
}) {
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);

  async function send(answer: Answer, note?: string) {
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    try {
      await onAnswer(payment, answer, note);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-2 px-3 py-2">
      <span>{`${payment.fromDebtorName} marked ${formatEuro(amountToCents(payment.amount))} as paid to you`}</span>
      {payment.note && <span className="text-sm text-foreground/70">{payment.note}</span>}
      {showGroup && (
        <Link href={`/groups/${payment.groupId}`} className="text-sm underline">
          Open group
        </Link>
      )}
      {!rejecting && (
        <div className="flex gap-2">
          <button
            type="button"
            disabled={busy}
            aria-label={`Confirm payment from ${payment.fromDebtorName}`}
            onClick={() => void send("confirmed")}
            className="rounded bg-foreground px-3 py-1 text-background disabled:opacity-50"
          >
            Confirm
          </button>
          <button
            type="button"
            disabled={busy}
            aria-label={`Reject payment from ${payment.fromDebtorName}`}
            onClick={() => setRejecting(true)}
            className="rounded border px-3 py-1 disabled:opacity-50"
          >
            Reject
          </button>
        </div>
      )}
      {rejecting && (
        <form
          className="flex flex-col gap-2 rounded border p-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (reason.trim()) void send("rejected", reason.trim());
          }}
        >
          <label className="flex flex-col gap-1">
            Reason
            <input className="rounded border px-2 py-2" value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} />
          </label>
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={busy || !reason.trim()}
              className="rounded bg-foreground px-3 py-1 text-background disabled:opacity-50"
            >
              Send rejection
            </button>
            <button type="button" disabled={busy} onClick={() => setRejecting(false)} className="rounded border px-3 py-1">
              Cancel
            </button>
          </div>
        </form>
      )}
    </li>
  );
}

/**
 * The settlement payments others reported to me and I still have to confirm or reject (R12). With a `groupId` only
 * that group's payments are shown. `onAnswered` runs after a successful answer, so the caller can look at the plan
 * again. `hideWhenEmpty` keeps a page quiet when nothing is waiting.
 */
export function SettlementInbox({
  groupId,
  onAnswered,
  hideWhenEmpty = false,
}: {
  groupId?: string;
  onAnswered?: () => void;
  hideWhenEmpty?: boolean;
}) {
  const { data, error, reload } = useApi<SettlementPayment[]>(PENDING_FOR_ME_PATH);
  const [answered, setAnswered] = useState<string[]>([]);
  const [actionError, setActionError] = useState<unknown>();
  const [notice, setNotice] = useState<string>();
  const dismissNotice = useCallback(() => setNotice(undefined), []);

  async function answer(payment: SettlementPayment, how: Answer, note?: string) {
    const route = how === "confirmed" ? "confirm" : "reject";
    try {
      await api(`/settlement-payments/${payment.id}/${route}`, {
        method: "POST",
        ...(note !== undefined ? { headers: { "content-type": "application/json" }, body: JSON.stringify({ note }) } : {}),
      });
    } catch (err) {
      setActionError(err);
      // Somebody (or another tab) answered it already: the row is stale, so load the list again.
      if (err instanceof ApiError && err.code === "PAYMENT_NOT_PENDING") void reload();
      return;
    }
    setActionError(undefined);
    setAnswered((ids) => [...ids, payment.id]);
    setNotice(how === "confirmed" ? "Payment confirmed. Balances update in a moment." : "Payment rejected.");
    onAnswered?.();
  }

  if (error && !data) return <ErrorBanner error={error} />;
  if (!data) return hideWhenEmpty ? null : <p>Loading…</p>;

  const rows = data.filter((p) => (groupId === undefined || p.groupId === groupId) && !answered.includes(p.id));
  if (rows.length === 0 && hideWhenEmpty) return notice ? <Toast message={notice} onDismiss={dismissNotice} /> : null;

  return (
    <section aria-label="Waiting for your answer" className="flex flex-col gap-2">
      <ErrorBanner error={actionError} />
      {rows.length === 0 ? (
        <p>Nothing is waiting for your answer.</p>
      ) : (
        <ul className="divide-y rounded border">
          {rows.map((p) => (
            <InboxRow key={p.id} payment={p} showGroup={groupId === undefined} onAnswer={answer} />
          ))}
        </ul>
      )}
      {notice && <Toast message={notice} onDismiss={dismissNotice} />}
    </section>
  );
}
