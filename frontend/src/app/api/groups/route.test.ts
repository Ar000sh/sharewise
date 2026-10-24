// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/server", () => ({ connection: async () => {} }));

import { GET, POST } from "@/app/api/groups/route";

afterEach(() => vi.restoreAllMocks());

describe("/api/groups route", () => {
  it("forwardsPostToBackendGroups", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response("{}", { status: 201 }));

    const res = await POST(
      new Request("http://localhost:3000/api/groups", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: '{"name":"Mallorca 2027"}',
      }),
    );

    expect(res.status).toBe(201);
    expect(String(fetchMock.mock.calls[0][0])).toBe("http://localhost:8080/groups");
    expect(fetchMock.mock.calls[0][1]?.method).toBe("POST");
  });

  it("forwardsGetToBackendGroups", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response("[]", { status: 200 }));

    const res = await GET(new Request("http://localhost:3000/api/groups"));

    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toBe("http://localhost:8080/groups");
  });
});
