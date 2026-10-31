import type { Tokens } from "@/lib/oidc";
import { SESSION_COOKIE } from "@/lib/user-cookie";

const WEEK_SECONDS = 7 * 24 * 60 * 60;
const LOGIN_FLOW_COOKIE = "sw_login";
const LOGIN_FLOW_SECONDS = 10 * 60;
// Not part of the base64url alphabet of a JWT, legal in a cookie value, so the tokens need no extra encoding
// (base64 would add a third to a cookie that has a 4 KB limit).
const SEPARATOR = "~";

/** What the login route remembers between the redirect to Keycloak and the callback. */
export type LoginFlow = { state: string; verifier: string; returnTo: string };

/** Reads one cookie from the request's Cookie header. */
export function readCookie(req: Request, name: string): string | undefined {
  const header = req.headers.get("cookie");
  if (!header) return undefined;
  for (const part of header.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (key === name) return rest.join("=");
  }
  return undefined;
}

/** Set-Cookie value that stores access and refresh token where only the server can read them. */
export function sessionCookie(t: Tokens): string {
  const value = [t.accessToken, t.refreshToken, String(t.expiresAt)].join(SEPARATOR);
  return `${SESSION_COOKIE}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${WEEK_SECONDS}`;
}

/** Set-Cookie value that deletes the session cookie. */
export function clearedSessionCookie(): string {
  return `${SESSION_COOKIE}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0`;
}

/** The tokens in the request's session cookie, or undefined when there is none or it is not ours. */
export function readSession(req: Request): Tokens | undefined {
  const raw = readCookie(req, SESSION_COOKIE);
  if (!raw) return undefined;
  const [accessToken, refreshToken, expiresAt, ...extra] = raw.split(SEPARATOR);
  const expires = Number(expiresAt);
  if (!accessToken || !refreshToken || extra.length > 0 || !Number.isFinite(expires)) return undefined;
  return { accessToken, refreshToken, expiresAt: expires };
}

/** Set-Cookie value for the short-lived cookie that carries state, PKCE verifier and returnTo through Keycloak. */
export function loginFlowCookie(flow: LoginFlow): string {
  const value = encodeURIComponent(JSON.stringify(flow));
  return `${LOGIN_FLOW_COOKIE}=${value}; Path=/api/auth; HttpOnly; SameSite=Lax; Max-Age=${LOGIN_FLOW_SECONDS}`;
}

/** Set-Cookie value that deletes the login-flow cookie once the callback has used it. */
export function clearedLoginFlowCookie(): string {
  return `${LOGIN_FLOW_COOKIE}=; Path=/api/auth; HttpOnly; SameSite=Lax; Max-Age=0`;
}

/** The login flow stored by the login route, or undefined when missing or damaged. */
export function readLoginFlow(req: Request): LoginFlow | undefined {
  const raw = readCookie(req, LOGIN_FLOW_COOKIE);
  if (!raw) return undefined;
  try {
    const flow = JSON.parse(decodeURIComponent(raw)) as Partial<LoginFlow>;
    if (typeof flow.state !== "string" || typeof flow.verifier !== "string") return undefined;
    return { state: flow.state, verifier: flow.verifier, returnTo: safeReturnTo(flow.returnTo ?? null) };
  } catch {
    return undefined;
  }
}

const SITE = "http://same-site.invalid";

/**
 * Only same-site paths (F5): "/join/abc" yes; "//evil.example", "https://..." and "/\evil.example" no. Browsers read
 * a backslash like a slash and ignore tabs and newlines inside a URL, so those are refused, and the result must
 * still be on our own origin once the URL is parsed the way a browser does it. Everything else becomes "/".
 */
export function safeReturnTo(value: string | null): string {
  if (!value || !value.startsWith("/") || value[1] === "/" || value[1] === "\\") return "/";
  if (/[\\\u0000-\u001f\u007f]/.test(value)) return "/";
  return new URL(value, SITE).origin === SITE ? value : "/";
}
