"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { api } from "@/lib/api";

type Invitation = components["schemas"]["Invitation"];

/** Invites an existing account by exact username or email. The backend finds the account; nothing here guesses. */
export function InviteForm({ groupId }: { groupId: string }) {
  const [identifier, setIdentifier] = useState("");
  const [busy, setBusy] = useState(false);
  const [invited, setInvited] = useState<string>();
  const [error, setError] = useState<unknown>();

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const value = identifier.trim();
    if (!value) return;
    setBusy(true);
    try {
      const invitation = await api<Invitation>(`/groups/${groupId}/invitations`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ identifier: value }),
      });
      setInvited(invitation.invitee.username);
      setError(undefined);
      setIdentifier("");
    } catch (e) {
      setError(e);
      setInvited(undefined);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-2">
      <label className="flex flex-col gap-1 text-sm">
        Username or email
        <input
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          className="rounded border px-2 py-2"
          autoComplete="off"
        />
      </label>
      <button type="submit" disabled={busy} className="self-start rounded border px-3 py-2 disabled:opacity-50">
        Invite
      </button>
      {invited && <p role="status">Invitation sent to @{invited}</p>}
      <ErrorBanner error={error} />
    </form>
  );
}
