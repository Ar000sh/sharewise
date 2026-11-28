import type { components } from "@/api/schema";
import { Avatar } from "@/components/Avatar";
import { formatDay } from "@/lib/dates";
import { amountToCents, formatEuro, formatSignedEuro, yourPartCents } from "@/lib/money";

type Expense = components["schemas"]["Expense"];
type Member = components["schemas"]["Member"];

/** How the row names the split, in the words of the form's three modes. */
const SPLIT_WORDS: Record<Expense["splitStrategy"], string> = {
  EQUAL: "equally",
  EXACT: "exact amounts",
  PERCENTAGE: "by percentages",
};

/** Consecutive expenses of the same date, in the order the API gives them (newest first). */
function byDay(expenses: Expense[]): { date: string; expenses: Expense[] }[] {
  const days: { date: string; expenses: Expense[] }[] = [];
  for (const expense of expenses) {
    const last = days.at(-1);
    if (last?.date === expense.date) last.expenses.push(expense);
    else days.push({ date: expense.date, expenses: [expense] });
  }
  return days;
}

/**
 * The expenses of a group grouped by day (board B-04). Each row says who paid, how it is split and what it means for
 * the person looking: their part, plus or minus, with the sign and not only the colour telling the direction.
 */
export function ExpenseList({
  expenses,
  members,
  formerMembers = [],
  currentUserId,
}: {
  expenses: Expense[];
  members: Member[];
  /** People who left or were removed: old expenses name them as "Bob (left)" (R13). */
  formerMembers?: Member[];
  currentUserId: string;
}) {
  if (expenses.length === 0) {
    return (
      <div className="card flex flex-col gap-1 p-6">
        <h2 className="text-2xl">No expenses yet</h2>
        <p className="text-muted">Record the first one. Everyone in the group sees it.</p>
      </div>
    );
  }

  const nameOf = (id: string) => {
    const member = members.find((m) => m.userId === id);
    if (member) return member.displayName;
    const former = formerMembers.find((m) => m.userId === id);
    return former ? `${former.displayName} (left)` : "Former member";
  };

  return (
    <div className="flex flex-col gap-2">
      {byDay(expenses).map((day) => (
        <section key={day.date} aria-label={formatDay(day.date)}>
          <h2 className="px-1 pt-3 pb-1 font-sans text-sm font-bold tracking-normal text-muted">{formatDay(day.date)}</h2>
          <ul className="card divide-y">
            {day.expenses.map((e) => {
              const part = yourPartCents(e, currentUserId);
              return (
                <li key={e.id} className="flex items-center gap-4 px-5 py-4">
                  <Avatar userId={e.paidBy} name={nameOf(e.paidBy)} />
                  <div className="flex min-w-0 flex-1 flex-col">
                    <span className="font-bold">{e.title}</span>
                    <span className="text-sm text-muted">
                      {e.paidBy === currentUserId ? "You" : nameOf(e.paidBy)} paid · {SPLIT_WORDS[e.splitStrategy]} between{" "}
                      {e.shares.length}
                    </span>
                  </div>
                  <div className="flex flex-col items-end">
                    <span className="font-bold whitespace-nowrap tabular-nums">{formatEuro(amountToCents(e.totalAmount))}</span>
                    <span
                      className={`text-sm whitespace-nowrap tabular-nums ${part > 0 ? "text-positive" : part < 0 ? "text-negative" : "text-muted"}`}
                    >
                      your part {formatSignedEuro(part)}
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}
