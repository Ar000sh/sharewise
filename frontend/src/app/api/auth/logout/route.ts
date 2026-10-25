import { connection } from "next/server";
import { clearedSessionCookie } from "@/lib/session";

/** Signing out only forgets the cookie; the backend session simply goes unused. */
export async function POST() {
  await connection();
  return new Response(null, { status: 204, headers: { "set-cookie": clearedSessionCookie() } });
}
