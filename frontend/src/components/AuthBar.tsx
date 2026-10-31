"use client";

import { useCurrentUser } from "@/lib/currentUser";

/** Header part that shows who is signed in. Finding out (and sending visitors to sign in) is the provider's job. */
export function AuthBar() {
  const user = useCurrentUser();

  if (!user) return null;
  return (
    <div className="flex items-center gap-3 text-sm">
      <span>
        Signed in as <strong>{user.displayName}</strong>
      </span>
      {/* A real form post, not fetch: the answer redirects to Keycloak's logout page, which a script cannot follow
          across origins. The full page load also drops all client state of the signed-out user. */}
      <form method="post" action="/api/auth/logout">
        <button type="submit" className="rounded border px-2 py-1">
          Sign out
        </button>
      </form>
    </div>
  );
}
