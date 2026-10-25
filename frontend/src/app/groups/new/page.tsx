import { CreateGroupForm } from "@/components/CreateGroupForm";

export default function NewGroupPage() {
  return (
    <main className="mx-auto max-w-2xl">
      <h1 className="mb-4 text-xl font-semibold">New group</h1>
      <CreateGroupForm />
    </main>
  );
}
