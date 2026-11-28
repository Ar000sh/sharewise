import { messageFor } from "@/lib/errors";

/**
 * Shows a readable message for a failed API call; renders nothing when there is no error. With `onRetry` it offers
 * "Try again" (design 2026-11-23, board B-15), for a load that failed rather than a form the user can send again.
 */
export function ErrorBanner({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null;
  return (
    <div role="alert" className="flex flex-wrap items-center gap-3 rounded-2xl bg-warning px-4 py-3 text-on-warning">
      <p className="flex-1">{messageFor(error)}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="btn-secondary border-on-warning text-on-warning">
          Try again
        </button>
      )}
    </div>
  );
}
