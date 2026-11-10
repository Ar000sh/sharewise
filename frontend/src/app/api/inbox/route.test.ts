// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/server", () => ({ connection: async () => {} }));

import { GET } from "@/app/api/inbox/route";

afterEach(() => vi.restoreAllMocks());

const invitations = [{ invitationId: "i1" }];
const notifications = { unreadCount: 0, items: [] };
const payments = [{ id: "p1", fromDebtorName: "Carol Tester" }];

/** Answers each of the three backend paths with the given response (cloned, as a body can be read once). */
function backend(answers: { invitations: Response; notifications: Response; payments: Response }) {
  return vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
    const target = String(url);
    if (target.includes("/invitations")) return answers.invitations.clone();
    if (target.includes("/notifications")) return answers.notifications.clone();
    return answers.payments.clone();
  });
}

const ok = { invitations: Response.json(invitations), notifications: Response.json(notifications), payments: Response.json(payments) };

describe("/api/inbox route", () => {
  it("returnsAllThreePartsWhenAllAnswer", async () => {
    const fetchMock = backend(ok);

    const res = await GET(new Request("http://localhost:3000/api/inbox"));

    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ invitations, notifications, payments });
    const targets = fetchMock.mock.calls.map((call) => String(call[0]));
    expect(targets).toContain("http://localhost:8080/invitations");
    expect(targets).toContain("http://localhost:8080/notifications?limit=10");
    expect(targets).toContain("http://localhost:8080/me/settlement-payments");
  });

  it("aFailedPartIsNullAndTheOthersStillArrive", async () => {
    backend({ ...ok, payments: new Response("{}", { status: 500 }) });

    const res = await GET(new Request("http://localhost:3000/api/inbox"));

    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ invitations, notifications, payments: null });
  });

  it("a401FromAnyPartIsPassedOn", async () => {
    backend({ ...ok, payments: new Response('{"code":"UNAUTHENTICATED"}', { status: 401 }) });

    const res = await GET(new Request("http://localhost:3000/api/inbox"));

    expect(res.status).toBe(401);
  });
});
