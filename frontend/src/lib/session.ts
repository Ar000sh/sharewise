import { SESSION_COOKIE } from "@/lib/user-cookie";

const WEEK_SECONDS = 7 * 24 * 60 * 60;

/** Set-Cookie value that stores the session token where only the server can read it. */
export function sessionCookie(token: string): string {
  return `${SESSION_COOKIE}=${encodeURIComponent(token)}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${WEEK_SECONDS}`;
}

/** Set-Cookie value that deletes the session cookie. */
export function clearedSessionCookie(): string {
  return `${SESSION_COOKIE}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0`;
}

/**
 * Turns the backend's answer to /auth/login or /auth/register into the browser's answer: on success the token moves
 * into an httpOnly cookie and only { userId, displayName } is returned; errors pass through unchanged.
 */
export async function signInResponse(backend: Response): Promise<Response> {
  if (!backend.ok) return backend;
  const { token, userId, displayName } = (await backend.json()) as {
    token: string;
    userId: string;
    displayName: string;
  };
  return new Response(JSON.stringify({ userId, displayName }), {
    status: backend.status,
    headers: { "content-type": "application/json", "set-cookie": sessionCookie(token) },
  });
}
