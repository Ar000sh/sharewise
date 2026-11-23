"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCurrentUser } from "@/lib/currentUser";
import { useInbox } from "@/lib/useInbox";

/** What waits for the viewer or is new: open invitations, payments waiting for an answer and unread entries. */
export function inboxCount(inbox: ReturnType<typeof useInbox>["inbox"]): number {
  return (inbox?.invitations?.length ?? 0) + (inbox?.payments?.length ?? 0) + (inbox?.notifications?.unreadCount ?? 0);
}

/**
 * The main navigation of the top bar (board B-01): My groups and the Inbox with its count, visible from every page
 * (requirement F3). Only for a signed-in visitor: the inbox is asked for only below this check, because for a visitor
 * who is signed out it answers 401, `api()` sends them to sign in, and on the sign-in page that would start again.
 */
export function MainNav() {
  const user = useCurrentUser();
  return user ? <SignedInNav /> : null;
}

function SignedInNav() {
  const pathname = usePathname();
  const { inbox } = useInbox();
  const count = inboxCount(inbox);
  const item = (current: boolean) =>
    `inline-flex min-h-11 items-center gap-2 rounded-full px-4 font-bold ${current ? "bg-surface-2" : ""}`;

  return (
    <nav aria-label="Main" className="flex items-center gap-1">
      <Link href="/" aria-current={pathname === "/" ? "page" : undefined} className={item(pathname === "/")}>
        My groups
      </Link>
      <Link
        href="/inbox"
        aria-current={pathname === "/inbox" ? "page" : undefined}
        aria-label={count > 0 ? `Inbox, ${count} new` : "Inbox"}
        className={item(pathname === "/inbox")}
      >
        Inbox
        {count > 0 && (
          <span aria-hidden="true" className="rounded-full bg-accent px-2 text-xs font-extrabold text-on-accent">
            {count}
          </span>
        )}
      </Link>
    </nav>
  );
}
