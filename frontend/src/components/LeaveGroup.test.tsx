import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { LeaveGroup } from "@/components/LeaveGroup";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

describe("LeaveGroup", () => {
  it("leavesOnlyAfterConfirmingAndGoesBackToMyGroups", async () => {
    // given
    apiMock.mockResolvedValue(undefined);
    render(<LeaveGroup groupId="g1" groupName="Mallorca 2027" />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Leave group" }));
    expect(apiMock).not.toHaveBeenCalled();
    await userEvent.click(within(screen.getByRole("dialog", { name: "Leave Mallorca 2027?" })).getByRole("button", { name: "Leave group" }));

    // then
    expect(apiMock).toHaveBeenCalledWith("/groups/g1/leave", { method: "POST" });
    expect(push).toHaveBeenCalledWith("/");
  });

  it("theLastAdminIsToldWhyAndStaysOnThePage", async () => {
    // given
    apiMock.mockRejectedValue(new ApiError(409, "LAST_ADMIN"));
    render(<LeaveGroup groupId="g1" groupName="Mallorca 2027" />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Leave group" }));
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Leave group" }));

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(/last admin/i);
    expect(push).not.toHaveBeenCalled();
  });

  it("cancelClosesTheDialogWithoutLeaving", async () => {
    // given
    render(<LeaveGroup groupId="g1" groupName="Mallorca 2027" />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Leave group" }));
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(apiMock).not.toHaveBeenCalled();
  });
});
