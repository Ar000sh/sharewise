import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { MemberList } from "@/components/MemberList";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

const members = [
  { userId: "u1", displayName: "Alice", role: "ADMIN" as const },
  { userId: "u2", displayName: "Bob", role: "MEMBER" as const },
];

describe("MemberList", () => {
  it("offersMakeAdminOnlyToAnAdminAndOnlyForMembersWhoAreNotAdmins", () => {
    const { rerender } = render(<MemberList members={members} />);
    expect(screen.queryByRole("button", { name: /make .* admin/i })).not.toBeInTheDocument();

    rerender(<MemberList members={members} promote={{ groupId: "g1", onPromoted: () => {} }} />);

    expect(screen.getByRole("button", { name: "Make Bob an admin" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Make Alice an admin" })).not.toBeInTheDocument();
  });

  it("promotesTheMemberAndTellsTheParent", async () => {
    apiMock.mockResolvedValue({ userId: "u2", displayName: "Bob", role: "ADMIN" });
    const onPromoted = vi.fn();
    render(<MemberList members={members} promote={{ groupId: "g1", onPromoted }} />);

    await userEvent.click(screen.getByRole("button", { name: "Make Bob an admin" }));

    expect(apiMock).toHaveBeenCalledWith("/groups/g1/members/u2/promote", { method: "POST" });
    expect(onPromoted).toHaveBeenCalledOnce();
  });

  it("showsTheMessageWhenThePromotionIsRefusedAndTellsNobody", async () => {
    apiMock.mockRejectedValue(new ApiError(403, "NOT_AN_ADMIN"));
    const onPromoted = vi.fn();
    render(<MemberList members={members} promote={{ groupId: "g1", onPromoted }} />);

    await userEvent.click(screen.getByRole("button", { name: "Make Bob an admin" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Only an admin can do this.");
    expect(onPromoted).not.toHaveBeenCalled();
  });

  it("offersRemoveOnlyToAnAdminAndNeverForThemselves", () => {
    // given
    const { rerender } = render(<MemberList members={members} currentUserId="u1" />);
    expect(screen.queryByRole("button", { name: /^remove/i })).not.toBeInTheDocument();

    // when
    rerender(<MemberList members={members} currentUserId="u1" remove={{ groupId: "g1", onRemoved: () => {} }} />);

    // then
    expect(screen.getByRole("button", { name: "Remove Bob" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remove Alice" })).not.toBeInTheDocument();
  });

  it("removesTheMemberOnlyAfterConfirmingAndTellsTheParent", async () => {
    // given
    apiMock.mockResolvedValue(undefined);
    const onRemoved = vi.fn();
    render(<MemberList members={members} currentUserId="u1" remove={{ groupId: "g1", onRemoved }} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Remove Bob" }));
    expect(apiMock).not.toHaveBeenCalled();
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Remove Bob" }));

    // then
    expect(apiMock).toHaveBeenCalledWith("/groups/g1/members/u2", { method: "DELETE" });
    expect(onRemoved).toHaveBeenCalledOnce();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("showsWhyARemovalIsRefusedInTheDialogAndTellsNobody", async () => {
    // given
    apiMock.mockRejectedValue(new ApiError(409, "BALANCE_NOT_ZERO"));
    const onRemoved = vi.fn();
    render(<MemberList members={members} currentUserId="u1" remove={{ groupId: "g1", onRemoved }} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Remove Bob" }));
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Remove Bob" }));

    // then
    expect(await within(screen.getByRole("dialog")).findByRole("alert")).toHaveTextContent(/balance must be 0/i);
    expect(onRemoved).not.toHaveBeenCalled();
  });

  it("listsFormerMembersUnderTheMembers", () => {
    // when
    render(<MemberList members={members} formerMembers={[{ userId: "u4", displayName: "David", role: "MEMBER" }]} />);

    // then
    expect(screen.getByRole("heading", { name: "Former members" })).toBeInTheDocument();
    expect(screen.getByText("David")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
  });

  it("showsMembersWithRoles", () => {
    render(
      <MemberList
        currentUserId="u2"
        members={[
          { userId: "u1", displayName: "Alice", role: "ADMIN" },
          { userId: "u2", displayName: "Bob", role: "MEMBER" },
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
