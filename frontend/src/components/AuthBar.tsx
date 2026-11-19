"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { components } from "@/api/schema";
import { InboxBell } from "@/components/InboxBell";
import { api } from "@/lib/api";
import { useCurrentUser, useUpdateCurrentUser } from "@/lib/currentUser";
import { messageFor } from "@/lib/errors";

type User = components["schemas"]["User"];

/** Header part that shows who is signed in and lets them change their display name. */
export function AuthBar() {
  const user = useCurrentUser();
  const updateCurrentUser = useUpdateCurrentUser();
  const queryClient = useQueryClient();

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!user) return null;

  function startEditing() {
    if (!user) return;
    setDraft(user.displayName);
    setError(null);
    setEditing(true);
  }

  function cancelEditing() {
    setEditing(false);
    setError(null);
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!user) return;
    const trimmed = draft.trim();
    if (!trimmed) {
      setError("Enter a display name.");
      return;
    }
    if (trimmed === user.displayName) {
      setEditing(false);
      setError(null);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await api<User>("/me", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ displayName: trimmed }),
      });
      updateCurrentUser(updated);
      await queryClient.invalidateQueries();
      setEditing(false);
    } catch (err) {
      setError(messageFor(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-3 text-sm">
      <InboxBell />
      {editing ? (
        <form onSubmit={onSubmit} className="flex flex-wrap items-center gap-2">
          <label htmlFor="authbar-display-name" className="sr-only">
            Display name
          </label>
          <input
            id="authbar-display-name"
            type="text"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            maxLength={100}
            disabled={saving}
            className="w-40 rounded border bg-background px-2 py-1 text-foreground"
          />
          <button
            type="submit"
            disabled={saving}
            className="rounded bg-foreground px-2 py-1 text-background disabled:opacity-50"
          >
            Save
          </button>
          <button
            type="button"
            onClick={cancelEditing}
            disabled={saving}
            className="rounded border px-2 py-1 disabled:opacity-50"
          >
            Cancel
          </button>
          {error && (
            <span role="alert" className="text-red-700 dark:text-red-400">
              {error}
            </span>
          )}
        </form>
      ) : (
        <span className="flex items-center gap-2">
          <span>
            Signed in as <strong>{user.displayName}</strong>
          </span>
          <button
            type="button"
            onClick={startEditing}
            className="rounded border px-2 py-0.5 text-xs text-foreground/70"
          >
            Change name
          </button>
        </span>
      )}
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
