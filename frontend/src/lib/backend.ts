import { SESSION_COOKIE } from "@/lib/user-cookie";

function readCookie(req: Request, name: string): string | undefined {
  const header = req.headers.get("cookie");
  if (!header) return undefined;
  for (const part of header.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (key === name) return decodeURIComponent(rest.join("="));
  }
  return undefined;
}

function problem(status: number, title: string, code: string, detail: string): Response {
  return new Response(JSON.stringify({ type: "about:blank", title, status, code, detail }), {
    status,
    headers: { "content-type": "application/problem+json" },
  });
}

/**
 * Forwards a request from a Next.js route handler to the Spring Boot backend at BACKEND_URL
 * and passes status and body back unchanged. The browser never calls the backend directly.
 */
export async function proxyToBackend(req: Request, backendPath: string): Promise<Response> {
  const base = process.env.BACKEND_URL ?? "http://localhost:8080";
  const url = new URL(req.url);
  const target = `${base}${backendPath}${url.search}`;

  const headers = new Headers();
  const contentType = req.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  const session = readCookie(req, SESSION_COOKIE);
  if (session) headers.set("authorization", `Bearer ${session}`);

  try {
    const hasBody = req.method !== "GET" && req.method !== "HEAD";
    const body = hasBody ? await req.text() : undefined;
    const res = await fetch(target, {
      method: req.method,
      headers,
      body: body || undefined,
      cache: "no-store",
    });
    const out = new Headers();
    const resType = res.headers.get("content-type");
    if (resType) out.set("content-type", resType);
    return new Response(res.status === 204 ? null : await res.text(), { status: res.status, headers: out });
  } catch {
    return problem(502, "Bad Gateway", "BACKEND_UNREACHABLE", "The backend could not be reached");
  }
}
