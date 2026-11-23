import "@testing-library/jest-dom/vitest";
import { configure } from "@testing-library/react";

// findBy* and waitFor give up after 1 s by default. On a busy machine (the push gate runs the frontend checks next to
// the backend tests, CI runners are small) a component that needs one render pass after a mocked fetch can take longer
// and a correct test fails. They retry and return as soon as the element is there, so a higher limit costs nothing when
// the tests are quick.
configure({ asyncUtilTimeout: 5000 });

// jsdom has the <dialog> element but not its showModal() and close(). This stand-in only switches `open`, so a component
// on the native dialog can be tested; the focus trap and the dimmed page are the browser's and the e2e journey uses them.
if (typeof HTMLDialogElement !== "undefined" && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.open = false;
  };
}
