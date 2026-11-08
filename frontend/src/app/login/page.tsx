import { safeReturnTo } from "@/lib/session";
import { ErrorBanner } from "@/components/ErrorBanner";
import { Icon } from "@/components/Icon";

/** Sign-in happens on Keycloak's own page; this page only starts it. `next` is where to come back to. */
export default async function LoginPage({ searchParams }: { searchParams: Promise<{ next?: string; reason?: string }> }) {
  const { next, reason } = await searchParams;
  const returnTo = encodeURIComponent(safeReturnTo(next ?? null));
  const explanation = reason === "cancelled"
    ? "Sign-in was cancelled. You can try again whenever you are ready."
    : reason === "expired"
      ? "Your sign-in attempt expired or could not be verified. Please start again."
      : undefined;

  return (
    <main id="main-content" className="page-shell">
      <div className="grid overflow-hidden rounded-2xl border border-line bg-surface text-foreground lg:min-h-[560px] lg:grid-cols-[1fr_1.05fr]">
        <section className="login-story relative hidden flex-col justify-between gap-10 overflow-hidden p-7 sm:p-12 lg:flex">
          <div className="relative">
            <p className="eyebrow">Shared expense management</p>
            <h2 className="mt-6 max-w-md text-4xl leading-[1.16] font-medium tracking-tight sm:text-5xl">Shared expenses.<br />Clearly organized.</h2>
            <p className="mt-5 max-w-sm text-sm leading-relaxed opacity-80">Keep your group expenses, individual shares, and balances in one place.</p>
          </div>
          <div className="relative grid gap-4 border-t border-current/20 pt-7">
            <div className="flex items-center gap-4 text-sm"><Icon name="group" /><span>Organize expenses by group</span></div>
            <div className="flex items-center gap-4 text-sm"><Icon name="receipt" /><span>Record a payment and split it equally</span></div>
            <div className="flex items-center gap-4 text-sm"><Icon name="balance" /><span>See what each member owes</span></div>
          </div>
          <p className="relative text-xs opacity-65">For households, trips, and everyday shared costs.</p>
        </section>
        <section aria-labelledby="sign-in-title" className="flex flex-col justify-center gap-7 p-7 sm:p-12 lg:p-14">
          <div>
            <span className="avatar mb-5"><Icon name="lock" /></span>
            <p className="eyebrow">Account access</p>
            <h1 id="sign-in-title" className="mt-3 text-3xl font-semibold tracking-tight">Welcome to ShareWise</h1>
            <p className="mt-3 text-sm leading-relaxed text-muted">Sign in to pick up where you left off, or create an account to start sharing.</p>
          </div>
          <ErrorBanner error={explanation} />
          <div className="flex flex-col gap-3">
            <a href={`/api/auth/login?returnTo=${returnTo}`} className="button-primary">Sign in<Icon name="arrow-right" /></a>
            <a href={`/api/auth/login?register=1&returnTo=${returnTo}`} className="button-secondary">Create an account</a>
          </div>
          <div className="rounded-2xl bg-soft p-5 text-sm text-foreground">
            <h3 className="font-semibold">What happens next?</h3>
            <p className="mt-2 leading-relaxed text-muted">You will open our secure sign-in page. Use your ShareWise username or email and password there, then you will return here automatically.</p>
          </div>
          <details className="text-xs text-muted">
            <summary className="font-medium">Why does sign-in open another page?</summary>
            <p className="mt-3 leading-relaxed">Keycloak manages ShareWise accounts and passwords. Your existing ShareWise account works on that page. Your password is entered there, and is never sent to the ShareWise application.</p>
          </details>
        </section>
      </div>
    </main>
  );
}
