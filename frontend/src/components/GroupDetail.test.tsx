import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { GroupDetail } from "@/components/GroupDetail";
import { CurrentUserProvider } from "@/lib/currentUser";
import { renderWithQueryClient, testQueryClient } from "@/lib/renderWithQueryClient";
import { POLL_MS } from "@/lib/useApi";
import { markGroupChanged } from "@/lib/useGroupChange";
import { LOOK_AGAIN_AFTER_MS } from "@/lib/useLookAgain";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
afterEach(() => {
  vi.resetAllMocks();
  vi.useRealTimers();
});

function callsTo(path: string) {
  return apiMock.mock.calls.filter(([called]) => called === path).length;
}

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
function serve(me: Promise<unknown> | unknown) {
  apiMock.mockImplementation(async (path: string) => {
    if (path === "/me") return me;
    if (path === "/groups/g1") return group;
    if (path === "/groups/g1/expenses") return [];
    if (path === "/groups/g1/balances") return [];
    if (path === "/groups/g1/settlement-plan") return [];
    throw new Error(`unexpected call ${path}`);
  });
}

function renderPage(client = testQueryClient()) {
  renderWithQueryClient(
    <CurrentUserProvider>
      <GroupDetail groupId="g1" />
    </CurrentUserProvider>,
    client,
  );
}

/** A client in which the group was just changed on this device, as the Add expense page leaves it after saving. */
function clientAfterAChange() {
  const client = testQueryClient();
  markGroupChanged(client, "g1");
  return client;
}

describe("GroupDetail", () => {
  it("leadsToTheAddExpensePageInsteadOfShowingTheFormOnTheList", async () => {
    // given
    serve({ userId: "u2", displayName: "Bob" });

    // when
    renderPage();

    // then
    expect(await screen.findByRole("heading", { name: "Mallorca 2027" })).toBeInTheDocument();
    for (const link of screen.getAllByRole("link", { name: "Add expense" })) {
      expect(link).toHaveAttribute("href", "/groups/g1/expenses/new");
    }
    expect(screen.queryByLabelText(/paid by/i)).not.toBeInTheDocument();
  });

  it("showsTheViewersOwnBalanceInWords", async () => {
    // given Bob owes 180 €
    serve({ userId: "u2", displayName: "Bob" });
    const answer = apiMock.getMockImplementation()!;
    apiMock.mockImplementation(async (path: string, init?: RequestInit) =>
      path === "/groups/g1/balances"
        ? [
            { userId: "u1", displayName: "Alice", netBalance: "180.00" },
            { userId: "u2", displayName: "Bob", netBalance: "-180.00" },
          ]
        : answer(path, init),
    );

    // when
    renderPage();

    // then the words and the sign say the direction, not only the colour
    expect(await screen.findByText(/you owe/i)).toHaveTextContent("You owe -180,00 €");
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

  it("showsTheInviteFormAndMakeAdminButtonsOnlyToAnAdmin", async () => {
    // given Alice is an admin
    serve({ userId: "u1", displayName: "Alice" });
    renderPage();
    await userEvent.click(await screen.findByRole("tab", { name: "Members" }));

    // then
    expect(await screen.findByLabelText("Username or email")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Make Bob an admin" })).toBeInTheDocument();
  });

  it("hidesTheInviteFormAndMakeAdminButtonsFromAMemberWhoIsNotAdmin", async () => {
    // given Bob is a plain member
    serve({ userId: "u2", displayName: "Bob" });
    renderPage();
    await userEvent.click(await screen.findByRole("tab", { name: "Members" }));

    // then the members are listed, but nothing to invite or promote with
    expect(await screen.findByText("Carol")).toBeInTheDocument();
    expect(screen.queryByLabelText("Username or email")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /make .* admin/i })).not.toBeInTheDocument();
  });

  it("saysTheBalancesAreUpdatingRightAfterAnExpenseWasSavedAndUpToDateLater", async () => {
    // given Bob just saved an expense on the Add expense page and came back
    vi.useFakeTimers({ shouldAdvanceTime: true });
    serve({ userId: "u2", displayName: "Bob" });

    // when
    renderPage(clientAfterAChange());

    // then the page says so until the last look again (design 2026-11-23, F4)
    expect(await screen.findByText("Updating…")).toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(LOOK_AGAIN_AFTER_MS.at(-1)!));
    expect(await screen.findByText("Balances up to date")).toBeInTheDocument();
  });

  it("theOpenGroupPageAsksAgainEveryTenSecondsSoOtherPeoplesChangesShowUp", async () => {
    // given Bob has the group open on the Expenses tab
    vi.useFakeTimers({ shouldAdvanceTime: true });
    serve({ userId: "u2", displayName: "Bob" });
    renderPage();
    await vi.waitFor(() => expect(callsTo("/groups/g1/expenses")).toBe(1));

    // when ten seconds pass
    await act(() => vi.advanceTimersByTimeAsync(POLL_MS));

    // then the group and its expenses were asked for again, without any action of his
    expect(callsTo("/groups/g1")).toBe(2);
    expect(callsTo("/groups/g1/expenses")).toBe(2);
  });

  it.each([
    ["Balances", "/groups/g1/balances"],
    ["Settle up", "/groups/g1/settlement-plan"],
  ])("theOpen%sTabAsksAgainEveryTenSeconds", async (tab, path) => {
    // given Bob is looking at the tab
    vi.useFakeTimers({ shouldAdvanceTime: true });
    serve({ userId: "u2", displayName: "Bob" });
    renderPage();
    await userEvent.click(await screen.findByRole("tab", { name: tab }));
    await vi.waitFor(() => expect(callsTo(path)).toBeGreaterThanOrEqual(1));
    const first = callsTo(path);

    // when ten seconds pass
    await act(() => vi.advanceTimersByTimeAsync(POLL_MS));

    // then the tab's data was asked for again
    expect(callsTo(path)).toBeGreaterThan(first);
  });

  it("aFailedPollKeepsThePageAndShowsTheBanner", async () => {
    // given Bob is on the Balances tab and the next poll of the group cannot reach the server
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let offline = false;
    apiMock.mockImplementation(async (path: string) => {
      if (path === "/me") return { userId: "u2", displayName: "Bob" };
      if (path === "/groups/g1" && offline) throw new ApiError(0, "NETWORK_ERROR", "Could not reach the server");
      if (path === "/groups/g1") return group;
      if (path === "/groups/g1/expenses" || path === "/groups/g1/balances") return [];
      throw new Error(`unexpected call ${path}`);
    });
    renderPage();
    await userEvent.click(await screen.findByRole("tab", { name: "Balances" }));
    offline = true;

    // when the poll fails
    await act(() => vi.advanceTimersByTimeAsync(POLL_MS));

    // then the page and the selected tab stay, with the message above them
    expect(await screen.findByRole("alert")).toHaveTextContent(/could not reach/i);
    expect(screen.getByRole("heading", { name: "Mallorca 2027" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Balances" })).toHaveAttribute("aria-selected", "true");
  });

  it("afterAnExpenseTheSettleUpTabLooksAgainWhenOpened", async () => {
    // given Bob just saved an expense and is back on the Expenses tab, where Settle up is not mounted
    serve({ userId: "u2", displayName: "Bob" });
    renderPage(clientAfterAChange());
    await screen.findByRole("heading", { name: "Mallorca 2027" });
    const planCalls = () => apiMock.mock.calls.filter(([path]) => path === "/groups/g1/settlement-plan").length;

    // when he opens Settle up
    await userEvent.click(screen.getByRole("tab", { name: "Settle up" }));
    await vi.waitFor(() => expect(planCalls()).toBeGreaterThanOrEqual(1));
    const first = planCalls();

    // then the plan is asked for again, because the ledger may not have booked the expense yet
    await vi.waitFor(() => expect(planCalls()).toBeGreaterThan(first), { timeout: 3000 });
  });
});
