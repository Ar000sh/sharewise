/** Plain page for "Keycloak cannot be reached" (F13): readable text instead of a stack trace or a blank screen. */
export function signInUnavailable(): Response {
  const html =
    "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Sign-in is unavailable</title></head>" +
    "<body style=\"font-family:sans-serif;max-width:32rem;margin:4rem auto;padding:0 1rem\">" +
    "<h1>Sign-in is unavailable</h1><p>The sign-in service cannot be reached right now. " +
    "Please try again in a moment.</p><p><a href=\"/login\">Back to the sign-in page</a></p></body></html>";
  return new Response(html, { status: 503, headers: { "content-type": "text/html; charset=utf-8" } });
}

/**
 * Plain page for "ShareWise refuses this account" (the backend answered 401 to /me after a valid Keycloak sign-in, for
 * example because the email belongs to another ShareWise account). No session is set; the link ends the Keycloak
 * session, which would otherwise sign the same person straight back in.
 */
export function signInRefused(endSessionUrl: string): Response {
  const html =
    "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Sign-in cannot be used</title></head>" +
    "<body style=\"font-family:sans-serif;max-width:32rem;margin:4rem auto;padding:0 1rem\">" +
    "<h1>This sign-in cannot be used with ShareWise</h1><p>You signed in, but ShareWise does not accept this " +
    "account. It may belong to another ShareWise account or be deactivated.</p>" +
    `<p><a href="${endSessionUrl.replaceAll("&", "&amp;")}">Sign out of the sign-in service</a></p>` +
    "<p><a href=\"/\">Back to the start page</a></p></body></html>";
  return new Response(html, { status: 403, headers: { "content-type": "text/html; charset=utf-8" } });
}
