import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { AuthForm, safeNext } from "@/components/AuthForm";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const assign = vi.fn();

afterEach(() => {
  vi.resetAllMocks();
  vi.unstubAllGlobals();
});

function stubLocation(search = "") {
  vi.stubGlobal("location", { ...window.location, search, assign });
}

describe("AuthForm", () => {
  it("signsInAndGoesToTheRequestedPage", async () => {
    stubLocation("?next=%2Fjoin%2Fabc");
    vi.mocked(api).mockResolvedValue({ userId: "u1", displayName: "Alice" });

    render(<AuthForm mode="login" />);
    await userEvent.type(screen.getByLabelText("Email"), "alice@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "secret-pass");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(assign).toHaveBeenCalledWith("/join/abc"));
    const [path, init] = vi.mocked(api).mock.calls[0];
    expect(path).toBe("/auth/login");
    expect(JSON.parse(String(init?.body))).toEqual({ email: "alice@example.com", password: "secret-pass" });
  });

  it("registersWithNameEmailAndPassword", async () => {
    stubLocation();
    vi.mocked(api).mockResolvedValue({ userId: "u1", displayName: "Alice" });

    render(<AuthForm mode="register" />);
    await userEvent.type(screen.getByLabelText("Name"), "Alice");
    await userEvent.type(screen.getByLabelText("Email"), "alice@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "secret-pass");
    await userEvent.click(screen.getByRole("button", { name: "Sign up" }));

    await waitFor(() => expect(assign).toHaveBeenCalledWith("/"));
    expect(vi.mocked(api).mock.calls[0][0]).toBe("/auth/register");
    expect(JSON.parse(String(vi.mocked(api).mock.calls[0][1]?.body))).toEqual({
      displayName: "Alice",
      email: "alice@example.com",
      password: "secret-pass",
    });
  });

  it("showsAReadableMessageForWrongCredentialsAndStaysOnThePage", async () => {
    stubLocation();
    vi.mocked(api).mockRejectedValue(new ApiError(401, "INVALID_CREDENTIALS"));

    render(<AuthForm mode="login" />);
    await userEvent.type(screen.getByLabelText("Email"), "alice@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "wrong-pass");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Wrong email or password");
    expect(assign).not.toHaveBeenCalled();
  });
});

describe("safeNext", () => {
  it.each([
    ["/join/abc", "/join/abc"],
    ["//evil.example", "/"],
    ["https://evil.example", "/"],
    [null, "/"],
    // Browsers read a backslash like a slash and ignore tabs and newlines inside a URL, so each of
    // these would leave the site after sign-in. The query string is decoded before it reaches safeNext.
    ["/\\evil.example", "/"],
    ["/\t/evil.example", "/"],
    ["/\n/evil.example", "/"],
    ["/\r/evil.example", "/"],
    ["\\\\evil.example", "/"],
    ["javascript:alert(1)", "/"],
    ["", "/"],
  ])("%j -> %j", (input, expected) => {
    expect(safeNext(input)).toBe(expected);
  });

  it("keepsPathsWithQueryAndEncodedCharacters", () => {
    expect(safeNext("/groups/abc?tab=Balances")).toBe("/groups/abc?tab=Balances");
    expect(safeNext("/join/a%5Cb")).toBe("/join/a%5Cb");
  });
});
