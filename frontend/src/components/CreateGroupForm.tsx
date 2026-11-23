"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { components } from "@/api/schema";
import { ErrorBanner } from "@/components/ErrorBanner";
import { api } from "@/lib/api";

type Group = components["schemas"]["Group"];

export function CreateGroupForm() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [error, setError] = useState<unknown>();
  // A double click must not create two groups (04 section 5: the submit button is disabled while busy).
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setError("Please enter a group name.");
      return;
    }
    setBusy(true);
    try {
      const group = await api<Group>("/groups", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ name: trimmed }),
      });
      router.push(`/groups/${group.id}`);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="card flex flex-col gap-5 p-6">
      <div className="flex flex-col gap-1">
        <label htmlFor="group-name" className="font-bold">
          Group name
        </label>
        <input
          id="group-name"
          aria-describedby="group-name-hint"
          value={name}
          maxLength={100}
          onChange={(e) => setName(e.target.value)}
        />
        <p id="group-name-hint" className="text-sm text-muted">
          A name your friends recognise.
        </p>
      </div>
      <p className="flex flex-wrap items-center gap-3 text-sm">
        <span className="rounded-full border-2 border-muted px-3 py-0.5 font-bold text-muted">EUR</span>
        <span className="text-muted">All amounts are in euro.</span>
      </p>
      <ul className="flex flex-col gap-2 border-t pt-5">
        <li>You become the first admin and can invite people by username or email.</li>
        <li>The group starts open: everyone can record expenses.</li>
      </ul>
      <ErrorBanner error={error} />
      <div className="flex flex-wrap gap-3">
        <button type="submit" disabled={busy} className="btn-primary">
          Create group
        </button>
        <Link href="/" className="btn-secondary">
          Cancel
        </Link>
      </div>
    </form>
  );
}
