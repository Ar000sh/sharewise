import type { components } from "@/api/schema";
import { amountToCents, formatEuro } from "@/lib/money";
import { Icon } from "@/components/Icon";

type Expense = components["schemas"]["Expense"];
type Member = components["schemas"]["Member"];

export function ExpenseList({ expenses, members }: { expenses: Expense[]; members: Member[] }) {
  if (expenses.length === 0) return <div className="empty-state"><span className="avatar"><Icon name="receipt" /></span><p className="font-medium">No expenses yet.</p><p className="max-w-xs text-sm leading-relaxed text-muted">Add the first one and give everyone a clear view of the costs.</p></div>;

  const nameOf = (id: string) => members.find((m) => m.userId === id)?.displayName ?? "Former member";

  return (
    <ul className="divide-y divide-line">
      {expenses.map((e) => (
        <li key={e.id} className="flex items-center justify-between gap-3 px-5 py-5 sm:px-6">
          <span className="avatar hidden rounded-xl sm:inline-flex"><Icon name="receipt" /></span>
          <div className="min-w-0 flex-1">
            <div className="break-words text-sm font-semibold">{e.title}</div>
            <div className="mt-1 text-xs text-muted">
              {nameOf(e.paidBy)} paid · {e.date}
            </div>
          </div>
          <div className="shrink-0 text-right text-sm font-semibold tabular-nums">{formatEuro(amountToCents(e.totalAmount))}</div>
        </li>
      ))}
    </ul>
  );
}
