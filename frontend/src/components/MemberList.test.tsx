import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MemberList } from "@/components/MemberList";

describe("MemberList", () => {
  it("showsMembersWithRoles", () => {
    render(
      <MemberList
        currentUserId="u2"
        members={[
          { userId: "u1", displayName: "Alice", role: "ADMIN" },
          { userId: "u2", displayName: "Bob", role: "MEMBER" },
        ]}
      />,
    );

    const items = screen.getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("Alice");
    expect(items[0]).toHaveTextContent("Admin");
    expect(items[1]).toHaveTextContent("Bob");
    expect(items[1]).toHaveTextContent("(you)");
    expect(items[1]).not.toHaveTextContent("Admin");
  });
});
