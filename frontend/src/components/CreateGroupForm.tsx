"use client";

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

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setError("Please enter a group name.");
      return;
    }
    try {
      const group = await api<Group>("/groups", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ name: trimmed }),
      });
      router.push(`/groups/${group.id}`);
    } catch (err) {
      setError(err);
    }
  }

  return (
    <form onSubmit={submit} className="flex max-w-md flex-col gap-3">
      <label className="flex flex-col gap-1">
        Group name
        <input
          className="rounded border px-2 py-2"
          value={name}
          maxLength={100}
          onChange={(e) => setName(e.target.value)}
        />
      </label>
      <ErrorBanner error={error} />
      <button type="submit" className="rounded bg-foreground px-3 py-2 text-background">
        Create group
      </button>
    </form>
  );
}
