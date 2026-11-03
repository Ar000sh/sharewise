import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { InboxBell } from "@/components/InboxBell";

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

type Inbox = { invitations: unknown[] | null; notifications: { unreadCount: number; items: unknown[] } | null };
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

beforeEach(() => {
  push.mockReset();
  inbox = { invitations: [invitation], notifications: { unreadCount: 1, items: [entry] } };
});
afterEach(() => {
  vi.resetAllMocks();
  vi.useRealTimers();
});

describe("InboxBell", () => {
  it("badgeIsPendingInvitationsPlusUnreadEntries", async () => {
    backend();

    render(<InboxBell />);

    expect(await screen.findByRole("button", { name: /inbox/i })).toHaveTextContent("2");
  });

  it("listsInvitationsBeforeEntriesWithAcceptAndDecline", async () => {
    backend();

    render(<InboxBell />);
    await openBell();

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
    };
    backend();

    render(<InboxBell />);
    await openBell();

    expect(screen.getByText("Someone declined your invitation to a group")).toBeInTheDocument();
  });

  it("acceptingPostsThenGoesToTheGroupAndDisablesTheButtonWhileRunning", async () => {
    let finish: (value: unknown) => void = () => {};
    backend((path) => (path === "/invitations/i1/accept" ? new Promise((resolve) => (finish = resolve)) : undefined));

    render(<InboxBell />);
    await openBell();
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

    render(<InboxBell />);
    await openBell();
    await userEvent.click(screen.getByRole("button", { name: /accept/i }));

    await waitFor(() => expect(screen.queryByText(/invited you to/)).not.toBeInTheDocument());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });

  it("showsTheMessageWhenAcceptingFailsForAnotherReason", async () => {
    backend((path) => {
      if (path === "/invitations/i1/accept") throw new ApiError(410, "INVITATION_EXPIRED");
    });

    render(<InboxBell />);
    await openBell();
    await userEvent.click(screen.getByRole("button", { name: /accept/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/has expired/i);
    expect(push).not.toHaveBeenCalled();
  });

  it("decliningRemovesTheInvitation", async () => {
    backend((path) => {
      if (path === "/invitations/i1/decline") inbox = { ...inbox, invitations: [] };
    });

    render(<InboxBell />);
    await openBell();
    await userEvent.click(screen.getByRole("button", { name: /decline/i }));

    await waitFor(() => expect(screen.queryByText(/invited you to/)).not.toBeInTheDocument());
    expect(apiMock).toHaveBeenCalledWith("/invitations/i1/decline", { method: "POST" });
  });

  it("clickingAnUnreadEntryMarksItRead", async () => {
    backend();

    render(<InboxBell />);
    await openBell();
    await userEvent.click(screen.getByRole("button", { name: /bob tester joined/i }));

    expect(apiMock).toHaveBeenCalledWith("/notifications/n1/read", { method: "POST" });
  });

  it("markAllAsReadPostsToReadAllAndReloads", async () => {
    backend((path) => {
      if (path === "/notifications/read-all") {
        inbox = { ...inbox, notifications: { unreadCount: 0, items: [{ ...entry, readAt: "2027-06-02T10:00:00Z" }] } };
      }
    });

    render(<InboxBell />);
    await openBell();
    await userEvent.click(screen.getByRole("button", { name: /mark all as read/i }));

    expect(apiMock).toHaveBeenCalledWith("/notifications/read-all", { method: "POST" });
    await waitFor(() => expect(screen.queryByRole("button", { name: /mark all as read/i })).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: /inbox/i })).toHaveTextContent("1");
  });

  it("showsWhatLoadedWhenTheInvitationsCouldNotBeLoaded", async () => {
    inbox = { invitations: null, notifications: { unreadCount: 1, items: [entry] } };
    backend();

    render(<InboxBell />);
    await openBell();

    expect(screen.getByText(/invitations could not be loaded/i)).toBeInTheDocument();
    expect(screen.getByText("Bob Tester joined Mallorca 2027")).toBeInTheDocument();
  });

  it("showsWhatLoadedWhenTheEntriesCouldNotBeLoaded", async () => {
    inbox = { invitations: [invitation], notifications: null };
    backend();

    render(<InboxBell />);
    await openBell();

    expect(screen.getByText(/entries could not be loaded/i)).toBeInTheDocument();
    expect(screen.getByText("Alice Tester invited you to Mallorca 2027")).toBeInTheDocument();
  });

  it("rendersNamesAsTextNotHtml", async () => {
    const evil = "<img src=x onerror=alert(1)>";
    inbox = {
      invitations: [{ ...invitation, group: { id: "g9", name: evil } }],
      notifications: { unreadCount: 1, items: [{ ...entry, actor: { ...entry.actor, displayName: evil } }] },
    };
    backend();

    const { container } = render(<InboxBell />);
    await openBell();

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
