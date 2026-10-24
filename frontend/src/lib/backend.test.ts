// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";
import { proxyToBackend } from "@/lib/backend";

const USER = "aaaaaaaa-0000-0000-0000-000000000001";

afterEach(() => {
  vi.restoreAllMocks();
  delete process.env.BACKEND_URL;
});

describe("proxyToBackend", () => {
  it("forwardsMethodBodyAndStatus", async () => {
    process.env.BACKEND_URL = "http://backend.test:8080";
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(
        new Response('{"id":"x"}', {
          status: 201,
          headers: { "content-type": "application/json" },
        }),
      );

    const res = await proxyToBackend(
      new Request("http://localhost:3000/api/groups?a=1", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: '{"name":"Mallorca 2027"}',
      }),
      "/groups",
    );

    expect(res.status).toBe(201);
    expect(await res.text()).toBe('{"id":"x"}');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe("http://backend.test:8080/groups?a=1");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe('{"name":"Mallorca 2027"}');
  });

  it("addsUserIdHeaderFromCookie", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response("[]", { status: 200 }));

    await proxyToBackend(
      new Request("http://localhost:3000/api/groups", {
        headers: { cookie: `other=1; sw_user_id=${USER}` },
      }),
      "/groups",
    );

    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers);
    expect(headers.get("x-user-id")).toBe(USER);
  });

  it("defaultsBackendUrlToLocalhost8080", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response("[]", { status: 200 }));

    await proxyToBackend(new Request("http://localhost:3000/api/users"), "/users");

    expect(String(fetchMock.mock.calls[0][0])).toBe("http://localhost:8080/users");
  });

  it("returns502ProblemWhenBackendUnreachable", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("fetch failed"));

    const res = await proxyToBackend(new Request("http://localhost:3000/api/groups"), "/groups");

    expect(res.status).toBe(502);
    expect(res.headers.get("content-type")).toContain("application/problem+json");
    expect(await res.json()).toMatchObject({ status: 502, code: "BACKEND_UNREACHABLE" });
  });
});
