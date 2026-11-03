import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { Dashboard } from "@/components/Dashboard";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

const PENDING = "/me/settlement-payments?status=PENDING&role=creditor";

function serve(getWaiting: () => unknown[], onConfirm?: () => void) {
  apiMock.mockImplementation(async (path: string) => {
    if (path === "/groups") return [];
    if (path === PENDING) return getWaiting();
    if (path === "/settlement-payments/p1/confirm") {
      onConfirm?.();
      return {};
    }
    throw new Error(`unexpected call ${path}`);
  });
}

describe("Dashboard", () => {
  it("theHeadingIsJustTheTitleAndTheBadgeSitsBesideIt", async () => {
    // given one payment waits
    serve(() => [{ id: "p1", groupId: "g1", groupName: "Mallorca", fromDebtor: "u2", fromDebtorName: "Bob", amount: "30.00" }]);

    // when
    render(<Dashboard />);
    const badge = await screen.findAllByLabelText("1 settlement payment waiting for your answer");

    // then the heading name is not polluted by the count, and the badge is not inside the heading
    const heading = screen.getByRole("heading", { name: "Your groups" });
    expect(heading).toBeInTheDocument();
    expect(heading).not.toContainElement(badge[0]);
  });

  it("theBadgeDropsRightAfterAPaymentWasConfirmed", async () => {
    // given
    let waiting = [{ id: "p1", groupId: "g1", groupName: "Mallorca", fromDebtor: "u2", fromDebtorName: "Bob", amount: "30.00" }];
    serve(
      () => waiting,
      () => {
        waiting = [];
      },
    );
    render(<Dashboard />);

    // when
    await userEvent.click(await screen.findByRole("button", { name: "Confirm payment from Bob" }));

    // then
    await vi.waitFor(() =>
      expect(screen.queryByLabelText(/settlement payments? waiting for your answer/)).not.toBeInTheDocument(),
    );
  });
});
