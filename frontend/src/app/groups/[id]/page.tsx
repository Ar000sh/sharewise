import { GroupDetail } from "@/components/GroupDetail";

export default async function GroupPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <main className="mx-auto max-w-2xl">
      <GroupDetail groupId={id} />
    </main>
  );
}
