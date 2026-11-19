"use client";

import Link from "next/link";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Icon } from "@/components/Icon";
import { useApi } from "@/lib/useApi";

type Group = components["schemas"]["Group"];

export function GroupList() {
  const { data: groups, error } = useApi<Group[]>("/groups");

  if (error) return <ErrorBanner error={error} />;
  if (!groups) return <p className="loading-state">Loading…</p>;
  if (groups.length === 0) {
    return (
      <div className="panel empty-state">
        <span className="avatar mb-2 size-14"><Icon name="group" className="size-7" /></span>
        <h2 className="text-xl font-semibold">No groups yet.</h2>
        <p className="max-w-sm text-sm leading-relaxed text-muted">Start with a trip, your household, or your next night out. Invite your people and add your first expense.</p>
        <Link href="/groups/new" className="button-primary mt-3">
          Create your first group
        </Link>
      </div>
    );
  }
  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {groups.map((g) => (
        <li key={g.id}>
          <Link href={`/groups/${g.id}`} className="panel group flex h-full flex-col gap-6 p-6 transition hover:border-accent hover:shadow-sm">
            <div className="flex items-center justify-between">
              <span className="avatar rounded-xl"><Icon name="group" /></span>
              <span className="rounded-full bg-soft px-2.5 py-1 text-xs font-medium text-accent">{g.status === "OPEN" ? "Open" : g.status.toLowerCase().replaceAll("_", " ")}</span>
            </div>
            <div className="min-w-0"><span className="block truncate text-lg font-semibold">{g.name}</span>
              <span className="mt-1 block text-sm text-muted">{g.memberCount} {g.memberCount === 1 ? "member" : "members"}</span>
            </div>
            <span className="flex items-center justify-between border-t border-line pt-4 text-xs font-medium text-accent">Open group<Icon name="arrow-right" className="transition group-hover:translate-x-1" /></span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
