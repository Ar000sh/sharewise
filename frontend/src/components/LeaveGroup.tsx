"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Dialog } from "@/components/Dialog";
import { ErrorBanner } from "@/components/ErrorBanner";
import { api } from "@/lib/api";

/**
 * "Leave group" for the person looking (J10). The backend decides: only at balance 0 (R11), and not as the last admin
 * while others remain (R12); its message is shown in the dialog. After leaving the group is gone from My groups.
 */
export function LeaveGroup({ groupId, groupName }: { groupId: string; groupName: string }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();

  async function leave() {
    setBusy(true);
    try {
      await api(`/groups/${groupId}/leave`, { method: "POST" });
      router.push("/");
    } catch (e) {
      setError(e);
      setBusy(false);
    }
  }

  function close() {
    setOpen(false);
    setError(undefined);
  }

  return (
    <>
      <button type="button" onClick={() => setOpen(true)} className="btn-secondary self-start">
        Leave group
      </button>
      {open && (
        <Dialog title={`Leave ${groupName}?`} onClose={close}>
          <p>You will no longer see this group. Old expenses keep your name. This only works when your balance is 0.</p>
          <ErrorBanner error={error} />
          <div className="flex flex-wrap gap-3">
            <button type="button" onClick={() => void leave()} disabled={busy} className="btn-primary">
              Leave group
            </button>
            <button type="button" onClick={close} className="btn-secondary">
              Cancel
            </button>
          </div>
        </Dialog>
      )}
    </>
  );
}
