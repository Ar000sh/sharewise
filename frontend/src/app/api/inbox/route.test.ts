// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/server", () => ({ connection: async () => {} }));

import { GET } from "@/app/api/inbox/route";

afterEach(() => vi.restoreAllMocks());

const invitations = [{ invitationId: "i1" }];
const notifications = { unreadCount: 0, items: [] };

function backend(invitationsAnswer: Response, notificationsAnswer: Response) {
  return vi.spyOn(globalThis, "fetch").mockImplementation(async (url) =>
    String(url).includes("/invitations") ? invitationsAnswer.clone() : notificationsAnswer.clone(),
  );
}

describe("/api/inbox route", () => {
  it("returnsBothPartsWhenBothAnswer", async () => {
    const fetchMock = backend(Response.json(invitations), Response.json(notifications));

    const res = await GET(new Request("http://localhost:3000/api/inbox"));

    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ invitations, notifications });
    const targets = fetchMock.mock.calls.map((call) => String(call[0]));
    expect(targets).toContain("http://localhost:8080/invitations");
    expect(targets).toContain("http://localhost:8080/notifications?limit=10");
  });

  it("aFailedPartIsNullAndTheOtherOneStillArrives", async () => {
    backend(Response.json(invitations), new Response("{}", { status: 500 }));

    const res = await GET(new Request("http://localhost:3000/api/inbox"));

    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ invitations, notifications: null });
  });

  it("a401FromEitherPartIsPassedOn", async () => {
    backend(Response.json(invitations), new Response('{"code":"UNAUTHENTICATED"}', { status: 401 }));

    const res = await GET(new Request("http://localhost:3000/api/inbox"));

    expect(res.status).toBe(401);
  });
});
