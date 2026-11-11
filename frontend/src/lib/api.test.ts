import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";

const replace = vi.fn();

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  replace.mockReset();
});

function stubLocation(pathname: string, search = "") {
  vi.stubGlobal("location", { ...window.location, pathname, search, replace });
}

function answer(status: number, code: string) {
  return new Response(JSON.stringify({ status, code }), {
    status,
    headers: { "content-type": "application/problem+json" },
  });
}

describe("api", () => {
  it("aRejectedSessionSendsTheVisitorToSignInAndBackToThisPage", async () => {
    // given
    stubLocation("/groups/g1", "?tab=Balances");
    vi.spyOn(globalThis, "fetch").mockResolvedValue(answer(401, "UNAUTHENTICATED"));

    // when
    const err = await api("/groups/g1/expenses", { method: "POST" }).catch((e) => e);

    // then
    expect(replace).toHaveBeenCalledWith("/login?next=%2Fgroups%2Fg1%3Ftab%3DBalances");
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 401, code: "UNAUTHENTICATED" });
  });

  // 422 and the network failure are checked in their own tests below.
  it.each([403, 404, 500])("otherErrorsDoNotNavigate %i", async (status) => {
    // given
    stubLocation("/groups/g1");
    vi.spyOn(globalThis, "fetch").mockResolvedValue(answer(status, "SOMETHING"));

    // when
    const err = await api("/groups/g1").catch((e) => e);

    // then
    expect(err).toMatchObject({ status });
    expect(replace).not.toHaveBeenCalled();
  });

  it("returnsParsedJsonOnSuccess", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(Response.json([{ id: "g1" }]));

    const result = await api<{ id: string }[]>("/groups");

    expect(result).toEqual([{ id: "g1" }]);
    expect(String(fetchMock.mock.calls[0][0])).toBe("/api/groups");
  });

  it("throwsApiErrorWithCodeFromProblemJson", async () => {
    stubLocation("/groups/x");
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({ status: 422, code: "PAYER_NOT_IN_GROUP", detail: "The payer is not a member" }),
        { status: 422, headers: { "content-type": "application/problem+json" } },
      ),
    );

    const err = await api("/groups/x/expenses", { method: "POST" }).catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 422, code: "PAYER_NOT_IN_GROUP", detail: "The payer is not a member" });
    expect(replace).not.toHaveBeenCalled();
  });

  it("throwsApiErrorWhenNetworkFailsAndDoesNotNavigate", async () => {
    stubLocation("/groups");
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));

    const err = await api("/groups").catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 0, code: "NETWORK_ERROR" });
    expect(replace).not.toHaveBeenCalled();
  });
});
