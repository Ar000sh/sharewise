"use client";

import Link from "next/link";
import { useState } from "react";
import { GroupList } from "@/components/GroupList";
import { PendingBadge } from "@/components/PendingBadge";
import { SettlementInbox } from "@/components/SettlementInbox";

/** The home page: my groups and the settlement payments waiting for my answer, with a badge that follows the answers. */
export function Dashboard() {
  const [answers, setAnswers] = useState(0);

  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center">
          <h1 className="text-xl font-semibold">Your groups</h1>
          <PendingBadge refreshKey={answers} />
        </div>
        <Link href="/groups/new" className="rounded bg-black px-3 py-2 text-white">
          New group
        </Link>
      </div>
      <SettlementInbox hideWhenEmpty onAnswered={() => setAnswers((n) => n + 1)} />
      <GroupList />
    </main>
  );
}
