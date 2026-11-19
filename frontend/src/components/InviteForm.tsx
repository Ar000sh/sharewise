"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Icon } from "@/components/Icon";
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
      const invitation = await api<Invitation>(
        `/groups/${groupId}/invitations`,
        {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({ identifier: value, role }),
        },
      );
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
    <form onSubmit={submit} className="flex flex-col gap-4">
      <label className="field-label">
        Username or email
        <input
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          className="field"
          placeholder="Their username or email"
          aria-describedby="invite-help"
          autoComplete="off"
        />
      </label>
      <p id="invite-help" className="text-xs leading-relaxed text-muted">
        They need to have signed in to ShareWise once. Their invitation will
        appear in their inbox.
      </p>
      <label className="flex flex-col gap-1 text-sm">
        Joins as
        <select
          value={role}
          onChange={(e) => setRole(e.target.value as "MEMBER" | "ADMIN")}
          className="rounded border bg-background px-2 py-2"
        >
          <option value="MEMBER">Member</option>
          <option value="ADMIN">Admin</option>
        </select>
      </label>
      <button
        type="submit"
        disabled={busy}
        className="button-primary self-start"
      >
        <Icon name="plus" />
        Invite
      </button>
      {invited && (
        <p role="status" className="rounded-xl bg-soft p-3 text-sm text-accent">
          Invitation sent to @{invited}
        </p>
      )}
      <ErrorBanner error={error} />
    </form>
  );
}
