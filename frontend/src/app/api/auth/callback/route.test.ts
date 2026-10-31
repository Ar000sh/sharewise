// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/server", () => ({ connection: async () => {} }));

import { GET } from "@/app/api/auth/callback/route";
import { loginFlowCookie } from "@/lib/session";

afterEach(() => vi.restoreAllMocks());

function callback(query: string) {
  const flow = loginFlowCookie({ state: "s-1", verifier: "v-1", returnTo: "/groups" }).split(";")[0];
  return new Request(`http://localhost:3000/api/auth/callback?${query}`, { headers: { cookie: flow } });
}

describe("/api/auth/callback route", () => {
  it("callbackWithAWrongStateSetsNoSessionCookie", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");

    const res = await GET(callback("code=abc&state=forged"));

    expect(res.status).toBe(302);
    expect(res.headers.get("location")).toBe("http://localhost:3000/login");
    expect(res.headers.getSetCookie().some((c) => c.startsWith("sw_session=") && !c.includes("Max-Age=0"))).toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("callbackWithATokenResponseMissingTheRefreshTokenSetsNoSessionCookie", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ access_token: "acc.ess.token", expires_in: 300 }), { status: 200 }),
    );

    // when
    const res = await GET(callback("code=abc&state=s-1"));

    // then
    expect(res.status).toBe(503);
    expect(res.headers.getSetCookie()).toEqual([]);
  });

  it("callbackWithAValidCodeSetsTheCookieAndCallsMe", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith("/protocol/openid-connect/token")) {
        return new Response(
          JSON.stringify({ access_token: "acc.ess.token", refresh_token: "ref.resh.token", expires_in: 300 }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }
      return new Response("{}", { status: 200 });
    });

    const res = await GET(callback("code=abc&state=s-1"));

    expect(res.status).toBe(302);
    expect(res.headers.get("location")).toBe("http://localhost:3000/groups");
    const session = res.headers.getSetCookie().find((c) => c.startsWith("sw_session="));
    expect(session).toContain("acc.ess.token");
    expect(session).toContain("HttpOnly");
    const me = fetchMock.mock.calls.find(([url]) => String(url) === "http://localhost:8080/me");
    expect(me).toBeDefined();
    expect(new Headers(me?.[1]?.headers).get("authorization")).toBe("Bearer acc.ess.token");
    const token = fetchMock.mock.calls[0];
    expect(String(token[1]?.body)).toContain("code_verifier=v-1");
  });

  it("callbackWithARefusedAccountSetsNoSessionCookieAndShowsAnExplanation", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      if (String(input).endsWith("/protocol/openid-connect/token")) {
        return new Response(
          JSON.stringify({ access_token: "acc.ess.token", refresh_token: "ref.resh.token", expires_in: 300 }),
          { status: 200 },
        );
      }
      return new Response("{}", { status: 401 });
    });

    // when
    const res = await GET(callback("code=abc&state=s-1"));

    // then
    expect(res.status).toBe(403);
    expect(res.headers.getSetCookie().some((c) => c.startsWith("sw_session=") && !c.includes("Max-Age=0"))).toBe(false);
    expect(res.headers.getSetCookie().some((c) => c.startsWith("sw_login=") && c.includes("Max-Age=0"))).toBe(true);
    const body = await res.text();
    expect(body).toContain("ShareWise");
    expect(body).toContain("openid-connect/logout");
  });
});
