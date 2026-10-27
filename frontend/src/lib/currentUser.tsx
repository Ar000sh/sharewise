"use client";

import { createContext, useContext, useEffect, useState } from "react";
import type { components } from "@/api/schema";
import { ApiError, api } from "@/lib/api";

type User = components["schemas"]["User"];

const PUBLIC_PATHS = ["/login", "/register"];

const CurrentUserContext = createContext<User | undefined>(undefined);

/**
 * Asks the server once "who am I" (the session cookie is httpOnly, so the page cannot read it) and shares the answer
 * with every component. A visitor without a session is sent to the sign-in page and back to this page afterwards.
 */
export function CurrentUserProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User>();

  useEffect(() => {
    const { pathname, search } = window.location;
    if (PUBLIC_PATHS.includes(pathname)) return;
    api<User>("/me")
      .then(setUser)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) {
          window.location.replace(`/login?next=${encodeURIComponent(pathname + search)}`);
        }
      });
  }, []);

  return <CurrentUserContext value={user}>{children}</CurrentUserContext>;
}

/** The signed-in user, or undefined while the answer is on its way (or on the sign-in pages). */
export function useCurrentUser(): User | undefined {
  return useContext(CurrentUserContext);
}
