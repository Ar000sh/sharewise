import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { JoinGroup } from "@/components/JoinGroup";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());
const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

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
