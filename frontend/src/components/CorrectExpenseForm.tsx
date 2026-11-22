"use client";
import { useState, useRef } from "react";
import type { components } from "@/api/schema";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { eurosToCents, centsToAmount } from "@/lib/money";
import { ErrorBanner } from "@/components/ErrorBanner";

/** A correction keeps the original and confirmed transfers; any new balance must be settled before closure. */
export function CorrectExpenseForm({ groupId, expense, onCorrected }: { groupId: string; expense: components["schemas"]["Expense"]; onCorrected: () => void }) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(expense.title);
  const [amount, setAmount] = useState(expense.totalAmount);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if(sending.current) return;
    const cents = eurosToCents(amount);
    if(cents === null || cents <= 0) return setError("Enter a positive amount with at most two decimal places.");
    sending.current=true; setBusy(true);
    try {
      await api(`/groups/${groupId}/expenses/${expense.id}/correct`, { method:"POST", headers:{"content-type":"application/json"}, body: JSON.stringify({title, totalAmount:centsToAmount(cents)}) });
      setEditing(false);setError(undefined);onCorrected();
    } catch(err){setError(messageFor(err));} finally {sending.current=false;setBusy(false);}
  }
  if(!editing)return <button className="text-sm underline" onClick={() => setEditing(true)}>Correct expense</button>;
  return <form className="mt-3 flex flex-col gap-2" onSubmit={submit}>
    <p className="text-sm text-muted">Payer and participants stay the same. The original expense and confirmed payments are preserved.</p>
    <label>Corrected title<input className="block rounded border p-2" required maxLength={200} value={title} onChange={e=>setTitle(e.target.value)} /></label>
    <label>Corrected amount (€)<input className="block rounded border p-2" inputMode="decimal" required value={amount} onChange={e=>setAmount(e.target.value)} /></label>
    <ErrorBanner error={error}/><div className="flex gap-2"><button className="button-secondary" disabled={busy}>Save correction</button><button type="button" disabled={busy} onClick={()=>setEditing(false)}>Cancel</button></div>
  </form>;
}
