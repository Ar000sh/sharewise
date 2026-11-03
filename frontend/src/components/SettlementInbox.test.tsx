import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { components } from "@/api/schema";
import { ApiError, api } from "@/lib/api";
import { PendingBadge } from "@/components/PendingBadge";
import { SettlementInbox } from "@/components/SettlementInbox";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

type Payment = components["schemas"]["SettlementPayment"];

const INBOX = "/me/settlement-payments?status=PENDING&role=creditor";

function payment(id: string, fromDebtorName: string, amount: string, extra: Partial<Payment> = {}): Payment {
  return {
    id,
    groupId: "g1",
    fromDebtor: `u-${fromDebtorName}`,
    fromDebtorName,
    toCreditor: "alice",
    toCreditorName: "Alice",
    amount,
    status: "PENDING",
    createdAt: "2027-01-01T10:00:00Z",
    ...extra,
  } as Payment;
}

/** Answers the inbox with `lists` one after the other (the last one repeats); `other` answers the POSTs. */
function serve(lists: Payment[][], other?: (path: string) => Promise<unknown>) {
  let loads = 0;
  apiMock.mockImplementation(async (path: string) => {
    if (path === INBOX) return lists[Math.min(loads++, lists.length - 1)];
    if (other) return other(path);
    throw new Error(`unexpected call ${path}`);
  });
}

function answerCalls() {
  return apiMock.mock.calls.filter(([path]) => path !== INBOX);
}

function inboxCalls() {
  return apiMock.mock.calls.filter(([path]) => path === INBOX).length;
}

describe("SettlementInbox", () => {
  it("listsPendingSettlementPaymentsAddressedToMe", async () => {
    // given Bob marked 30,00 and Carol 12,50 as paid to me, Bob left a note
    serve([[payment("p1", "Bob", "30.00", { note: "Cash at lunch" }), payment("p2", "Carol", "12.50")]]);

    // when
    render(<SettlementInbox />);

    // then each row says who paid how much, without a gendered pronoun, and the note is shown
    expect(await screen.findByText("Bob marked 30,00 € as paid to you")).toBeInTheDocument();
    expect(screen.getByText("Carol marked 12,50 € as paid to you")).toBeInTheDocument();
    expect(screen.getByText("Cash at lunch")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Confirm payment from Bob" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reject payment from Carol" })).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /open group/i })[0]).toHaveAttribute("href", "/groups/g1");
    expect(apiMock).toHaveBeenCalledWith(INBOX);
  });

  it("onlyThePaymentsOfTheGivenGroupAreListed", async () => {
    // given payments from two groups
    serve([[payment("p1", "Bob", "30.00"), payment("p2", "Carol", "12.50", { groupId: "g2" })]]);

    // when the inbox of group g1 is shown
    render(<SettlementInbox groupId="g1" />);

    // then only the payment of g1 is there, and no link to a group (we are in it)
    expect(await screen.findByText("Bob marked 30,00 € as paid to you")).toBeInTheDocument();
    expect(screen.queryByText("Carol marked 12,50 € as paid to you")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /open group/i })).not.toBeInTheDocument();
  });

  it("saysNothingIsWaitingWhenTheListIsEmpty", async () => {
    serve([[]]);

    render(<SettlementInbox groupId="g1" />);

    expect(await screen.findByText("Nothing is waiting for your answer.")).toBeInTheDocument();
  });

  it("confirmCallsTheRouteAndRemovesTheRow", async () => {
    // given
    serve([[payment("p1", "Bob", "30.00")]], async () => ({}));
    const onAnswered = vi.fn();
    render(<SettlementInbox onAnswered={onAnswered} />);

    // when
    await userEvent.click(await screen.findByRole("button", { name: "Confirm payment from Bob" }));

    // then the confirm route was called once, the row is gone, the caller knows and the user is told
    expect(await screen.findByText("Nothing is waiting for your answer.")).toBeInTheDocument();
    expect(answerCalls()).toHaveLength(1);
    const [path, init] = answerCalls()[0];
    expect(path).toBe("/settlement-payments/p1/confirm");
    expect(init?.method).toBe("POST");
    expect(screen.queryByText("Bob marked 30,00 € as paid to you")).not.toBeInTheDocument();
    expect(onAnswered).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("status")).toHaveTextContent(/confirmed/i);
  });

  it("rejectNeedsANoteBeforeSending", async () => {
    // given
    serve([[payment("p1", "Bob", "30.00")]], async () => ({}));
    render(<SettlementInbox />);

    // when Reject is opened
    await userEvent.click(await screen.findByRole("button", { name: "Reject payment from Bob" }));

    // then the send button waits for a reason and nothing was sent
    const send = screen.getByRole("button", { name: "Send rejection" });
    expect(send).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Reason"), "   ");
    expect(send).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Reason"), "Not received");
    expect(send).toBeEnabled();
    expect(answerCalls()).toHaveLength(0);
  });

  it("rejectSendsTheNoteAndRemovesTheRow", async () => {
    // given
    serve([[payment("p1", "Bob", "30.00")]], async () => ({}));
    const onAnswered = vi.fn();
    render(<SettlementInbox onAnswered={onAnswered} />);
    await userEvent.click(await screen.findByRole("button", { name: "Reject payment from Bob" }));
    await userEvent.type(screen.getByLabelText("Reason"), "  Not received  ");

    // when
    await userEvent.click(screen.getByRole("button", { name: "Send rejection" }));

    // then the trimmed note went out, the row is gone and the caller knows
    expect(await screen.findByText("Nothing is waiting for your answer.")).toBeInTheDocument();
    const [path, init] = answerCalls()[0];
    expect(path).toBe("/settlement-payments/p1/reject");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({ note: "Not received" });
    expect(onAnswered).toHaveBeenCalledTimes(1);
  });

  it("alreadyAnsweredShowsTheProblemMessage", async () => {
    // given the payment was answered elsewhere; the second load no longer lists it
    serve([[payment("p1", "Bob", "30.00")], []], async () => {
      throw new ApiError(409, "PAYMENT_NOT_PENDING", "already answered");
    });
    const onAnswered = vi.fn();
    render(<SettlementInbox onAnswered={onAnswered} />);

    // when
    await userEvent.click(await screen.findByRole("button", { name: "Confirm payment from Bob" }));

    // then the message is shown, the list was loaded again and the row is gone
    expect(await screen.findByRole("alert")).toHaveTextContent("This settlement payment was already answered.");
    expect(await screen.findByText("Nothing is waiting for your answer.")).toBeInTheDocument();
    expect(inboxCalls()).toBe(2);
    expect(onAnswered).not.toHaveBeenCalled();
  });

  it("showsAnotherFailureAndKeepsTheRowForANewTry", async () => {
    serve([[payment("p1", "Bob", "30.00")]], async () => {
      throw new ApiError(403, "NOT_THE_CREDITOR", "no");
    });
    render(<SettlementInbox />);

    await userEvent.click(await screen.findByRole("button", { name: "Confirm payment from Bob" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Only the person who is owed can do this.");
    expect(screen.getByRole("button", { name: "Confirm payment from Bob" })).toBeEnabled();
    expect(inboxCalls()).toBe(1);
  });

  it("twoClicksOnConfirmSendOnlyOneRequest", async () => {
    // given the confirm stays open
    let finish: (value: unknown) => void = () => {};
    serve([[payment("p1", "Bob", "30.00")]], () => new Promise((resolve) => (finish = resolve)));
    render(<SettlementInbox />);
    const confirm = await screen.findByRole("button", { name: "Confirm payment from Bob" });

    // when it is clicked twice in the same tick
    act(() => {
      fireEvent.click(confirm);
      fireEvent.click(confirm);
    });

    // then one request left and the row's buttons are disabled meanwhile
    expect(answerCalls()).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Confirm payment from Bob" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Reject payment from Bob" })).toBeDisabled();
    await act(async () => finish({}));
  });
});

describe("PendingBadge", () => {
  it("badgeShowsTheCountAndHidesAtZero", async () => {
    // given three pending payments, two of them in g1
    serve([[payment("p1", "Bob", "30.00"), payment("p2", "Carol", "12.50"), payment("p3", "Dan", "5.00", { groupId: "g2" })]]);

    // when
    const all = render(<PendingBadge />);

    // then the count of all groups is shown with an accessible name
    expect(await screen.findByLabelText("3 settlement payments waiting for your answer")).toHaveTextContent("3");
    all.unmount();

    // and the badge of one group counts only its own
    serve([[payment("p1", "Bob", "30.00"), payment("p3", "Dan", "5.00", { groupId: "g2" })]]);
    const one = render(<PendingBadge groupId="g1" />);
    expect(await screen.findByLabelText("1 settlement payment waiting for your answer")).toHaveTextContent("1");
    one.unmount();

    // and at zero nothing is rendered
    serve([[]]);
    const none = render(<PendingBadge />);
    await vi.waitFor(() => expect(inboxCalls()).toBe(3));
    expect(none.container).toBeEmptyDOMElement();
  });

  it("badgeRendersNothingWhenTheCountCannotBeLoaded", async () => {
    apiMock.mockRejectedValue(new ApiError(500, "X", "boom"));

    const view = render(<PendingBadge />);

    await vi.waitFor(() => expect(apiMock).toHaveBeenCalled());
    expect(view.container).toBeEmptyDOMElement();
  });

  it("badgeAsksAgainAsSoonAsItsRefreshKeyChanges", async () => {
    // given the badge shows two
    serve([[payment("p1", "Bob", "30.00"), payment("p2", "Carol", "1.00")], [payment("p2", "Carol", "1.00")]]);
    const view = render(<PendingBadge refreshKey={0} />);
    await screen.findByLabelText("2 settlement payments waiting for your answer");
    expect(inboxCalls()).toBe(1);

    // when the key is bumped (an answer was given)
    view.rerender(<PendingBadge refreshKey={1} />);

    // then the count drops without waiting for the poll
    expect(await screen.findByLabelText("1 settlement payment waiting for your answer")).toBeInTheDocument();
    expect(inboxCalls()).toBe(2);
  });

  it("badgePollsAgainEvery30Seconds", async () => {
    // given
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      serve([[payment("p1", "Bob", "30.00")], [payment("p1", "Bob", "30.00"), payment("p2", "Carol", "1.00")]]);
      const view = render(<PendingBadge />);
      await screen.findByLabelText("1 settlement payment waiting for your answer");
      expect(inboxCalls()).toBe(1);

      // when 30 seconds pass
      await act(() => vi.advanceTimersByTimeAsync(30_000));

      // then the count was asked for again and changed
      expect(inboxCalls()).toBe(2);
      expect(screen.getByLabelText("2 settlement payments waiting for your answer")).toBeInTheDocument();

      // and after unmounting nothing is asked any more
      view.unmount();
      await act(() => vi.advanceTimersByTimeAsync(90_000));
      expect(inboxCalls()).toBe(2);
    } finally {
      vi.useRealTimers();
    }
  });
});
