"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";

type Invitation = components["schemas"]["Invitation"];

export function InviteButton({ groupId }: { groupId: string }) {
  const [link, setLink] = useState<string>();
  const [error, setError] = useState<string>();

  async function create() {
    try {
      const invitation = await api<Invitation>(`/groups/${groupId}/invitations`, { method: "POST" });
      setLink(`${window.location.origin}/join/${invitation.token}`);
      setError(undefined);
    } catch (e) {
      setError(messageFor(e));
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <button type="button" onClick={create} className="self-start rounded border px-3 py-2">
        Invite someone
      </button>
      {link && (
        <label className="flex flex-col gap-1 text-sm">
          Invite link (valid for 7 days)
          <input
            readOnly
            value={link}
            onFocus={(e) => e.currentTarget.select()}
            className="rounded border px-2 py-2"
          />
        </label>
      )}
      {error && <p role="alert" className="text-red-700">{error}</p>}
    </div>
  );
}
