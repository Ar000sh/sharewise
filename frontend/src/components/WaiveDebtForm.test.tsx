import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { SettleUpTab } from "@/components/SettleUpTab";
import { CurrentUserProvider } from "@/lib/currentUser";
import { renderWithQueryClient } from "@/lib/renderWithQueryClient";
import { markGroupChanged } from "@/lib/useGroupChange";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

const ALICE = "u1";
const BOB = "u2";
const CAROL = "u3";
const DAVID = "u4";

type Edge = {
  fromDebtor: string;
  fromDebtorName: string;
  toCreditor: string;
  toCreditorName: string;
  amount: string;
  pendingAmount: string;
  payableAmount: string;
};

function edge(from: string, fromName: string, amount: string, pendingAmount = "0.00", payableAmount = amount): Edge {
  return { fromDebtor: from, fromDebtorName: fromName, toCreditor: ALICE, toCreditorName: "Alice", amount, pendingAmount, payableAmount };
}

/** Alice is owed by Carol (30,00 of it already reported), David and Bob. */
const plan = [edge(CAROL, "Carol", "270.00", "30.00", "240.00"), edge(DAVID, "David", "270.00"), edge(BOB, "Bob", "90.00")];

/** Alice signed in; the plan answers one per call (the last repeating); every POST is answered by `other`. */
function serve(plans: Edge[][], other: () => Promise<unknown> = async () => ({})) {
  let planCalls = 0;
  apiMock.mockImplementation(async (path: string, init?: RequestInit) => {
    if (path === "/me") return { userId: ALICE, displayName: "Alice" };
    if (path === "/groups/g1/settlement-plan") return plans[Math.min(planCalls++, plans.length - 1)];
    if (path === "/inbox") return { invitations: [], notifications: { unreadCount: 0, items: [] }, payments: [] };
    if (path === "/groups/g1/settlement-payments" && !init) return [];
    return other();
  });
}

function renderTab() {
  return renderWithQueryClient(
    <CurrentUserProvider>
      <SettleUpTab groupId="g1" />
    </CurrentUserProvider>,
  );
}

function waiveCalls() {
  return apiMock.mock.calls.filter(([path]) => path === "/groups/g1/debt-waivers");
}

/** Opens the waiver dialog of one row ("Waive debt" on the row of `name`). */
async function openWaiver(name: string) {
  const row = (await screen.findByText(`${name} pays you`, { exact: false })).closest("li")!;
  await userEvent.click(within(row).getByRole("button", { name: "Waive debt" }));
  return screen.getByRole("dialog", { name: "Waive a debt" });
}

describe("WaiveDebtForm", () => {
  it("waiveDebtOnARowOpensADialogAboutThatPersonWithWhatCanBeWaivedNow", async () => {
    // given
    serve([plan]);
    renderTab();

    // when
    const dialog = await openWaiver("Carol");

    // then it is about Carol only, starting at what is not reserved by her reported payment
    expect(dialog).toHaveTextContent("Carol owes you 240,00 €");
    expect(within(dialog).getByLabelText("Waived amount (EUR)")).toHaveValue("240.00");
    expect(within(dialog).queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("waivingSendsTheRequestWithoutAnEmptyReasonClosesTheDialogAndSaysSo", async () => {
    // given
    serve([plan]);
    renderTab();
    const dialog = await openWaiver("Bob");

    // when
    await userEvent.click(within(dialog).getByRole("button", { name: "Waive debt" }));

    // then
    expect(JSON.parse(String(waiveCalls()[0][1]?.body))).toEqual({ fromDebtor: BOB, amount: "90.00" });
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(await screen.findByRole("status")).toHaveTextContent("Debt waived.");
  });

  it("aGivenReasonAndAPartialAmountAreSent", async () => {
    // given
    serve([plan]);
    renderTab();
    const dialog = await openWaiver("David");

    // when
    const amount = within(dialog).getByLabelText("Waived amount (EUR)");
    await userEvent.clear(amount);
    await userEvent.type(amount, "36,00");
    await userEvent.type(within(dialog).getByLabelText("Reason (optional)"), "Birthday");
    await userEvent.click(within(dialog).getByRole("button", { name: "Waive debt" }));

    // then
    expect(JSON.parse(String(waiveCalls()[0][1]?.body))).toEqual({ fromDebtor: DAVID, amount: "36.00", note: "Birthday" });
  });

  it("moreThanIsOpenIsRefusedWithoutSending", async () => {
    // given
    serve([plan]);
    renderTab();
    const dialog = await openWaiver("Bob");

    // when
    const amount = within(dialog).getByLabelText("Waived amount (EUR)");
    await userEvent.clear(amount);
    await userEvent.type(amount, "90,01");
    await userEvent.click(within(dialog).getByRole("button", { name: "Waive debt" }));

    // then
    expect(within(dialog).getByRole("alert")).toHaveTextContent("You can waive up to 90,00 € here.");
    expect(waiveCalls()).toHaveLength(0);
  });

  it("aServerRefusalShowsTheWaiverWording", async () => {
    // given
    serve([plan], async () => {
      throw new ApiError(422, "PAYMENT_EXCEEDS_OPEN_DEBT", "too much");
    });
    renderTab();
    const dialog = await openWaiver("Bob");

    // when
    await userEvent.click(within(dialog).getByRole("button", { name: "Waive debt" }));

    // then
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("You can only waive up to what is still open.");
  });

  describe("whenThePlanChangesWhileTheDialogIsOpen", () => {
    it("aDebtorWhoseDebtDisappearsGetsNoWaiverAndNobodyElseIsOffered", async () => {
      // given Alice opened the waiver for Bob and typed an amount, then somebody's payment made Bob's debt disappear (F1)
      serve([plan, plan.slice(0, 2)]);
      const { client } = renderTab();
      const dialog = await openWaiver("Bob");
      await userEvent.type(within(dialog).getByLabelText("Reason (optional)"), "Gift");

      // when the plan is asked again
      act(() => markGroupChanged(client, "g1"));

      // then the dialog says so, offers nothing to send and never switches to Carol or David
      expect(await within(dialog).findByText("Nothing is open with Bob any more.")).toBeInTheDocument();
      expect(within(dialog).queryByRole("button", { name: "Waive debt" })).not.toBeInTheDocument();
      expect(waiveCalls()).toHaveLength(0);
    });

    it("theDialogStaysAboutItsPersonWhenThePlanIsReordered", async () => {
      // given Alice opened the waiver for Carol, then the plan comes back in another order with David first
      serve([plan, [plan[1], plan[0], plan[2]]]);
      const { client } = renderTab();
      const dialog = await openWaiver("Carol");

      // when
      act(() => markGroupChanged(client, "g1"));
      await waitFor(() => expect(screen.getAllByText(/pays you/)[0]).toHaveTextContent("David"));

      // then it is still about Carol
      expect(dialog).toHaveTextContent("Carol owes you 240,00 €");
    });
  });
});
