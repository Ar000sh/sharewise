import { messageFor } from "@/lib/errors";

/** Shows a readable message for a failed API call; renders nothing when there is no error. */
export function ErrorBanner({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <p role="alert" className="rounded border border-red-300 bg-red-50 px-3 py-2 text-red-800">
      {messageFor(error)}
    </p>
  );
}
