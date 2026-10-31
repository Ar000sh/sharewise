import { connection } from "next/server";
import { endSessionUrl } from "@/lib/oidc";
import { problem } from "@/lib/backend";
import { clearedSessionCookie, isSameOrigin } from "@/lib/session";

/**
 * Forgets the cookie and ends the Keycloak session too, otherwise the next "Sign in" would sign straight back in (D9).
 * POST only: a GET could be triggered by any other site with an image tag (F2), and a POST from another origin is
 * refused like in the proxy. 303 makes the browser follow with GET.
 */
export async function POST(req: Request) {
  await connection();
  if (!isSameOrigin(req)) {
    return problem(403, "Forbidden", "FORBIDDEN_ORIGIN", "The request does not come from this application");
  }
  const headers = new Headers({ location: endSessionUrl() });
  headers.append("set-cookie", clearedSessionCookie());
  return new Response(null, { status: 303, headers });
}
