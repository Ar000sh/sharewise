import { CreateGroupForm } from "@/components/CreateGroupForm";
import { Icon } from "@/components/Icon";
import Link from "next/link";

export default function NewGroupPage() {
  return (
    <main id="main-content" className="page-shell">
      <Link href="/" className="mb-8 inline-flex items-center gap-2 text-sm text-muted hover:text-foreground"><Icon name="arrow-left" />Your groups</Link>
      <div className="mx-auto max-w-xl">
        <p className="eyebrow mb-3">Group setup</p>
        <h1 className="page-title">New group</h1>
        <p className="mt-3 mb-8 text-sm leading-relaxed text-muted">Create a group, invite its members, and start recording shared expenses.</p>
        <div className="panel p-6 sm:p-8"><CreateGroupForm /></div>
      </div>
    </main>
  );
}
