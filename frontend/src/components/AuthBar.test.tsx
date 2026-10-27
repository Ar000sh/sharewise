import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { AuthBar } from "@/components/AuthBar";
import { CurrentUserProvider } from "@/lib/currentUser";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
const assign = vi.fn();

afterEach(() => {
  vi.resetAllMocks();
  vi.unstubAllGlobals();
});

describe("AuthBar", () => {
  it("showsWhoIsSignedInAndSignsOut", async () => {
    vi.stubGlobal("location", { ...window.location, pathname: "/", search: "", assign });
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    apiMock.mockResolvedValue({ userId: "u1", displayName: "Alice" });

    render(
      <CurrentUserProvider>
        <AuthBar />
      </CurrentUserProvider>,
    );
    expect(await screen.findByText("Alice")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));

    await waitFor(() => expect(assign).toHaveBeenCalledWith("/login"));
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/logout", { method: "POST" });
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
