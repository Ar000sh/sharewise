import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Avatar, personColour } from "@/components/Avatar";

describe("Avatar", () => {
  it("showsTheFirstLetterOfTheNameInUpperCase", () => {
    // when
    const { container } = render(<Avatar userId="u1" name="alice" />);

    // then
    expect(container).toHaveTextContent("A");
  });

  it("isHiddenFromScreenReadersBecauseTheNameIsWrittenNextToIt", () => {
    // when
    const { container } = render(<Avatar userId="u1" name="Alice" />);

    // then
    expect(container.firstChild).toHaveAttribute("aria-hidden", "true");
  });

  it("aPersonKeepsTheirColourAndPeopleGetDifferentOnes", () => {
    // given
    const ids = ["7f1c2d3e-0000-4000-8000-000000000001", "7f1c2d3e-0000-4000-8000-000000000002", "b0b"];

    // when
    const colours = ids.map(personColour);

    // then
    expect(ids.map(personColour)).toEqual(colours);
    expect(new Set(colours).size).toBeGreaterThan(1);
  });
});
