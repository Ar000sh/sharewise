import { describe, expect, it } from "vitest";
import { eurosToCents, formatEuro } from "@/lib/money";

describe("eurosToCents", () => {
  it.each([
    ["19,99", 1999],
    ["19.99", 1999],
    ["0,10", 10],
    ["0.1", 10],
    ["5", 500],
    ["5,5", 550],
    [" 12,00 ", 1200],
    ["1000", 100000],
  ])("parses %s as %i cents", (input, cents) => {
    expect(eurosToCents(input)).toBe(cents);
  });

  it.each(["", "abc", "1.000,50", "1,000.50", "-5", "0", "1,234", "12.345", "1e3", "5,"])(
    "rejects %j",
    (input) => {
      expect(eurosToCents(input)).toBeNull();
    },
  );
});

describe("formatEuro", () => {
  it("formatsCentsWithComma", () => {
    expect(formatEuro(1999)).toBe("19,99 €");
    expect(formatEuro(10)).toBe("0,10 €");
  });

  it("formatsNegativeAndThousands", () => {
    expect(formatEuro(-27000)).toBe("-270,00 €");
    expect(formatEuro(123456)).toBe("1.234,56 €");
  });

  it("formatsZero", () => {
    expect(formatEuro(0)).toBe("0,00 €");
  });
});
