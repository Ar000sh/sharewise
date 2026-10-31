import { connection } from "next/server";
import { endSessionUrl } from "@/lib/oidc";
import { clearedSessionCookie } from "@/lib/session";

/**
 * Forgets the cookie and ends the Keycloak session too, otherwise the next "Sign in" would sign straight back in (D9).
 * POST only: a GET could be triggered by any other site with an image tag (F2). 303 makes the browser follow with GET.
 */
export async function POST() {
  await connection();
  const headers = new Headers({ location: endSessionUrl() });
  headers.append("set-cookie", clearedSessionCookie());
  return new Response(null, { status: 303, headers });
}
