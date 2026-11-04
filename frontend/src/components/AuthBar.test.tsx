import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { AuthBar } from "@/components/AuthBar";
import { CurrentUserProvider } from "@/lib/currentUser";

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

    render(
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

    const { container } = render(
      <CurrentUserProvider>
        <AuthBar />
      </CurrentUserProvider>,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
