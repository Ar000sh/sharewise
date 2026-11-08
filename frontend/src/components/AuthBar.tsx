"use client";

import { InboxBell } from "@/components/InboxBell";
import { Avatar } from "@/components/Avatar";
import Link from "next/link";
import { useCurrentUser } from "@/lib/currentUser";

/** Header part that shows who is signed in. Finding out (and sending visitors to sign in) is the provider's job. */
export function AuthBar() {
  const user = useCurrentUser();

  if (!user) return null;
  return (
    <div className="flex flex-wrap items-center gap-3 text-sm">
      <Link href="/" className="mr-2 hidden text-sm font-medium text-muted hover:text-foreground sm:block">Your groups</Link>
      <InboxBell />
      <div className="flex items-center gap-2 border-l border-line pl-3">
        <Avatar name={user.displayName} className="hidden sm:inline-flex" />
        <span className="max-w-36 truncate text-xs text-muted sm:max-w-48">Signed in as <strong className="block truncate text-sm font-semibold text-foreground">{user.displayName}</strong></span>
      </div>
      {/* A real form post, not fetch: the answer redirects to Keycloak's logout page, which a script cannot follow
          across origins. The full page load also drops all client state of the signed-out user. */}
      <form method="post" action="/api/auth/logout">
        <button type="submit" className="button-secondary min-h-10 px-3 text-xs">
          Sign out
        </button>
      </form>
    </div>
  );
}
