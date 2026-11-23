"use client";

import Link from "next/link";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { GroupStatusBadge } from "@/components/GroupStatusBadge";
import { useApi } from "@/lib/useApi";
import { useInbox } from "@/lib/useInbox";

type Group = components["schemas"]["Group"];

export function GroupList() {
  const { data: groups, error, reload } = useApi<Group[]>("/groups");
  // Read from the one inbox query (design 2026-11-23, F3), never a second request for the same payments.
  const { inbox } = useInbox();
  const waitingIn = (groupId: string) => (inbox?.payments ?? []).filter((p) => p.groupId === groupId).length;

  if (error) return <ErrorBanner error={error} onRetry={reload} />;
  if (!groups) return <p>Loading…</p>;
  if (groups.length === 0) {
    return (
      <div className="card flex flex-col gap-2 p-6">
        <h2 className="text-2xl">No groups yet</h2>
        <p className="text-muted">Create a group for a trip or a flat, or accept an invitation from your inbox.</p>
        <Link href="/groups/new" className="self-start font-bold underline">
          Create your first group
        </Link>
      </div>
    );
  }
  return (
    <ul className="card divide-y">
      {groups.map((g) => (
        <li key={g.id}>
          <Link href={`/groups/${g.id}`} className="flex flex-col gap-2 px-6 py-5">
            <span className="font-display text-2xl font-bold tracking-tight">{g.name}</span>
            <span className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm">
              <GroupStatusBadge status={g.status} />
              <span className="text-muted">
                {g.memberCount} {g.memberCount === 1 ? "member" : "members"}
              </span>
              {waitingIn(g.id) > 0 && (
                <span className="rounded-full bg-warning px-3 py-0.5 font-bold text-on-warning">
                  {waitingIn(g.id)} waiting for you
                </span>
              )}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
