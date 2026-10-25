import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { UserSwitcher } from "@/components/UserSwitcher";
import { USER_COOKIE } from "@/lib/user-cookie";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const reload = vi.fn();
const users = [
  { userId: "u1", displayName: "Alice" },
  { userId: "u2", displayName: "Bob" },
];

function stubReload() {
  vi.stubGlobal("location", { ...window.location, reload });
}

afterEach(() => {
  vi.resetAllMocks();
  vi.unstubAllGlobals();
  document.cookie = `${USER_COOKIE}=; path=/; max-age=0`;
});

describe("UserSwitcher", () => {
  it("reloadsOnceWhenItHadToCreateTheUserCookie", async () => {
    stubReload();
    vi.mocked(api).mockResolvedValue(users);

    render(<UserSwitcher />);

    await waitFor(() => expect(reload).toHaveBeenCalledTimes(1));
    expect(document.cookie).toContain(`${USER_COOKIE}=u1`);
  });

  it("doesNotReloadWhenTheCookieAlreadyExists", async () => {
    stubReload();
    document.cookie = `${USER_COOKIE}=u2; path=/`;
    vi.mocked(api).mockResolvedValue(users);

    render(<UserSwitcher />);

    expect(await screen.findByRole("combobox")).toHaveValue("u2");
    expect(reload).not.toHaveBeenCalled();
  });
});
