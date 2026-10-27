import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { CurrentUserProvider, useCurrentUser } from "@/lib/currentUser";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
const replace = vi.fn();

afterEach(() => {
  vi.resetAllMocks();
  vi.unstubAllGlobals();
});

function stubLocation(pathname: string, search = "") {
  vi.stubGlobal("location", { ...window.location, pathname, search, replace });
}

function WhoAmI() {
  const user = useCurrentUser();
  return <span>{user ? user.displayName : "nobody"}</span>;
}

describe("CurrentUserProvider", () => {
  it("asksTheServerOnceAndSharesTheAnswerWithEveryConsumer", async () => {
    stubLocation("/groups/g1");
    apiMock.mockResolvedValue({ userId: "u1", displayName: "Alice" });

    render(
      <CurrentUserProvider>
        <WhoAmI />
        <WhoAmI />
      </CurrentUserProvider>,
    );

    expect(await screen.findAllByText("Alice")).toHaveLength(2);
    expect(apiMock).toHaveBeenCalledTimes(1);
    expect(apiMock).toHaveBeenCalledWith("/me");
  });

  it("sendsAVisitorWithoutASessionToSignInAndBackToTheSamePage", async () => {
    stubLocation("/groups/g1", "?tab=Balances");
    apiMock.mockRejectedValue(new ApiError(401, "UNAUTHENTICATED"));

    render(
      <CurrentUserProvider>
        <WhoAmI />
      </CurrentUserProvider>,
    );

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fgroups%2Fg1%3Ftab%3DBalances"));
    expect(screen.getByText("nobody")).toBeInTheDocument();
  });

  it.each(["/login", "/register"])("doesNotAskOnThePublicPage %s", (pathname) => {
    stubLocation(pathname);

    render(
      <CurrentUserProvider>
        <WhoAmI />
      </CurrentUserProvider>,
    );

    expect(apiMock).not.toHaveBeenCalled();
  });
});
