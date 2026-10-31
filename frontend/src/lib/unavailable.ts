/** Plain page for "Keycloak cannot be reached" (F13): readable text instead of a stack trace or a blank screen. */
export function signInUnavailable(): Response {
  const html =
    "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Sign-in is unavailable</title></head>" +
    "<body style=\"font-family:sans-serif;max-width:32rem;margin:4rem auto;padding:0 1rem\">" +
    "<h1>Sign-in is unavailable</h1><p>The sign-in service cannot be reached right now. " +
    "Please try again in a moment.</p><p><a href=\"/login\">Back to the sign-in page</a></p></body></html>";
  return new Response(html, { status: 503, headers: { "content-type": "text/html; charset=utf-8" } });
}
