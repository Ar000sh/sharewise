import { afterEach, describe, expect, it, vi } from "vitest";
import { formatDay } from "@/lib/dates";

afterEach(() => vi.unstubAllEnvs());

describe("formatDay", () => {
  it("namesTheWeekdayTheDayAndTheMonth", () => {
    expect(formatDay("2027-07-19")).toBe("Monday 19 Jul");
    expect(formatDay("2027-07-09")).toBe("Friday 9 Jul");
  });

  it("staysOnTheSameDayWestOfGreenwich", () => {
    // given a viewer in New York, where midnight UTC is still the evening before
    vi.stubEnv("TZ", "America/New_York");

    // when / then
    expect(formatDay("2027-07-19")).toBe("Monday 19 Jul");
  });
});
