"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { api } from "@/lib/api";

type Member = components["schemas"]["Member"];

type Props = {
  members: Member[];
  currentUserId?: string;
  /** Set only for an admin: then every member who is not an admin yet gets a "Make admin" button. */
  promote?: { groupId: string; onPromoted: () => void };
};

export function MemberList({ members, currentUserId, promote }: Props) {
  const [busyId, setBusyId] = useState<string>();
  const [error, setError] = useState<unknown>();

  async function makeAdmin(member: Member) {
    if (!promote) return;
    setBusyId(member.userId);
    try {
      await api(`/groups/${promote.groupId}/members/${member.userId}/promote`, { method: "POST" });
      setError(undefined);
      promote.onPromoted();
    } catch (e) {
      setError(e);
    } finally {
      setBusyId(undefined);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <ul className="divide-y rounded border">
        {members.map((m) => (
          <li key={m.userId} className="flex items-center justify-between gap-2 px-3 py-2">
            <span>
              {m.displayName}
              {m.userId === currentUserId && <span className="text-foreground/70"> (you)</span>}
            </span>
            {m.role === "ADMIN" && (
              <span className="rounded bg-foreground/10 px-2 py-0.5 text-xs">Admin</span>
            )}
            {promote && m.role !== "ADMIN" && (
              <button
                type="button"
                onClick={() => void makeAdmin(m)}
                disabled={busyId === m.userId}
                aria-label={`Make ${m.displayName} an admin`}
                className="rounded border px-2 py-0.5 text-xs disabled:opacity-50"
              >
                Make admin
              </button>
            )}
          </li>
        ))}
      </ul>
      <ErrorBanner error={error} />
    </div>
  );
}
