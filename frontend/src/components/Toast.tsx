"use client";

import { useEffect } from "react";

/** A short message at the bottom of the screen that dismisses itself. Announced to screen readers. */
export function Toast({
  message,
  onDismiss,
  durationMs = 4000,
}: {
  message: string;
  onDismiss: () => void;
  durationMs?: number;
}) {
  useEffect(() => {
    const timer = setTimeout(onDismiss, durationMs);
    return () => clearTimeout(timer);
  }, [message, onDismiss, durationMs]);

  return (
    <div
      role="status"
      className="fixed bottom-4 left-1/2 -translate-x-1/2 rounded bg-black px-4 py-2 text-sm text-white shadow"
    >
      {message}
    </div>
  );
}
