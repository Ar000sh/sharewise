import type { components } from "@/api/schema";
import { Avatar } from "@/components/Avatar";

type Member = components["schemas"]["Member"];

export function MemberList({ members, currentUserId }: { members: Member[]; currentUserId?: string }) {
  return (
    <ul className="divide-y divide-line">
      {members.map((m) => (
        <li key={m.userId} className="flex items-center justify-between gap-3 px-6 py-5">
          <Avatar name={m.displayName} />
          <span className="min-w-0 flex-1 break-words text-sm font-medium">
            {m.displayName}
            {m.userId === currentUserId && <span className="text-foreground/70"> (you)</span>}
          </span>
          {m.role === "ADMIN" && (
            <span className="rounded-full bg-soft px-2.5 py-1 text-xs font-medium text-accent">Admin</span>
          )}
        </li>
      ))}
    </ul>
  );
}
