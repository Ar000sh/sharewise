/** An error response from the API (problem+json), or a network failure (status 0). */
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    public detail?: string,
  ) {
    super(detail ?? code);
    this.name = "ApiError";
  }
}

/**
 * Calls the backend through the Next.js proxy. `path` is the backend path, e.g. "/groups";
 * it is requested as "/api/groups". Throws ApiError for non-2xx responses. A 401 (the session is gone) also sends
 * the visitor to sign in and back to this page afterwards, so no caller handles it on its own.
 */
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`/api${path}`, init);
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "Could not reach the server");
  }
  if (!res.ok) {
    if (res.status === 401) {
      const { pathname, search } = window.location;
      window.location.replace(`/login?next=${encodeURIComponent(pathname + search)}`);
    }
    const problem = await res.json().catch(() => ({}));
    throw new ApiError(res.status, problem.code ?? "UNKNOWN", problem.detail);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}
