import { describe, expect, it } from "vitest";
import { amountToCents, centsToAmount, eurosToCents, formatEuro } from "@/lib/money";

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

describe("API amounts", () => {
  it.each([
    ["900.00", 90000],
    ["-90.00", -9000],
    ["0.00", 0],
    ["0.07", 7],
    ["-0.07", -7],
    ["1234.56", 123456],
  ])("amountToCents(%s) is %i and back", (amount, cents) => {
    expect(amountToCents(amount)).toBe(cents);
    expect(centsToAmount(cents)).toBe(amount === "-0.00" ? "0.00" : amount);
  });

  it.each(["900", "900.0", "900,00", "1e3", "", "abc"])("amountToCents rejects %j", (amount) => {
    expect(() => amountToCents(amount)).toThrow();
  });
});
