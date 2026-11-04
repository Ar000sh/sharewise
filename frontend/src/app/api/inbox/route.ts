import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";

/**
 * The bell shows two things from two contexts: the caller's open invitations (state, asked from the group context)
 * and the inbox entries (things that happened, from the notification context). This handler asks both and merges the
 * answers, so the browser makes one call. A part that failed is null; the other one still arrives.
 */
export async function GET(req: Request) {
  await connection();
  const [invitations, notifications] = await Promise.all([
    proxyToBackend(req, "/invitations"),
    proxyToBackend(req, "/notifications?limit=10"),
  ]);

  if (invitations.status === 401) return invitations;
  if (notifications.status === 401) return notifications;

  const headers = new Headers({ "content-type": "application/json" });
  // A refreshed session cookie may ride on either answer; both carry the same one.
  const cookie = invitations.headers.get("set-cookie") ?? notifications.headers.get("set-cookie");
  if (cookie) headers.append("set-cookie", cookie);

  const body = {
    invitations: invitations.ok ? await invitations.json() : null,
    notifications: notifications.ok ? await notifications.json() : null,
  };
  return new Response(JSON.stringify(body), { status: 200, headers });
}
