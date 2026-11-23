import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
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

  it("offersNoTryAgainWithoutARetry", () => {
    // when
    render(<ErrorBanner error="Please enter a group name." />);

    // then
    expect(screen.queryByRole("button", { name: "Try again" })).not.toBeInTheDocument();
  });

  it("tryAgainLoadsAgain", async () => {
    // given
    const retry = vi.fn();
    render(<ErrorBanner error={new ApiError(0, "NETWORK_ERROR")} onRetry={retry} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(retry).toHaveBeenCalledOnce();
  });
});
