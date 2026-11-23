"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { Avatar } from "@/components/Avatar";
import { ErrorBanner } from "@/components/ErrorBanner";
import { api } from "@/lib/api";
import { amountToCents, formatSignedEuro } from "@/lib/money";

type Member = components["schemas"]["Member"];
type Balance = components["schemas"]["Balance"];

type Props = {
  members: Member[];
  currentUserId?: string;
  /** Each member's balance, shown next to the name (board B-09); absent while it loads. */
  balances?: Balance[];
  /** Set only for an admin: then every member who is not an admin yet gets a "Make admin" button. */
  promote?: { groupId: string; onPromoted: () => void };
};

export function MemberList({ members, currentUserId, balances, promote }: Props) {
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
    <section aria-label="Members" className="flex flex-col gap-3">
      <h2 className="text-2xl">Members</h2>
      <ul className="card divide-y">
        {members.map((m) => {
          const balance = balances?.find((b) => b.userId === m.userId);
          const cents = balance ? amountToCents(balance.netBalance) : undefined;
          return (
          <li key={m.userId} className="flex flex-wrap items-center gap-4 px-5 py-4">
            <Avatar userId={m.userId} name={m.displayName} />
            <div className="flex min-w-0 flex-1 flex-col">
              <span className="font-bold">
                {m.displayName}
                {m.userId === currentUserId && <span className="font-normal text-muted"> (you)</span>}
              </span>
              <span className="text-sm text-muted">{m.role === "ADMIN" ? "Admin" : "Member"}</span>
            </div>
            {cents !== undefined && (
              <span className={`font-bold tabular-nums ${cents > 0 ? "text-positive" : cents < 0 ? "text-negative" : "text-muted"}`}>
                {formatSignedEuro(cents)}
              </span>
            )}
            {promote && m.role !== "ADMIN" && (
              <button
                type="button"
                onClick={() => void makeAdmin(m)}
                disabled={busyId === m.userId}
                aria-label={`Make ${m.displayName} an admin`}
                className="btn-secondary"
              >
                Make admin
              </button>
            )}
          </li>
          );
        })}
      </ul>
      <ErrorBanner error={error} />
    </section>
  );
}
