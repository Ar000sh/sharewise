// @vitest-environment node
import { describe, expect, it, vi } from "vitest";

vi.mock("next/server", () => ({ connection: async () => {} }));

import * as logout from "@/app/api/auth/logout/route";

function post(origin?: string) {
  return new Request("http://localhost:3000/api/auth/logout", {
    method: "POST",
    headers: origin ? { origin } : {},
  });
}

describe("/api/auth/logout route", () => {
  it("logoutIsPostOnlyAndClearsTheCookie", async () => {
    expect("GET" in logout).toBe(false);

    const res = await logout.POST(post("http://localhost:3000"));

    expect(res.status).toBe(303);
    expect(res.headers.get("set-cookie")).toContain("sw_session=; ");
    expect(res.headers.get("set-cookie")).toContain("Max-Age=0");
    const location = new URL(res.headers.get("location") ?? "");
    expect(location.pathname).toBe("/realms/sharewise/protocol/openid-connect/logout");
    expect(location.searchParams.get("client_id")).toBe("sharewise-web");
    expect(location.searchParams.get("post_logout_redirect_uri")).toBe("http://localhost:3000/login");
  });

  it("logoutFromAnotherOriginIsRejectedAndTheSessionStays", async () => {
    for (const req of [post("https://evil.example"), post()]) {
      const res = await logout.POST(req);

      expect(res.status).toBe(403);
      expect(res.headers.get("content-type")).toContain("application/problem+json");
      expect((await res.json()).code).toBe("FORBIDDEN_ORIGIN");
      expect(res.headers.get("set-cookie")).toBeNull();
      expect(res.headers.get("location")).toBeNull();
    }
  });
});
