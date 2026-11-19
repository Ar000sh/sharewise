import { connection } from "next/server";
import { FETCH_TIMEOUT_MS, TokenRejectedError, appUrl, endSessionUrl, exchangeCode } from "@/lib/oidc";
import { clearedLoginFlowCookie, readLoginFlow, sessionCookie } from "@/lib/session";
import { signInRefused, signInUnavailable } from "@/lib/unavailable";

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

  function retry(reason: "cancelled" | "expired"): Response {
    const query = new URLSearchParams({ reason });
    if (flow) query.set("next", flow.returnTo);
    return redirect(`/login?${query}`, [clearedLoginFlowCookie()]);
  }

  // Only a matching browser flow can be resumed; explanations never expose provider error details.
  if (!flow || params.get("state") !== flow.state) return retry("expired");
  if (params.get("error") || !code) return retry(params.get("error") === "access_denied" ? "cancelled" : "expired");

  let tokens;
  try {
    tokens = await exchangeCode(code, flow.verifier);
  } catch (err) {
    if (err instanceof TokenRejectedError) return retry("expired");
    return signInUnavailable();
  }

  // The only fetch of the backend outside proxyToBackend: it needs the brand-new token, which is not in a cookie yet.
  // 401 means ShareWise refuses this account (R15): no session, an explanation instead. Any other outcome, also a
  // timeout or an error, is not fatal; the backend creates the account on the next authenticated request as well.
  try {
    const me = await fetch(`${process.env.BACKEND_URL ?? "http://localhost:8080"}/me`, {
      headers: { authorization: `Bearer ${tokens.accessToken}` },
      cache: "no-store",
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    });
    if (me.status === 401) {
      const refused = signInRefused(endSessionUrl());
      refused.headers.append("set-cookie", clearedLoginFlowCookie());
      return refused;
    }
  } catch {
    // see above
  }

  return redirect(flow.returnTo, [sessionCookie(tokens), clearedLoginFlowCookie()]);
}
