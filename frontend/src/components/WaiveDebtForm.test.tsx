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
};

function edge(from: string, fromName: string, amount: string, pendingAmount = "0.00", to = ALICE, toName = "Alice"): Edge {
  return { fromDebtor: from, fromDebtorName: fromName, toCreditor: to, toCreditorName: toName, amount, pendingAmount };
}

/** Alice is owed by Carol, David and Bob; Carol owes Bob nothing, but Bob owes David (not Alice's business). */
const plan = [edge(CAROL, "Carol", "270.00", "30.00"), edge(DAVID, "David", "270.00"), edge(BOB, "Bob", "90.00")];

const INBOX = "/me/settlement-payments?status=PENDING&role=creditor";

function serve(me: { userId: string; displayName: string }, plans: Edge[][], other?: () => Promise<unknown>) {
  let planCalls = 0;
  apiMock.mockImplementation(async (path: string) => {
    if (path === "/me") return me;
    if (path === INBOX) return [];
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
  it("waiveButtonOnARowPrefillsDebtorAndAmount", async () => {
    // given Alice is owed by three people, Carol has 30,00 pending
    serve({ userId: ALICE, displayName: "Alice" }, [plan]);
    renderTab();
    const owed = await screen.findByRole("region", { name: "Owed to you" });

    // when she waives on Carol's row
    const carolRow = within(owed).getByText("Carol owes 270,00 €").closest("li")!;
    await userEvent.click(within(carolRow).getByRole("button", { name: "Waive" }));

    // then the form has Carol selected and the amount that is not pending
    expect(screen.getByLabelText("Debtor")).toHaveDisplayValue("Carol");
    expect(screen.getByLabelText("Waived amount (€)")).toHaveValue("240.00");
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

  it("noteIsRequired", async () => {
    // given
    serve({ userId: ALICE, displayName: "Alice" }, [plan]);
    renderTab();
    await screen.findByLabelText("Debtor");

    // when she submits without a reason
    await userEvent.click(screen.getByRole("button", { name: "Waive debt" }));

    // then she is told and nothing is sent
    expect(screen.getByRole("alert")).toHaveTextContent(/reason/i);
    expect(waiveCalls()).toHaveLength(0);
  });

  it("successRefreshesThePlan", async () => {
    // given after the waiver Bob's debt is gone from the plan
    serve({ userId: ALICE, displayName: "Alice" }, [plan, plan.slice(0, 2)], async () => ({}));
    renderTab();
    expect(await screen.findByText("Bob owes 90,00 €")).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText("Debtor"), "Bob");
    await userEvent.type(screen.getByLabelText("Reason"), "A gift");

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
    await userEvent.type(screen.getByLabelText("Reason"), "Forgiven");

    // when
    await userEvent.click(screen.getByRole("button", { name: "Waive debt" }));

    // then the message is shown and the form stays usable
    expect(await screen.findByRole("alert")).toHaveTextContent(/up to/i);
    expect(screen.getByRole("button", { name: "Waive debt" })).toBeEnabled();
  });
});
