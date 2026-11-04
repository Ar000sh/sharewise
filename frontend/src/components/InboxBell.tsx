"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ApiError, api } from "@/lib/api";

type PendingInvitation = components["schemas"]["PendingInvitation"];
type NotificationList = components["schemas"]["NotificationList"];
type Notification = components["schemas"]["Notification"];
type Group = components["schemas"]["Group"];
type Inbox = { invitations: PendingInvitation[] | null; notifications: NotificationList | null };

/** How often the bell asks again, in milliseconds. It also asks when the tab becomes visible. */
const POLL_MS = 30_000;

/** The sentence of an entry. Names are plain text: React escapes them, nothing here builds HTML. */
function describe(entry: Notification): string {
  const who = entry.actor?.displayName ?? "Someone";
  const group = entry.group?.name ?? "a group";
  return entry.type === "MEMBER_JOINED" ? `${who} joined ${group}` : `${who} declined your invitation to ${group}`;
}

/**
 * The bell in the header: open invitations (state, to accept or decline) first, then what happened (entries). The
 * badge counts both. One call to `/api/inbox` returns the two parts; either may be null when its source failed.
 */
export function InboxBell() {
  const router = useRouter();
  const [inbox, setInbox] = useState<Inbox>();
  const [open, setOpen] = useState(false);
  const [busyId, setBusyId] = useState<string>();
  const [error, setError] = useState<unknown>();
  const stopped = useRef(false);

  const load = useCallback(async () => {
    if (stopped.current) return;
    try {
      setInbox(await api<Inbox>("/inbox"));
    } catch (e) {
      // Signed out: asking again every 30 seconds would only repeat the 401.
      if (e instanceof ApiError && e.status === 401) stopped.current = true;
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
   * Runs one action of the bell, then asks again so the screen shows the truth. A 404 means the thing is already gone
   * (answered in a second tab, a double click): nothing to report, just refresh.
   */
  async function perform(action: () => Promise<unknown>, busy?: string) {
    setBusyId(busy);
    setError(undefined);
    try {
      await action();
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 404)) setError(e);
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

  const markRead = (entry: Notification) => perform(() => api(`/notifications/${entry.id}/read`, { method: "POST" }));

  const markAllRead = () => perform(() => api("/notifications/read-all", { method: "POST" }));

  const invitations = inbox?.invitations;
  const notifications = inbox?.notifications;
  const openInvitations = invitations ?? [];
  const entries = notifications?.items ?? [];
  const count = openInvitations.length + (notifications?.unreadCount ?? 0);
  const empty = openInvitations.length === 0 && entries.length === 0;

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label={count > 0 ? `Inbox, ${count} new` : "Inbox"}
        aria-expanded={open}
        className="relative rounded border px-2 py-1"
      >
        <span aria-hidden="true">&#128276;</span>
        {count > 0 && (
          <span className="ml-1 rounded-full bg-red-600 px-1.5 text-xs text-white">{count}</span>
        )}
      </button>
      {open && (
        <div role="region" aria-label="Inbox entries" className="absolute right-0 z-10 mt-2 w-80 max-w-[90vw] flex flex-col gap-2 rounded border bg-background p-3 text-sm text-foreground shadow">
          <ErrorBanner error={error} />
          {invitations === null && <p className="text-foreground/70">Invitations could not be loaded.</p>}
          {notifications === null && <p className="text-foreground/70">Entries could not be loaded.</p>}
          <ul className="flex flex-col gap-2">
            {openInvitations.map((invitation) => (
              <li key={invitation.invitationId} className="flex flex-col gap-1 border-l-4 border-blue-500 pl-2">
                <span>{`${invitation.invitedBy.displayName} invited you to ${invitation.group.name}`}</span>
                <span className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => void accept(invitation)}
                    disabled={busyId === invitation.invitationId}
                    aria-label={`Accept invitation to ${invitation.group.name}`}
                    className="rounded bg-foreground px-2 py-1 text-background disabled:opacity-50"
                  >
                    Accept
                  </button>
                  <button
                    type="button"
                    onClick={() => void decline(invitation)}
                    disabled={busyId === invitation.invitationId}
                    aria-label={`Decline invitation to ${invitation.group.name}`}
                    className="rounded border px-2 py-1 disabled:opacity-50"
                  >
                    Decline
                  </button>
                </span>
              </li>
            ))}
            {entries.map((entry) => (
              <li key={entry.id}>
                {entry.readAt ? (
                  <span className="text-foreground/60">{describe(entry)}</span>
                ) : (
                  <button type="button" onClick={() => void markRead(entry)} className="text-left font-medium">
                    {describe(entry)}
                  </button>
                )}
              </li>
            ))}
          </ul>
          {empty && invitations !== null && notifications !== null && <p className="text-foreground/70">Nothing new.</p>}
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
