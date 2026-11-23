import { NewExpense } from "@/components/NewExpense";

export default async function NewExpensePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <main className="mx-auto max-w-3xl">
      <NewExpense groupId={id} />
    </main>
  );
}
