"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ExpenseForm } from "@/components/ExpenseForm";
import { useCurrentUser } from "@/lib/currentUser";
import { useApi } from "@/lib/useApi";
import { useGroupChange } from "@/lib/useGroupChange";

type GroupDetailData = components["schemas"]["GroupDetail"];

/**
 * Add expense on its own page (board B-05, design 2026-11-23, U7). After saving it goes back to the group, which shows
 * "Updating…" until the ledger has booked the expense: the change time is kept in the query cache, which survives the
 * client-side navigation (F4).
 */
export function NewExpense({ groupId }: { groupId: string }) {
  const router = useRouter();
  const me = useCurrentUser();
  const { data: group, error, reload } = useApi<GroupDetailData>(`/groups/${groupId}`);
  const { markChanged } = useGroupChange(groupId);

  if (error && !group) return <ErrorBanner error={error} onRetry={reload} />;
  // Only once we know who is asking: the "Paid by" default is chosen when the form first appears.
  if (!group || !me) return <p>Loading…</p>;

  return (
    <div className="flex flex-col gap-6">
      <Link href={`/groups/${groupId}`} className="self-start font-bold">
        ← {group.name}
      </Link>
      <h1 className="text-4xl sm:text-5xl">Add expense</h1>
      <ExpenseForm
        groupId={groupId}
        members={group.members}
        currentUserId={me.userId}
        onRecorded={() => {
          markChanged();
          router.push(`/groups/${groupId}`);
        }}
      />
    </div>
  );
}
