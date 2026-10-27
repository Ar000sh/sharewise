import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { InviteButton } from "@/components/InviteButton";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

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
