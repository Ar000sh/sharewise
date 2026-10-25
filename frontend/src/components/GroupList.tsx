"use client";

import Link from "next/link";
import type { components } from "@/api/schema";
import { messageFor } from "@/lib/errors";
import { useApi } from "@/lib/useApi";

type Group = components["schemas"]["Group"];

export function GroupList() {
  const { data: groups, error } = useApi<Group[]>("/groups");

  if (error) return <p role="alert" className="text-red-700">{messageFor(error)}</p>;
  if (!groups) return <p>Loading…</p>;
  if (groups.length === 0) {
    return (
      <p>
        No groups yet.{" "}
        <Link href="/groups/new" className="underline">
          Create your first group
        </Link>
      </p>
    );
  }
  return (
    <ul className="divide-y rounded border">
      {groups.map((g) => (
        <li key={g.id}>
          <Link href={`/groups/${g.id}`} className="flex justify-between gap-2 px-3 py-3">
            <span className="font-medium">{g.name}</span>
            <span className="text-sm text-gray-600">
              {g.memberCount} {g.memberCount === 1 ? "member" : "members"}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
