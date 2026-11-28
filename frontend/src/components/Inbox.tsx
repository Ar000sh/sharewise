"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ApiError, api } from "@/lib/api";
import { amountToCents, formatEuro } from "@/lib/money";
import { useAnswerPayment, useInbox, useReloadInbox } from "@/lib/useInbox";

type PendingInvitation = components["schemas"]["PendingInvitation"];
type Notification = components["schemas"]["Notification"];
type SettlementPayment = components["schemas"]["SettlementPayment"];
type Group = components["schemas"]["Group"];

/** The sentence of an entry. Names are plain text: React escapes them, nothing here builds HTML. */
function describe(entry: Notification): string {
  const who = entry.actor?.displayName ?? "Someone";
  const group = entry.group?.name ?? "a group";
  switch (entry.type) {
    case "MEMBER_JOINED":
      return `${who} joined ${group}`;
    case "INVITATION_DECLINED":
      return `${who} declined your invitation to ${group}`;
    case "MEMBER_PROMOTED":
      return `${who} made you an admin of ${group}`;
    case "MEMBER_REMOVED":
      return `${who} removed you from ${group}`;
    case "GROUP_CLOSED":
      return `${who} closed ${group} for settlement`;
    case "GROUP_REOPENED":
      return `${who} reopened ${group}`;
    case "GROUP_ARCHIVED":
      // An automatic archive has no actor: the last balance reached 0.
      return entry.actor ? `${who} archived ${group}` : `${group} was archived: everybody is settled`;
    case "PAYMENT_CONFIRMED":
      return `${who} confirmed your payment in ${group}`;
    case "PAYMENT_REJECTED":
      return `${who} rejected your payment in ${group}`;
    case "DEBT_WAIVED":
      return `${who} waived part of your debt in ${group}`;
    case "EXPENSE_RECORDED":
      return `${who} recorded an expense in ${group}`;
  }
}

/**
 * One payment waiting for my answer (R16): confirm, or reject with a reason. The caller sends the answer and asks the
 * inbox again; `busy` disables the buttons meanwhile, so a double click sends one request.
 */
function PaymentRow({
  payment,
  busy,
  onConfirm,
  onReject,
}: {
  payment: SettlementPayment;
  busy: boolean;
  onConfirm: () => void;
  onReject: (reason: string) => void;
}) {
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  return (
    <li className="flex flex-col gap-2 px-5 py-4">
      <span className="font-bold">{`${payment.fromDebtorName} marked ${formatEuro(amountToCents(payment.amount))} as paid to you`}</span>
      {payment.note && <span className="text-sm text-muted">Note: “{payment.note}”</span>}
      <Link href={`/groups/${payment.groupId}`} className="self-start text-sm font-bold underline">
        Open group
      </Link>
      {!rejecting && (
        <span className="flex flex-wrap gap-3">
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            aria-label={`Confirm payment from ${payment.fromDebtorName}`}
            className="btn-primary"
          >
            Confirm
          </button>
          <button
            type="button"
            onClick={() => setRejecting(true)}
            disabled={busy}
            aria-label={`Reject payment from ${payment.fromDebtorName}`}
            className="btn-secondary"
          >
            Reject
          </button>
        </span>
      )}
      {rejecting && (
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (reason.trim()) onReject(reason.trim());
          }}
        >
          <label className="flex flex-col gap-1 font-bold">
            Reason
            <input className="font-normal" value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} />
          </label>
          <span className="flex flex-wrap gap-3">
            <button type="submit" disabled={busy || !reason.trim()} className="btn-primary">
              Send rejection
            </button>
            <button type="button" disabled={busy} onClick={() => setRejecting(false)} className="btn-secondary">
              Cancel
            </button>
          </span>
        </form>
      )}
    </li>
  );
}

/**
 * The inbox page (board B-03, design 2026-11-23, U5): what waits for my answer (state: open invitations to accept or
 * decline, payments to confirm or reject) first, then what happened (entries). One call to `/api/inbox` returns the
 * three parts; each may be null when its source failed. It reads the shared inbox query, so an answer given here
 * disappears from a group's Settle up tab too (F3).
 */
export function Inbox() {
  const router = useRouter();
  const { inbox, error: loadError } = useInbox();
  const reloadInbox = useReloadInbox();
  const { confirm, reject } = useAnswerPayment();
  const [busyId, setBusyId] = useState<string>();
  const [error, setError] = useState<unknown>();

  /**
   * Runs one action, then asks again so the screen shows the truth. A 404 means the thing is already done (an
   * invitation answered in a second tab, a double click): nothing to report, just refresh. Payments have the same rule
   * inside `useAnswerPayment`.
   */
  async function perform(action: () => Promise<unknown>, rowId?: string) {
    setBusyId(rowId);
    setError(undefined);
    try {
      await action();
    } catch (e) {
      const alreadyDone = e instanceof ApiError && e.status === 404;
      if (!alreadyDone) setError(e);
    } finally {
      setBusyId(undefined);
    }
    await reloadInbox();
  }

  const accept = (invitation: PendingInvitation) =>
    perform(async () => {
      const group = await api<Group>(`/invitations/${invitation.invitationId}/accept`, { method: "POST" });
      router.push(`/groups/${group.id}`);
    }, invitation.invitationId);

  const decline = (invitation: PendingInvitation) =>
    perform(() => api(`/invitations/${invitation.invitationId}/decline`, { method: "POST" }), invitation.invitationId);

  const confirmPayment = (payment: SettlementPayment) => perform(() => confirm(payment), payment.id);

  const rejectPayment = (payment: SettlementPayment, note: string) => perform(() => reject(payment, note), payment.id);

  const markRead = (entry: Notification) => perform(() => api(`/notifications/${entry.id}/read`, { method: "POST" }));

  const markAllRead = () => perform(() => api("/notifications/read-all", { method: "POST" }));

  if (loadError && !inbox) return <ErrorBanner error={loadError} onRetry={() => void reloadInbox()} />;
  if (!inbox) return <p>Loading…</p>;

  const { invitations, notifications, payments } = inbox;
  const openInvitations = invitations ?? [];
  const waitingPayments = payments ?? [];
  const entries = notifications?.items ?? [];
  const waiting = openInvitations.length + waitingPayments.length;
  const empty = waiting === 0 && entries.length === 0;

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-4xl sm:text-5xl">Inbox</h1>
        {notifications && notifications.unreadCount > 0 && (
          <button type="button" onClick={() => void markAllRead()} className="btn-secondary">
            Mark all as read
          </button>
        )}
      </div>
      <ErrorBanner error={error} />
      {invitations === null && <p className="text-muted">Invitations could not be loaded.</p>}
      {payments === null && <p className="text-muted">Payments could not be loaded.</p>}
      {notifications === null && <p className="text-muted">Entries could not be loaded.</p>}

      {waiting > 0 && (
        <section aria-label="Needs your answer" className="flex flex-col gap-3">
          <h2 className="text-2xl">Needs your answer</h2>
          <ul className="card divide-y border-accent">
            {openInvitations.map((invitation) => (
              <li key={invitation.invitationId} className="flex flex-col gap-2 px-5 py-4">
                <span className="font-bold">{`${invitation.invitedBy.displayName} invited you to ${invitation.group.name}${invitation.role === "ADMIN" ? " as an admin" : ""}`}</span>
                <span className="text-sm text-muted">You only share in expenses recorded after you join.</span>
                <span className="flex flex-wrap gap-3">
                  <button
                    type="button"
                    onClick={() => void accept(invitation)}
                    disabled={busyId === invitation.invitationId}
                    aria-label={`Accept invitation to ${invitation.group.name}`}
                    className="btn-primary"
                  >
                    Accept
                  </button>
                  <button
                    type="button"
                    onClick={() => void decline(invitation)}
                    disabled={busyId === invitation.invitationId}
                    aria-label={`Decline invitation to ${invitation.group.name}`}
                    className="btn-secondary"
                  >
                    Decline
                  </button>
                </span>
              </li>
            ))}
            {waitingPayments.map((payment) => (
              <PaymentRow
                key={payment.id}
                payment={payment}
                busy={busyId === payment.id}
                onConfirm={() => void confirmPayment(payment)}
                onReject={(note) => void rejectPayment(payment, note)}
              />
            ))}
          </ul>
        </section>
      )}

      {entries.length > 0 && (
        <section aria-label="Notifications" className="flex flex-col gap-3">
          <h2 className="text-2xl">Notifications</h2>
          <ul className="card divide-y">
            {entries.map((entry) => (
              <li key={entry.id} className="flex items-center gap-3 px-5 py-4">
                {entry.readAt ? (
                  <span className="flex-1 text-muted">{describe(entry)}</span>
                ) : (
                  <>
                    <button type="button" onClick={() => void markRead(entry)} className="flex-1 text-left font-bold">
                      {describe(entry)}
                    </button>
                    <span className="rounded-full bg-warning px-3 py-0.5 text-sm font-bold text-on-warning">New</span>
                  </>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      {empty && invitations !== null && payments !== null && notifications !== null && (
        <p className="card p-6 text-muted">Inbox is empty. No invitations, no payments waiting, no news.</p>
      )}
    </div>
  );
}
