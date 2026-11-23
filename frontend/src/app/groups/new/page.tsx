import { CreateGroupForm } from "@/components/CreateGroupForm";

export default function NewGroupPage() {
  return (
    <main className="mx-auto max-w-3xl">
      <h1 className="mb-6 text-3xl sm:text-4xl">New group</h1>
      <CreateGroupForm />
    </main>
  );
}
