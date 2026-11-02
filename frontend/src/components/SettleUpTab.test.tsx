import { act, fireEvent, render, screen, within } from "@testing-library/react";
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

type Edge = { fromDebtor: string; fromDebtorName: string; toCreditor: string; toCreditorName: string; amount: string; pendingAmount: string };

function edge(from: string, fromName: string, amount: string, pendingAmount = "0.00"): Edge {
  return { fromDebtor: from, fromDebtorName: fromName, toCreditor: ALICE, toCreditorName: "Alice", amount, pendingAmount };
}

/** The Mallorca plan: Alice is owed by all three. */
const mallorca = [edge(CAROL, "Carol", "270.00"), edge(DAVID, "David", "270.00"), edge(BOB, "Bob", "90.00")];

/** Answers "who am I" and the plan; every other call is answered by `other` (the payment POST). */
function serve(me: { userId: string; displayName: string }, plan: Edge[][] | Edge[], other?: () => Promise<unknown>) {
  const plans = Array.isArray(plan[0]) ? (plan as Edge[][]) : [plan as Edge[]];
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

function paymentCalls() {
  return apiMock.mock.calls.filter(([path]) => path === "/groups/g1/settlement-payments");
}

describe("SettleUpTab", () => {
  it("showsTheRowsWhereIOweAndWhereIAmOwed", async () => {
    // given Carol owes Alice 270,00 and the others owe Alice too
    serve({ userId: CAROL, displayName: "Carol" }, mallorca);

    // when Carol looks at the tab
    const carolView = renderTab();

    // then she sees what she owes, and the rest of the group's plan quietly below
    const owe = await screen.findByRole("region", { name: "You owe" });
    expect(within(owe).getByText("270,00 € to Alice")).toBeInTheDocument();
    const others = screen.getByRole("region", { name: "Others" });
    expect(within(others).getByText("David owes Alice 270,00 €")).toBeInTheDocument();
    expect(within(others).getByText("Bob owes Alice 90,00 €")).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Owed to you" })).not.toBeInTheDocument();
    carolView.unmount();

    // and Alice sees the three who owe her, without any action button
    serve({ userId: ALICE, displayName: "Alice" }, mallorca);
    renderTab();
    const owed = await screen.findByRole("region", { name: "Owed to you" });
    expect(within(owed).getByText("Carol owes 270,00 €")).toBeInTheDocument();
    expect(within(owed).getByText("David owes 270,00 €")).toBeInTheDocument();
    expect(within(owed).getByText("Bob owes 90,00 €")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "I paid" })).not.toBeInTheDocument();
  });

  it("iPaidSendsTheAmountAndDisablesTheButtonWhileSending", async () => {
    // given the POST stays open until the test lets it finish
    let finish: (value: unknown) => void = () => {};
    serve({ userId: CAROL, displayName: "Carol" }, mallorca, () => new Promise((resolve) => (finish = resolve)));
    renderTab();
    await userEvent.click(await screen.findByRole("button", { name: "I paid" }));
    expect(screen.getByLabelText("Amount (€)")).toHaveValue("270.00");

    // when she sends it and tries again while it is on its way
    const send = screen.getByRole("button", { name: "Send payment" });
    await userEvent.click(send);
    await userEvent.click(send);

    // then exactly one request left, with the amount as a string with two decimals, and the button is disabled
    expect(send).toBeDisabled();
    expect(paymentCalls()).toHaveLength(1);
    const [, init] = paymentCalls()[0];
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({ toCreditor: ALICE, amount: "270.00" });
    finish({});
  });

  it("showsPendingAmountOnTheRow", async () => {
    // given Carol already reported 30,00 that Alice has not confirmed yet
    serve({ userId: CAROL, displayName: "Carol" }, [edge(CAROL, "Carol", "270.00", "30.00")]);

    // when
    renderTab();

    // then the row says so, and the form offers only what is still open
    expect(await screen.findByText("30,00 € awaiting confirmation")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "I paid" }));
    expect(screen.getByLabelText("Amount (€)")).toHaveValue("240.00");
  });

  it("showsTheProblemMessageWhenTheAmountIsTooHigh", async () => {
    // given the backend refuses the payment
    serve({ userId: CAROL, displayName: "Carol" }, mallorca, async () => {
      throw new ApiError(422, "PAYMENT_EXCEEDS_OPEN_DEBT", "too much");
    });
    renderTab();
    await userEvent.click(await screen.findByRole("button", { name: "I paid" }));

    // when
    await userEvent.click(screen.getByRole("button", { name: "Send payment" }));

    // then the message is shown and the form stays open for a correction
    expect(await screen.findByRole("alert")).toHaveTextContent(/only pay up to/i);
    expect(screen.getByLabelText("Amount (€)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send payment" })).toBeEnabled();
  });

  it("emptyPlanSaysEverybodyIsSettled", async () => {
    serve({ userId: CAROL, displayName: "Carol" }, []);

    renderTab();

    expect(await screen.findByText("Everybody is settled.")).toBeInTheDocument();
  });

  it("iPaidCannotExceedTheOpenAmount", async () => {
    // given
    serve({ userId: CAROL, displayName: "Carol" }, mallorca);
    renderTab();
    await userEvent.click(await screen.findByRole("button", { name: "I paid" }));

    // when she types more than the 270,00 she owes
    const amount = screen.getByLabelText("Amount (€)");
    await userEvent.clear(amount);
    await userEvent.type(amount, "270.01");
    await userEvent.click(screen.getByRole("button", { name: "Send payment" }));

    // then nothing is sent and she is told why
    expect(screen.getByRole("alert")).toHaveTextContent(/270,00 €/);
    expect(paymentCalls()).toHaveLength(0);
  });

  it("reloadsThePlanAfterASuccessfulPayment", async () => {
    // given the plan after the payment shows the amount as pending
    serve({ userId: CAROL, displayName: "Carol" }, [mallorca, [edge(CAROL, "Carol", "270.00", "100.00"), ...mallorca.slice(1)]], async () => ({}));
    renderTab();
    await userEvent.click(await screen.findByRole("button", { name: "I paid" }));
    const amount = screen.getByLabelText("Amount (€)");
    await userEvent.clear(amount);
    await userEvent.type(amount, "100,00");

    // when
    await userEvent.click(screen.getByRole("button", { name: "Send payment" }));

    // then the amount went out with two decimals, and the plan shows the pending part
    expect(await screen.findByText("100,00 € awaiting confirmation")).toBeInTheDocument();
    expect(JSON.parse(String(paymentCalls()[0][1]?.body))).toEqual({ toCreditor: ALICE, amount: "100.00" });
    expect(await screen.findByRole("status")).toHaveTextContent(/payment/i);
  });

  it("lookingAgainAfterAPaymentFetchesThePlanAgain", async () => {
    // given a payment was just reported and the plan calls so far were counted
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      serve({ userId: CAROL, displayName: "Carol" }, mallorca, async () => ({}));
      renderTab();
      await userEvent.click(await screen.findByRole("button", { name: "I paid" }));
      await userEvent.click(screen.getByRole("button", { name: "Send payment" }));
      await screen.findByRole("status");
      const planCalls = () => apiMock.mock.calls.filter(([path]) => path === "/groups/g1/settlement-plan").length;
      const afterReload = planCalls();

      // when about half a second passes
      await act(() => vi.advanceTimersByTimeAsync(600));

      // then the plan was asked for again without any user action
      expect(planCalls()).toBeGreaterThan(afterReload);
    } finally {
      vi.useRealTimers();
    }
  });

  it("twoSubmitsInTheSameTickSendOnlyOnePayment", async () => {
    // given
    let finish: (value: unknown) => void = () => {};
    serve({ userId: CAROL, displayName: "Carol" }, mallorca, () => new Promise((resolve) => (finish = resolve)));
    renderTab();
    await userEvent.click(await screen.findByRole("button", { name: "I paid" }));
    const form = screen.getByRole("button", { name: "Send payment" }).closest("form")!;

    // when the form is submitted twice before React re-rendered
    act(() => {
      fireEvent.submit(form);
      fireEvent.submit(form);
    });

    // then one request left
    expect(paymentCalls()).toHaveLength(1);
    finish({});
  });
});
