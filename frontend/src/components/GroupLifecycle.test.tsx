import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { GroupLifecycle } from "@/components/GroupLifecycle";
import { api, ApiError } from "@/lib/api";
import { renderWithQueryClient } from "@/lib/renderWithQueryClient";

vi.mock("@/lib/api", async orig => ({ ...await orig<typeof import("@/lib/api")>(), api: vi.fn() }));
const mock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

function serve(status: "OPEN" | "CLOSED" = "OPEN", reject = false) {
  mock.mockImplementation(async (path, init) => {
    if (init?.method === "POST") {
      if (reject) throw new ApiError(422, "BALANCES_NOT_ZERO");
      return { status: status === "OPEN" ? "CLOSED" : "OPEN", revision: 1, archiveCount: 1, history: [] };
    }
    if (path.endsWith("/archives") || path.endsWith("/balances") || path.endsWith("/settlement-payments")) return [];
    return { status, revision: 0, archiveCount: 0, history: [] };
  });
}

it("warns before closing and sends the revision only after confirmation", async () => {
  serve(); const user = userEvent.setup();
  renderWithQueryClient(<GroupLifecycle groupId="g1" isAdmin />);
  await user.click(await screen.findByRole("button", { name: "Close group" }));
  expect(screen.getByText(/Only an admin can reopen/)).toBeInTheDocument();
  expect(mock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false);
  await user.click(screen.getByRole("button", { name: "Confirm closure" }));
  expect(mock).toHaveBeenCalledWith("/groups/g1/close", expect.objectContaining({ body: JSON.stringify({ expectedRevision: 0, reason: "" }) }));
});

it("requires a reopening reason and explains that archives are preserved", async () => {
  serve("CLOSED"); const user = userEvent.setup();
  renderWithQueryClient(<GroupLifecycle groupId="g1" isAdmin />);
  await user.click(await screen.findByRole("button", { name: "Reopen for corrections" }));
  expect(screen.getByRole("button", { name: "Confirm reopening" })).toBeDisabled();
  await user.type(screen.getByLabelText("Reason for reopening"), "Wrong receipt");
  await user.click(screen.getByRole("button", { name: "Confirm reopening" }));
  expect(mock).toHaveBeenCalledWith("/groups/g1/reopen", expect.objectContaining({ body: JSON.stringify({ expectedRevision: 0, reason: "Wrong receipt" }) }));
});

it("does not offer lifecycle writes to a member", async () => {
  serve(); renderWithQueryClient(<GroupLifecycle groupId="g1" isAdmin={false} />);
  await screen.findByText(/0 archive versions/);
  expect(screen.queryByRole("button", { name: "Close group" })).not.toBeInTheDocument();
});

it("keeps the confirmation open and explains outstanding balances", async () => {
  serve("OPEN", true); const user = userEvent.setup();
  renderWithQueryClient(<GroupLifecycle groupId="g1" isAdmin />);
  await user.click(await screen.findByRole("button", { name: "Close group" }));
  await user.click(screen.getByRole("button", { name: "Confirm closure" }));
  expect(await screen.findByText("Every balance must be zero before closing the group.")).toBeInTheDocument();
});
