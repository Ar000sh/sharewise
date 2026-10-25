"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { components } from "@/api/schema";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";

type Group = components["schemas"]["Group"];

/** Joining is an explicit click, so reloading or prefetching the link never joins by accident. */
export function JoinGroup({ token }: { token: string }) {
  const router = useRouter();
  const [error, setError] = useState<string>();

  async function join() {
    try {
      const group = await api<Group>(`/invitations/${encodeURIComponent(token)}/accept`, { method: "POST" });
      router.push(`/groups/${group.id}`);
    } catch (e) {
      setError(messageFor(e));
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <p>You have been invited to join a group.</p>
      <button type="button" onClick={join} className="self-start rounded bg-black px-3 py-2 text-white">
        Join group
      </button>
      {error && (
        <div role="alert" className="text-red-700">
          <p>{error}</p>
          <Link href="/" className="underline">
            Go to your groups
          </Link>
        </div>
      )}
    </div>
  );
}
