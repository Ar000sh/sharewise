import { appUrl, refresh, type Tokens } from "@/lib/oidc";
import { clearedSessionCookie, readSession, sessionCookie } from "@/lib/session";

/** Refresh when the access token has less than this left (milliseconds, like `Tokens.expiresAt`). */
const REFRESH_MARGIN_MS = 30_000;

/** Refreshes in progress, by refresh token: parallel requests wait for one result instead of replaying the token (F7). */
const refreshing = new Map<string, Promise<Tokens>>();

function refreshOnce(refreshToken: string): Promise<Tokens> {
  const running = refreshing.get(refreshToken);
  if (running) return running;
  const started = refresh(refreshToken).finally(() => refreshing.delete(refreshToken));
  refreshing.set(refreshToken, started);
  return started;
}

function problem(status: number, title: string, code: string, detail: string, setCookie?: string): Response {
  const headers = new Headers({ "content-type": "application/problem+json" });
  if (setCookie) headers.append("set-cookie", setCookie);
  return new Response(JSON.stringify({ type: "about:blank", title, status, code, detail }), { status, headers });
}

/**
 * Forwards a request from a Next.js route handler to the Spring Boot backend at BACKEND_URL
 * and passes status and body back unchanged. The browser never calls the backend directly.
 *
 * The cookie is sent by the browser on its own, so a request that changes something must come from our own page:
 * its Origin has to be ours (F2). The bearer token is the access token inside the session cookie; when it is about
 * to expire it is refreshed first and the new tokens are set on the response (F7, F12).
 */
export async function proxyToBackend(req: Request, backendPath: string): Promise<Response> {
  const changesState = req.method !== "GET" && req.method !== "HEAD";
  if (changesState && req.headers.get("origin") !== new URL(appUrl()).origin) {
    return problem(403, "Forbidden", "FORBIDDEN_ORIGIN", "The request does not come from this application");
  }

  let session = readSession(req);
  let newCookie: string | undefined;
  if (session && session.expiresAt - Date.now() < REFRESH_MARGIN_MS) {
    try {
      session = await refreshOnce(session.refreshToken);
      newCookie = sessionCookie(session);
    } catch {
      return problem(401, "Unauthorized", "UNAUTHENTICATED", "The session has expired", clearedSessionCookie());
    }
  }

  const base = process.env.BACKEND_URL ?? "http://localhost:8080";
  const url = new URL(req.url);
  const target = `${base}${backendPath}${url.search}`;

  const headers = new Headers();
  const contentType = req.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  if (session) headers.set("authorization", `Bearer ${session.accessToken}`);

  try {
    const body = changesState ? await req.text() : undefined;
    const res = await fetch(target, {
      method: req.method,
      headers,
      body: body || undefined,
      cache: "no-store",
    });
    const out = new Headers();
    const resType = res.headers.get("content-type");
    if (resType) out.set("content-type", resType);
    if (newCookie) out.append("set-cookie", newCookie);
    return new Response(res.status === 204 ? null : await res.text(), { status: res.status, headers: out });
  } catch {
    return problem(502, "Bad Gateway", "BACKEND_UNREACHABLE", "The backend could not be reached", newCookie);
  }
}
