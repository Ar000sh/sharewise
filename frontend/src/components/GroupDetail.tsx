"use client";

import { useCallback, useState } from "react";
import Link from "next/link";
import type { components } from "@/api/schema";
import { GroupLifecycle } from "@/components/GroupLifecycle";
import { BalancesTab } from "@/components/BalancesTab";
import { ErrorBanner } from "@/components/ErrorBanner";
import { ExpenseForm } from "@/components/ExpenseForm";
import { ExpenseList } from "@/components/ExpenseList";
import { InviteForm } from "@/components/InviteForm";
import { Icon } from "@/components/Icon";
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
  const { data: group, error } = useApi<GroupDetailData>(`/groups/${groupId}`, {
    poll: true,
  });
  const me = useCurrentUser();
  const { data: expenses, error: expensesError } = useApi<Expense[]>(
    `/groups/${groupId}/expenses`,
    { poll: true },
  );
  const { markChanged } = useGroupChange(groupId);
  const [tab, setTab] = useState<Tab>("Expenses");
  const [notice, setNotice] = useState<string>();
  const dismissNotice = useCallback(() => setNotice(undefined), []);

  // A failed poll keeps the page (and what the user typed) and shows the message above it; only a first load can fail it.
  if (error && !group) return <ErrorBanner error={error} />;
  if (!group) return <p className="loading-state">Loading…</p>;

  // Only a hint for the screen: the backend refuses non-admins with 403 NOT_AN_ADMIN whatever the UI shows.
  const isAdmin = group.members.some(
    (m) => m.userId === me?.userId && m.role === "ADMIN",
  );

  return (
    <div className="flex flex-col gap-7">
      <Link
        href="/"
        className="inline-flex self-start items-center gap-2 text-sm text-muted hover:text-foreground"
      >
        <Icon name="arrow-left" />
        Your groups
      </Link>
      <header className="flex items-start gap-4">
        <span className="avatar size-14 rounded-2xl">
          <Icon name="group" className="size-7" />
        </span>
        <div className="min-w-0">
          <p className="eyebrow mb-2">Your shared space</p>
          <h1 className="page-title break-words">{group.name}</h1>
          <p className="mt-3 text-sm text-muted">
            {group.memberCount} {group.memberCount === 1 ? "member" : "members"}{" "}
            · EUR · Equal splits
          </p>
        </div>
      </header>
      <GroupLifecycle groupId={group.id} isAdmin={isAdmin} />
      <ErrorBanner error={error} />
      <div
        role="tablist"
        aria-label="Group sections"
        className="flex self-start gap-1 rounded-2xl border border-line bg-surface p-1.5 text-foreground"
      >
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
              const next =
                event.key === "ArrowRight"
                  ? (index + 1) % TABS.length
                  : event.key === "ArrowLeft"
                    ? (index + TABS.length - 1) % TABS.length
                    : event.key === "Home"
                      ? 0
                      : event.key === "End"
                        ? TABS.length - 1
                        : undefined;
              if (next === undefined) return;
              event.preventDefault();
              setTab(TABS[next]);
              document.getElementById(`tab-${TABS[next]}`)?.focus();
            }}
            className={`tab-button ${tab === t ? "bg-soft text-accent" : "text-muted hover:text-foreground"}`}
          >
            <Icon
              name={
                t === "Expenses"
                  ? "receipt"
                  : t === "Balances"
                    ? "balance"
                    : "group"
              }
              className="hidden sm:block"
            />
            {t}
          </button>
        ))}
      </div>

      {tab === "Expenses" && (
        <div
          id="panel-Expenses"
          role="tabpanel"
          aria-labelledby="tab-Expenses"
          className="grid items-start gap-6 lg:grid-cols-[1fr_360px]"
        >
          <section className="panel overflow-hidden">
            <div className="flex items-center justify-between border-b border-line px-6 py-5">
              <h2 className="font-semibold">Shared expenses</h2>
              {expenses && (
                <span className="text-xs text-muted">
                  {expenses.length}{" "}
                  {expenses.length === 1 ? "expense" : "expenses"}
                </span>
              )}
            </div>
            <ErrorBanner error={expensesError} />
            {expenses ? (
              <ExpenseList expenses={expenses} members={group.members} correction={group.status === "OPEN" && me ? { groupId: group.id, caller: me.userId, isAdmin, onCorrected: markChanged } : undefined} />
            ) : (
              !expensesError && (
                <p className="p-6 text-sm text-muted">Loading…</p>
              )
            )}
          </section>
          {/* Only once we know who is asking: the "Paid by" default is chosen when the form first appears. */}
          {group.status !== "OPEN" ? <p className="text-sm text-muted">This group is closed. An admin must reopen it before expenses can change.</p> : me ? (
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
            <p className="loading-state">Loading…</p>
          )}
        </div>
      )}
      {tab === "Balances" && (
        <section
          id="panel-Balances"
          role="tabpanel"
          aria-labelledby="tab-Balances"
        >
          <BalancesTab groupId={group.id} />
        </section>
      )}
      {tab === "Members" && (
        <section
          id="panel-Members"
          role="tabpanel"
          aria-labelledby="tab-Members"
          className="grid items-start gap-6 lg:grid-cols-[1fr_360px]"
        >
          <div className="panel overflow-hidden">
            <h2 className="border-b border-line px-6 py-5 font-semibold">
              The people in your group
            </h2>
            <MemberList
              members={group.members}
              currentUserId={me?.userId}
              promote={
                isAdmin && group.status === "OPEN"
                  ? { groupId: group.id, onPromoted: markChanged }
                  : undefined
              }
            />
          </div>
          {isAdmin && group.status === "OPEN" && (
            <div className="panel p-6">
              <h2 className="mb-2 font-semibold">Invite a member</h2>
              <p className="mb-6 text-sm leading-relaxed text-muted">
                Invite someone to share expenses with your group.
              </p>
              <InviteForm groupId={group.id} />
            </div>
          )}
        </section>
      )}
      {tab === "Settle up" && (
        <section
          id="panel-Settle up"
          role="tabpanel"
          aria-labelledby="tab-Settle up"
        >
          {group.status === "OPEN" ? <SettleUpTab groupId={group.id} /> : <p className="text-sm text-muted">This group is closed. Payment history is preserved in its archives.</p>}
        </section>
      )}
      {notice && <Toast message={notice} onDismiss={dismissNotice} />}
    </div>
  );
}
