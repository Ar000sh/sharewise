import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api";

/** How often a failed call is asked again (the default of three tries), except a 4xx: asking again will not change it. */
function retry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
  return failureCount < 3;
}

/**
 * The one cache of server data in the browser (design 2026-11-11, L1). `staleTime: 0` means a cached answer is shown at
 * once and asked for again whenever its page opens, so the cache never hides a change for long.
 */
export function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { staleTime: 0, refetchOnWindowFocus: true, retry } },
  });
}
