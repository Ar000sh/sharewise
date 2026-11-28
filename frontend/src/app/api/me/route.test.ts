// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/server", () => ({ connection: async () => {} }));

import { GET, PATCH } from "@/app/api/me/route";

afterEach(() => vi.restoreAllMocks());

describe("/api/me route", () => {
  it("forwardsGetToBackendMe", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response("{}", { status: 200 }));

    const res = await GET(new Request("http://localhost:3000/api/me"));

    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toBe("http://localhost:8080/me");
  });

  it("forwardsPatchToBackendMe", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response("{}", { status: 200 }));

    const res = await PATCH(
      new Request("http://localhost:3000/api/me", {
        method: "PATCH",
        headers: { "content-type": "application/json", origin: "http://localhost:3000" },
        body: '{"displayName":"Alice M."}',
      }),
    );

    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toBe("http://localhost:8080/me");
    expect(fetchMock.mock.calls[0][1]?.method).toBe("PATCH");
  });
});
