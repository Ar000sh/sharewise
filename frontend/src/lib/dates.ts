/**
 * Formats an API date ("2027-07-19", a calendar day without a time) as a day heading: "Monday 19 Jul". The day is read
 * as UTC and shown in UTC, so it never moves to the day before or after in the viewer's time zone (design 2026-11-23,
 * F7): `new Date("2027-07-19")` alone is midnight UTC, which is still the 18th west of Greenwich.
 */
export function formatDay(date: string): string {
  const [year, month, day] = date.split("-").map(Number);
  const parts = new Intl.DateTimeFormat("en-GB", {
    weekday: "long",
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  }).formatToParts(new Date(Date.UTC(year, month - 1, day)));
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? "";
  return `${part("weekday")} ${part("day")} ${part("month")}`;
}
