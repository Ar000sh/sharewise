"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { Avatar } from "@/components/Avatar";
import { Dialog } from "@/components/Dialog";
import { ErrorBanner } from "@/components/ErrorBanner";
import { api } from "@/lib/api";
import { amountToCents, formatSignedEuro } from "@/lib/money";

type Member = components["schemas"]["Member"];
type Balance = components["schemas"]["Balance"];

type Props = {
  members: Member[];
  currentUserId?: string;
  /** Each member's balance, shown next to the name (board B-09); absent while it loads. */
  balances?: Balance[];
  /** Set only for an admin: then every member who is not an admin yet gets a "Make admin" button. */
  promote?: { groupId: string; onPromoted: () => void };
  /** Set only for an admin: then every other member gets a "Remove" button (J11; the backend checks R11). */
  remove?: { groupId: string; onRemoved: () => void };
  /** People who left or were removed (R13), listed under the members. */
  formerMembers?: Member[];
};

export function MemberList({ members, currentUserId, balances, promote, remove, formerMembers = [] }: Props) {
  const [busyId, setBusyId] = useState<string>();
  const [error, setError] = useState<unknown>();
  const [removing, setRemoving] = useState<Member>();
  const [removeError, setRemoveError] = useState<unknown>();

  async function removeMember(member: Member) {
    if (!remove) return;
    setBusyId(member.userId);
    try {
      await api(`/groups/${remove.groupId}/members/${member.userId}`, { method: "DELETE" });
      setRemoving(undefined);
      setRemoveError(undefined);
      remove.onRemoved();
    } catch (e) {
      setRemoveError(e);
    } finally {
      setBusyId(undefined);
    }
  }

  function closeRemove() {
    setRemoving(undefined);
    setRemoveError(undefined);
  }

  async function makeAdmin(member: Member) {
    if (!promote) return;
    setBusyId(member.userId);
    try {
      await api(`/groups/${promote.groupId}/members/${member.userId}/promote`, { method: "POST" });
      setError(undefined);
      promote.onPromoted();
    } catch (e) {
      setError(e);
    } finally {
      setBusyId(undefined);
    }
  }

  return (
    <section aria-label="Members" className="flex flex-col gap-3">
      <h2 className="text-2xl">Members</h2>
      <ul className="card divide-y">
        {members.map((m) => {
          const balance = balances?.find((b) => b.userId === m.userId);
          const cents = balance ? amountToCents(balance.netBalance) : undefined;
          return (
          <li key={m.userId} className="flex flex-wrap items-center gap-4 px-5 py-4">
            <Avatar userId={m.userId} name={m.displayName} />
            <div className="flex min-w-0 flex-1 flex-col">
              <span className="font-bold">
                {m.displayName}
                {m.userId === currentUserId && <span className="font-normal text-muted"> (you)</span>}
              </span>
              <span className="text-sm text-muted">{m.role === "ADMIN" ? "Admin" : "Member"}</span>
            </div>
            {cents !== undefined && (
              <span className={`font-bold tabular-nums ${cents > 0 ? "text-positive" : cents < 0 ? "text-negative" : "text-muted"}`}>
                {formatSignedEuro(cents)}
              </span>
            )}
            {promote && m.role !== "ADMIN" && (
              <button
                type="button"
                onClick={() => void makeAdmin(m)}
                disabled={busyId === m.userId}
                aria-label={`Make ${m.displayName} an admin`}
                className="btn-secondary"
              >
                Make admin
              </button>
            )}
            {remove && m.userId !== currentUserId && (
              <button
                type="button"
                onClick={() => setRemoving(m)}
                disabled={busyId === m.userId}
                aria-label={`Remove ${m.displayName}`}
                className="btn-secondary"
              >
                Remove
              </button>
            )}
          </li>
          );
        })}
      </ul>
      <ErrorBanner error={error} />
      {formerMembers.length > 0 && (
        <div className="flex flex-col gap-1">
          <h3 className="text-sm font-bold text-muted">Former members</h3>
          <p className="text-sm text-muted">{formerMembers.map((m) => m.displayName).join(", ")}</p>
        </div>
      )}
      {removing && (
        <Dialog title={`Remove ${removing.displayName}?`} onClose={closeRemove}>
          <p>
            {removing.displayName} will no longer be in the group, but stays visible in old expenses. This only works when
            their balance is 0.
          </p>
          <ErrorBanner error={removeError} />
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => void removeMember(removing)}
              disabled={busyId === removing.userId}
              className="btn-primary"
            >
              Remove {removing.displayName}
            </button>
            <button type="button" onClick={closeRemove} className="btn-secondary">
              Cancel
            </button>
          </div>
        </Dialog>
      )}
    </section>
  );
}
