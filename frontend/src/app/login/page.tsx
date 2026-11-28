import { safeReturnTo } from "@/lib/session";

/** Sign-in happens on Keycloak's own page; this page only starts it. `next` is where to come back to. */
export default async function LoginPage({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const { next } = await searchParams;
  const returnTo = encodeURIComponent(safeReturnTo(next ?? null));

  return (
    <main className="mx-auto flex max-w-xl flex-col gap-6 pt-6 sm:pt-12">
      <h1 className="text-4xl sm:text-5xl">Shared expenses made simple.</h1>
      <p className="text-lg text-muted">Record expenses together, always know the balance, settle with the fewest payments.</p>
      <div className="flex flex-wrap gap-3">
        <a href={`/api/auth/login?returnTo=${returnTo}`} className="btn-primary">
          Sign in
        </a>
        <a href={`/api/auth/login?register=1&returnTo=${returnTo}`} className="btn-secondary">
          Create an account
        </a>
      </div>
      <p className="rounded-2xl bg-surface-2 px-4 py-3 text-sm">
        ShareWise has no passwords. You sign in on your identity provider and come back here.
      </p>
    </main>
  );
}
