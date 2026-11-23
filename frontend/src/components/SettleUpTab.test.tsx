import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { SettleUpTab } from "@/components/SettleUpTab";
import { CurrentUserProvider } from "@/lib/currentUser";
import { renderWithQueryClient } from "@/lib/renderWithQueryClient";
import { lastChangedKey, markGroupChanged } from "@/lib/useGroupChange";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
afterEach(() => {
  vi.resetAllMocks();
  vi.useRealTimers();
});

const ALICE = "u1";
const BOB = "u2";
const CAROL = "u3";
const DAVID = "u4";

type Edge = { fromDebtor: string; fromDebtorName: string; toCreditor: string; toCreditorName: string; amount: string; pendingAmount: string; payableAmount: string };

function edge(from: string, fromName: string, amount: string, pendingAmount = "0.00", payableAmount = amount): Edge {
  return { fromDebtor: from, fromDebtorName: fromName, toCreditor: ALICE, toCreditorName: "Alice", amount, pendingAmount, payableAmount };
}

/** The Mallorca plan: Alice is owed by all three. */
const mallorca = [edge(CAROL, "Carol", "270.00"), edge(DAVID, "David", "270.00"), edge(BOB, "Bob", "90.00")];

/** David says he paid Alice 270,00; it waits for her answer. */
const davidsPayment = {
  id: "p1",
  groupId: "g1",
  fromDebtor: DAVID,
  fromDebtorName: "David",
  toCreditor: ALICE,
  toCreditorName: "Alice",
  amount: "270.00",
  status: "PENDING",
  note: "Bank transfer",
  createdAt: "2027-07-27T16:40:00Z",
  resolvedAt: null,
};

type Served = { plans: Edge[][] | Edge[]; waiting?: unknown[]; history?: unknown[]; other?: (path: string) => Promise<unknown> };

/** Answers "who am I", the plan (one answer per call, the last one repeating), the inbox, the history and `other`. */
function serve(me: { userId: string; displayName: string }, { plans, waiting = [], history = [], other }: Served) {
  const answers = Array.isArray(plans[0]) ? (plans as Edge[][]) : [plans as Edge[]];
  let planCalls = 0;
  apiMock.mockImplementation(async (path: string, init?: RequestInit) => {
    if (path === "/me") return me;
    if (path === "/groups/g1/settlement-plan") return answers[Math.min(planCalls++, answers.length - 1)];
    if (path === "/inbox") return { invitations: [], notifications: { unreadCount: 0, items: [] }, payments: waiting };
    if (path === "/groups/g1/settlement-payments" && !init) return history;
    if (other) return other(path);
    throw new Error(`unexpected call ${path}`);
  });
}

function renderTab() {
  return renderWithQueryClient(
    <CurrentUserProvider>
      <SettleUpTab groupId="g1" />
    </CurrentUserProvider>,
  );
}

const calls = (path: string) => apiMock.mock.calls.filter(([called, init]) => called === path && init?.method === "POST");
const paymentCalls = () => calls("/groups/g1/settlement-payments");
const planCalls = () => apiMock.mock.calls.filter(([path]) => path === "/groups/g1/settlement-plan").length;

async function openReport() {
  await userEvent.click(await screen.findByRole("button", { name: "Report a payment" }));
  return screen.getByRole("dialog", { name: "Report a payment" });
}

describe("SettleUpTab", () => {
  it("showsWhatIOweWhatICanReceiveAndTheRestOfThePlan", async () => {
    // given Carol owes Alice 270,00 and the others owe Alice too
    serve({ userId: CAROL, displayName: "Carol" }, { plans: mallorca });

    // when Carol looks at the tab
    const carolView = renderTab();

    // then she sees what she owes, that nobody owes her, and the rest of the group's plan below
    const owe = await screen.findByRole("region", { name: "You owe" });
    expect(within(owe).getByText("Pay Alice 270,00 €")).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "You can receive" })).toHaveTextContent("No one owes you in this group.");
    const others = screen.getByRole("region", { name: "Between other members" });
    expect(within(others).getByText("David pays Alice 270,00 €")).toBeInTheDocument();
    expect(screen.getByText(/fewest payments: 3 in total/)).toBeInTheDocument();
    carolView.unmount();

    // and Alice sees the three who pay her, with a waiver but nothing to report
    serve({ userId: ALICE, displayName: "Alice" }, { plans: mallorca });
    renderTab();
    const receive = await screen.findByRole("region", { name: "You can receive" });
    expect(within(receive).getByText("Carol pays you 270,00 €")).toBeInTheDocument();
    expect(within(receive).getByText("Bob pays you 90,00 €")).toBeInTheDocument();
    expect(within(receive).getAllByRole("button", { name: "Waive debt" })).toHaveLength(3);
    expect(screen.queryByRole("button", { name: "Report a payment" })).not.toBeInTheDocument();
  });

  it("reportingAPaymentSendsTheAmountAndDisablesTheButtonWhileSending", async () => {
    // given the POST stays open until the test lets it finish
    let finish: (value: unknown) => void = () => {};
    serve({ userId: CAROL, displayName: "Carol" }, { plans: mallorca, other: () => new Promise((resolve) => (finish = resolve)) });
    renderTab();
    const dialog = await openReport();
    expect(within(dialog).getByLabelText("Amount (EUR)")).toHaveValue("270.00");

    // when she sends it and tries again while it is on its way
    const send = within(dialog).getByRole("button", { name: "Report payment" });
    await userEvent.click(send);
    await userEvent.click(send);

    // then exactly one request left, with the amount as a string with two decimals, and the button is disabled
    expect(send).toBeDisabled();
    expect(paymentCalls()).toHaveLength(1);
    expect(JSON.parse(String(paymentCalls()[0][1]?.body))).toEqual({ toCreditor: ALICE, amount: "270.00" });
    finish({});
  });

  it("showsThePendingAmountOnTheRowAndOffersWhatIsStillPayable", async () => {
    // given Carol already reported 30,00 that Alice has not confirmed yet
    serve({ userId: CAROL, displayName: "Carol" }, { plans: [edge(CAROL, "Carol", "270.00", "30.00", "240.00")] });

    // when
    renderTab();

    // then the row says so, and the dialog starts at the payable amount
    expect(await screen.findByText("30,00 € awaiting confirmation")).toBeInTheDocument();
    const dialog = await openReport();
    expect(within(dialog).getByLabelText("Amount (EUR)")).toHaveValue("240.00");
  });

  it("showsTheProblemMessageWhenTheServerRefusesTheAmountAndStaysOpen", async () => {
    // given the backend refuses the payment
    serve({ userId: CAROL, displayName: "Carol" }, {
      plans: mallorca,
      other: async () => {
        throw new ApiError(422, "PAYMENT_EXCEEDS_OPEN_DEBT", "too much");
      },
    });
    renderTab();
    const dialog = await openReport();

    // when
    await userEvent.click(within(dialog).getByRole("button", { name: "Report payment" }));

    // then the message is shown and the dialog stays open for a correction
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(/only pay up to/i);
    expect(within(dialog).getByRole("button", { name: "Report payment" })).toBeEnabled();
  });

  it("aReportCannotExceedWhatIsOpen", async () => {
    // given
    serve({ userId: CAROL, displayName: "Carol" }, { plans: mallorca });
    renderTab();
    const dialog = await openReport();

    // when she types more than the 270,00 she owes
    const amount = within(dialog).getByLabelText("Amount (EUR)");
    await userEvent.clear(amount);
    await userEvent.type(amount, "270.01");
    await userEvent.click(within(dialog).getByRole("button", { name: "Report payment" }));

    // then nothing is sent and she is told why
    expect(within(dialog).getByRole("alert")).toHaveTextContent(/270,00 €/);
    expect(paymentCalls()).toHaveLength(0);
  });

  it("noReportButtonAndAnExplanationWhenNothingIsPayableAndNothingIsPending", async () => {
    // given the server says nothing can be paid on this pair right now
    serve({ userId: CAROL, displayName: "Carol" }, { plans: [edge(CAROL, "Carol", "270.00", "0.00", "0.00")] });

    // when
    renderTab();

    // then
    expect(await screen.findByText("Not payable right now: payments that wait for an answer use up this amount.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Report a payment" })).not.toBeInTheDocument();
  });

  it("noReportButtonButThePendingAmountWhenTheWholeDebtIsAlreadyReported", async () => {
    // given everything open is already reported
    serve({ userId: CAROL, displayName: "Carol" }, { plans: [edge(CAROL, "Carol", "270.00", "270.00", "0.00")] });

    // when
    renderTab();

    // then
    expect(await screen.findByText("270,00 € awaiting confirmation")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Report a payment" })).not.toBeInTheDocument();
  });

  it("afterAReportTheDialogClosesThePlanShowsThePendingPartAndThePageSaysSo", async () => {
    // given the plan after the payment shows the amount as pending
    serve({ userId: CAROL, displayName: "Carol" }, {
      plans: [mallorca, [edge(CAROL, "Carol", "270.00", "100.00", "170.00"), ...mallorca.slice(1)]],
      other: async () => ({}),
    });
    renderTab();
    const dialog = await openReport();
    const amount = within(dialog).getByLabelText("Amount (EUR)");
    await userEvent.clear(amount);
    await userEvent.type(amount, "100,00");

    // when
    await userEvent.click(within(dialog).getByRole("button", { name: "Report payment" }));

    // then
    expect(await screen.findByText("100,00 € awaiting confirmation")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(JSON.parse(String(paymentCalls()[0][1]?.body))).toEqual({ toCreditor: ALICE, amount: "100.00" });
    expect(await screen.findByRole("status")).toHaveTextContent(/payment reported/i);
  });

  it("aReportDialogWhoseDebtDisappearsSaysSoAndOffersNothingToSend", async () => {
    // given Carol opened the dialog, then Alice waived her whole debt and the plan no longer has the entry (F2)
    serve({ userId: CAROL, displayName: "Carol" }, { plans: [mallorca, mallorca.slice(1)] });
    const { client } = renderTab();
    const dialog = await openReport();

    // when the group page looks again
    act(() => markGroupChanged(client, "g1"));

    // then the dialog says so and there is nothing left to send
    expect(await within(dialog).findByText("Nothing is open with Alice any more.")).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Report payment" })).not.toBeInTheDocument();
    expect(paymentCalls()).toHaveLength(0);
  });

  it("twoSubmitsInTheSameTickSendOnlyOnePayment", async () => {
    // given
    let finish: (value: unknown) => void = () => {};
    serve({ userId: CAROL, displayName: "Carol" }, { plans: mallorca, other: () => new Promise((resolve) => (finish = resolve)) });
    renderTab();
    const dialog = await openReport();
    const form = within(dialog).getByRole("button", { name: "Report payment" }).closest("form")!;

    // when the form is submitted twice before React re-rendered
    act(() => {
      fireEvent.submit(form);
      fireEvent.submit(form);
    });

    // then one request left
    expect(paymentCalls()).toHaveLength(1);
    finish({});
  });

  it("emptyPlanSaysEverybodyIsSettled", async () => {
    serve({ userId: CAROL, displayName: "Carol" }, { plans: [] });

    renderTab();

    expect(await screen.findByText("Everybody is settled.")).toBeInTheDocument();
  });

  it("looksAtThePlanAgainWhenItsGroupIsMarkedChanged", async () => {
    // given Alice is looking at the plan
    serve({ userId: ALICE, displayName: "Alice" }, { plans: mallorca });
    const { client } = renderTab();
    await screen.findByRole("region", { name: "You can receive" });
    const before = planCalls();

    // when something in the group changed (an answer in the bell, an expense)
    act(() => markGroupChanged(client, "g1"));

    // then the plan is asked for at once and again after the ledger had a moment to book it
    await waitFor(() => expect(planCalls()).toBeGreaterThanOrEqual(before + 2), { timeout: 3000 });
  });

  describe("needsYourAnswer", () => {
    it("showsThePaymentsWaitingInThisGroupFirstAndNotThoseOfOtherGroups", async () => {
      // given David's payment waits in this group and another one in another group
      serve({ userId: ALICE, displayName: "Alice" }, { plans: mallorca, waiting: [davidsPayment, { ...davidsPayment, id: "p9", groupId: "g9" }] });

      // when
      renderTab();

      // then it is the first section, with the note
      const section = await screen.findByRole("region", { name: "Needs your answer" });
      expect(within(section).getAllByRole("listitem")).toHaveLength(1);
      expect(section).toHaveTextContent("David says they paid you 270,00 €");
      expect(section).toHaveTextContent("Bank transfer");
      expect(screen.getAllByRole("region")[0]).toBe(section);
    });

    it("confirmingMarksTheGroupChangedAndAsksTheInboxAgainSoEveryPlaceAgrees", async () => {
      // given (F3: the bell reads the same inbox query)
      serve({ userId: ALICE, displayName: "Alice" }, { plans: mallorca, waiting: [davidsPayment], other: async () => ({}) });
      const { client } = renderTab();
      const section = await screen.findByRole("region", { name: "Needs your answer" });
      const inboxCalls = () => apiMock.mock.calls.filter(([path]) => path === "/inbox").length;
      const before = inboxCalls();

      // when
      await userEvent.click(within(section).getByRole("button", { name: "Confirm payment from David" }));

      // then
      expect(calls("/settlement-payments/p1/confirm")).toHaveLength(1);
      await waitFor(() => expect(inboxCalls()).toBeGreaterThan(before));
      expect(client.getQueryData(lastChangedKey("g1"))).toEqual(expect.any(Number));
    });

    it("rejectingAsksForAReasonInADialogAndSendsIt", async () => {
      // given
      serve({ userId: ALICE, displayName: "Alice" }, { plans: mallorca, waiting: [davidsPayment], other: async () => ({}) });
      renderTab();
      await userEvent.click(await screen.findByRole("button", { name: "Reject payment from David" }));
      const dialog = screen.getByRole("dialog", { name: "Reject payment" });
      expect(within(dialog).getByRole("button", { name: "Reject payment" })).toBeDisabled();

      // when
      await userEvent.type(within(dialog).getByLabelText("Reason"), "Not on my account yet");
      await userEvent.click(within(dialog).getByRole("button", { name: "Reject payment" }));

      // then
      const [[, init]] = calls("/settlement-payments/p1/reject");
      expect(JSON.parse(String(init?.body))).toEqual({ note: "Not on my account yet" });
      await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    });
  });

  it("thePaymentHistoryShowsEveryPaymentAndFiltersByStatus", async () => {
    // given one pending and one rejected payment
    const rejected = { ...davidsPayment, id: "p2", fromDebtor: BOB, fromDebtorName: "Bob", amount: "90.00", status: "REJECTED", note: "Not received" };
    serve({ userId: ALICE, displayName: "Alice" }, { plans: mallorca, history: [davidsPayment, rejected] });
    renderTab();
    const history = await screen.findByRole("region", { name: "Payment history" });
    await waitFor(() => expect(within(history).getAllByRole("listitem")).toHaveLength(2));
    expect(within(history).getAllByRole("listitem")[0]).toHaveTextContent("David → you");

    // when
    await userEvent.click(within(history).getByRole("button", { name: "Rejected" }));

    // then
    const rows = within(history).getAllByRole("listitem");
    expect(rows).toHaveLength(1);
    expect(rows[0]).toHaveTextContent("Bob → you");
    expect(rows[0]).toHaveTextContent("Rejected");
  });
});
