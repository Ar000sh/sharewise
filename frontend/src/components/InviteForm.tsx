"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { api } from "@/lib/api";

type Invitation = components["schemas"]["Invitation"];

/** Invites an existing account by exact username or email. The backend finds the account; nothing here guesses. */
export function InviteForm({ groupId }: { groupId: string }) {
  const [identifier, setIdentifier] = useState("");
  const [role, setRole] = useState<"MEMBER" | "ADMIN">("MEMBER");
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
        body: JSON.stringify({ identifier: value, role }),
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
    <form onSubmit={submit} aria-label="Invite someone" className="card flex flex-col gap-4 p-6">
      <h2 className="text-2xl">Invite someone</h2>
      <div className="flex flex-col gap-4 sm:flex-row">
        <label className="flex flex-1 flex-col gap-1 font-bold">
          Username or email
          <input
            className="font-normal"
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
            autoComplete="off"
          />
        </label>
        <label className="flex flex-col gap-1 font-bold">
          Joins as
          <select
            className="font-normal"
            value={role}
            onChange={(e) => setRole(e.target.value as "MEMBER" | "ADMIN")}
          >
            <option value="MEMBER">Member</option>
            <option value="ADMIN">Admin</option>
          </select>
        </label>
      </div>
      <p className="text-sm text-muted">
        {/* R10: a late joiner shares only in later expenses; R9: invitations expire after 7 days. */}
        Someone who joins later only shares in expenses recorded after joining. Invitations last 7 days.
      </p>
      <button type="submit" disabled={busy} className="btn-primary self-start">
        Send invitation
      </button>
      {invited && <p role="status">Invitation sent to @{invited}</p>}
      <ErrorBanner error={error} />
    </form>
  );
}
