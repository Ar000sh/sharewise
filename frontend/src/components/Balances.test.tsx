import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { BalancesTab } from "@/components/BalancesTab";
import { ErrorBanner } from "@/components/ErrorBanner";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
afterEach(() => {
  vi.resetAllMocks();
  vi.useRealTimers();
});

const balances = [
  { userId: "u1", displayName: "Alice", netBalance: "630.00" },
  { userId: "u2", displayName: "Bob", netBalance: "-90.00" },
  { userId: "u3", displayName: "Carol", netBalance: "-270.00" },
  { userId: "u4", displayName: "David", netBalance: "-270.00" },
];

describe("BalancesTab", () => {
  it("balancesShowSignedAmountsAndSumZero", async () => {
    apiMock.mockResolvedValue(balances);

    render(<BalancesTab groupId="g1" />);

    expect(await screen.findByText("+630,00 €")).toBeInTheDocument();
    expect(screen.getByText("-90,00 €")).toBeInTheDocument();
    expect(screen.getAllByText("-270,00 €")).toHaveLength(2);
    expect(screen.getByText(/sum/i)).toHaveTextContent("0,00 €");
    expect(apiMock).toHaveBeenCalledWith("/groups/g1/balances");
  });

  it("zeroBalanceMembersAreListed", async () => {
    apiMock.mockResolvedValue([
      { userId: "u1", displayName: "Alice", netBalance: "0.00" },
      { userId: "u2", displayName: "Bob", netBalance: "0.00" },
    ]);

    render(<BalancesTab groupId="g1" />);

    const rows = await screen.findAllByRole("listitem");
    expect(rows).toHaveLength(2);
    expect(rows[1]).toHaveTextContent("Bob");
    expect(rows[1]).toHaveTextContent("0,00 €");
  });

  it("backendUnreachableShowsErrorBanner", async () => {
    apiMock.mockRejectedValue(new ApiError(502, "BACKEND_UNREACHABLE", "Backend is down"));

    render(<BalancesTab groupId="g1" />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/not reachable/i);
  });

  it("unauthenticatedPromptsToPickUser", async () => {
    apiMock.mockRejectedValue(new ApiError(401, "UNAUTHENTICATED", "Missing or invalid session"));

    render(<BalancesTab groupId="g1" />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/please sign in/i);
  });

  describe("afterAnExpenseWasJustRecorded", () => {
    beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));

    const before = [
      { userId: "u1", displayName: "Alice", netBalance: "0.00" },
      { userId: "u2", displayName: "Bob", netBalance: "0.00" },
    ];
    const after = [
      { userId: "u1", displayName: "Alice", netBalance: "90.00" },
      { userId: "u2", displayName: "Bob", netBalance: "-90.00" },
    ];

    it("looksAgainForAFewSecondsBecauseTheLedgerIsUpdatedAfterTheExpenseIsSaved", async () => {
      // given the first answers still show the old balances
      apiMock.mockResolvedValueOnce(before).mockResolvedValueOnce(before).mockResolvedValue(after);

      // when the tab opens right after an expense was recorded
      render(<BalancesTab groupId="g1" lastRecordedAt={Date.now()} />);
      expect(await screen.findByText(/updating/i)).toBeInTheDocument();
      await act(() => vi.advanceTimersByTimeAsync(1200));

      // then the new numbers appear without a manual reload, and the hint disappears when the window is over
      expect(await screen.findByText("+90,00 €")).toBeInTheDocument();
      await act(() => vi.advanceTimersByTimeAsync(4000));
      expect(screen.queryByText(/updating/i)).not.toBeInTheDocument();
    });

    it("stopsLookingAfterTheBoundedWindow", async () => {
      apiMock.mockResolvedValue(before);

      render(<BalancesTab groupId="g1" lastRecordedAt={Date.now()} />);
      await screen.findByText("Sum: 0,00 €", { exact: false });
      await act(() => vi.advanceTimersByTimeAsync(10_000));
      const calls = apiMock.mock.calls.length;
      await act(() => vi.advanceTimersByTimeAsync(10_000));

      expect(apiMock.mock.calls.length).toBe(calls);
      expect(calls).toBeLessThanOrEqual(5);
    });

    it("doesNotLookAgainWhenNothingWasRecordedRecently", async () => {
      apiMock.mockResolvedValue(before);

      render(<BalancesTab groupId="g1" />);
      await screen.findByText("Sum: 0,00 €", { exact: false });
      await act(() => vi.advanceTimersByTimeAsync(10_000));

      expect(apiMock).toHaveBeenCalledTimes(1);
      expect(screen.queryByText(/updating/i)).not.toBeInTheDocument();
    });
  });
});

describe("ErrorBanner", () => {
  it("showsNothingWithoutError", () => {
    const { container } = render(<ErrorBanner error={undefined} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("showsNetworkErrorAsBanner", () => {
    render(<ErrorBanner error={new ApiError(0, "NETWORK_ERROR", "Could not reach the server")} />);

    expect(screen.getByRole("alert")).toHaveTextContent(/could not reach the server/i);
  });
});
