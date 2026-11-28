import { CorrectExpense } from "@/components/CorrectExpense";

export default async function CorrectExpensePage({ params }: { params: Promise<{ id: string; expenseId: string }> }) {
  const { id, expenseId } = await params;
  return (
    <main className="mx-auto max-w-3xl">
      <CorrectExpense groupId={id} expenseId={expenseId} />
    </main>
  );
}
