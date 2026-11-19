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

  it("preservesTheHttpStatusWhenTheConfiguredBackendReturnsAnHtmlErrorPage", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("<html>Not found</html>", {
      status: 404, headers: { "content-type": "text/html" },
    }));

    await expect(api("/groups", { method: "POST" })).rejects.toMatchObject({
      status: 404, code: "HTTP_ERROR",
    });
  });

  it("reportsAnUnconfirmedActionWhenASuccessfulResponseIsNotJson", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("<html>Wrong service</html>", { status: 200 }));

    await expect(api("/groups", { method: "POST" })).rejects.toMatchObject({
      status: 200, code: "INVALID_RESPONSE",
    });
  });

  it("handlesANullErrorBodyWithoutThrowingATypeError", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("null", { status: 500 }));
    await expect(api("/groups")).rejects.toMatchObject({ status: 500, code: "HTTP_ERROR" });
  });
});
