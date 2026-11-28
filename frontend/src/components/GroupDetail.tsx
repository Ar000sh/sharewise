"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { BalancesTab } from "@/components/BalancesTab";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ExpenseList } from "@/components/ExpenseList";
import { GroupHero } from "@/components/GroupHero";
import { InviteForm } from "@/components/InviteForm";
import { GroupLifecycle } from "@/components/GroupLifecycle";
import { LeaveGroup } from "@/components/LeaveGroup";
import { MemberList } from "@/components/MemberList";
import { SettleUpTab } from "@/components/SettleUpTab";
import { useCurrentUser } from "@/lib/currentUser";
import { useApi } from "@/lib/useApi";
import { useGroupChange } from "@/lib/useGroupChange";
import { useInbox } from "@/lib/useInbox";

type GroupDetailData = components["schemas"]["GroupDetail"];
type Expense = components["schemas"]["Expense"];
type Balance = components["schemas"]["Balance"];

const TABS = ["Expenses", "Balances", "Settle up", "Members"] as const;
type Tab = (typeof TABS)[number];

export function GroupDetail({ groupId }: { groupId: string }) {
  // The open page asks again every few seconds: other members change the group while it is open (design 2026-11-11, L2).
  const { data: group, error, reload } = useApi<GroupDetailData>(`/groups/${groupId}`, { poll: true });
  const me = useCurrentUser();
  const { data: expenses, error: expensesError } = useApi<Expense[]>(`/groups/${groupId}/expenses`, { poll: true });
  const { markChanged } = useGroupChange(groupId);
  const [tab, setTab] = useState<Tab>("Expenses");
  // The same cached query as the hero and the Balances tab: no second request.
  const { data: balances } = useApi<Balance[]>(`/groups/${groupId}/balances`, { poll: true });
  const { inbox } = useInbox();
  const waiting = (inbox?.payments ?? []).filter((p) => p.groupId === groupId).length;

  // A failed poll keeps the page (and what the user typed) and shows the message above it; only a first load can fail it.
  if (error && !group) return <ErrorBanner error={error} onRetry={reload} />;
  if (!group) return <p>Loading…</p>;

  // Only a hint for the screen: the backend refuses non-admins with 403 NOT_AN_ADMIN whatever the UI shows.
  const isAdmin = group.members.some((m) => m.userId === me?.userId && m.role === "ADMIN");
  // An archived group is read-only history (L6): no member changes either.
  const archived = group.status === "ARCHIVED";

  return (
    <div className="flex flex-col gap-6 pb-20 sm:pb-0">
      <GroupHero group={group} currentUserId={me?.userId} waiting={waiting} onOpenSettleUp={() => setTab("Settle up")} />
      <GroupLifecycle groupId={group.id} status={group.status} isAdmin={isAdmin} onChanged={markChanged} />
      <ErrorBanner error={error} />
      <div role="tablist" aria-label="Group sections" className="flex gap-2 overflow-x-auto pb-1">
        {TABS.map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={tab === t}
            aria-label={t === "Settle up" && waiting > 0 ? `Settle up, ${waiting} waiting` : undefined}
            onClick={() => setTab(t)}
            className={`min-h-11 shrink-0 rounded-full border-2 px-5 font-bold ${tab === t ? "border-foreground bg-foreground text-background" : "bg-surface"}`}
          >
            {t}
            {t === "Settle up" && waiting > 0 && (
              <span aria-hidden="true" className="ml-2 rounded-full bg-accent px-2 text-xs font-extrabold text-on-accent">
                {waiting}
              </span>
            )}
          </button>
        ))}
      </div>

      {tab === "Expenses" && (
        <div className="flex flex-col gap-4">
          <ErrorBanner error={expensesError} />
          {expenses && me ? (
            <ExpenseList
              expenses={expenses}
              members={group.members}
              formerMembers={group.formerMembers}
              currentUserId={me.userId}
              change={group.status === "OPEN" ? { groupId: group.id, isAdmin, onChanged: markChanged } : undefined}
            />
          ) : (
            <p>Loading…</p>
          )}
        </div>
      )}
      {tab === "Balances" && <BalancesTab groupId={group.id} />}
      {tab === "Settle up" && <SettleUpTab groupId={group.id} />}
      {tab === "Members" && (
        <section className="flex flex-col gap-3">
          <MemberList
            members={group.members}
            formerMembers={group.formerMembers}
            currentUserId={me?.userId}
            balances={balances}
            promote={isAdmin && !archived ? { groupId: group.id, onPromoted: markChanged } : undefined}
            remove={isAdmin && !archived ? { groupId: group.id, onRemoved: markChanged } : undefined}
          />
          {isAdmin && group.status === "OPEN" && <InviteForm groupId={group.id} />}
          {!archived && <LeaveGroup groupId={group.id} groupName={group.name} />}
        </section>
      )}
    </div>
  );
}
