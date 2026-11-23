"use client";

import { useEffect, useId, useRef } from "react";

/**
 * A modal dialog (design 2026-11-23, U6, board B-12) on the native `<dialog>`: the browser traps the focus, dims the
 * page and closes it with Escape. It is open while it is rendered; `onClose` asks the parent to stop rendering it. On a
 * phone it rises from the bottom as a sheet.
 */
export function Dialog({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
    return () => dialog?.close();
  }, []);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      // Escape: the parent decides (it stops rendering the dialog), so React and the DOM never disagree.
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="m-auto w-[min(32rem,calc(100%-2rem))] rounded-3xl bg-surface p-6 text-foreground shadow-2xl backdrop:bg-black/50 max-sm:mb-0 max-sm:w-full max-sm:max-w-full max-sm:rounded-b-none"
    >
      <div className="flex flex-col gap-4">
        <h2 id={titleId} className="text-2xl">
          {title}
        </h2>
        {children}
      </div>
    </dialog>
  );
}
