import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { MainNav } from "@/components/MainNav";
import { CurrentUserProvider } from "@/lib/currentUser";
import { renderWithQueryClient } from "@/lib/renderWithQueryClient";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
const pathname = vi.fn(() => "/");
vi.mock("next/navigation", () => ({ usePathname: () => pathname() }));

const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

/** Signed in as Alice, with one invitation, one payment waiting and one unread entry. */
function serve(signedIn = true) {
  apiMock.mockImplementation(async (path: string) => {
    if (path === "/me") {
      if (!signedIn) throw new Error("signed out");
      return { userId: "u1", displayName: "Alice", username: "alice" };
    }
    if (path === "/inbox") {
      return { invitations: [{}], payments: [{ groupId: "g1" }], notifications: { unreadCount: 1, items: [] } };
    }
    throw new Error(`unexpected call ${path}`);
  });
}

function renderNav() {
  renderWithQueryClient(
    <CurrentUserProvider>
      <MainNav />
    </CurrentUserProvider>,
  );
}

describe("MainNav", () => {
  it("theInboxLinkCountsOpenInvitationsWaitingPaymentsAndUnreadEntries", async () => {
    // given
    serve();

    // when
    renderNav();

    // then
    const inbox = await screen.findByRole("link", { name: "Inbox, 3 new" });
    expect(inbox).toHaveAttribute("href", "/inbox");
    expect(inbox).toHaveTextContent("3");
  });

  it("aSignedOutVisitorGetsNoNavigationAndTheInboxIsNotAskedFor", async () => {
    // given (the inbox would answer 401 and send the visitor to the sign-in page, where this would start again)
    serve(false);

    // when
    renderNav();

    // then
    await vi.waitFor(() => expect(apiMock).toHaveBeenCalledWith("/me"));
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
    expect(apiMock).not.toHaveBeenCalledWith("/inbox");
  });

  it("marksThePageTheVisitorIsOn", async () => {
    // given
    serve();
    pathname.mockReturnValue("/inbox");

    // when
    renderNav();

    // then
    expect(await screen.findByRole("link", { name: /^Inbox/ })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "My groups" })).not.toHaveAttribute("aria-current");
  });
});
