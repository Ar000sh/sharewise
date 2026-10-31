import { connection } from "next/server";
import { TokenRejectedError, appUrl, exchangeCode } from "@/lib/oidc";
import { clearedLoginFlowCookie, readLoginFlow, sessionCookie } from "@/lib/session";
import { signInUnavailable } from "@/lib/unavailable";

function redirect(path: string, cookies: string[]): Response {
  const headers = new Headers({ location: `${appUrl()}${path}` });
  for (const cookie of cookies) headers.append("set-cookie", cookie);
  return new Response(null, { status: 302, headers });
}

/**
 * Where Keycloak sends the browser back to. Checks `state` against the login-flow cookie (so only the browser that
 * started the login can finish it), swaps the code for tokens with the PKCE verifier, stores them in the session
 * cookie and calls the backend's /me once so the Account exists before the first page (D4).
 */
export async function GET(req: Request) {
  await connection();
  const params = new URL(req.url).searchParams;
  const flow = readLoginFlow(req);
  const code = params.get("code");

  // A missing cookie, a wrong state or an error from Keycloak all end the same way: no session, back to the login page.
  if (!flow || !code || params.get("state") !== flow.state) {
    return redirect("/login", [clearedLoginFlowCookie()]);
  }

  let tokens;
  try {
    tokens = await exchangeCode(code, flow.verifier);
  } catch (err) {
    if (err instanceof TokenRejectedError) return redirect("/login", [clearedLoginFlowCookie()]);
    return signInUnavailable();
  }

  // The only fetch of the backend outside proxyToBackend: it needs the brand-new token, which is not in a cookie yet.
  // A failure is not fatal; the backend creates the account on the next authenticated request as well.
  try {
    await fetch(`${process.env.BACKEND_URL ?? "http://localhost:8080"}/me`, {
      headers: { authorization: `Bearer ${tokens.accessToken}` },
      cache: "no-store",
    });
  } catch {
    // see above
  }

  return redirect(flow.returnTo, [sessionCookie(tokens), clearedLoginFlowCookie()]);
}
