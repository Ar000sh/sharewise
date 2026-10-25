"use client";

import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import type { components } from "@/api/schema";
import { ApiError, api } from "@/lib/api";

type User = components["schemas"]["User"];

const PUBLIC_PATHS = ["/login", "/register"];

/** Header part that shows who is signed in, and sends visitors without a session to the sign-in page. */
export function AuthBar() {
  const pathname = usePathname();
  const [user, setUser] = useState<User>();
  const isPublic = PUBLIC_PATHS.includes(pathname);

  useEffect(() => {
    if (isPublic) return;
    api<User>("/me")
      .then(setUser)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) {
          window.location.replace(`/login?next=${encodeURIComponent(pathname)}`);
        }
      });
  }, [pathname, isPublic]);

  async function signOut() {
    await fetch("/api/auth/logout", { method: "POST" });
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
