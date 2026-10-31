/** Tokens of one signed-in session. No id token is kept, so the session cookie stays well under 4 KB. */
export type Tokens = { accessToken: string; refreshToken: string; expiresAt: number };

/** Keycloak answered, but refused the code or the refresh token (expired, replayed, revoked). */
export class TokenRejectedError extends Error {
  constructor() {
    super("Keycloak rejected the grant");
    this.name = "TokenRejectedError";
  }
}

/** How long a call to Keycloak may take before sign-in reports "unavailable" instead of hanging (F13). */
export const FETCH_TIMEOUT_MS = 5000;

function issuer(): string {
  return (process.env.KEYCLOAK_ISSUER ?? "http://localhost:8180/realms/sharewise").replace(/\/$/, "");
}

function clientId(): string {
  return process.env.KEYCLOAK_CLIENT_ID ?? "sharewise-web";
}

/** Public URL of this app, the one the browser sees. Redirects are built from it, never from the request's host. */
export function appUrl(): string {
  return (process.env.APP_URL ?? "http://localhost:3000").replace(/\/$/, "");
}

function redirectUri(): string {
  return `${appUrl()}/api/auth/callback`;
}

function base64Url(bytes: Uint8Array): string {
  let text = "";
  for (const b of bytes) text += String.fromCharCode(b);
  return btoa(text).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function randomString(): string {
  return base64Url(crypto.getRandomValues(new Uint8Array(32)));
}

/** Random, unguessable value that ties the callback to the browser that started the login (F6). */
export function generateState(): string {
  return randomString();
}

/** PKCE code verifier: a random secret that only this server knows until the code is exchanged. */
export function generateVerifier(): string {
  return randomString();
}

/** PKCE challenge (method S256): the hash of the verifier that goes to Keycloak first. */
export async function challengeFor(verifier: string): Promise<string> {
  const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(hash));
}

/** Where the browser goes to sign in; with `register` it lands on Keycloak's registration page instead. */
export function authorizationUrl(opts: { state: string; challenge: string; register: boolean }): string {
  const endpoint = opts.register ? "registrations" : "auth";
  const url = new URL(`${issuer()}/protocol/openid-connect/${endpoint}`);
  url.searchParams.set("client_id", clientId());
  url.searchParams.set("redirect_uri", redirectUri());
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", "openid");
  url.searchParams.set("state", opts.state);
  url.searchParams.set("code_challenge", opts.challenge);
  url.searchParams.set("code_challenge_method", "S256");
  return url.toString();
}

async function tokenRequest(params: Record<string, string>): Promise<Tokens> {
  const res = await fetch(`${issuer()}/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ client_id: clientId(), ...params }),
    cache: "no-store",
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
  });
  if (res.status >= 500) throw new Error(`Keycloak answered ${res.status}`);
  if (!res.ok) throw new TokenRejectedError();
  const body = (await res.json()) as Record<string, unknown>;
  const { access_token: access, refresh_token: refreshToken, expires_in: expiresIn } = body;
  // Never store a half-valid session: that would redirect as if signed in and loop while the SSO session lives.
  if (typeof access !== "string" || !access || typeof refreshToken !== "string" || !refreshToken) {
    throw new Error("Keycloak answered without access and refresh token");
  }
  if (typeof expiresIn !== "number" || !Number.isFinite(expiresIn)) {
    throw new Error("Keycloak answered without a numeric expires_in");
  }
  return { accessToken: access, refreshToken, expiresAt: Date.now() + expiresIn * 1000 };
}

/** Swaps the one-time code for tokens; Keycloak checks the PKCE verifier against the challenge sent earlier. */
export function exchangeCode(code: string, verifier: string): Promise<Tokens> {
  return tokenRequest({
    grant_type: "authorization_code",
    code,
    code_verifier: verifier,
    redirect_uri: redirectUri(),
  });
}

/** Gets a fresh access token. Throws TokenRejectedError when the refresh token is expired or revoked (F12). */
export function refresh(refreshToken: string): Promise<Tokens> {
  return tokenRequest({ grant_type: "refresh_token", refresh_token: refreshToken });
}

/** RP-initiated logout: ends the Keycloak session and comes back to the login page (D9). No id token needed. */
export function endSessionUrl(): string {
  const url = new URL(`${issuer()}/protocol/openid-connect/logout`);
  url.searchParams.set("client_id", clientId());
  url.searchParams.set("post_logout_redirect_uri", `${appUrl()}/login`);
  return url.toString();
}

/** True when Keycloak answers at all; used to show "sign-in unavailable" instead of a browser error page (F13). */
export async function keycloakReachable(): Promise<boolean> {
  try {
    const res = await fetch(`${issuer()}/.well-known/openid-configuration`, {
      cache: "no-store",
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    });
    return res.ok;
  } catch {
    return false;
  }
}
