import { centsToAmount } from "@/lib/money";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api";
import { ExpenseForm } from "@/components/ExpenseForm";
import { ExpenseList } from "@/components/ExpenseList";

vi.mock("@/lib/api", async (orig) => ({ ...(await orig<typeof import("@/lib/api")>()), api: vi.fn() }));

const apiMock = vi.mocked(api);
afterEach(() => vi.resetAllMocks());

const members = [
  { userId: "u1", displayName: "Alice", role: "ADMIN" as const },
  { userId: "u2", displayName: "Bob", role: "MEMBER" as const },
  { userId: "u3", displayName: "Carol", role: "MEMBER" as const },
];

function renderForm(onRecorded = vi.fn()) {
  render(<ExpenseForm groupId="g1" members={members} currentUserId="u2" onRecorded={onRecorded} />);
  return onRecorded;
}

async function fill(title: string, amount: string) {
  await userEvent.type(screen.getByLabelText(/title/i), title);
  await userEvent.type(screen.getByLabelText(/amount/i), amount);
}

function postedBody() {
  const [, init] = apiMock.mock.calls[0];
  return JSON.parse(String(init?.body));
}

describe("ExpenseForm", () => {
  it("formDefaultsPayerToCurrentUserAndParticipantsToAll", () => {
    renderForm();

    expect(screen.getByLabelText(/paid by/i)).toHaveValue("u2");
    for (const name of ["Alice", "Bob", "Carol"]) {
      expect(screen.getByRole("checkbox", { name })).toBeChecked();
    }
  });

  it.each([
    ["19.99", 1999],
    ["19,99", 1999],
    ["0,10", 10],
  ])("convertsEuroInputToCentsWithoutFloatErrors (%s)", async (typed, cents) => {
    apiMock.mockResolvedValue({});
    renderForm();

    await fill("Lunch", typed);
    await userEvent.click(screen.getByRole("button", { name: /add expense/i }));

    expect(postedBody()).toMatchObject({ totalAmount: centsToAmount(cents), splitStrategy: "EQUAL" });
  });

  it("aSecondClickWhileTheExpenseIsBeingSentDoesNotRecordItTwice", async () => {
    // given a request that is still in flight
    let finish: (value: unknown) => void = () => {};
    apiMock.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    renderForm();
    await fill("Groceries", "12");
    const button = screen.getByRole("button", { name: /add expense/i });

    // when the button is clicked twice
    await userEvent.click(button);
    await userEvent.click(button);

    // then only one request went out and the button is disabled meanwhile
    expect(apiMock).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();
    finish({});
    await vi.waitFor(() => expect(button).toBeEnabled());
  });

  it("submitPostsExpenseAndRefreshesList", async () => {
    apiMock.mockResolvedValue({});
    const onRecorded = renderForm();

    await fill("Hotel", "900");
    await userEvent.click(screen.getByRole("checkbox", { name: "Carol" }));
    await userEvent.click(screen.getByRole("button", { name: /add expense/i }));

    expect(apiMock.mock.calls[0][0]).toBe("/groups/g1/expenses");
    expect(postedBody()).toMatchObject({
      title: "Hotel",
      totalAmount: "900.00",
      paidBy: "u2",
      participantIds: ["u1", "u2"],
      date: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
    });
    expect(onRecorded).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText(/title/i)).toHaveValue("");
  });

  it("showsSharePreviewPerParticipant", async () => {
    renderForm();

    await fill("Taxi", "10");

    const preview = screen.getByRole("list", { name: /shares/i });
    const rows = within(preview).getAllByRole("listitem");
    expect(rows.map((r) => r.textContent)).toEqual([
      "Alice3,34 €",
      "Bob3,33 €",
      "Carol3,33 €",
    ]);
  });

  it("serverErrorCodeShownAsMessage", async () => {
    apiMock.mockRejectedValue(new ApiError(422, "PAYER_NOT_IN_GROUP", "raw"));
    const onRecorded = renderForm();

    await fill("Taxi", "10");
    await userEvent.click(screen.getByRole("button", { name: /add expense/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The payer is not a member of this group.");
    expect(onRecorded).not.toHaveBeenCalled();
  });

  it("rejectsInvalidAmountWithoutCallingTheServer", async () => {
    renderForm();

    await fill("Taxi", "1.000,50");
    await userEvent.click(screen.getByRole("button", { name: /add expense/i }));

    expect(screen.getByRole("alert")).toHaveTextContent(/valid amount/i);
    expect(apiMock).not.toHaveBeenCalled();
  });
});

describe("ExpenseList", () => {
  it("showsExpensesWithPayerAndAmount", () => {
    render(
      <ExpenseList
        members={members}
        expenses={[
          {
            id: "e1",
            title: "Dinner",
            totalAmount: "180.00",
            date: "2027-06-02",
            paidBy: "u1",
            recordedBy: "u1",
            splitStrategy: "EQUAL",
            status: "ACTIVE",
            shares: [],
          },
        ]}
      />,
    );

    const item = screen.getByRole("listitem");
    expect(item).toHaveTextContent("Dinner");
    expect(item).toHaveTextContent("180,00 €");
    expect(item).toHaveTextContent("Alice");
    expect(item).toHaveTextContent("2027-06-02");
  });

  it("showsEmptyState", () => {
    render(<ExpenseList members={members} expenses={[]} />);

    expect(screen.getByText(/no expenses yet/i)).toBeInTheDocument();
  });
});
