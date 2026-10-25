import { JoinGroup } from "@/components/JoinGroup";

export default async function JoinPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return (
    <main className="mx-auto max-w-2xl">
      <h1 className="mb-4 text-xl font-semibold">Join a group</h1>
      <JoinGroup token={token} />
    </main>
  );
}
