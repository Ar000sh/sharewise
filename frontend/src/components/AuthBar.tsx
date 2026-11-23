"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { components } from "@/api/schema";
import { Avatar } from "@/components/Avatar";
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
    <div className="relative ml-auto flex flex-wrap items-center justify-end gap-3 text-sm">
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
            className="w-40"
          />
          <button
            type="submit"
            disabled={saving}
            className="btn-primary"
          >
            Save
          </button>
          <button
            type="button"
            onClick={cancelEditing}
            disabled={saving}
            className="btn-secondary"
          >
            Cancel
          </button>
          {error && (
            <span role="alert" className="font-bold text-negative">
              {error}
            </span>
          )}
        </form>
      ) : (
        <span className="flex items-center gap-2">
          <Avatar userId={user.userId} name={user.displayName} />
          {/* On a phone the avatar stands for the name; the button below still says whose name it changes. */}
          <span className="hidden sm:inline">
            Signed in as <strong>{user.displayName}</strong>
          </span>
          <button
            type="button"
            onClick={startEditing}
            className="text-sm text-muted underline"
          >
            Change name
          </button>
        </span>
      )}
      {/* A real form post, not fetch: the answer redirects to Keycloak's logout page, which a script cannot follow
          across origins. The full page load also drops all client state of the signed-out user. */}
      <form method="post" action="/api/auth/logout">
        <button type="submit" className="btn-secondary">
          Sign out
        </button>
      </form>
    </div>
  );
}
