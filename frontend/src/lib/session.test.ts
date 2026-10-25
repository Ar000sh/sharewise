// @vitest-environment node
import { describe, expect, it } from "vitest";
import { signInResponse } from "@/lib/session";

describe("signInResponse", () => {
  it("movesTheTokenIntoAnHttpOnlyCookieAndKeepsItOutOfTheBody", async () => {
    const backend = new Response(JSON.stringify({ token: "t0ken", userId: "u1", displayName: "Alice" }), {
      status: 201,
    });

    const res = await signInResponse(backend);

    expect(res.status).toBe(201);
    expect(await res.json()).toEqual({ userId: "u1", displayName: "Alice" });
    const cookie = res.headers.get("set-cookie") ?? "";
    expect(cookie).toContain("sw_session=t0ken");
    expect(cookie).toContain("HttpOnly");
    expect(cookie).toContain("SameSite=Lax");
  });

  it("passesErrorsThroughWithoutACookie", async () => {
    const backend = new Response('{"code":"INVALID_CREDENTIALS"}', { status: 401 });

    const res = await signInResponse(backend);

    expect(res.status).toBe(401);
    expect(res.headers.get("set-cookie")).toBeNull();
  });
});
