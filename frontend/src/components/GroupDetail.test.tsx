import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { InviteButton } from "@/components/InviteButton";
import { JoinGroup } from "@/components/JoinGroup";
import { MemberList } from "@/components/MemberList";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

describe("MemberList", () => {
  it("showsMembersWithRoles", () => {
    render(
      <MemberList
        currentUserId="u2"
        members={[
          { memberId: "u1", displayName: "Alice", role: "ADMIN" },
          { memberId: "u2", displayName: "Bob", role: "MEMBER" },
        ]}
      />,
    );

    const items = screen.getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("Alice");
    expect(items[0]).toHaveTextContent("Admin");
    expect(items[1]).toHaveTextContent("Bob");
    expect(items[1]).toHaveTextContent("(you)");
    expect(items[1]).not.toHaveTextContent("Admin");
  });
});

describe("InviteButton", () => {
  it("inviteButtonShowsCopyableLink", async () => {
    apiMock.mockResolvedValue({ token: "k3Xq9v2mT8aLw0pZ", expiresAt: "2027-06-08T10:00:00Z" });

    render(<InviteButton groupId="g1" />);
    await userEvent.click(screen.getByRole("button", { name: /invite/i }));

    const field = await screen.findByLabelText(/invite link/i);
    expect(field).toHaveValue(`${window.location.origin}/join/k3Xq9v2mT8aLw0pZ`);
    expect(apiMock).toHaveBeenCalledWith("/groups/g1/invitations", { method: "POST" });
  });

  it("showsErrorWhenInviteFails", async () => {
    apiMock.mockRejectedValue(new ApiError(403, "NOT_A_MEMBER"));

    render(<InviteButton groupId="g1" />);
    await userEvent.click(screen.getByRole("button", { name: /invite/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("You are not a member of this group.");
  });
});

describe("JoinGroup", () => {
  it("joinPageAcceptsTokenAndRedirects", async () => {
    apiMock.mockResolvedValue({ id: "g1", name: "Mallorca", status: "OPEN", memberCount: 2 });

    render(<JoinGroup token="tok" />);
    await userEvent.click(screen.getByRole("button", { name: /join/i }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/groups/g1"));
    expect(apiMock).toHaveBeenCalledWith("/invitations/tok/accept", { method: "POST" });
  });

  it("joinPageShowsExpiredMessageOn410", async () => {
    apiMock.mockRejectedValue(new ApiError(410, "INVITATION_EXPIRED", "This invitation has expired"));

    render(<JoinGroup token="tok" />);
    await userEvent.click(screen.getByRole("button", { name: /join/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/expired/i);
    expect(push).not.toHaveBeenCalled();
  });

  it("joinPageShowsAlreadyMemberOn409", async () => {
    apiMock.mockRejectedValue(new ApiError(409, "ALREADY_MEMBER", "You are already a member"));

    render(<JoinGroup token="tok" />);
    await userEvent.click(screen.getByRole("button", { name: /join/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/already a member/i);
    expect(screen.getByRole("link", { name: /your groups/i })).toHaveAttribute("href", "/");
  });
});
