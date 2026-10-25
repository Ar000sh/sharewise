"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { BalancesTab } from "@/components/BalancesTab";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ExpenseForm } from "@/components/ExpenseForm";
import { ExpenseList } from "@/components/ExpenseList";
import { InviteButton } from "@/components/InviteButton";
import { MemberList } from "@/components/MemberList";
import { useApi } from "@/lib/useApi";

type GroupDetailData = components["schemas"]["GroupDetail"];
type Expense = components["schemas"]["Expense"];
type User = components["schemas"]["User"];

const TABS = ["Expenses", "Balances", "Members"] as const;
type Tab = (typeof TABS)[number];

export function GroupDetail({ groupId }: { groupId: string }) {
  const { data: group, error } = useApi<GroupDetailData>(`/groups/${groupId}`);
  const { data: me } = useApi<User>("/me");
  const { data: expenses, error: expensesError, reload: reloadExpenses } = useApi<Expense[]>(
    `/groups/${groupId}/expenses`,
  );
  const [tab, setTab] = useState<Tab>("Expenses");
  const [balancesKey, setBalancesKey] = useState(0);

  if (error) return <ErrorBanner error={error} />;
  if (!group) return <p>Loading…</p>;

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">{group.name}</h1>
      <div role="tablist" className="flex gap-1 border-b">
        {TABS.map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={`px-3 py-2 ${tab === t ? "border-b-2 border-black font-medium" : "text-gray-600"}`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === "Expenses" && (
        <div className="flex flex-col gap-4">
          <ExpenseForm
            groupId={group.id}
            members={group.members}
            currentUserId={me?.userId}
            onRecorded={() => {
              void reloadExpenses();
              setBalancesKey((k) => k + 1);
            }}
          />
          <ErrorBanner error={expensesError} />
          {expenses && <ExpenseList expenses={expenses} members={group.members} />}
        </div>
      )}
      {tab === "Balances" && <BalancesTab groupId={group.id} refreshKey={balancesKey} />}
      {tab === "Members" && (
        <section className="flex flex-col gap-3">
          <MemberList members={group.members} currentUserId={me?.userId} />
          <InviteButton groupId={group.id} />
        </section>
      )}
    </div>
  );
}
