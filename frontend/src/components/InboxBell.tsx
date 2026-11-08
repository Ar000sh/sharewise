"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Icon } from "@/components/Icon";
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
  const container = useRef<HTMLDivElement>(null);
  const toggle = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const dismissOutside = (event: PointerEvent) => {
      if (event.target instanceof Node && !container.current?.contains(event.target)) setOpen(false);
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
          <span className="absolute -top-1 -right-1 flex min-h-4 min-w-4 items-center justify-center rounded-full bg-accent px-1 text-[10px] text-accent-foreground">{count}</span>
        )}
      </button>
      {open && (
        <div id="inbox-entries" role="region" aria-label="Inbox entries" className="panel fixed inset-x-4 top-36 z-30 flex max-h-[70vh] flex-col gap-4 overflow-y-auto p-5 text-sm shadow-xl sm:absolute sm:inset-x-auto sm:top-auto sm:right-0 sm:mt-3 sm:w-96 sm:max-w-[90vw]">
          <div className="flex items-center justify-between"><h2 className="font-semibold">Your inbox</h2><button type="button" aria-label="Close inbox" onClick={() => { setOpen(false); toggle.current?.focus(); }} className="rounded-lg p-2 text-muted hover:bg-soft"><Icon name="close" /></button></div>
          <ErrorBanner error={error} />
          {invitations === null && <p className="text-foreground/70">Invitations could not be loaded.</p>}
          {notifications === null && <p className="text-foreground/70">Entries could not be loaded.</p>}
          <ul className="flex flex-col gap-2">
            {openInvitations.map((invitation) => (
              <li key={invitation.invitationId} className="flex flex-col gap-3 rounded-xl bg-soft p-4 text-foreground">
                <span>{`${invitation.invitedBy.displayName} invited you to ${invitation.group.name}`}</span>
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
            {entries.map((entry) => (
              <li key={entry.id} className="border-t border-line py-3 leading-relaxed">
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
