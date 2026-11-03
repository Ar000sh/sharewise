import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api";
import { messageFor } from "@/lib/errors";

describe("messageFor", () => {
  it("mapsKnownCodesToReadableMessages", () => {
    const err = new ApiError(422, "PAYER_NOT_IN_GROUP", "raw detail");
    expect(messageFor(err)).toBe("The payer is not a member of this group.");
  });

  it.each([
    ["ACCOUNT_NOT_FOUND", "No account found. The person must have signed in to ShareWise once."],
    ["ALREADY_INVITED", "This person already has an open invitation."],
    ["NOTIFICATION_NOT_FOUND", "This entry no longer exists."],
    ["INVITATION_NOT_FOUND", "This invitation is no longer valid."],
    ["INVITATION_EXPIRED", "This invitation has expired. Ask for a new one."],
  ])("hasAReadableMessageFor%s", (code, message) => {
    expect(messageFor(new ApiError(404, code, "raw detail"))).toBe(message);
  });

  it("fallsBackToServerDetailForUnknownCodes", () => {
    expect(messageFor(new ApiError(500, "SOMETHING_NEW", "Boom happened"))).toBe("Boom happened");
  });

  it("fallsBackToGenericMessageWithoutDetail", () => {
    expect(messageFor(new ApiError(500, "UNKNOWN"))).toBe("Something went wrong.");
  });

  it("handlesNonApiErrors", () => {
    expect(messageFor(new Error("x"))).toBe("Something went wrong.");
  });
});
