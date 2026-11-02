import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api";
import { messageFor } from "@/lib/errors";

describe("messageFor", () => {
  it("mapsKnownCodesToReadableMessages", () => {
    const err = new ApiError(422, "PAYER_NOT_IN_GROUP", "raw detail");
    expect(messageFor(err)).toBe("The payer is not a member of this group.");
  });

  it.each([
    ["PAYMENT_EXCEEDS_OPEN_DEBT", "You can only pay up to what you owe."],
    ["SETTLEMENT_PAYMENT_NOT_FOUND", "This settlement payment does not exist."],
    ["PAYMENT_NOT_PENDING", "This settlement payment was already answered."],
    ["NOT_THE_CREDITOR", "Only the person who is owed can do this."],
  ])("mapsTheSettlementCode%sToItsMessage", (code, message) => {
    expect(messageFor(new ApiError(422, code, "raw detail"))).toBe(message);
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
