import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Toast } from "@/components/Toast";

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe("Toast", () => {
  it("announcesItsMessageAndDismissesItselfAfterTheDuration", () => {
    const onDismiss = vi.fn();

    render(<Toast message="Expense saved." onDismiss={onDismiss} durationMs={4000} />);

    expect(screen.getByRole("status")).toHaveTextContent("Expense saved.");
    act(() => vi.advanceTimersByTime(3999));
    expect(onDismiss).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(1));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });
});
