import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { CreateGroupForm } from "@/components/CreateGroupForm";
import { GroupList } from "@/components/GroupList";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

describe("GroupList", () => {
  it("rendersGroupsFromApi", async () => {
    apiMock.mockResolvedValue([
      { id: "g1", name: "Mallorca 2027", status: "OPEN", memberCount: 4 },
      { id: "g2", name: "Flat", status: "OPEN", memberCount: 1 },
    ]);

    render(<GroupList />);

    const link = await screen.findByRole("link", { name: /Mallorca 2027/ });
    expect(link).toHaveAttribute("href", "/groups/g1");
    expect(screen.getByText("4 members")).toBeInTheDocument();
    expect(screen.getByText("1 member")).toBeInTheDocument();
    expect(apiMock).toHaveBeenCalledWith("/groups");
  });

  it("showsEmptyStateWhenNoGroups", async () => {
    apiMock.mockResolvedValue([]);

    render(<GroupList />);

    expect(await screen.findByText(/no groups yet/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /create/i })).toHaveAttribute("href", "/groups/new");
  });

  it("showsErrorMessageWhenLoadingFails", async () => {
    apiMock.mockRejectedValue(new ApiError(401, "UNAUTHENTICATED", "Missing or unknown X-User-Id"));

    render(<GroupList />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/please sign in/i);
  });
});

describe("CreateGroupForm", () => {
  it("createFormPostsNameAndNavigatesToGroup", async () => {
    apiMock.mockResolvedValue({ id: "g9", name: "Trip", status: "OPEN", memberCount: 1 });

    render(<CreateGroupForm />);
    await userEvent.type(screen.getByLabelText(/name/i), "  Trip ");
    await userEvent.click(screen.getByRole("button", { name: /create/i }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/groups/g9"));
    expect(apiMock).toHaveBeenCalledWith("/groups", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ name: "Trip" }),
    });
  });

  it("blankNameShowsValidationMessage", async () => {
    render(<CreateGroupForm />);
    await userEvent.type(screen.getByLabelText(/name/i), "   ");
    await userEvent.click(screen.getByRole("button", { name: /create/i }));

    expect(screen.getByRole("alert")).toHaveTextContent(/enter a group name/i);
    expect(apiMock).not.toHaveBeenCalled();
  });

  it("showsServerErrorAndStaysOnPage", async () => {
    apiMock.mockRejectedValue(new ApiError(400, "BAD_REQUEST", "name too long"));

    render(<CreateGroupForm />);
    await userEvent.type(screen.getByLabelText(/name/i), "x");
    await userEvent.click(screen.getByRole("button", { name: /create/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("name too long");
    expect(push).not.toHaveBeenCalled();
  });
});
