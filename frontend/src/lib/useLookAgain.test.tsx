import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useLookAgain } from "@/lib/useLookAgain";

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe("useLookAgain", () => {
  it("callsReloadAtEachDelayAfterAFreshChangeAndNeverMoreThanFourTimes", async () => {
    // given a change that just happened
    const reload = vi.fn();
    const changedAt = Date.now();
    renderHook(() => useLookAgain(changedAt, reload));
    expect(reload).not.toHaveBeenCalled();

    // when time passes
    await act(() => vi.advanceTimersByTimeAsync(499));
    expect(reload).not.toHaveBeenCalled();
    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(reload).toHaveBeenCalledTimes(1);
    await act(() => vi.advanceTimersByTimeAsync(500));
    expect(reload).toHaveBeenCalledTimes(2);
    await act(() => vi.advanceTimersByTimeAsync(1000));
    expect(reload).toHaveBeenCalledTimes(3);
    await act(() => vi.advanceTimersByTimeAsync(2000));

    // then the window is over: four looks, and no more
    expect(reload).toHaveBeenCalledTimes(4);
    await act(() => vi.advanceTimersByTimeAsync(20_000));
    expect(reload).toHaveBeenCalledTimes(4);
  });

  it("doesNotCallReloadWhenTheChangeIsOlderThanTheLastDelay", async () => {
    const reload = vi.fn();
    const changedAt = Date.now();

    const { result } = renderHook(() => useLookAgain(changedAt - 10_000, reload));
    await act(() => vi.advanceTimersByTimeAsync(10_000));

    expect(reload).not.toHaveBeenCalled();
    expect(result.current.updating).toBe(false);
  });

  it("doesNotCallReloadWithoutAChange", async () => {
    const reload = vi.fn();

    renderHook(() => useLookAgain(undefined, reload));
    await act(() => vi.advanceTimersByTimeAsync(10_000));

    expect(reload).not.toHaveBeenCalled();
  });

  it("isUpdatingWhilePendingAndNotAfterTheLastLook", async () => {
    const reload = vi.fn();
    const changedAt = Date.now();

    const { result } = renderHook(() => useLookAgain(changedAt, reload));
    expect(result.current.updating).toBe(true);
    await act(() => vi.advanceTimersByTimeAsync(3999));
    expect(result.current.updating).toBe(true);
    await act(() => vi.advanceTimersByTimeAsync(1));

    expect(result.current.updating).toBe(false);
  });

  it("stopsLookingAfterUnmount", async () => {
    const reload = vi.fn();
    const changedAt = Date.now();
    const { unmount } = renderHook(() => useLookAgain(changedAt, reload));

    unmount();
    await act(() => vi.advanceTimersByTimeAsync(10_000));

    expect(reload).not.toHaveBeenCalled();
  });
});
