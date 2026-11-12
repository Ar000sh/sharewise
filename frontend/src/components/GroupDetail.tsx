"use client";

import { useCallback, useState } from "react";
import type { components } from "@/api/schema";
import { BalancesTab } from "@/components/BalancesTab";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ExpenseForm } from "@/components/ExpenseForm";
import { ExpenseList } from "@/components/ExpenseList";
import { InviteForm } from "@/components/InviteForm";
import { MemberList } from "@/components/MemberList";
import { SettleUpTab } from "@/components/SettleUpTab";
import { Toast } from "@/components/Toast";
import { useCurrentUser } from "@/lib/currentUser";
import { useApi } from "@/lib/useApi";
import { useGroupChange } from "@/lib/useGroupChange";

type GroupDetailData = components["schemas"]["GroupDetail"];
type Expense = components["schemas"]["Expense"];

const TABS = ["Expenses", "Balances", "Settle up", "Members"] as const;
type Tab = (typeof TABS)[number];

export function GroupDetail({ groupId }: { groupId: string }) {
  // The open page asks again every few seconds: other members change the group while it is open (design 2026-11-11, L2).
  const { data: group, error } = useApi<GroupDetailData>(`/groups/${groupId}`, { poll: true });
  const me = useCurrentUser();
  const { data: expenses, error: expensesError } = useApi<Expense[]>(`/groups/${groupId}/expenses`, { poll: true });
  const { markChanged } = useGroupChange(groupId);
  const [tab, setTab] = useState<Tab>("Expenses");
  const [notice, setNotice] = useState<string>();
  const dismissNotice = useCallback(() => setNotice(undefined), []);

  // A failed poll keeps the page (and what the user typed) and shows the message above it; only a first load can fail it.
  if (error && !group) return <ErrorBanner error={error} />;
  if (!group) return <p>Loading…</p>;

  // Only a hint for the screen: the backend refuses non-admins with 403 NOT_AN_ADMIN whatever the UI shows.
  const isAdmin = group.members.some((m) => m.userId === me?.userId && m.role === "ADMIN");

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">{group.name}</h1>
      <ErrorBanner error={error} />
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
                markChanged();
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
      {tab === "Balances" && <BalancesTab groupId={group.id} />}
      {tab === "Settle up" && <SettleUpTab groupId={group.id} />}
      {tab === "Members" && (
        <section className="flex flex-col gap-3">
          <MemberList
            members={group.members}
            currentUserId={me?.userId}
            promote={isAdmin ? { groupId: group.id, onPromoted: markChanged } : undefined}
          />
          {isAdmin && <InviteForm groupId={group.id} />}
        </section>
      )}
      {notice && <Toast message={notice} onDismiss={dismissNotice} />}
    </div>
  );
}
