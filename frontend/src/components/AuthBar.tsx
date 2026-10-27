"use client";

import { useCurrentUser } from "@/lib/currentUser";

/** Header part that shows who is signed in. Finding out (and sending visitors to sign in) is the provider's job. */
export function AuthBar() {
  const user = useCurrentUser();

  async function signOut() {
    await fetch("/api/auth/logout", { method: "POST" });
    // A full page load on purpose: it drops all client state of the signed-out user.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign("/login");
  }

  if (!user) return null;
  return (
    <div className="flex items-center gap-3 text-sm">
      <span>
        Signed in as <strong>{user.displayName}</strong>
      </span>
      <button onClick={signOut} className="rounded border px-2 py-1">
        Sign out
      </button>
    </div>
  );
}
