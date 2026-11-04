// @vitest-environment node
import { describe, expect, it } from "vitest";
import { clearedSessionCookie, readSession, safeReturnTo, sessionCookie } from "@/lib/session";

describe("safeReturnTo", () => {
  it.each([
    ["/groups", "/groups"],
    ["//evil.com", "/"],
    ["https://evil.com", "/"],
    ["/\\evil.com", "/"],
    [null, "/"],
    // Browsers read a backslash like a slash and ignore tabs and newlines inside a URL, so each of
    // these would leave the site after sign-in.
    ["/\t/evil.example", "/"],
    ["/\n/evil.example", "/"],
    ["\\\\evil.example", "/"],
    ["javascript:alert(1)", "/"],
    ["", "/"],
  ])("acceptsOnlyRelativePaths %j -> %j", (input, expected) => {
    expect(safeReturnTo(input)).toBe(expected);
  });

  it("keepsPathsWithQueryAndEncodedCharacters", () => {
    expect(safeReturnTo("/groups/abc?tab=Balances")).toBe("/groups/abc?tab=Balances");
    expect(safeReturnTo("/groups/a%5Cb")).toBe("/groups/a%5Cb");
  });
});

describe("session cookie", () => {
  const tokens = { accessToken: "a.b.c", refreshToken: "d.e.f", expiresAt: 1_900_000_000_000 };

  it("isHttpOnlyAndSameSiteLaxAndReadsBackFromARequest", () => {
    const cookie = sessionCookie(tokens);

    expect(cookie).toContain("HttpOnly");
    expect(cookie).toContain("SameSite=Lax");
    const req = new Request("http://localhost:3000/api/me", { headers: { cookie: cookie.split(";")[0] } });
    expect(readSession(req)).toEqual(tokens);
  });

  it("isGoneAfterClearing", () => {
    expect(clearedSessionCookie()).toContain("Max-Age=0");
    const req = new Request("http://localhost:3000/", { headers: { cookie: "sw_session=garbage" } });
    expect(readSession(req)).toBeUndefined();
  });
});
