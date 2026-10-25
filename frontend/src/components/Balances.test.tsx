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
  { memberId: "u1", displayName: "Alice", balanceCents: 63000 },
  { memberId: "u2", displayName: "Bob", balanceCents: -9000 },
  { memberId: "u3", displayName: "Carol", balanceCents: -27000 },
  { memberId: "u4", displayName: "David", balanceCents: -27000 },
];

describe("BalancesTab", () => {
  it("balancesShowSignedAmountsAndSumZero", async () => {
    apiMock.mockResolvedValue(balances);

    render(<BalancesTab groupId="g1" refreshKey={0} />);

    expect(await screen.findByText("+630,00 €")).toBeInTheDocument();
    expect(screen.getByText("-90,00 €")).toBeInTheDocument();
    expect(screen.getAllByText("-270,00 €")).toHaveLength(2);
    expect(screen.getByText(/sum/i)).toHaveTextContent("0,00 €");
    expect(apiMock).toHaveBeenCalledWith("/groups/g1/balances");
  });

  it("zeroBalanceMembersAreListed", async () => {
    apiMock.mockResolvedValue([
      { memberId: "u1", displayName: "Alice", balanceCents: 0 },
      { memberId: "u2", displayName: "Bob", balanceCents: 0 },
    ]);

    render(<BalancesTab groupId="g1" refreshKey={0} />);

    const rows = await screen.findAllByRole("listitem");
    expect(rows).toHaveLength(2);
    expect(rows[1]).toHaveTextContent("Bob");
    expect(rows[1]).toHaveTextContent("0,00 €");
  });

  it("backendUnreachableShowsErrorBanner", async () => {
    apiMock.mockRejectedValue(new ApiError(502, "BACKEND_UNREACHABLE", "Backend is down"));

    render(<BalancesTab groupId="g1" refreshKey={0} />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/not reachable/i);
  });

  it("unauthenticatedPromptsToPickUser", async () => {
    apiMock.mockRejectedValue(new ApiError(401, "UNAUTHENTICATED", "Missing or unknown X-User-Id"));

    render(<BalancesTab groupId="g1" refreshKey={0} />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/please sign in/i);
  });

  it("refetchesWhenRefreshKeyChanges", async () => {
    apiMock.mockResolvedValue(balances);

    const { rerender } = render(<BalancesTab groupId="g1" refreshKey={0} />);
    await screen.findByText("+630,00 €");
    rerender(<BalancesTab groupId="g1" refreshKey={1} />);

    await vi.waitFor(() => expect(apiMock).toHaveBeenCalledTimes(2));
  });

  describe("whenBalancesDoNotYetSumToZero", () => {
    beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));

    it("refetchesShortlyUntilTheSumIsZero", async () => {
      apiMock
        .mockResolvedValueOnce([
          { memberId: "u1", displayName: "Alice", balanceCents: 500 },
          { memberId: "u2", displayName: "Bob", balanceCents: 0 },
        ])
        .mockResolvedValue([
          { memberId: "u1", displayName: "Alice", balanceCents: 500 },
          { memberId: "u2", displayName: "Bob", balanceCents: -500 },
        ]);

      render(<BalancesTab groupId="g1" refreshKey={0} />);
      await screen.findByText("+5,00 €");
      await act(() => vi.advanceTimersByTimeAsync(1500));

      expect(await screen.findByText("-5,00 €")).toBeInTheDocument();
      expect(apiMock).toHaveBeenCalledTimes(2);
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
