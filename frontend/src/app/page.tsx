import Link from "next/link";
import { GroupList } from "@/components/GroupList";

export default function Home() {
  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Your groups</h1>
        <Link href="/groups/new" className="rounded bg-black px-3 py-2 text-white">
          New group
        </Link>
      </div>
      <GroupList />
    </main>
  );
}
