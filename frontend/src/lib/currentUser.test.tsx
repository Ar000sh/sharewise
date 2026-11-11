import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { CurrentUserProvider, useCurrentUser } from "@/lib/currentUser";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);

afterEach(() => {
  vi.resetAllMocks();
  vi.unstubAllGlobals();
});

function stubLocation(pathname: string) {
  vi.stubGlobal("location", { ...window.location, pathname });
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

  it.each(["/login"])("doesNotAskOnThePublicPage %s", (pathname) => {
    stubLocation(pathname);

    render(
      <CurrentUserProvider>
        <WhoAmI />
      </CurrentUserProvider>,
    );

    expect(apiMock).not.toHaveBeenCalled();
  });
});
