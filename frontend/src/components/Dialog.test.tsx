import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Dialog } from "@/components/Dialog";

describe("Dialog", () => {
  it("opensAsAModalNamedByItsTitle", () => {
    // when
    render(
      <Dialog title="Report a payment" onClose={vi.fn()}>
        <p>Body</p>
      </Dialog>,
    );

    // then
    const dialog = screen.getByRole("dialog", { name: "Report a payment" });
    expect(dialog).toHaveAttribute("open");
    expect(dialog).toHaveTextContent("Body");
  });

  it("escapeAsksTheParentToCloseIt", () => {
    // given
    const onClose = vi.fn();
    render(
      <Dialog title="Waive a debt" onClose={onClose}>
        <p>Body</p>
      </Dialog>,
    );

    // when the browser reports Escape (the dialog's cancel event)
    fireEvent(screen.getByRole("dialog"), new Event("cancel", { cancelable: true }));

    // then the parent decides; the dialog does not close itself behind React's back
    expect(onClose).toHaveBeenCalledOnce();
    expect(screen.getByRole("dialog")).toHaveAttribute("open");
  });
});
