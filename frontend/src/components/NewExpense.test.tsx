import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { NewExpense } from "@/components/NewExpense";
import { CurrentUserProvider } from "@/lib/currentUser";
import { renderWithQueryClient } from "@/lib/renderWithQueryClient";
import { lastChangedKey } from "@/lib/useGroupChange";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

const group = {
  id: "g1",
  name: "Mallorca 2027",
  status: "OPEN",
  memberCount: 2,
  members: [
    { userId: "u1", displayName: "Alice", role: "ADMIN" },
    { userId: "u2", displayName: "Bob", role: "MEMBER" },
  ],
};

/** Answers the calls of the page. `me` may be a promise so a test decides when "who am I" arrives. */
function serve(me: Promise<unknown> | unknown) {
  apiMock.mockImplementation(async (path: string) => {
    if (path === "/me") return me;
    if (path === "/groups/g1") return group;
    if (path === "/groups/g1/expenses") return {};
    throw new Error(`unexpected call ${path}`);
  });
}

function renderPage() {
  return renderWithQueryClient(
    <CurrentUserProvider>
      <NewExpense groupId="g1" />
    </CurrentUserProvider>,
  );
}

describe("NewExpense", () => {
  it("showsTheFormOnlyOnceItKnowsWhoIsAskingSoThePayerDefaultIsRight", async () => {
    // given the group arrives before "who am I"
    let answerMe: (user: unknown) => void = () => {};
    serve(new Promise((resolve) => (answerMe = resolve)));

    // when
    renderPage();
    await vi.waitFor(() => expect(apiMock).toHaveBeenCalledWith("/groups/g1"));

    // then there is no form yet, so the payer cannot silently default to the first member
    expect(screen.queryByLabelText(/paid by/i)).not.toBeInTheDocument();
    answerMe({ userId: "u2", displayName: "Bob" });
    expect(await screen.findByLabelText(/paid by/i)).toHaveValue("u2");
  });

  it("afterSavingGoesBackToTheGroupAndMarksItChangedSoItShowsUpdating", async () => {
    // given
    serve({ userId: "u2", displayName: "Bob" });
    const { client } = renderPage();
    await userEvent.type(await screen.findByLabelText("Amount (EUR)"), "180");
    await userEvent.type(screen.getByLabelText(/what was it for/i), "Dinner");

    // when
    await userEvent.click(screen.getByRole("button", { name: "Save expense" }));

    // then
    await vi.waitFor(() => expect(push).toHaveBeenCalledWith("/groups/g1"));
    expect(client.getQueryData(lastChangedKey("g1"))).toEqual(expect.any(Number));
  });

  it("cancelLeadsBackToTheGroupWithoutSaving", async () => {
    // given
    serve({ userId: "u2", displayName: "Bob" });

    // when
    renderPage();

    // then
    expect(await screen.findByRole("link", { name: "Cancel" })).toHaveAttribute("href", "/groups/g1");
    expect(apiMock).not.toHaveBeenCalledWith("/groups/g1/expenses", expect.anything());
  });
});
