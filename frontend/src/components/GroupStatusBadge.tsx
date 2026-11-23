import type { components } from "@/api/schema";

type GroupStatus = components["schemas"]["Group"]["status"];

/** How each group status reads on the screen (requirements: "the UI must make the current state obvious"). */
const STATUS_LABEL: Record<GroupStatus, string> = {
  OPEN: "Open",
  CLOSED_FOR_SETTLEMENT: "Closed for settlement",
  ARCHIVED: "Archived",
};

/** The status of a group in words, on the group list and on the group page alike. */
export function GroupStatusBadge({ status }: { status: GroupStatus }) {
  return (
    <span
      className={`rounded-full border-2 px-3 py-0.5 text-sm font-bold ${status === "OPEN" ? "border-positive text-positive" : "border-muted text-muted"}`}
    >
      {STATUS_LABEL[status]}
    </span>
  );
}
