import Link from "next/link";
import { GroupList } from "@/components/GroupList";

/** My groups (board B-01): every group of the signed-in person, and the way to create one. */
export default function Home() {
  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-4xl sm:text-5xl">My groups</h1>
          <p className="mt-2 text-muted">Your trips and flats. Everything is in euro.</p>
        </div>
        <Link href="/groups/new" className="btn-primary">
          Create group
        </Link>
      </div>
      <GroupList />
    </main>
  );
}
