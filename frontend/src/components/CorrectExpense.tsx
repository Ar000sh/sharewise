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
type Expense = components["schemas"]["Expense"];

/**
 * Correct an expense on its own page, like Add expense (J5). The form starts with the expense's values; after saving it
 * goes back to the group, which shows "Updating…" until the ledger has booked the difference.
 */
export function CorrectExpense({ groupId, expenseId }: { groupId: string; expenseId: string }) {
  const router = useRouter();
  const me = useCurrentUser();
  const { data: group, error, reload } = useApi<GroupDetailData>(`/groups/${groupId}`);
  const { data: expenses, error: expensesError } = useApi<Expense[]>(`/groups/${groupId}/expenses`);
  const { markChanged } = useGroupChange(groupId);

  if (error && !group) return <ErrorBanner error={error} onRetry={reload} />;
  if (expensesError && !expenses) return <ErrorBanner error={expensesError} />;
  if (!group || !me || !expenses) return <p>Loading…</p>;
  const expense = expenses.find((e) => e.id === expenseId);
  if (!expense) return <ErrorBanner error="This expense does not exist." />;

  return (
    <div className="flex flex-col gap-6">
      <Link href={`/groups/${groupId}`} className="self-start font-bold">
        ← {group.name}
      </Link>
      <h1 className="text-4xl sm:text-5xl">Correct expense</h1>
      <ExpenseForm
        groupId={groupId}
        members={group.members}
        currentUserId={me.userId}
        correcting={expense}
        onRecorded={() => {
          markChanged();
          router.push(`/groups/${groupId}`);
        }}
      />
    </div>
  );
}
