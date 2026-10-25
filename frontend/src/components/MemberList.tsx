import type { components } from "@/api/schema";

type Member = components["schemas"]["Member"];

export function MemberList({ members, currentUserId }: { members: Member[]; currentUserId?: string }) {
  return (
    <ul className="divide-y rounded border">
      {members.map((m) => (
        <li key={m.memberId} className="flex items-center justify-between gap-2 px-3 py-2">
          <span>
            {m.displayName}
            {m.memberId === currentUserId && <span className="text-gray-600"> (you)</span>}
          </span>
          {m.role === "ADMIN" && (
            <span className="rounded bg-gray-100 px-2 py-0.5 text-xs">Admin</span>
          )}
        </li>
      ))}
    </ul>
  );
}
