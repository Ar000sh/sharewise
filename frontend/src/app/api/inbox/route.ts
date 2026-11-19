import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";

/**
 * The bell shows three things from three places: the caller's open invitations and the payments waiting for the
 * caller's answer (both state, asked as queries) and the inbox entries (things that happened, from the notification
 * context). This handler asks all three and merges the answers, so the browser makes one call. A part that failed is
 * null; the others still arrive.
 */
export async function GET(req: Request) {
  await connection();
  const answers = await Promise.all([
    proxyToBackend(req, "/invitations"),
    proxyToBackend(req, "/notifications?limit=10"),
    proxyToBackend(req, "/me/settlement-payments"),
  ]);
  const [invitations, notifications, payments] = answers;

  const unauthenticated = answers.find((answer) => answer.status === 401);
  if (unauthenticated) return unauthenticated;

  const headers = new Headers({ "content-type": "application/json" });
  // A refreshed session cookie may ride on any answer; all carry the same one.
  const cookie = answers.map((answer) => answer.headers.get("set-cookie")).find((value) => value);
  if (cookie) headers.append("set-cookie", cookie);

  const body = {
    invitations: invitations.ok ? await invitations.json() : null,
    notifications: notifications.ok ? await notifications.json() : null,
    payments: payments.ok ? await payments.json() : null,
  };
  return new Response(JSON.stringify(body), { status: 200, headers });
}
