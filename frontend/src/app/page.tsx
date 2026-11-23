import Link from "next/link";
import { GroupList } from "@/components/GroupList";

export default function Home() {
  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl sm:text-4xl">Your groups</h1>
        <Link href="/groups/new" className="btn-primary">
          New group
        </Link>
      </div>
      <GroupList />
    </main>
  );
}
