"use client";

import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ApiError, api } from "@/lib/api";
import { amountToCents, formatEuro } from "@/lib/money";
import { markGroupChanged } from "@/lib/useGroupChange";

type PendingInvitation = components["schemas"]["PendingInvitation"];
type NotificationList = components["schemas"]["NotificationList"];
type Notification = components["schemas"]["Notification"];
type SettlementPayment = components["schemas"]["SettlementPayment"];
type Group = components["schemas"]["Group"];
type Inbox = {
  invitations: PendingInvitation[] | null;
  notifications: NotificationList | null;
  payments: SettlementPayment[] | null;
};

/** How often the bell asks again, in milliseconds. It also asks when the tab becomes visible. */
const POLL_MS = 30_000;

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
 * One payment waiting for my answer (R16): confirm, or reject with a reason. The caller sends the answer and reloads
 * the bell; `busy` disables the buttons meanwhile, so a double click sends one request.
 */
function PaymentRow({
  payment,
  busy,
  onConfirm,
  onReject,
  onOpenGroup,
}: {
  payment: SettlementPayment;
  busy: boolean;
  onOpenGroup: () => void;
  onConfirm: () => void;
  onReject: (reason: string) => void;
}) {
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  return (
    <li className="flex flex-col gap-1 border-l-4 border-positive pl-3">
      <span>{`${payment.fromDebtorName} marked ${formatEuro(amountToCents(payment.amount))} as paid to you`}</span>
      {payment.note && <span className="text-muted">{payment.note}</span>}
      <Link href={`/groups/${payment.groupId}`} onClick={onOpenGroup} className="underline">
        Open group
      </Link>
      {!rejecting && (
        <span className="flex gap-2">
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
          className="flex flex-col gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            if (reason.trim()) onReject(reason.trim());
          }}
        >
          <label className="flex flex-col gap-1">
            Reason
            <input value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} />
          </label>
          <span className="flex gap-2">
            <button
              type="submit"
              disabled={busy || !reason.trim()}
              className="btn-primary"
            >
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
 * The bell in the header: what waits for my answer (state: open invitations to accept or decline, payments to confirm
 * or reject) first, then what happened (entries). The badge counts open invitations, waiting payments and unread
 * entries. One call to `/api/inbox` returns the three parts; each may be null when its source failed.
 */
export function InboxBell() {
  const router = useRouter();
  const client = useQueryClient();
  const [inbox, setInbox] = useState<Inbox>();
  const [open, setOpen] = useState(false);
  const [busyId, setBusyId] = useState<string>();
  const [error, setError] = useState<unknown>();

  const load = useCallback(async () => {
    try {
      setInbox(await api<Inbox>("/inbox"));
    } catch {
      // The next poll tries again; a 401 has already sent the visitor to sign in (in api()).
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- data fetching on mount, as in useApi
    void load();
    const timer = setInterval(() => void load(), POLL_MS);
    const onVisible = () => {
      if (document.visibilityState !== "hidden") void load();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [load]);

  /**
   * Runs one action of the bell, then asks again so the screen shows the truth. A 404, or a payment that is no longer
   * pending, means the thing is already done (answered in a second tab, a double click): nothing to report, just
   * refresh.
   */
  async function perform(action: () => Promise<unknown>, rowId?: string) {
    setBusyId(rowId);
    setError(undefined);
    try {
      await action();
    } catch (e) {
      const alreadyDone = e instanceof ApiError && (e.status === 404 || e.code === "PAYMENT_NOT_PENDING");
      if (!alreadyDone) setError(e);
    } finally {
      setBusyId(undefined);
    }
    await load();
  }

  const accept = (invitation: PendingInvitation) =>
    perform(async () => {
      const group = await api<Group>(`/invitations/${invitation.invitationId}/accept`, { method: "POST" });
      setOpen(false);
      router.push(`/groups/${group.id}`);
    }, invitation.invitationId);

  const decline = (invitation: PendingInvitation) =>
    perform(() => api(`/invitations/${invitation.invitationId}/decline`, { method: "POST" }), invitation.invitationId);

  const confirmPayment = (payment: SettlementPayment) =>
    perform(async () => {
      await api(`/settlement-payments/${payment.id}/confirm`, { method: "POST" });
      markGroupChanged(client, payment.groupId);
    }, payment.id);

  const rejectPayment = (payment: SettlementPayment, note: string) =>
    perform(async () => {
      await api(`/settlement-payments/${payment.id}/reject`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ note }),
      });
      markGroupChanged(client, payment.groupId);
    }, payment.id);

  const markRead = (entry: Notification) => perform(() => api(`/notifications/${entry.id}/read`, { method: "POST" }));

  const markAllRead = () => perform(() => api("/notifications/read-all", { method: "POST" }));

  const invitations = inbox?.invitations;
  const notifications = inbox?.notifications;
  const payments = inbox?.payments;
  const openInvitations = invitations ?? [];
  const waitingPayments = payments ?? [];
  const entries = notifications?.items ?? [];
  const count = openInvitations.length + waitingPayments.length + (notifications?.unreadCount ?? 0);
  const empty = openInvitations.length === 0 && waitingPayments.length === 0 && entries.length === 0;

  return (
    // Not `relative`: the dropdown is placed against the whole header group (AuthBar), so it opens at the right edge of
    // the screen, never over the page's tabs and never off the left edge of a phone.
    <div>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label={count > 0 ? `Inbox, ${count} new` : "Inbox"}
        aria-expanded={open}
        className="btn-secondary"
      >
        <span aria-hidden="true">Inbox</span>
        {count > 0 && (
          <span className="rounded-full bg-accent px-2 text-xs font-extrabold text-on-accent">{count}</span>
        )}
      </button>
      {open && (
        <div role="region" aria-label="Inbox entries" className="card absolute right-0 z-10 mt-2 flex w-80 max-w-[90vw] flex-col gap-3 p-4 text-sm text-foreground shadow-xl">
          <ErrorBanner error={error} />
          {invitations === null && <p className="text-muted">Invitations could not be loaded.</p>}
          {payments === null && <p className="text-muted">Payments could not be loaded.</p>}
          {notifications === null && <p className="text-muted">Entries could not be loaded.</p>}
          <ul className="flex flex-col gap-2">
            {openInvitations.map((invitation) => (
              <li key={invitation.invitationId} className="flex flex-col gap-1 border-l-4 border-accent pl-3">
                <span>{`${invitation.invitedBy.displayName} invited you to ${invitation.group.name}${invitation.role === "ADMIN" ? " as an admin" : ""}`}</span>
                <span className="flex gap-2">
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
                onOpenGroup={() => setOpen(false)}
                onConfirm={() => void confirmPayment(payment)}
                onReject={(note) => void rejectPayment(payment, note)}
              />
            ))}
            {entries.map((entry) => (
              <li key={entry.id}>
                {entry.readAt ? (
                  <span className="text-muted">{describe(entry)}</span>
                ) : (
                  <button type="button" onClick={() => void markRead(entry)} className="text-left font-medium">
                    {describe(entry)}
                  </button>
                )}
              </li>
            ))}
          </ul>
          {empty && invitations !== null && payments !== null && notifications !== null && <p className="text-muted">Nothing new.</p>}
          {notifications && notifications.unreadCount > 0 && (
            <button type="button" onClick={() => void markAllRead()} className="self-start underline">
              Mark all as read
            </button>
          )}
        </div>
      )}
    </div>
  );
}
