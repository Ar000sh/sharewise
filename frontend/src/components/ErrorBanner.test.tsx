import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api";
import { ErrorBanner } from "@/components/ErrorBanner";

describe("ErrorBanner", () => {
  it("rendersNothingWithoutAnError", () => {
    const { container } = render(<ErrorBanner error={undefined} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("showsTheReadableMessageOfAnApiError", () => {
    render(<ErrorBanner error={new ApiError(403, "NOT_A_MEMBER")} />);

    expect(screen.getByRole("alert")).toHaveTextContent("You are not a member of this group.");
  });

  it("showsAPlainValidationMessageAsItIs", () => {
    render(<ErrorBanner error="Please enter a group name." />);

    expect(screen.getByRole("alert")).toHaveTextContent("Please enter a group name.");
  });
});
