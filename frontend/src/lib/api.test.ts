import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";

afterEach(() => vi.restoreAllMocks());

describe("api", () => {
  it("returnsParsedJsonOnSuccess", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(Response.json([{ id: "g1" }]));

    const result = await api<{ id: string }[]>("/groups");

    expect(result).toEqual([{ id: "g1" }]);
    expect(String(fetchMock.mock.calls[0][0])).toBe("/api/groups");
  });

  it("throwsApiErrorWithCodeFromProblemJson", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({ status: 422, code: "PAYER_NOT_IN_GROUP", detail: "The payer is not a member" }),
        { status: 422, headers: { "content-type": "application/problem+json" } },
      ),
    );

    const err = await api("/groups/x/expenses", { method: "POST" }).catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 422, code: "PAYER_NOT_IN_GROUP", detail: "The payer is not a member" });
  });

  it("throwsApiErrorWhenNetworkFails", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));

    const err = await api("/groups").catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 0, code: "NETWORK_ERROR" });
  });
});
