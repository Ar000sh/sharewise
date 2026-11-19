import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { AuthBar } from "@/components/AuthBar";
import { CurrentUserProvider } from "@/lib/currentUser";
import { renderWithQueryClient } from "@/lib/renderWithQueryClient";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

const apiMock = vi.mocked(api);
const assign = vi.fn();

afterEach(() => {
  vi.resetAllMocks();
  vi.unstubAllGlobals();
});

describe("AuthBar", () => {
  it("showsWhoIsSignedInAndSignsOutWithAPostToTheLogoutRoute", async () => {
    vi.stubGlobal("location", { ...window.location, pathname: "/", search: "", assign });
    apiMock.mockImplementation(async (path: string) =>
      path === "/inbox"
        ? { invitations: [], notifications: { unreadCount: 0, items: [] } }
        : { userId: "u1", displayName: "Alice", username: "alice" },
    );

    renderWithQueryClient(
      <CurrentUserProvider>
        <AuthBar />
      </CurrentUserProvider>,
    );

    expect(await screen.findByText("Alice")).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: /inbox/i })).toBeInTheDocument();
    const button = screen.getByRole("button", { name: "Sign out" });
    expect(button.closest("form")).toHaveAttribute("method", "post");
    expect(button.closest("form")).toHaveAttribute("action", "/api/auth/logout");
  });

  it("showsNothingWhileItDoesNotKnowWhoIsSignedIn", () => {
    vi.stubGlobal("location", { ...window.location, pathname: "/login", search: "", assign });

    const { container } = renderWithQueryClient(
      <CurrentUserProvider>
        <AuthBar />
      </CurrentUserProvider>,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("changesTheDisplayNameInlineAndUpdatesTheHeader", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("location", { ...window.location, pathname: "/", search: "", assign });
    apiMock.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === "/inbox") {
        return { invitations: [], notifications: { unreadCount: 0, items: [] } };
      }
      if (path === "/me" && init?.method === "PATCH") {
        return { userId: "u1", displayName: "Alice M.", username: "alice" };
      }
      return { userId: "u1", displayName: "Alice", username: "alice" };
    });

    renderWithQueryClient(
      <CurrentUserProvider>
        <AuthBar />
      </CurrentUserProvider>,
    );

    expect(await screen.findByText("Alice")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Change name" }));

    const input = screen.getByLabelText("Display name");
    expect(input).toHaveValue("Alice");
    await user.clear(input);
    await user.type(input, "  Alice M.  ");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Alice M.")).toBeInTheDocument();
    expect(apiMock).toHaveBeenCalledWith("/me", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ displayName: "Alice M." }),
    });
  });

  it("rejectsABlankDisplayNameWithoutCallingTheApi", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("location", { ...window.location, pathname: "/", search: "", assign });
    apiMock.mockImplementation(async (path: string) =>
      path === "/inbox"
        ? { invitations: [], notifications: { unreadCount: 0, items: [] } }
        : { userId: "u1", displayName: "Alice", username: "alice" },
    );

    renderWithQueryClient(
      <CurrentUserProvider>
        <AuthBar />
      </CurrentUserProvider>,
    );

    expect(await screen.findByText("Alice")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Change name" }));

    const input = screen.getByLabelText("Display name");
    await user.clear(input);
    await user.type(input, "   ");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Enter a display name.");
    expect(apiMock).not.toHaveBeenCalledWith("/me", expect.objectContaining({ method: "PATCH" }));
  });

  it("showsTheErrorMessageWhenChangingTheNameFails", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("location", { ...window.location, pathname: "/", search: "", assign });
    apiMock.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === "/inbox") {
        return { invitations: [], notifications: { unreadCount: 0, items: [] } };
      }
      if (path === "/me" && init?.method === "PATCH") {
        throw new ApiError(400, "BAD_REQUEST", "The name must have 1 to 100 characters");
      }
      return { userId: "u1", displayName: "Alice", username: "alice" };
    });

    renderWithQueryClient(
      <CurrentUserProvider>
        <AuthBar />
      </CurrentUserProvider>,
    );

    expect(await screen.findByText("Alice")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Change name" }));
    const input = screen.getByLabelText("Display name");
    await user.clear(input);
    await user.type(input, "Bob");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The name must have 1 to 100 characters");
  });
});
