import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderResult } from "@testing-library/react";

/** A client for one test: no retries, so an error shows at once and nothing is shared between tests. */
export function testQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

/** Renders a component that uses `useApi` inside a query client; pass the client to seed or inspect the cache. */
export function renderWithQueryClient(
  ui: React.ReactElement,
  client: QueryClient = testQueryClient(),
): RenderResult & { client: QueryClient } {
  const result = render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
  return {
    ...result,
    client,
    rerender: (next) => result.rerender(<QueryClientProvider client={client}>{next}</QueryClientProvider>),
  };
}
