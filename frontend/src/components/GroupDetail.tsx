"use client";

import { useCallback, useState } from "react";
import Link from "next/link";
import type { components } from "@/api/schema";
import { BalancesTab } from "@/components/BalancesTab";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ExpenseForm } from "@/components/ExpenseForm";
import { ExpenseList } from "@/components/ExpenseList";
import { InviteForm } from "@/components/InviteForm";
import { Icon } from "@/components/Icon";
import { MemberList } from "@/components/MemberList";
import { Toast } from "@/components/Toast";
import { useCurrentUser } from "@/lib/currentUser";
import { useApi } from "@/lib/useApi";

type GroupDetailData = components["schemas"]["GroupDetail"];
type Expense = components["schemas"]["Expense"];

const TABS = ["Expenses", "Balances", "Members"] as const;
type Tab = (typeof TABS)[number];

export function GroupDetail({ groupId }: { groupId: string }) {
  const { data: group, error } = useApi<GroupDetailData>(`/groups/${groupId}`);
  const me = useCurrentUser();
  const { data: expenses, error: expensesError, reload: reloadExpenses } = useApi<Expense[]>(
    `/groups/${groupId}/expenses`,
  );
  const [tab, setTab] = useState<Tab>("Expenses");
  const [lastRecordedAt, setLastRecordedAt] = useState<number>();
  const [notice, setNotice] = useState<string>();
  const dismissNotice = useCallback(() => setNotice(undefined), []);

  if (error) return <ErrorBanner error={error} />;
  if (!group) return <p className="loading-state">Loading…</p>;

  return (
    <div className="flex flex-col gap-7">
      <Link href="/" className="inline-flex self-start items-center gap-2 text-sm text-muted hover:text-foreground"><Icon name="arrow-left" />Your groups</Link>
      <header className="flex items-start gap-4">
        <span className="avatar size-14 rounded-2xl"><Icon name="group" className="size-7" /></span>
        <div className="min-w-0">
          <p className="eyebrow mb-2">Your shared space</p>
          <h1 className="page-title break-words">{group.name}</h1>
          <p className="mt-3 text-sm text-muted">{group.memberCount} {group.memberCount === 1 ? "member" : "members"} · EUR · Equal splits</p>
        </div>
      </header>
      <div role="tablist" aria-label="Group sections" className="flex self-start gap-1 rounded-2xl border border-line bg-surface p-1.5 text-foreground">
        {TABS.map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            id={`tab-${t}`}
            aria-controls={`panel-${t}`}
            aria-selected={tab === t}
            tabIndex={tab === t ? 0 : -1}
            onClick={() => setTab(t)}
            onKeyDown={(event) => {
              const index = TABS.indexOf(t);
              const next = event.key === "ArrowRight" ? (index + 1) % TABS.length
                : event.key === "ArrowLeft" ? (index + TABS.length - 1) % TABS.length
                  : event.key === "Home" ? 0 : event.key === "End" ? TABS.length - 1 : undefined;
              if (next === undefined) return;
              event.preventDefault();
              setTab(TABS[next]);
              document.getElementById(`tab-${TABS[next]}`)?.focus();
            }}
            className={`tab-button ${tab === t ? "bg-soft text-accent" : "text-muted hover:text-foreground"}`}
          >
            <Icon name={t === "Expenses" ? "receipt" : t === "Balances" ? "balance" : "group"} className="hidden sm:block" />
            {t}
          </button>
        ))}
      </div>

      {tab === "Expenses" && (
        <div id="panel-Expenses" role="tabpanel" aria-labelledby="tab-Expenses" className="grid items-start gap-6 lg:grid-cols-[1fr_360px]">
          <section className="panel overflow-hidden">
            <div className="flex items-center justify-between border-b border-line px-6 py-5">
              <h2 className="font-semibold">Shared expenses</h2>
              {expenses && <span className="text-xs text-muted">{expenses.length} {expenses.length === 1 ? "expense" : "expenses"}</span>}
            </div>
            <ErrorBanner error={expensesError} />
            {expenses ? <ExpenseList expenses={expenses} members={group.members} /> : !expensesError && <p className="p-6 text-sm text-muted">Loading…</p>}
          </section>
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
            <p className="loading-state">Loading…</p>
          )}
        </div>
      )}
      {tab === "Balances" && <section id="panel-Balances" role="tabpanel" aria-labelledby="tab-Balances"><BalancesTab groupId={group.id} lastRecordedAt={lastRecordedAt} /></section>}
      {tab === "Members" && (
        <section id="panel-Members" role="tabpanel" aria-labelledby="tab-Members" className="grid items-start gap-6 lg:grid-cols-[1fr_360px]">
          <div className="panel overflow-hidden"><h2 className="border-b border-line px-6 py-5 font-semibold">The people in your group</h2><MemberList members={group.members} currentUserId={me?.userId} /></div>
          <div className="panel p-6"><h2 className="mb-2 font-semibold">Invite a member</h2><p className="mb-6 text-sm leading-relaxed text-muted">Invite someone to share expenses with your group.</p><InviteForm groupId={group.id} /></div>
        </section>
      )}
      {notice && <Toast message={notice} onDismiss={dismissNotice} />}
    </div>
  );
}
