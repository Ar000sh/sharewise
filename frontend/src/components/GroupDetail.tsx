"use client";

import { useCallback, useState } from "react";
import type { components } from "@/api/schema";
import { BalancesTab } from "@/components/BalancesTab";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ExpenseForm } from "@/components/ExpenseForm";
import { ExpenseList } from "@/components/ExpenseList";
import { InviteForm } from "@/components/InviteForm";
import { MemberList } from "@/components/MemberList";
import { Toast } from "@/components/Toast";
import { useCurrentUser } from "@/lib/currentUser";
import { useApi } from "@/lib/useApi";

type GroupDetailData = components["schemas"]["GroupDetail"];
type Expense = components["schemas"]["Expense"];

const TABS = ["Expenses", "Balances", "Members"] as const;
type Tab = (typeof TABS)[number];

export function GroupDetail({ groupId }: { groupId: string }) {
  const { data: group, error, reload: reloadGroup } = useApi<GroupDetailData>(`/groups/${groupId}`);
  const me = useCurrentUser();
  const { data: expenses, error: expensesError, reload: reloadExpenses } = useApi<Expense[]>(
    `/groups/${groupId}/expenses`,
  );
  const [tab, setTab] = useState<Tab>("Expenses");
  const [lastRecordedAt, setLastRecordedAt] = useState<number>();
  const [notice, setNotice] = useState<string>();
  const dismissNotice = useCallback(() => setNotice(undefined), []);

  if (error) return <ErrorBanner error={error} />;
  if (!group) return <p>Loading…</p>;

  // Only a hint for the screen: the backend refuses non-admins with 403 NOT_AN_ADMIN whatever the UI shows.
  const isAdmin = group.members.some((m) => m.userId === me?.userId && m.role === "ADMIN");

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
            className={`px-3 py-2 ${tab === t ? "border-b-2 border-foreground font-medium" : "text-foreground/70"}`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === "Expenses" && (
        <div className="flex flex-col gap-4">
          {/* Only once we know who is asking: the "Paid by" default is chosen when the form first appears. */}
          {me ? (
            <ExpenseForm
              groupId={group.id}
              members={group.members}
              currentUserId={me.userId}
              onRecorded={() => {
                void reloadExpenses();
                setLastRecordedAt(Date.now());
                setNotice("Expense saved. Balances update in a moment.");
              }}
            />
          ) : (
            <p>Loading…</p>
          )}
          <ErrorBanner error={expensesError} />
          {expenses && <ExpenseList expenses={expenses} members={group.members} />}
        </div>
      )}
      {tab === "Balances" && <BalancesTab groupId={group.id} lastRecordedAt={lastRecordedAt} />}
      {tab === "Members" && (
        <section className="flex flex-col gap-3">
          <MemberList
            members={group.members}
            currentUserId={me?.userId}
            promote={isAdmin ? { groupId: group.id, onPromoted: () => void reloadGroup() } : undefined}
          />
          {isAdmin && <InviteForm groupId={group.id} />}
        </section>
      )}
      {notice && <Toast message={notice} onDismiss={dismissNotice} />}
    </div>
  );
}
