import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api";
import { messageFor } from "@/lib/errors";

describe("messageFor", () => {
  it("mapsKnownCodesToReadableMessages", () => {
    const err = new ApiError(422, "PAYER_NOT_IN_GROUP", "raw detail");
    expect(messageFor(err)).toBe("The payer is not a member of this group.");
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
