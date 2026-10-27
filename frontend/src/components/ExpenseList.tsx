import type { components } from "@/api/schema";
import { amountToCents, formatEuro } from "@/lib/money";

type Expense = components["schemas"]["Expense"];
type Member = components["schemas"]["Member"];

export function ExpenseList({ expenses, members }: { expenses: Expense[]; members: Member[] }) {
  if (expenses.length === 0) return <p>No expenses yet.</p>;

  const nameOf = (id: string) => members.find((m) => m.userId === id)?.displayName ?? "Former member";

  return (
    <ul className="divide-y rounded border">
      {expenses.map((e) => (
        <li key={e.id} className="flex items-start justify-between gap-2 px-3 py-2">
          <div>
            <div className="font-medium">{e.title}</div>
            <div className="text-sm text-gray-600">
              {nameOf(e.paidBy)} paid · {e.date}
            </div>
          </div>
          <div className="whitespace-nowrap font-medium">{formatEuro(amountToCents(e.totalAmount))}</div>
        </li>
      ))}
    </ul>
  );
}
