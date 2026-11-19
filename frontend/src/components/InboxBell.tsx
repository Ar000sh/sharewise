"use client";

import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Icon } from "@/components/Icon";
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
    <li className="flex flex-col gap-1 border-l-4 border-green-600 pl-2">
      <span>{`${payment.fromDebtorName} marked ${formatEuro(amountToCents(payment.amount))} as paid to you`}</span>
      {payment.note && (
        <span className="text-foreground/70">{payment.note}</span>
      )}
      <Link
        href={`/groups/${payment.groupId}`}
        onClick={onOpenGroup}
        className="underline"
      >
        Open group
      </Link>
      {!rejecting && (
        <span className="flex gap-2">
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            aria-label={`Confirm payment from ${payment.fromDebtorName}`}
            className="rounded bg-foreground px-2 py-1 text-background disabled:opacity-50"
          >
            Confirm
          </button>
          <button
            type="button"
            onClick={() => setRejecting(true)}
            disabled={busy}
            aria-label={`Reject payment from ${payment.fromDebtorName}`}
            className="rounded border px-2 py-1 disabled:opacity-50"
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
            <input
              className="rounded border px-2 py-1"
              value={reason}
              maxLength={200}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <span className="flex gap-2">
            <button
              type="submit"
              disabled={busy || !reason.trim()}
              className="rounded bg-foreground px-2 py-1 text-background disabled:opacity-50"
            >
              Send rejection
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => setRejecting(false)}
              className="rounded border px-2 py-1"
            >
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
  const container = useRef<HTMLDivElement>(null);
  const toggle = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const dismissOutside = (event: PointerEvent) => {
      if (
        event.target instanceof Node &&
        !container.current?.contains(event.target)
      )
        setOpen(false);
    };
    const dismissOnEscape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      setOpen(false);
      toggle.current?.focus();
    };
    document.addEventListener("pointerdown", dismissOutside);
    document.addEventListener("keydown", dismissOnEscape);
    return () => {
      document.removeEventListener("pointerdown", dismissOutside);
      document.removeEventListener("keydown", dismissOnEscape);
    };
  }, [open]);

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
      const alreadyDone =
        e instanceof ApiError &&
        (e.status === 404 || e.code === "PAYMENT_NOT_PENDING");
      if (!alreadyDone) setError(e);
    } finally {
      setBusyId(undefined);
    }
    await load();
  }

  const accept = (invitation: PendingInvitation) =>
    perform(async () => {
      const group = await api<Group>(
        `/invitations/${invitation.invitationId}/accept`,
        { method: "POST" },
      );
      setOpen(false);
      router.push(`/groups/${group.id}`);
    }, invitation.invitationId);

  const decline = (invitation: PendingInvitation) =>
    perform(
      () =>
        api(`/invitations/${invitation.invitationId}/decline`, {
          method: "POST",
        }),
      invitation.invitationId,
    );

  const confirmPayment = (payment: SettlementPayment) =>
    perform(async () => {
      await api(`/settlement-payments/${payment.id}/confirm`, {
        method: "POST",
      });
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

  const markRead = (entry: Notification) =>
    perform(() => api(`/notifications/${entry.id}/read`, { method: "POST" }));

  const markAllRead = () =>
    perform(() => api("/notifications/read-all", { method: "POST" }));

  const invitations = inbox?.invitations;
  const notifications = inbox?.notifications;
  const payments = inbox?.payments;
  const openInvitations = invitations ?? [];
  const waitingPayments = payments ?? [];
  const entries = notifications?.items ?? [];
  const count =
    openInvitations.length +
    waitingPayments.length +
    (notifications?.unreadCount ?? 0);
  const empty =
    openInvitations.length === 0 &&
    waitingPayments.length === 0 &&
    entries.length === 0;

  return (
    <div ref={container} className="relative">
      <button
        ref={toggle}
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label={count > 0 ? `Inbox, ${count} new` : "Inbox"}
        aria-expanded={open}
        aria-controls="inbox-entries"
        className="button-secondary relative size-11 p-2"
      >
        <Icon name="bell" />
        {count > 0 && (
          <span className="absolute -top-1 -right-1 flex min-h-4 min-w-4 items-center justify-center rounded-full bg-accent px-1 text-[10px] text-accent-foreground">
            {count}
          </span>
        )}
      </button>
      {open && (
        <div
          id="inbox-entries"
          role="region"
          aria-label="Inbox entries"
          className="panel fixed inset-x-4 top-36 z-30 flex max-h-[70vh] flex-col gap-4 overflow-y-auto p-5 text-sm shadow-xl sm:absolute sm:inset-x-auto sm:top-auto sm:right-0 sm:mt-3 sm:w-96 sm:max-w-[90vw]"
        >
          <div className="flex items-center justify-between">
            <h2 className="font-semibold">Your inbox</h2>
            <button
              type="button"
              aria-label="Close inbox"
              onClick={() => {
                setOpen(false);
                toggle.current?.focus();
              }}
              className="rounded-lg p-2 text-muted hover:bg-soft"
            >
              <Icon name="close" />
            </button>
          </div>
          <ErrorBanner error={error} />
          {invitations === null && (
            <p className="text-foreground/70">
              Invitations could not be loaded.
            </p>
          )}
          {payments === null && (
            <p className="text-foreground/70">Payments could not be loaded.</p>
          )}
          {notifications === null && (
            <p className="text-foreground/70">Entries could not be loaded.</p>
          )}
          <ul className="flex flex-col gap-2">
            {openInvitations.map((invitation) => (
              <li
                key={invitation.invitationId}
                className="flex flex-col gap-3 rounded-xl bg-soft p-4 text-foreground"
              >
                <span>{`${invitation.invitedBy.displayName} invited you to ${invitation.group.name}${invitation.role === "ADMIN" ? " as an admin" : ""}`}</span>
                <span className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => void accept(invitation)}
                    disabled={busyId === invitation.invitationId}
                    aria-label={`Accept invitation to ${invitation.group.name}`}
                    className="button-primary min-h-9 px-3 py-2 text-xs"
                  >
                    Accept
                  </button>
                  <button
                    type="button"
                    onClick={() => void decline(invitation)}
                    disabled={busyId === invitation.invitationId}
                    aria-label={`Decline invitation to ${invitation.group.name}`}
                    className="button-secondary min-h-9 px-3 py-2 text-xs"
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
              <li
                key={entry.id}
                className="border-t border-line py-3 leading-relaxed"
              >
                {entry.readAt ? (
                  <span className="text-foreground/60">{describe(entry)}</span>
                ) : (
                  <button
                    type="button"
                    onClick={() => void markRead(entry)}
                    className="text-left font-medium"
                  >
                    {describe(entry)}
                  </button>
                )}
              </li>
            ))}
          </ul>
          {empty &&
            invitations !== null &&
            payments !== null &&
            notifications !== null && (
              <p className="text-foreground/70">Nothing new.</p>
            )}
          {notifications && notifications.unreadCount > 0 && (
            <button
              type="button"
              onClick={() => void markAllRead()}
              className="self-start underline"
            >
              Mark all as read
            </button>
          )}
        </div>
      )}
    </div>
  );
}
