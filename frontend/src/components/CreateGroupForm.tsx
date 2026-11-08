"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Icon } from "@/components/Icon";
import { api } from "@/lib/api";

type Group = components["schemas"]["Group"];

export function CreateGroupForm() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    const trimmed = name.trim();
    if (!trimmed) {
      setError("Please enter a group name.");
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      const group = await api<Group>("/groups", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ name: trimmed }),
      });
      router.push(`/groups/${group.id}`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-5">
      <label className="field-label">
        Group name
        <input
          className="field"
          placeholder="e.g. Mallorca 2027 or Our apartment"
          value={name}
          maxLength={100}
          onChange={(e) => setName(e.target.value)}
        />
      </label>
      <p className="text-xs leading-relaxed text-muted">You will be the first member and admin. Invite others from the Members tab after creating your group.</p>
      <ErrorBanner error={error} />
      <button type="submit" disabled={busy} className="button-primary">
        Create group<Icon name="arrow-right" />
      </button>
    </form>
  );
}
