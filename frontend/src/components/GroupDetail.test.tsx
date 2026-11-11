import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { GroupDetail } from "@/components/GroupDetail";
import { CurrentUserProvider } from "@/lib/currentUser";
import { renderWithQueryClient } from "@/lib/renderWithQueryClient";
import { POLL_MS } from "@/lib/useApi";

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

function renderPage() {
  renderWithQueryClient(
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
    // given Bob recorded an expense on the Expenses tab, where neither Balances nor Settle up is mounted
    serve({ userId: "u2", displayName: "Bob" });
    renderPage();
    await userEvent.type(await screen.findByLabelText(/title/i), "Dinner");
    await userEvent.type(screen.getByLabelText(/amount/i), "180");
    await userEvent.click(screen.getByRole("button", { name: /add expense/i }));
    await screen.findByRole("status");
    const planCalls = () => apiMock.mock.calls.filter(([path]) => path === "/groups/g1/settlement-plan").length;

    // when he opens Settle up
    await userEvent.click(screen.getByRole("tab", { name: "Settle up" }));
    await vi.waitFor(() => expect(planCalls()).toBeGreaterThanOrEqual(1));
    const first = planCalls();

    // then the plan is asked for again, because the ledger may not have booked the expense yet
    await vi.waitFor(() => expect(planCalls()).toBeGreaterThan(first), { timeout: 3000 });
  });
});
