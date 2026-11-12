import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { testQueryClient } from "@/lib/renderWithQueryClient";
import { useApi } from "@/lib/useApi";
import { lastChangedKey, markGroupChanged, useGroupChange } from "@/lib/useGroupChange";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

function wrapperFor(client: QueryClient) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function callsTo(path: string) {
  return apiMock.mock.calls.filter(([called]) => called === path).length;
}

describe("useGroupChange", () => {
  it("markingAGroupChangedSetsTheTimeAndAsksItsQueriesAgain", async () => {
    // given a group's balances are on screen
    apiMock.mockResolvedValue([]);
    const client = testQueryClient();
    const { result } = renderHook(
      () => ({ balances: useApi<unknown[]>("/groups/g1/balances"), change: useGroupChange("g1") }),
      { wrapper: wrapperFor(client) },
    );
    await waitFor(() => expect(callsTo("/groups/g1/balances")).toBe(1));
    expect(result.current.change.lastChangedAt).toBeUndefined();

    // when the group is marked changed
    act(() => markGroupChanged(client, "g1"));

    // then the balances are asked for again and the time of the change can be read
    await waitFor(() => expect(callsTo("/groups/g1/balances")).toBe(2));
    expect(result.current.change.lastChangedAt).toEqual(expect.any(Number));
  });

  it("anotherGroupIsNotAskedAgain", async () => {
    // given two groups are on screen
    apiMock.mockResolvedValue([]);
    const client = testQueryClient();
    renderHook(() => ({ g1: useApi<unknown[]>("/groups/g1/balances"), g2: useApi<unknown[]>("/groups/g2/balances") }), {
      wrapper: wrapperFor(client),
    });
    await waitFor(() => expect(callsTo("/groups/g2/balances")).toBe(1));

    // when the first group is marked changed
    act(() => markGroupChanged(client, "g1"));
    await waitFor(() => expect(callsTo("/groups/g1/balances")).toBe(2));

    // then the second group was not asked again, and has no change time
    expect(callsTo("/groups/g2/balances")).toBe(1);
    expect(client.getQueryData(lastChangedKey("g2"))).toBeUndefined();
  });

  it("markChangedFromTheHookKeepsItsIdentityBetweenRenders", () => {
    const { result, rerender } = renderHook(() => useGroupChange("g1"), { wrapper: wrapperFor(testQueryClient()) });
    const first = result.current.markChanged;

    rerender();

    expect(result.current.markChanged).toBe(first);
  });
});
