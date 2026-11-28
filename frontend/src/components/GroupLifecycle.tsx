"use client";

import { useState } from "react";
import type { components } from "@/api/schema";
import { Dialog } from "@/components/Dialog";
import { ErrorBanner } from "@/components/ErrorBanner";
import { api } from "@/lib/api";

type GroupStatus = components["schemas"]["Group"]["status"];
type Action = "close" | "reopen" | "archive";

/** What each admin action is called, what its dialog says, and its button. */
const ACTIONS: Record<Action, { title: string; text: string; button: string }> = {
  close: {
    title: "Close for settlement?",
    text: "Nobody can add, correct or remove expenses any more; settling up still works. Balances do not have to be 0. The group is archived automatically once everybody is settled. You can reopen it until then.",
    button: "Close for settlement",
  },
  reopen: {
    title: "Reopen the group?",
    text: "Expenses can be added, corrected and removed again.",
    button: "Reopen group",
  },
  archive: {
    title: "Archive the group?",
    text: "Archiving is final: the group becomes read-only history. It only works when every balance is 0.",
    button: "Archive group",
  },
};

/**
 * The group's lifecycle (J12, J13, board B-14): a banner for everybody while the group is closed for settlement or
 * archived, and for an admin the actions that the status allows, each behind a confirmation. The backend decides (L1 to
 * L6); a refusal shows its message in the dialog.
 */
export function GroupLifecycle({
  groupId,
  status,
  isAdmin,
  onChanged,
}: {
  groupId: string;
  status: GroupStatus;
  isAdmin: boolean;
  onChanged: () => void;
}) {
  const [action, setAction] = useState<Action>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();

  const available: Action[] = !isAdmin ? [] : status === "OPEN" ? ["close"] : status === "CLOSED_FOR_SETTLEMENT" ? ["reopen", "archive"] : [];

  async function run(chosen: Action) {
    setBusy(true);
    try {
      await api(`/groups/${groupId}/${chosen}`, { method: "POST" });
      setAction(undefined);
      setError(undefined);
      onChanged();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  function close() {
    setAction(undefined);
    setError(undefined);
  }

  if (status === "OPEN" && available.length === 0) return null;

  return (
    <section aria-label="Group status" className="flex flex-col gap-3">
      {status === "CLOSED_FOR_SETTLEMENT" && (
        <p role="status" className="rounded-2xl bg-warning px-4 py-3 text-on-warning">
          <span className="font-bold">Closed for settlement.</span> No new expenses; settle up to finish. The group is
          archived automatically once everybody is settled.
        </p>
      )}
      {status === "ARCHIVED" && (
        <p role="status" className="rounded-2xl bg-surface-2 px-4 py-3">
          <span className="font-bold">Archived.</span> This group is read-only history.
        </p>
      )}
      {available.length > 0 && (
        <div className="flex flex-wrap gap-3">
          {available.map((a) => (
            <button key={a} type="button" onClick={() => setAction(a)} className="btn-secondary">
              {ACTIONS[a].button}
            </button>
          ))}
        </div>
      )}
      {action && (
        <Dialog title={ACTIONS[action].title} onClose={close}>
          <p>{ACTIONS[action].text}</p>
          <ErrorBanner error={error} />
          <div className="flex flex-wrap gap-3">
            <button type="button" onClick={() => void run(action)} disabled={busy} className="btn-primary">
              {ACTIONS[action].button}
            </button>
            <button type="button" onClick={close} className="btn-secondary">
              Cancel
            </button>
          </div>
        </Dialog>
      )}
    </section>
  );
}
