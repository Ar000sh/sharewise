import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import LoginPage from "@/app/login/page";

describe("Sign-in page", () => {
  it("explainsTheHandoffAndKeepsTheDestinationForSignInAndRegistration", async () => {
    render(await LoginPage({ searchParams: Promise.resolve({ next: "/groups/trip" }) }));

    expect(screen.getByText(/use your ShareWise username or email and password there/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sign in" })).toHaveAttribute("href", "/api/auth/login?returnTo=%2Fgroups%2Ftrip");
    expect(screen.getByRole("link", { name: "Create an account" })).toHaveAttribute("href", "/api/auth/login?register=1&returnTo=%2Fgroups%2Ftrip");
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument();
  });

  it.each([
    ["cancelled", /sign-in was cancelled/i],
    ["expired", /sign-in attempt expired or could not be verified/i],
  ])("explainsTheRetryReason %s", async (reason, message) => {
    render(await LoginPage({ searchParams: Promise.resolve({ reason }) }));
    expect(screen.getByRole("alert")).toHaveTextContent(message);
  });
});
