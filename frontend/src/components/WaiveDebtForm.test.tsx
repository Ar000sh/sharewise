import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { SettleUpTab } from "@/components/SettleUpTab";
import { CurrentUserProvider } from "@/lib/currentUser";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

const ALICE = "u1";
const BOB = "u2";
const CAROL = "u3";
const DAVID = "u4";

type Edge = {
  fromDebtor: string;
  fromDebtorName: string;
  toCreditor: string;
  toCreditorName: string;
  amount: string;
  pendingAmount: string;
  payableAmount: string;
};

function edge(from: string, fromName: string, amount: string, pendingAmount = "0.00", to = ALICE, toName = "Alice", payableAmount = amount): Edge {
  return { fromDebtor: from, fromDebtorName: fromName, toCreditor: to, toCreditorName: toName, amount, pendingAmount, payableAmount };
}

/** Alice is owed by Carol, David and Bob; Carol owes Bob nothing, but Bob owes David (not Alice's business). */
const plan = [edge(CAROL, "Carol", "270.00", "30.00", ALICE, "Alice", "240.00"), edge(DAVID, "David", "270.00"), edge(BOB, "Bob", "90.00")];

function serve(me: { userId: string; displayName: string }, plans: Edge[][], other?: () => Promise<unknown>) {
  let planCalls = 0;
  apiMock.mockImplementation(async (path: string) => {
    if (path === "/me") return me;
    if (path === "/groups/g1/settlement-plan") return plans[Math.min(planCalls++, plans.length - 1)];
    if (other) return other();
    throw new Error(`unexpected call ${path}`);
  });
}

function renderTab() {
  return render(
    <CurrentUserProvider>
      <SettleUpTab groupId="g1" />
    </CurrentUserProvider>,
  );
}

function waiveCalls() {
  return apiMock.mock.calls.filter(([path]) => path === "/groups/g1/debt-waivers");
}

describe("WaiveDebtForm", () => {
  it("waiveButtonOnARowOpensTheFormInThatRowWithDebtorAndAmount", async () => {
    // given Alice is owed by three people, Carol has 30,00 pending
    serve({ userId: ALICE, displayName: "Alice" }, [plan]);
    renderTab();
    const owed = await screen.findByRole("region", { name: "Owed to you" });

    // when she waives on Carol's row
    const carolRow = within(owed).getByText("Carol owes 270,00 €").closest("li")!;
    await userEvent.click(within(carolRow).getByRole("button", { name: "Waive" }));

    // then the form opens in that row with Carol fixed and the amount that is not pending
    expect(within(carolRow).getByLabelText("Debtor")).toHaveDisplayValue("Carol");
    expect(within(carolRow).getByLabelText("Debtor")).toBeDisabled();
    expect(within(carolRow).getByLabelText("Waived amount (€)")).toHaveValue("240.00");
    expect(within(carolRow).queryByRole("button", { name: "Waive" })).not.toBeInTheDocument();
  });

  it("waivingFromARowSendsTheRequestWithoutAReasonAndRefreshesThePlan", async () => {
    // given Bob's debt is gone from the plan after the waiver
    serve({ userId: ALICE, displayName: "Alice" }, [plan, plan.slice(0, 2)], async () => ({}));
    renderTab();
    const owed = await screen.findByRole("region", { name: "Owed to you" });
    const bobRow = within(owed).getByText("Bob owes 90,00 €").closest("li")!;
    await userEvent.click(within(bobRow).getByRole("button", { name: "Waive" }));

    // when she waives without giving a reason
    await userEvent.click(within(bobRow).getByRole("button", { name: "Waive debt" }));

    // then the request has no note, the row is gone and she is told
    await vi.waitFor(() => expect(waiveCalls()).toHaveLength(1));
    expect(JSON.parse(String(waiveCalls()[0][1]?.body))).toEqual({ fromDebtor: BOB, amount: "90.00" });
    await vi.waitFor(() => expect(screen.queryByText("Bob owes 90,00 €")).not.toBeInTheDocument());
    expect(await screen.findByRole("status")).toHaveTextContent(/waived/i);
  });

  it("dropdownListsOnlyPeopleWhoOweMe", async () => {
    // given the plan has a row between two other people
    const others = [...plan, edge(BOB, "Bob", "10.00", "0.00", DAVID, "David")];
    serve({ userId: ALICE, displayName: "Alice" }, [others]);

    // when
    renderTab();
    const debtor = await screen.findByLabelText("Debtor");

    // then only the three who owe Alice are offered
    const options = within(debtor).getAllByRole("option").map((o) => o.textContent);
    expect(options).toEqual(["Carol", "David", "Bob"]);
  });

  it("theReasonIsOptionalAndIsLeftOutOfTheRequestWhenEmpty", async () => {
    // given
    serve({ userId: ALICE, displayName: "Alice" }, [plan], async () => ({}));
    renderTab();
    await screen.findByLabelText("Debtor");
    expect(screen.getByLabelText("Reason (optional)")).toHaveValue("");

    // when she submits without a reason
    await userEvent.click(screen.getByRole("button", { name: "Waive debt" }));

    // then the request is sent without a note and no error is shown
    await vi.waitFor(() => expect(waiveCalls()).toHaveLength(1));
    expect(JSON.parse(String(waiveCalls()[0][1]?.body))).toEqual({ fromDebtor: CAROL, amount: "240.00" });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("successRefreshesThePlan", async () => {
    // given after the waiver Bob's debt is gone from the plan
    serve({ userId: ALICE, displayName: "Alice" }, [plan, plan.slice(0, 2)], async () => ({}));
    renderTab();
    expect(await screen.findByText("Bob owes 90,00 €")).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText("Debtor"), "Bob");
    await userEvent.type(screen.getByLabelText("Reason (optional)"), "A gift");

    // when
    await userEvent.click(screen.getByRole("button", { name: "Waive debt" }));

    // then the request carries debtor, amount with two decimals and the note, and the plan is fetched again
    expect(JSON.parse(String(waiveCalls()[0][1]?.body))).toEqual({ fromDebtor: BOB, amount: "90.00", note: "A gift" });
    expect(waiveCalls()[0][1]?.method).toBe("POST");
    await vi.waitFor(() => expect(screen.queryByText("Bob owes 90,00 €")).not.toBeInTheDocument());
    expect(await screen.findByRole("status")).toHaveTextContent(/waived/i);
  });

  it("tooHighAmountShowsTheProblemMessage", async () => {
    // given the backend refuses the amount
    serve({ userId: ALICE, displayName: "Alice" }, [plan], async () => {
      throw new ApiError(422, "PAYMENT_EXCEEDS_OPEN_DEBT", "too much");
    });
    renderTab();
    await screen.findByLabelText("Debtor");
    await userEvent.type(screen.getByLabelText("Reason (optional)"), "Forgiven");

    // when
    await userEvent.click(screen.getByRole("button", { name: "Waive debt" }));

    // then the message is shown and the form stays usable
    expect(await screen.findByRole("alert")).toHaveTextContent(/up to/i);
    expect(screen.getByRole("button", { name: "Waive debt" })).toBeEnabled();
  });

  it("afterAFullWaiveTheFormOffersTheNextDebtorAndCanBeSubmitted", async () => {
    // given Bob's debt disappears from the plan after the waiver
    serve({ userId: ALICE, displayName: "Alice" }, [plan, plan.slice(0, 2)], async () => ({}));
    renderTab();
    await screen.findByText("Bob owes 90,00 €");
    await userEvent.selectOptions(screen.getByLabelText("Debtor"), "Bob");
    await userEvent.type(screen.getByLabelText("Reason (optional)"), "A gift");
    await userEvent.click(screen.getByRole("button", { name: "Waive debt" }));
    await vi.waitFor(() => expect(screen.queryByText("Bob owes 90,00 €")).not.toBeInTheDocument());

    // when she waives again with what the form now shows
    expect(screen.getByLabelText("Debtor")).toHaveDisplayValue("Carol");
    await userEvent.type(screen.getByLabelText("Reason (optional)"), "Second");
    await userEvent.click(screen.getByRole("button", { name: "Waive debt" }));

    // then the request is for the shown debtor with the amount shown, not for the removed one
    await vi.waitFor(() => expect(waiveCalls()).toHaveLength(2));
    expect(JSON.parse(String(waiveCalls()[1][1]?.body))).toEqual({ fromDebtor: CAROL, amount: "240.00", note: "Second" });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("afterAPartialWaiveTheAmountFollowsTheNewOpenDebt", async () => {
    // given David owed 270,00 and 40,00 of it is waived
    const after = [plan[0], edge(DAVID, "David", "230.00"), plan[2]];
    serve({ userId: ALICE, displayName: "Alice" }, [plan, after], async () => ({}));
    renderTab();
    await screen.findByText("David owes 270,00 €");
    await userEvent.selectOptions(screen.getByLabelText("Debtor"), "David");
    const amount = screen.getByLabelText("Waived amount (€)");
    await userEvent.clear(amount);
    await userEvent.type(amount, "40,00");
    await userEvent.type(screen.getByLabelText("Reason (optional)"), "Part");
    await userEvent.click(screen.getByRole("button", { name: "Waive debt" }));

    // when the plan has reloaded
    await screen.findByText("David owes 230,00 €");

    // then the amount is the new open debt and the next waive is accepted
    expect(screen.getByLabelText("Waived amount (€)")).toHaveValue("230.00");
    await userEvent.type(screen.getByLabelText("Reason (optional)"), "Rest");
    await userEvent.click(screen.getByRole("button", { name: "Waive debt" }));
    await vi.waitFor(() => expect(waiveCalls()).toHaveLength(2));
    expect(JSON.parse(String(waiveCalls()[1][1]?.body))).toEqual({ fromDebtor: DAVID, amount: "230.00", note: "Rest" });
  });
});
