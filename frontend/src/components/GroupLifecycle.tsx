"use client";

import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { components } from "@/api/schema";
import { api } from "@/lib/api";
import { useApi } from "@/lib/useApi";
import { useGroupChange } from "@/lib/useGroupChange";
import { messageFor } from "@/lib/errors";
import { amountToCents, formatEuro } from "@/lib/money";
import { ErrorBanner } from "@/components/ErrorBanner";

type Lifecycle = components["schemas"]["Lifecycle"];
type Archive = components["schemas"]["GroupArchive"];

/** Current lifecycle and immutable historical versions. Closure is always revalidated by the backend. */
export function GroupLifecycle({ groupId, isAdmin }: { groupId: string; isAdmin: boolean }) {
  const { data, error, reload } = useApi<Lifecycle>(`/groups/${groupId}/lifecycle`, { poll: true });
  const { data: archives, error: archiveError } = useApi<Archive[]>(`/groups/${groupId}/archives`, { poll: true });
  const { markChanged } = useGroupChange(groupId);
  const client = useQueryClient();
  const [confirming, setConfirming] = useState(false);
  const [reason, setReason] = useState("");
  const [failure, setFailure] = useState<string>();
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!data || sending.current) return;
    sending.current = true;
    setBusy(true);
    setFailure(undefined);
    try {
      const result = await api<Lifecycle>(`/groups/${groupId}/${data.status === "OPEN" ? "close" : "reopen"}`, {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ expectedRevision: data.revision, reason }),
      });
      client.setQueryData(["groups", groupId, "lifecycle"], result);
      setConfirming(false); setReason(""); markChanged();
      void client.invalidateQueries({ queryKey: ["groups"] });
    } catch (err) {
      setFailure(messageFor(err)); void reload();
    } finally { sending.current = false; setBusy(false); }
  }

  return <section className="panel flex flex-col gap-4 p-5" aria-label="Group lifecycle">
    <ErrorBanner error={error} /><ErrorBanner error={archiveError} />
    {data && <>
      <p className="font-semibold">{data.status === "OPEN" ? "Open" : "Closed"} · {data.archiveCount} archive versions</p>
      <p className="text-sm text-muted">{data.status === "OPEN"
        ? "Expenses and repayments are open. An admin can close once every balance is zero and all payments are processed."
        : "Financial changes are locked. An admin can reopen for corrections; existing archives will stay unchanged."}</p>
      {isAdmin && !confirming && <button className="button-secondary self-start" onClick={() => setConfirming(true)}>
        {data.status === "OPEN" ? "Close group" : "Reopen for corrections"}
      </button>}
      {isAdmin && confirming && <form onSubmit={submit} className="flex flex-col gap-3">
        <p>{data.status === "OPEN"
          ? "Closing creates an archive and prevents changes. Only an admin can reopen this group. Closing again creates a new version and preserves this one."
          : "Reopening allows corrections and may create new debts. Confirmed payments and earlier archives are preserved."}</p>
        {data.status === "CLOSED" && <label className="flex flex-col gap-1">Reason for reopening
          <textarea required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} className="rounded border p-2" />
        </label>}
        {data.status === "OPEN" && <ClosureSummary groupId={groupId} />}
        <ErrorBanner error={failure} />
        <div className="flex gap-3"><button disabled={busy || (data.status === "CLOSED" && !reason.trim())} className="button-primary">{busy ? "Saving…" : data.status === "OPEN" ? "Confirm closure" : "Confirm reopening"}</button>
          <button type="button" disabled={busy} className="button-secondary" onClick={() => { setConfirming(false); setFailure(undefined); }}>Cancel</button></div>
      </form>}
      {data.history.length > 0 && <details><summary className="cursor-pointer font-medium">Lifecycle history</summary><ul className="mt-2 space-y-2">
        {data.history.map(entry => <li key={entry.id} className="text-sm">{entry.action === "CLOSED" ? "Closed" : "Reopened"} · {new Date(entry.occurredAt).toLocaleString()}{entry.reason && ` — ${entry.reason}`}</li>)}
      </ul></details>}
    </>}
    {archives && archives.length > 0 && <div className="space-y-3"><h2 className="font-semibold">Archives</h2>
      {data?.status === "OPEN" && <p className="text-sm text-muted">Group reopened: current data may differ from these snapshots.</p>}
      {[...archives].reverse().map(archive => <ArchiveVersion key={archive.version} archive={archive} latest={archive.version === archives.at(-1)?.version} />)}
    </div>}
  </section>;
}

function ArchiveVersion({ archive, latest }: { archive: Archive; latest: boolean }) {
  const snapshot = archive.snapshot;
  const name = (id: string) => snapshot.members.find(m => m.userId === id)?.displayName ?? id;
  const money = (amount: string) => formatEuro(amountToCents(amount));
  return <details className="rounded border border-line p-3">
    <summary className="cursor-pointer font-medium">Version {archive.version} · {latest ? "Latest archive" : "Earlier archive"} · {new Date(archive.closedAt).toLocaleString()}</summary>
    <div className="mt-3 space-y-4 text-sm">
      <p>{snapshot.name} · {snapshot.currency} · Closed by {name(archive.closedBy)}</p>
      <h3 className="font-semibold">Expenses and corrections</h3>
      {snapshot.finances.expenses.length === 0 && <p>No expenses.</p>}
      <ul className="space-y-3">{snapshot.finances.expenses.map(e => <li key={e.id}>
        <p>{e.title} — {money(e.totalAmount)} · {e.status} · {e.date} · Paid by {name(e.paidBy)}</p>
        <p className="text-muted">{e.shares.map(s => `${name(s.participantId)}: ${money(s.owedShare)}`).join("; ")}</p>
        {e.correctedFrom && <p className="text-muted">Replaces expense {e.correctedFrom}</p>}
      </li>)}</ul>
      <h3 className="font-semibold">Payment history</h3>
      {snapshot.finances.payments.length === 0 && <p>No payments.</p>}
      <ul>{snapshot.finances.payments.map(p => <li key={p.id}>{name(p.fromDebtor)} → {name(p.toCreditor)}: {money(p.amount)} · {p.status}{p.note && ` — ${p.note}`}</li>)}</ul>
      <h3 className="font-semibold">Balances at closure</h3>
      <ul>{snapshot.finances.balances.map(b => <li key={b.userId}>{name(b.userId)}: {money(b.netBalance)}</li>)}</ul>
    </div>
  </details>;
}

function ClosureSummary({ groupId }: { groupId: string }) {
  const balances = useApi<components["schemas"]["Balance"][]>(`/groups/${groupId}/balances`, { poll: true });
  const payments = useApi<components["schemas"]["SettlementPayment"][]>(`/groups/${groupId}/settlement-payments`, { poll: true });
  return <div className="rounded border border-line p-3 text-sm">
    <h3 className="font-semibold">Current settlement summary</h3>
    <ErrorBanner error={balances.error} /><ErrorBanner error={payments.error} />
    {!balances.data && !balances.error && <p>Loading balances…</p>}
    <ul>{balances.data?.map(b => <li key={b.userId}>{b.displayName}: {formatEuro(amountToCents(b.netBalance))}</li>)}</ul>
    {payments.data && <p>{payments.data.filter(p => p.status === "PENDING").length} payments awaiting an answer.</p>}
    <p className="text-muted">Closure is available once every balance is zero and all financial updates are complete.</p>
  </div>;
}
