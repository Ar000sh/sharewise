import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { InviteForm } from "@/components/InviteForm";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

const invitation = {
  invitationId: "i1",
  invitee: { userId: "u2", displayName: "Bob Tester", username: "bob" },
  expiresAt: "2027-06-08T10:00:00Z",
};

describe("InviteForm", () => {
  it("sendsTheIdentifierTrimmedAndShowsTheInvitedUsername", async () => {
    apiMock.mockResolvedValue(invitation);

    render(<InviteForm groupId="g1" />);
    const field = screen.getByLabelText("Username or email");
    await userEvent.type(field, "  bob  ");
    await userEvent.click(screen.getByRole("button", { name: "Invite" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Invitation sent to @bob");
    expect(apiMock).toHaveBeenCalledWith("/groups/g1/invitations", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ identifier: "bob" }),
    });
    expect(field).toHaveValue("");
  });

  it("showsTheMessageWhenTheInviteIsRefusedAndNoSuccess", async () => {
    // the wording of every code is tested once, in errors.test.ts; this proves the form shows it
    apiMock.mockRejectedValue(new ApiError(404, "ACCOUNT_NOT_FOUND"));

    render(<InviteForm groupId="g1" />);
    await userEvent.type(screen.getByLabelText("Username or email"), "someone");
    await userEvent.click(screen.getByRole("button", { name: "Invite" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/no account found/i);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("disablesTheButtonWhileSending", async () => {
    let finish: (value: typeof invitation) => void = () => {};
    apiMock.mockReturnValue(new Promise((resolve) => (finish = resolve)));

    render(<InviteForm groupId="g1" />);
    await userEvent.type(screen.getByLabelText("Username or email"), "bob");
    await userEvent.click(screen.getByRole("button", { name: "Invite" }));

    expect(screen.getByRole("button", { name: "Invite" })).toBeDisabled();
    finish(invitation);
    expect(await screen.findByRole("status")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Invite" })).toBeEnabled();
  });

  it("doesNotSendABlankIdentifier", async () => {
    render(<InviteForm groupId="g1" />);
    await userEvent.type(screen.getByLabelText("Username or email"), "   ");
    await userEvent.click(screen.getByRole("button", { name: "Invite" }));

    expect(apiMock).not.toHaveBeenCalled();
  });
});
