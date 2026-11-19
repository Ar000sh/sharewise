import { focusManager, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { makeQueryClient } from "@/lib/queryClient";
import { testQueryClient } from "@/lib/renderWithQueryClient";
import { POLL_MS, queryKeyOf, useApi } from "@/lib/useApi";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);

function wrapperFor(client: QueryClient) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function hook(path: string, options?: { poll?: boolean }) {
  return renderHook(() => useApi<number[]>(path, options), { wrapper: wrapperFor(testQueryClient()) });
}

beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
afterEach(() => {
  vi.resetAllMocks();
  vi.useRealTimers();
  focusManager.setFocused(undefined);
});

describe("useApi", () => {
  it("theQueryKeyIsThePathInSegments", () => {
    expect(queryKeyOf("/groups/g1/balances")).toEqual(["groups", "g1", "balances"]);
  });

  it("loadsThePathAndReturnsTheData", async () => {
    // given
    apiMock.mockResolvedValue([1]);

    // when
    const { result } = hook("/groups/g1/balances");

    // then
    await waitFor(() => expect(result.current.data).toEqual([1]));
    expect(apiMock).toHaveBeenCalledWith("/groups/g1/balances");
    expect(result.current.error).toBeUndefined();
  });

  it("aFailedCallIsTheErrorAndIsNotRetriedFor4xx", async () => {
    // given the default client of the app (retries on), a 403 is not worth asking again
    apiMock.mockRejectedValue(new ApiError(403, "NOT_A_MEMBER"));
    const { result } = renderHook(() => useApi<number[]>("/groups/g1"), { wrapper: wrapperFor(makeQueryClient()) });

    // when / then
    await waitFor(() => expect(result.current.error).toBeInstanceOf(ApiError));
    expect(apiMock).toHaveBeenCalledTimes(1);
  });

  it("reloadKeepsItsIdentityBetweenRenders", async () => {
    apiMock.mockResolvedValue([1]);
    const { result, rerender } = hook("/groups/g1/balances");
    await waitFor(() => expect(result.current.data).toEqual([1]));
    const first = result.current.reload;

    rerender();

    expect(result.current.reload).toBe(first);
  });

  it("reloadAsksAgain", async () => {
    apiMock.mockResolvedValue([1]);
    const { result } = hook("/groups/g1/balances");
    await waitFor(() => expect(apiMock).toHaveBeenCalledTimes(1));

    await act(() => result.current.reload());

    expect(apiMock).toHaveBeenCalledTimes(2);
  });

  it("pollAsksAgainEveryTenSeconds", async () => {
    apiMock.mockResolvedValue([1]);
    hook("/groups/g1/balances", { poll: true });
    await waitFor(() => expect(apiMock).toHaveBeenCalledTimes(1));

    await act(() => vi.advanceTimersByTimeAsync(POLL_MS));
    expect(apiMock).toHaveBeenCalledTimes(2);

    await act(() => vi.advanceTimersByTimeAsync(POLL_MS));
    expect(apiMock).toHaveBeenCalledTimes(3);
  });

  it("pollPausesWhileTheBrowserTabIsHidden", async () => {
    apiMock.mockResolvedValue([1]);
    hook("/groups/g1/balances", { poll: true });
    await waitFor(() => expect(apiMock).toHaveBeenCalledTimes(1));

    act(() => focusManager.setFocused(false));
    await act(() => vi.advanceTimersByTimeAsync(2 * POLL_MS));

    expect(apiMock).toHaveBeenCalledTimes(1);
  });

  it("withoutPollNothingIsAskedAgain", async () => {
    apiMock.mockResolvedValue([1]);
    hook("/groups/g1/balances");
    await waitFor(() => expect(apiMock).toHaveBeenCalledTimes(1));

    await act(() => vi.advanceTimersByTimeAsync(2 * POLL_MS));

    expect(apiMock).toHaveBeenCalledTimes(1);
  });
});
