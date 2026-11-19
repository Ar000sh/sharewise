"use client";

import { createContext, useContext, useEffect, useState } from "react";
import type { components } from "@/api/schema";
import { api } from "@/lib/api";
import { ErrorBanner } from "@/components/ErrorBanner";

type User = components["schemas"]["User"];

const PUBLIC_PATHS = ["/login"];

const CurrentUserContext = createContext<User | undefined>(undefined);

/**
 * Asks the server once "who am I" (the session cookie is httpOnly, so the page cannot read it) and shares the answer
 * with every component. A visitor without a session gets a 401, and `api()` sends them to sign in.
 */
export function CurrentUserProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [user, setUser] = useState<User>();
  const [error, setError] = useState<unknown>();

  useEffect(() => {
    if (PUBLIC_PATHS.includes(window.location.pathname)) return;
    // A 401 has already sent the visitor to sign in (in api()); after any other failure the user stays unknown.
    api<User>("/me")
      .then(setUser)
      .catch((err) => setError(err));
  }, []);

  return (
    <CurrentUserContext value={user}>
      {error ? (
        <main id="main-content" className="page-shell">
          <div className="panel mx-auto flex max-w-lg flex-col gap-5 p-6">
            <h1 className="text-xl font-semibold">
              We could not load your account
            </h1>
            <ErrorBanner error={error} />
            <button
              type="button"
              className="button-primary"
              onClick={() => window.location.reload()}
            >
              Try again
            </button>
          </div>
        </main>
      ) : (
        children
      )}
    </CurrentUserContext>
  );
}

/** The signed-in user, or undefined while the answer is on its way (or on the sign-in pages). */
export function useCurrentUser(): User | undefined {
  return useContext(CurrentUserContext);
}
