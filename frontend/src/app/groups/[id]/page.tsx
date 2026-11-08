import { GroupDetail } from "@/components/GroupDetail";

export default async function GroupPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <main id="main-content" className="page-shell">
      <GroupDetail groupId={id} />
    </main>
  );
}
