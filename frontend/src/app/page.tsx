import Link from "next/link";
import { GroupList } from "@/components/GroupList";
import { Icon } from "@/components/Icon";

export default function Home() {
  return (
    <main id="main-content" className="page-shell flex flex-col gap-8">
      <div className="flex flex-wrap items-end justify-between gap-5">
        <div>
          <p className="eyebrow mb-3">Overview</p>
          <h1 className="page-title">Your groups</h1>
          <p className="mt-3 text-sm text-muted">Manage shared expenses and balances for each of your groups.</p>
        </div>
        <Link href="/groups/new" className="button-primary"><Icon name="plus" />New group</Link>
      </div>
      <GroupList />
      <aside className="flex items-start gap-4 rounded-2xl border border-dashed border-line p-5 text-sm text-muted">
        <Icon name="bell" />
        <p>Expecting an invitation? Open the inbox in the header to accept it and join your group.</p>
      </aside>
    </main>
  );
}
