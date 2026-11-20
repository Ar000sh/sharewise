"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { Avatar } from "@/components/Avatar";
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
    <>
    <ul className="divide-y divide-line">
      {members.map((m) => (
        <li key={m.userId} className="flex items-center justify-between gap-3 px-6 py-5">
          <Avatar name={m.displayName} />
          <span className="min-w-0 flex-1 break-words text-sm font-medium">
            {m.displayName}
            {m.userId === currentUserId && <span className="text-foreground/70"> (you)</span>}
          </span>
          {m.role === "ADMIN" && (
            <span className="rounded-full bg-soft px-2.5 py-1 text-xs font-medium text-accent">Admin</span>
          )}
          {promote && m.role !== "ADMIN" && (
            <button
              type="button"
              onClick={() => void makeAdmin(m)}
              disabled={busyId === m.userId}
              aria-label={`Make ${m.displayName} an admin`}
              className="button-secondary min-h-9 px-3 py-2 text-xs"
            >
              Make admin
            </button>
          )}
        </li>
      ))}
    </ul>
    <div>
      <ErrorBanner error={error} />
    </div>
    </>
  );
}
