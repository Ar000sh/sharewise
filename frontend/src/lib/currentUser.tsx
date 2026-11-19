"use client";

import { createContext, useContext, useEffect, useState } from "react";
import type { components } from "@/api/schema";
import { api } from "@/lib/api";

type User = components["schemas"]["User"];

const PUBLIC_PATHS = ["/login"];

interface CurrentUserContextValue {
  user: User | undefined;
  setUser: (user: User) => void;
}

const CurrentUserContext = createContext<CurrentUserContextValue>({
  user: undefined,
  setUser: () => {},
});

/**
 * Asks the server once "who am I" (the session cookie is httpOnly, so the page cannot read it) and shares the answer
 * with every component. A visitor without a session gets a 401, and `api()` sends them to sign in.
 */
export function CurrentUserProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User>();

  useEffect(() => {
    if (PUBLIC_PATHS.includes(window.location.pathname)) return;
    // A 401 has already sent the visitor to sign in (in api()); after any other failure the user stays unknown.
    api<User>("/me")
      .then(setUser)
      .catch(() => {});
  }, []);

  return <CurrentUserContext value={{ user, setUser }}>{children}</CurrentUserContext>;
}

/** The signed-in user, or undefined while the answer is on its way (or on the sign-in pages). */
export function useCurrentUser(): User | undefined {
  return useContext(CurrentUserContext).user;
}

/** Updates the cached current user after a profile change (e.g. PATCH /me). */
export function useUpdateCurrentUser(): (user: User) => void {
  return useContext(CurrentUserContext).setUser;
}
