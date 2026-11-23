import Link from "next/link";
import { CreateGroupForm } from "@/components/CreateGroupForm";

/** Create group (board B-02). */
export default function NewGroupPage() {
  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-6">
      <Link href="/" className="self-start font-bold">
        ← My groups
      </Link>
      <h1 className="text-4xl sm:text-5xl">Where are you going, or who are you living with?</h1>
      <CreateGroupForm />
    </main>
  );
}
