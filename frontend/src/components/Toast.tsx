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
      className="fixed bottom-5 left-1/2 z-50 w-max max-w-[calc(100vw-2rem)] -translate-x-1/2 rounded-2xl bg-foreground px-5 py-4 text-sm text-background shadow-lg"
    >
      {message}
    </div>
  );
}
