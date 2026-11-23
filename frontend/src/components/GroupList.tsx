"use client";

import Link from "next/link";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { useApi } from "@/lib/useApi";

type Group = components["schemas"]["Group"];

/** How each group status reads on the screen (requirements: "the UI must make the current state obvious"). */
const STATUS_LABEL: Record<Group["status"], string> = {
  OPEN: "Open",
  CLOSED_FOR_SETTLEMENT: "Closed for settlement",
  ARCHIVED: "Archived",
};

export function GroupList() {
  const { data: groups, error, reload } = useApi<Group[]>("/groups");

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
              <span
                className={`rounded-full border-2 px-3 py-0.5 font-bold ${g.status === "OPEN" ? "border-positive text-positive" : "border-muted text-muted"}`}
              >
                {STATUS_LABEL[g.status]}
              </span>
              <span className="text-muted">
                {g.memberCount} {g.memberCount === 1 ? "member" : "members"}
              </span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
