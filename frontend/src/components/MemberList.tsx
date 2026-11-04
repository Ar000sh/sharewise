import type { components } from "@/api/schema";

type Member = components["schemas"]["Member"];

export function MemberList({ members, currentUserId }: { members: Member[]; currentUserId?: string }) {
  return (
    <ul className="divide-y rounded border">
      {members.map((m) => (
        <li key={m.userId} className="flex items-center justify-between gap-2 px-3 py-2">
          <span>
            {m.displayName}
            {m.userId === currentUserId && <span className="text-foreground/70"> (you)</span>}
          </span>
          {m.role === "ADMIN" && (
            <span className="rounded bg-foreground/10 px-2 py-0.5 text-xs">Admin</span>
          )}
        </li>
      ))}
    </ul>
  );
}
