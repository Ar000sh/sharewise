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

/** Equal split in cents; the first participants get the remainder, like the backend. */
export function equalShares(totalCents: number, count: number): number[] {
  const base = Math.floor(totalCents / count);
  const remainder = totalCents - base * count;
  return Array.from({ length: count }, (_, i) => base + (i < remainder ? 1 : 0));
}

/**
 * Parses a percentage typed by a user into hundredths of a percent ("33,33" and "33.33" give 3333) without floating
 * point. One decimal separator, at most two decimals; zero, signs and anything else give null.
 */
export function percentToHundredths(input: string): number | null {
  const match = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(input.trim());
  if (!match) return null;
  const hundredths = Number(match[1]) * 100 + Number((match[2] ?? "").padEnd(2, "0"));
  return Number.isSafeInteger(hundredths) && hundredths > 0 ? hundredths : null;
}

/**
 * Percentage split in cents, the same rule as the backend (design D4): every share is rounded down, the cents that are
 * left over go to the largest dropped fractions, ties to the earlier participant. Integers only.
 */
export function percentageShares(totalCents: number, hundredths: number[]): number[] {
  const exact = hundredths.map((h) => totalCents * h);
  const shares = exact.map((e) => Math.floor(e / 10000));
  const leftover = totalCents - shares.reduce((sum, s) => sum + s, 0);
  const byLargestRest = exact
    .map((e, i) => ({ i, rest: e % 10000 }))
    .sort((a, b) => b.rest - a.rest || a.i - b.i);
  for (const { i } of byLargestRest.slice(0, leftover)) shares[i] += 1;
  return shares;
}

/** Formats hundredths of a percent as "33,33 %". */
export function formatPercent(hundredths: number): string {
  return `${Math.trunc(hundredths / 100)},${String(hundredths % 100).padStart(2, "0")} %`;
}

/** Formats cents as "1.234,56 €" (German style), with a leading "-" when negative. */
export function formatEuro(cents: number): string {
  const abs = Math.abs(cents);
  const euros = String(Math.trunc(abs / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  const rest = String(abs % 100).padStart(2, "0");
  return `${cents < 0 ? "-" : ""}${euros},${rest} €`;
}

/** Formats cents with a sign where the direction matters: "+540,00 €", "-180,00 €", "0,00 €". */
export function formatSignedEuro(cents: number): string {
  return cents > 0 ? `+${formatEuro(cents)}` : formatEuro(cents);
}

/**
 * What an expense means for one person, in cents: what they paid (the whole amount, if they are the payer) minus their
 * own share. Built only from the server's shares, never by splitting again, so remainder cents (R13) are exactly the
 * ones the ledger booked (design 2026-11-23, F5).
 */
export function yourPartCents(
  expense: { totalAmount: string; paidBy: string; shares: { participantId: string; owedShare: string }[] },
  userId: string,
): number {
  const paid = expense.paidBy === userId ? amountToCents(expense.totalAmount) : 0;
  const share = expense.shares.find((s) => s.participantId === userId);
  return paid - (share ? amountToCents(share.owedShare) : 0);
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
