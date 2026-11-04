import { safeReturnTo } from "@/lib/session";

/** Sign-in happens on Keycloak's own page; this page only starts it. `next` is where to come back to. */
export default async function LoginPage({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const { next } = await searchParams;
  const returnTo = encodeURIComponent(safeReturnTo(next ?? null));

  return (
    <div className="mx-auto flex max-w-sm flex-col gap-4">
      <h1 className="text-xl font-semibold">Sign in</h1>
      <a href={`/api/auth/login?returnTo=${returnTo}`} className="rounded bg-foreground px-3 py-2 text-center text-background">
        Sign in
      </a>
      <p className="text-sm">
        New here?{" "}
        <a className="underline" href={`/api/auth/login?register=1&returnTo=${returnTo}`}>
          Create an account
        </a>
      </p>
    </div>
  );
}
