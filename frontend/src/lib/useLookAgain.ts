"use client";

import { useEffect, useState } from "react";

/**
 * Milliseconds after a change was made at which the data is fetched again. The ledger is updated by an event after
 * the change is saved, so the first answer may still be the old one. The window is bounded: after the last look
 * nothing is asked again.
 */
export const LOOK_AGAIN_AFTER_MS = [500, 1000, 2000, 4000];

/**
 * Calls `reload` at each of the delays above, counted from `lastChangedAt` (ms since 1970, `undefined` when nothing
 * changed on this page). Delays that have already passed are skipped. `updating` is true until the last look.
 */
export function useLookAgain(lastChangedAt: number | undefined, reload: () => unknown): { updating: boolean } {
  const [updating, setUpdating] = useState(false);

  useEffect(() => {
    if (lastChangedAt === undefined) return;
    const age = Date.now() - lastChangedAt;
    const remaining = LOOK_AGAIN_AFTER_MS.filter((delay) => delay > age);
    if (remaining.length === 0) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- starts a bounded series of timers
    setUpdating(true);
    const timers = remaining.map((delay, i) =>
      setTimeout(() => {
        void reload();
        if (i === remaining.length - 1) setUpdating(false);
      }, delay - age),
    );
    return () => {
      timers.forEach(clearTimeout);
      setUpdating(false);
    };
  }, [lastChangedAt, reload]);

  return { updating };
}
