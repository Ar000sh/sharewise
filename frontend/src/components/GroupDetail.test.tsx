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
function serve(me: Promise<unknown> | unknown) {
  apiMock.mockImplementation(async (path: string) => {
    if (path === "/me") return me;
    if (path === "/groups/g1") return group;
    if (path === "/groups/g1/expenses") return [];
    if (path === "/groups/g1/balances") return [];
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
