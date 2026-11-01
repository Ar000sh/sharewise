import { connection } from "next/server";
import { authorizationUrl, challengeFor, generateState, generateVerifier, keycloakReachable } from "@/lib/oidc";
import { loginFlowCookie, safeReturnTo } from "@/lib/session";
import { signInUnavailable } from "@/lib/unavailable";

/**
 * Starts the OIDC code flow with PKCE: remembers state, verifier and where to return in a short-lived httpOnly
 * cookie, then sends the browser to Keycloak (`?register=1` opens its registration page instead of the login).
 */
export async function GET(req: Request) {
  await connection();
  if (!(await keycloakReachable())) return signInUnavailable();

  const params = new URL(req.url).searchParams;
  const state = generateState();
  const verifier = generateVerifier();
  const location = authorizationUrl({
    state,
    challenge: await challengeFor(verifier),
    register: params.get("register") === "1",
  });
  const headers = new Headers({ location });
  headers.append("set-cookie", loginFlowCookie({ state, verifier, returnTo: safeReturnTo(params.get("returnTo")) }));
  return new Response(null, { status: 302, headers });
}
