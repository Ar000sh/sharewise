import type { components } from "@/api/schema";
import { formatEuro } from "@/lib/money";

type Expense = components["schemas"]["Expense"];
type Member = components["schemas"]["Member"];

export function ExpenseList({ expenses, members }: { expenses: Expense[]; members: Member[] }) {
  if (expenses.length === 0) return <p>No expenses yet.</p>;

  const nameOf = (id: string) => members.find((m) => m.memberId === id)?.displayName ?? "Former member";

  return (
    <ul className="divide-y rounded border">
      {expenses.map((e) => (
        <li key={e.id} className="flex items-start justify-between gap-2 px-3 py-2">
          <div>
            <div className="font-medium">{e.title}</div>
            <div className="text-sm text-gray-600">
              {nameOf(e.payerId)} paid · {e.date}
            </div>
          </div>
          <div className="whitespace-nowrap font-medium">{formatEuro(e.amountCents)}</div>
        </li>
      ))}
    </ul>
  );
}
