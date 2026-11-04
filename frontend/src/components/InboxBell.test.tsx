import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { InboxBell } from "@/components/InboxBell";
import { PAYMENT_ANSWERED } from "@/lib/paymentAnswered";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
const apiMock = vi.mocked(api);
const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const invitation = {
  invitationId: "i1",
  group: { id: "g1", name: "Mallorca 2027" },
  invitedBy: { userId: "u1", displayName: "Alice Tester", username: "alice" },
  expiresAt: "2027-06-08T10:00:00Z",
};
const entry = {
  id: "n1",
  type: "MEMBER_JOINED",
  occurredAt: "2027-06-02T09:30:00Z",
  readAt: null,
  actor: { userId: "u2", displayName: "Bob Tester", username: "bob" },
  group: { id: "g1", name: "Mallorca 2027" },
  reference: "u2",
};

const payment = {
  id: "p1",
  groupId: "g1",
  fromDebtor: "u3",
  fromDebtorName: "Carol Tester",
  toCreditor: "u1",
  toCreditorName: "Alice Tester",
  amount: "360.00",
  status: "PENDING",
  note: "Cash at the airport",
  createdAt: "2027-06-02T09:00:00Z",
  resolvedAt: null,
};

type Inbox = {
  invitations: unknown[] | null;
  notifications: { unreadCount: number; items: unknown[] } | null;
  payments: unknown[] | null;
};
let inbox: Inbox;

/** Answers `/inbox` with the current `inbox` and everything else with `others`. */
function backend(others: (path: string) => unknown = () => undefined) {
  apiMock.mockImplementation(async (path: string) => {
    if (path === "/inbox") return structuredClone(inbox);
    return others(path);
  });
}

async function openBell() {
  await userEvent.click(await screen.findByRole("button", { name: /inbox/i }));
}

/** Renders the bell and opens its dropdown, the set-up of most tests. */
async function renderOpened() {
  const view = render(<InboxBell />);
  await openBell();
  return view;
}

beforeEach(() => {
  push.mockReset();
  inbox = { invitations: [invitation], notifications: { unreadCount: 1, items: [entry] }, payments: [] };
});
afterEach(() => {
  vi.resetAllMocks();
  vi.useRealTimers();
});

describe("InboxBell", () => {
  it("badgeIsOpenInvitationsPlusWaitingPaymentsPlusUnreadEntries", async () => {
    // given one invitation, one payment waiting and one unread entry
    inbox = { ...inbox, payments: [payment] };
    backend();

    // when
    render(<InboxBell />);

    // then
    expect(await screen.findByRole("button", { name: /inbox/i })).toHaveTextContent("3");
  });

  it("showsAPaymentWaitingForMyAnswerBeforeTheEntries", async () => {
    // given
    inbox = { ...inbox, invitations: [], payments: [payment] };
    backend();

    // when
    await renderOpened();

    // then the sentence, the note and a link to the group come first, then the entry
    const items = screen.getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("Carol Tester marked 360,00 € as paid to you");
    expect(items[0]).toHaveTextContent("Cash at the airport");
    expect(within(items[0]).getByRole("link", { name: "Open group" })).toHaveAttribute("href", "/groups/g1");
    expect(items[1]).toHaveTextContent("Bob Tester joined Mallorca 2027");
  });

  it("confirmingAPaymentPostsToConfirmAndAsksAgain", async () => {
    // given
    inbox = { ...inbox, payments: [payment] };
    backend((path) => {
      if (path === "/settlement-payments/p1/confirm") inbox = { ...inbox, payments: [] };
    });
    await renderOpened();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Confirm payment from Carol Tester" }));

    // then
    expect(apiMock).toHaveBeenCalledWith("/settlement-payments/p1/confirm", { method: "POST" });
    await waitFor(() => expect(screen.queryByText(/marked 360,00/)).not.toBeInTheDocument());
  });

  it("rejectingAPaymentNeedsAReasonAndSendsItAsTheNote", async () => {
    // given
    inbox = { ...inbox, payments: [payment] };
    backend((path) => {
      if (path === "/settlement-payments/p1/reject") inbox = { ...inbox, payments: [] };
    });
    await renderOpened();
    await userEvent.click(screen.getByRole("button", { name: "Reject payment from Carol Tester" }));

    // when she has not typed a reason, nothing can be sent
    const send = screen.getByRole("button", { name: "Send rejection" });
    expect(send).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Reason"), "Not received");
    await userEvent.click(send);

    // then the reason goes out as the note
    const [, init] = apiMock.mock.calls.find(([path]) => path === "/settlement-payments/p1/reject")!;
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({ note: "Not received" });
    await waitFor(() => expect(screen.queryByText(/marked 360,00/)).not.toBeInTheDocument());
  });

  it("noRejectionIsSentWhileTheReasonIsEmpty", async () => {
    // given
    inbox = { ...inbox, payments: [payment] };
    backend();
    await renderOpened();
    await userEvent.click(screen.getByRole("button", { name: "Reject payment from Carol Tester" }));

    // when the empty form is submitted
    await userEvent.type(screen.getByLabelText("Reason"), "   {Enter}");

    // then
    expect(apiMock.mock.calls.filter(([path]) => String(path).includes("/reject"))).toHaveLength(0);
  });

  it("a404AfterConfirmCountsAsDoneAndShowsNoError", async () => {
    // given the payment was answered in another tab
    inbox = { ...inbox, payments: [payment] };
    backend((path) => {
      if (path === "/settlement-payments/p1/confirm") {
        inbox = { ...inbox, payments: [] };
        throw new ApiError(404, "PAYMENT_NOT_FOUND");
      }
    });
    await renderOpened();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Confirm payment from Carol Tester" }));

    // then
    await waitFor(() => expect(screen.queryByText(/marked 360,00/)).not.toBeInTheDocument());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("aPaymentThatIsNoLongerPendingCountsAsDoneAndShowsNoError", async () => {
    // given a second tab answered the payment first
    inbox = { ...inbox, payments: [payment] };
    backend((path) => {
      if (path === "/settlement-payments/p1/confirm") {
        inbox = { ...inbox, payments: [] };
        throw new ApiError(409, "PAYMENT_NOT_PENDING");
      }
    });
    await renderOpened();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Confirm payment from Carol Tester" }));

    // then
    await waitFor(() => expect(screen.queryByText(/marked 360,00/)).not.toBeInTheDocument());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("announcesAnAnsweredPaymentSoAnOpenSettleUpTabLooksAgain", async () => {
    // given
    inbox = { ...inbox, payments: [payment] };
    backend();
    const announced = vi.fn();
    window.addEventListener(PAYMENT_ANSWERED, announced);
    await renderOpened();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Confirm payment from Carol Tester" }));

    // then
    await waitFor(() => expect(announced).toHaveBeenCalledTimes(1));
    window.removeEventListener(PAYMENT_ANSWERED, announced);
  });

  it("failedAnswerAnnouncesNothing", async () => {
    // given the server refuses the confirm
    inbox = { ...inbox, payments: [payment] };
    backend((path) => {
      if (path === "/settlement-payments/p1/confirm") throw new ApiError(403, "NOT_THE_CREDITOR");
    });
    const announced = vi.fn();
    window.addEventListener(PAYMENT_ANSWERED, announced);
    await renderOpened();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Confirm payment from Carol Tester" }));

    // then
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(announced).not.toHaveBeenCalled();
    window.removeEventListener(PAYMENT_ANSWERED, announced);
  });

  it("openingTheGroupFromAPaymentClosesTheDropdown", async () => {
    // given
    inbox = { ...inbox, payments: [payment] };
    backend();
    await renderOpened();

    // when
    await userEvent.click(screen.getByRole("link", { name: "Open group" }));

    // then
    expect(screen.queryByRole("region", { name: "Inbox entries" })).not.toBeInTheDocument();
  });

  it("describesTheThreeSettlementEntries", async () => {
    // given
    const item = (id: string, type: string) => ({ ...entry, id, type });
    inbox = {
      invitations: [],
      payments: [],
      notifications: {
        unreadCount: 3,
        items: [item("n1", "PAYMENT_CONFIRMED"), item("n2", "PAYMENT_REJECTED"), item("n3", "DEBT_WAIVED")],
      },
    };
    backend();

    // when
    await renderOpened();

    // then
    expect(screen.getByText("Bob Tester confirmed your payment in Mallorca 2027")).toBeInTheDocument();
    expect(screen.getByText("Bob Tester rejected your payment in Mallorca 2027")).toBeInTheDocument();
    expect(screen.getByText("Bob Tester waived part of your debt in Mallorca 2027")).toBeInTheDocument();
  });

  it("showsWhatLoadedWhenThePaymentsCouldNotBeLoaded", async () => {
    inbox = { ...inbox, payments: null };
    backend();

    await renderOpened();

    expect(screen.getByText("Payments could not be loaded.")).toBeInTheDocument();
    expect(screen.getByText("Alice Tester invited you to Mallorca 2027")).toBeInTheDocument();
  });

  it("listsInvitationsBeforeEntriesWithAcceptAndDecline", async () => {
    backend();

    await renderOpened();

    const items = screen.getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("Alice Tester invited you to Mallorca 2027");
    expect(within(items[0]).getByRole("button", { name: /accept/i })).toBeInTheDocument();
    expect(within(items[0]).getByRole("button", { name: /decline/i })).toBeInTheDocument();
    expect(items[1]).toHaveTextContent("Bob Tester joined Mallorca 2027");
  });

  it("describesADeclinedInvitationAndFallsBackWhenNamesAreMissing", async () => {
    inbox = {
      invitations: [],
      notifications: {
        unreadCount: 1,
        items: [{ ...entry, id: "n2", type: "INVITATION_DECLINED", actor: null, group: null }],
      },
      payments: [],
    };
    backend();

    await renderOpened();

    expect(screen.getByText("Someone declined your invitation to a group")).toBeInTheDocument();
  });

  it("acceptingPostsThenGoesToTheGroupAndDisablesTheButtonWhileRunning", async () => {
    let finish: (value: unknown) => void = () => {};
    backend((path) => (path === "/invitations/i1/accept" ? new Promise((resolve) => (finish = resolve)) : undefined));

    await renderOpened();
    await userEvent.click(screen.getByRole("button", { name: /accept/i }));

    expect(screen.getByRole("button", { name: /accept/i })).toBeDisabled();
    finish({ id: "g1", name: "Mallorca 2027", status: "OPEN", memberCount: 2 });
    await waitFor(() => expect(push).toHaveBeenCalledWith("/groups/g1"));
    expect(apiMock).toHaveBeenCalledWith("/invitations/i1/accept", { method: "POST" });
  });

  it("a404AfterAcceptCountsAsDoneAndShowsNoError", async () => {
    backend((path) => {
      if (path === "/invitations/i1/accept") {
        inbox = { ...inbox, invitations: [] };
        throw new ApiError(404, "INVITATION_NOT_FOUND");
      }
    });

    await renderOpened();
    await userEvent.click(screen.getByRole("button", { name: /accept/i }));

    await waitFor(() => expect(screen.queryByText(/invited you to/)).not.toBeInTheDocument());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });

  it("showsTheMessageWhenAcceptingFailsForAnotherReason", async () => {
    backend((path) => {
      if (path === "/invitations/i1/accept") throw new ApiError(410, "INVITATION_EXPIRED");
    });

    await renderOpened();
    await userEvent.click(screen.getByRole("button", { name: /accept/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/has expired/i);
    expect(push).not.toHaveBeenCalled();
  });

  it("decliningRemovesTheInvitation", async () => {
    backend((path) => {
      if (path === "/invitations/i1/decline") inbox = { ...inbox, invitations: [] };
    });

    await renderOpened();
    await userEvent.click(screen.getByRole("button", { name: /decline/i }));

    await waitFor(() => expect(screen.queryByText(/invited you to/)).not.toBeInTheDocument());
    expect(apiMock).toHaveBeenCalledWith("/invitations/i1/decline", { method: "POST" });
  });

  it("clickingAnUnreadEntryMarksItRead", async () => {
    backend();

    await renderOpened();
    await userEvent.click(screen.getByRole("button", { name: /bob tester joined/i }));

    expect(apiMock).toHaveBeenCalledWith("/notifications/n1/read", { method: "POST" });
  });

  it("markAllAsReadPostsToReadAllAndReloads", async () => {
    backend((path) => {
      if (path === "/notifications/read-all") {
        inbox = { ...inbox, notifications: { unreadCount: 0, items: [{ ...entry, readAt: "2027-06-02T10:00:00Z" }] } };
      }
    });

    await renderOpened();
    await userEvent.click(screen.getByRole("button", { name: /mark all as read/i }));

    expect(apiMock).toHaveBeenCalledWith("/notifications/read-all", { method: "POST" });
    await waitFor(() => expect(screen.queryByRole("button", { name: /mark all as read/i })).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: /inbox/i })).toHaveTextContent("1");
  });

  it("showsWhatLoadedWhenTheInvitationsCouldNotBeLoaded", async () => {
    inbox = { invitations: null, notifications: { unreadCount: 1, items: [entry] }, payments: [] };
    backend();

    await renderOpened();

    expect(screen.getByText(/invitations could not be loaded/i)).toBeInTheDocument();
    expect(screen.getByText("Bob Tester joined Mallorca 2027")).toBeInTheDocument();
  });

  it("showsWhatLoadedWhenTheEntriesCouldNotBeLoaded", async () => {
    inbox = { invitations: [invitation], notifications: null, payments: [] };
    backend();

    await renderOpened();

    expect(screen.getByText(/entries could not be loaded/i)).toBeInTheDocument();
    expect(screen.getByText("Alice Tester invited you to Mallorca 2027")).toBeInTheDocument();
  });

  it("rendersNamesAsTextNotHtml", async () => {
    const evil = "<img src=x onerror=alert(1)>";
    inbox = {
      invitations: [{ ...invitation, group: { id: "g9", name: evil } }],
      notifications: { unreadCount: 1, items: [{ ...entry, actor: { ...entry.actor, displayName: evil } }] },
      payments: [],
    };
    backend();

    const { container } = await renderOpened();

    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByText(`Alice Tester invited you to ${evil}`)).toBeInTheDocument();
    expect(screen.getByText(`${evil} joined Mallorca 2027`)).toBeInTheDocument();
  });

  it("stopsPollingAfter401", async () => {
    vi.useFakeTimers();
    apiMock.mockRejectedValue(new ApiError(401, "UNAUTHENTICATED"));

    render(<InboxBell />);
    await act(async () => {});
    await vi.advanceTimersByTimeAsync(90_000);

    expect(apiMock).toHaveBeenCalledTimes(1);
  });

  it("pollsEveryThirtySecondsAndWhenTheTabBecomesVisible", async () => {
    vi.useFakeTimers();
    backend();

    render(<InboxBell />);
    await act(async () => {});
    expect(apiMock).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(30_000);
    expect(apiMock).toHaveBeenCalledTimes(2);

    await act(async () => {
      document.dispatchEvent(new Event("visibilitychange"));
    });
    expect(apiMock).toHaveBeenCalledTimes(3);
  });
});
