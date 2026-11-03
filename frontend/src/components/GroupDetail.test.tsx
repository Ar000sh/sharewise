import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { GroupDetail } from "@/components/GroupDetail";
import { CurrentUserProvider } from "@/lib/currentUser";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

const group = {
  id: "g1",
  name: "Mallorca 2027",
  status: "OPEN",
  memberCount: 3,
  members: [
    { userId: "u1", displayName: "Alice", role: "ADMIN" },
    { userId: "u2", displayName: "Bob", role: "MEMBER" },
    { userId: "u3", displayName: "Carol", role: "MEMBER" },
  ],
};

/** Answers the calls the page makes. `me` may be a promise so a test decides when "who am I" arrives. */
function serve(me: Promise<unknown> | unknown, pending: unknown[] = []) {
  apiMock.mockImplementation(async (path: string) => {
    if (path === "/me") return me;
    if (path === "/groups/g1") return group;
    if (path === "/groups/g1/expenses") return [];
    if (path === "/groups/g1/balances") return [];
    if (path === "/groups/g1/settlement-plan") return [];
    if (path === "/me/settlement-payments?status=PENDING&role=creditor") return pending;
    throw new Error(`unexpected call ${path}`);
  });
}

function renderPage() {
  render(
    <CurrentUserProvider>
      <GroupDetail groupId="g1" />
    </CurrentUserProvider>,
  );
}

describe("GroupDetail", () => {
  it("offersTheExpenseFormOnlyOnceItKnowsWhoIsAskingSoThePayerDefaultIsRight", async () => {
    // given the group arrives before "who am I"
    let answerMe: (user: unknown) => void = () => {};
    serve(new Promise((resolve) => (answerMe = resolve)));

    // when
    renderPage()
    expect(await screen.findByRole("heading", { name: "Mallorca 2027" })).toBeInTheDocument();

    // then there is no form yet, so the payer cannot silently default to the first member
    expect(screen.queryByRole("button", { name: /add expense/i })).not.toBeInTheDocument();
    answerMe({ userId: "u2", displayName: "Bob" });
    expect(await screen.findByLabelText(/paid by/i)).toHaveValue("u2");
  });

  it("offersASettleUpTabBetweenBalancesAndMembers", async () => {
    // given
    serve({ userId: "u2", displayName: "Bob" });
    renderPage();

    // when
    await userEvent.click(await screen.findByRole("tab", { name: "Settle up" }));

    // then
    expect(screen.getAllByRole("tab").map((t) => t.textContent)).toEqual(["Expenses", "Balances", "Settle up", "Members"]);
    expect(await screen.findByText("Everybody is settled.")).toBeInTheDocument();
  });

  it("showsHowManyPaymentsWaitForAnAnswerNextToTheSettleUpTab", async () => {
    // given one payment of this group and one of another group wait for Alice
    const waiting = [
      { id: "p1", groupId: "g1", fromDebtorName: "Bob", amount: "30.00" },
      { id: "p2", groupId: "g2", fromDebtorName: "Carol", amount: "5.00" },
    ];
    serve({ userId: "u1", displayName: "Alice" }, waiting);

    // when
    renderPage();

    // then only the group's own payment is counted
    expect(await screen.findByLabelText("1 settlement payment waiting for your answer")).toBeInTheDocument();
  });

  it("theSettleUpBadgeDropsRightAfterAPaymentWasConfirmed", async () => {
    // given one payment waits for Alice; once it is confirmed the list is empty
    let waiting = [{ id: "p1", groupId: "g1", fromDebtor: "u2", fromDebtorName: "Bob", amount: "30.00" }];
    apiMock.mockImplementation(async (path: string) => {
      if (path === "/me") return { userId: "u1", displayName: "Alice" };
      if (path === "/groups/g1") return group;
      if (path === "/groups/g1/expenses") return [];
      if (path === "/groups/g1/settlement-plan") return [];
      if (path === "/me/settlement-payments?status=PENDING&role=creditor") return waiting;
      if (path === "/settlement-payments/p1/confirm") {
        waiting = [];
        return {};
      }
      throw new Error(`unexpected call ${path}`);
    });
    renderPage();
    await screen.findByLabelText("1 settlement payment waiting for your answer");
    await userEvent.click(screen.getByRole("tab", { name: /Settle up/ }));

    // when Alice confirms
    await userEvent.click(await screen.findByRole("button", { name: "Confirm payment from Bob" }));

    // then the badge is gone at once
    await vi.waitFor(() =>
      expect(screen.queryByLabelText(/settlement payments? waiting for your answer/)).not.toBeInTheDocument(),
    );
  });

  it("tellsTheUserThatBalancesFollowAfterAnExpenseWasRecorded", async () => {
    // given
    serve({ userId: "u2", displayName: "Bob" });
    renderPage();
    await userEvent.type(await screen.findByLabelText(/title/i), "Dinner");
    await userEvent.type(screen.getByLabelText(/amount/i), "180");

    // when
    await userEvent.click(screen.getByRole("button", { name: /add expense/i }));

    // then
    expect(await screen.findByRole("status")).toHaveTextContent(/expense saved.*balances/i);
  });
});
