/**
 * Parses a euro amount typed by a user into cents without floating point.
 * Accepts "19,99" or "19.99" (one decimal separator, at most two decimals).
 * Thousands separators ("1.000,50"), signs and zero are rejected: returns null.
 */
export function eurosToCents(input: string): number | null {
  const match = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(input.trim());
  if (!match) return null;
  const cents = Number(match[1]) * 100 + Number((match[2] ?? "").padEnd(2, "0"));
  return Number.isSafeInteger(cents) && cents > 0 ? cents : null;
}

/** Formats cents as "1.234,56 €" (German style), with a leading "-" when negative. */
export function formatEuro(cents: number): string {
  const abs = Math.abs(cents);
  const euros = String(Math.trunc(abs / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  const rest = String(abs % 100).padStart(2, "0");
  return `${cents < 0 ? "-" : ""}${euros},${rest} €`;
}

/**
 * Converts an API amount (a decimal string with two decimals, e.g. "900.00" or "-90.00") to integer cents without
 * floating point. Throws on anything else: the backend promises this format.
 */
export function amountToCents(amount: string): number {
  const match = /^(-?)(\d+)\.(\d{2})$/.exec(amount);
  if (!match) throw new Error(`Not an API amount: ${amount}`);
  const cents = Number(match[2]) * 100 + Number(match[3]);
  return match[1] === "-" ? -cents : cents;
}

/** Converts integer cents to an API amount: 90000 becomes "900.00", -9 becomes "-0.09". */
export function centsToAmount(cents: number): string {
  const abs = Math.abs(cents);
  return `${cents < 0 ? "-" : ""}${Math.trunc(abs / 100)}.${String(abs % 100).padStart(2, "0")}`;
}
