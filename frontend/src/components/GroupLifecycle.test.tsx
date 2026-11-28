import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { GroupLifecycle } from "@/components/GroupLifecycle";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

describe("GroupLifecycle", () => {
  it("showsNothingToAMemberOfAnOpenGroup", () => {
    // when
    const { container } = render(<GroupLifecycle groupId="g1" status="OPEN" isAdmin={false} onChanged={vi.fn()} />);

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("anAdminClosesAnOpenGroupOnlyAfterConfirming", async () => {
    // given
    apiMock.mockResolvedValue({});
    const onChanged = vi.fn();
    render(<GroupLifecycle groupId="g1" status="OPEN" isAdmin onChanged={onChanged} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Close for settlement" }));
    expect(apiMock).not.toHaveBeenCalled();
    const dialog = screen.getByRole("dialog", { name: "Close for settlement?" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Close for settlement" }));

    // then
    expect(apiMock).toHaveBeenCalledWith("/groups/g1/close", { method: "POST" });
    expect(onChanged).toHaveBeenCalledOnce();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("aClosedGroupShowsTheBannerToEverybodyAndReopenAndArchiveOnlyToAnAdmin", () => {
    // given
    const { rerender } = render(<GroupLifecycle groupId="g1" status="CLOSED_FOR_SETTLEMENT" isAdmin={false} onChanged={vi.fn()} />);
    expect(screen.getByRole("status")).toHaveTextContent("Closed for settlement.");
    expect(screen.queryByRole("button")).not.toBeInTheDocument();

    // when
    rerender(<GroupLifecycle groupId="g1" status="CLOSED_FOR_SETTLEMENT" isAdmin onChanged={vi.fn()} />);

    // then
    expect(screen.getByRole("button", { name: "Reopen group" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Archive group" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Close for settlement" })).not.toBeInTheDocument();
  });

  it("aRefusedArchiveShowsWhyInTheDialogAndChangesNothing", async () => {
    // given
    apiMock.mockRejectedValue(new ApiError(409, "BALANCE_NOT_ZERO"));
    const onChanged = vi.fn();
    render(<GroupLifecycle groupId="g1" status="CLOSED_FOR_SETTLEMENT" isAdmin onChanged={onChanged} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Archive group" }));
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Archive group" }));

    // then
    expect(apiMock).toHaveBeenCalledWith("/groups/g1/archive", { method: "POST" });
    expect(await within(screen.getByRole("dialog")).findByRole("alert")).toHaveTextContent(/balance must be 0/i);
    expect(onChanged).not.toHaveBeenCalled();
  });

  it("anArchivedGroupShowsTheBannerAndNoActionsEvenToAnAdmin", () => {
    // when
    render(<GroupLifecycle groupId="g1" status="ARCHIVED" isAdmin onChanged={vi.fn()} />);

    // then
    expect(screen.getByRole("status")).toHaveTextContent("Archived.");
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
