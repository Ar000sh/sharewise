import { amountToCents, centsToAmount } from "@/lib/money";
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
  await userEvent.type(screen.getByLabelText(/what was it for/i), title);
  await userEvent.type(screen.getByLabelText("Amount (EUR)"), amount);
}

async function choose(mode: "Equally" | "Exact amounts" | "Percentages") {
  await userEvent.click(screen.getByRole("radio", { name: mode }));
}

async function type(label: string, value: string) {
  await userEvent.type(screen.getByLabelText(label), value);
}

function postedBody() {
  const [, init] = apiMock.mock.calls[0];
  return JSON.parse(String(init?.body));
}

describe("ExpenseForm", () => {
  it("formDefaultsPayerAndParticipantsToTheCurrentUserOnly", () => {
    renderForm();

    expect(screen.getByLabelText(/paid by/i)).toHaveValue("u2");
    expect(screen.getByRole("checkbox", { name: "Bob" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Alice" })).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Carol" })).not.toBeChecked();
  });

  it("selectEveryoneTicksAllMembers", async () => {
    // given
    renderForm();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Select everyone" }));

    // then
    for (const name of ["Alice", "Bob", "Carol"]) {
      expect(screen.getByRole("checkbox", { name })).toBeChecked();
    }
  });

  it("aMemberWhoJoinsWhileTheFormIsOpenIsNotInTheSplit", async () => {
    // given Bob ticked everybody and typed an expense
    const { rerender } = render(<ExpenseForm groupId="g1" members={members} currentUserId="u2" onRecorded={vi.fn()} />);
    await userEvent.click(screen.getByRole("button", { name: "Select everyone" }));
    await fill("Hotel", "900");

    // when Dave accepts an invitation and the group page hands the form the new member list
    const dave = { userId: "u4", displayName: "Dave", role: "MEMBER" as const };
    rerender(<ExpenseForm groupId="g1" members={[...members, dave]} currentUserId="u2" onRecorded={vi.fn()} />);

    // then Dave is listed but not ticked, the split is still over the three, and what Bob typed is still there
    expect(screen.getByRole("checkbox", { name: "Dave" })).not.toBeChecked();
    const rows = within(screen.getByRole("list", { name: /shares/i })).getAllByRole("listitem");
    expect(rows.map((r) => r.textContent)).toEqual(["Alice300,00 €", "Bob300,00 €", "Carol300,00 €"]);
    expect(screen.getByLabelText(/what was it for/i)).toHaveValue("Hotel");
  });

  it("theRecorderCannotUntickThemselvesButOthersCanBeUnticked", async () => {
    // given Bob (u2) is signed in and ticked everybody
    renderForm();
    await userEvent.click(screen.getByRole("button", { name: "Select everyone" }));

    // when everybody else is unticked and Bob tries to untick himself
    await userEvent.click(screen.getByRole("checkbox", { name: "Alice" }));
    await userEvent.click(screen.getByRole("checkbox", { name: "Carol" }));
    await userEvent.click(screen.getByRole("checkbox", { name: "Bob" }));

    // then Bob stays ticked and disabled, the others are unticked, and Bob alone is in the preview
    expect(screen.getByRole("checkbox", { name: "Bob" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Bob" })).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: "Alice" })).not.toBeChecked();
    await fill("Taxi", "10");
    const rows = within(screen.getByRole("list", { name: /shares/i })).getAllByRole("listitem");
    expect(rows.map((r) => r.textContent)).toEqual(["Bob10,00 €"]);
  });

  it.each([
    ["19.99", 1999],
    ["19,99", 1999],
    ["0,10", 10],
  ])("convertsEuroInputToCentsWithoutFloatErrors (%s)", async (typed, cents) => {
    apiMock.mockResolvedValue({});
    renderForm();

    await fill("Lunch", typed);
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));

    expect(postedBody()).toMatchObject({ totalAmount: centsToAmount(cents), splitStrategy: "EQUAL" });
  });

  it("aSecondClickWhileTheExpenseIsBeingSentDoesNotRecordItTwice", async () => {
    // given a request that is still in flight
    let finish: (value: unknown) => void = () => {};
    apiMock.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    renderForm();
    await fill("Groceries", "12");
    const button = screen.getByRole("button", { name: /save expense/i });

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
    await userEvent.click(screen.getByRole("checkbox", { name: "Alice" }));
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));

    expect(apiMock.mock.calls[0][0]).toBe("/groups/g1/expenses");
    expect(postedBody()).toMatchObject({
      title: "Hotel",
      totalAmount: "900.00",
      paidBy: "u2",
      splitStrategy: "EQUAL",
      date: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
    });
    // toEqual, not toMatchObject: an EQUAL entry must carry no value at all
    expect(postedBody().participants).toEqual([{ userId: "u1" }, { userId: "u2" }]);
    expect(onRecorded).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText(/what was it for/i)).toHaveValue("");
  });

  it("showsSharePreviewPerParticipant", async () => {
    renderForm();
    await userEvent.click(screen.getByRole("button", { name: "Select everyone" }));

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
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The payer is not a member of this group.");
    expect(onRecorded).not.toHaveBeenCalled();
  });

  it("anExactSplitPostsTheTypedAmounts", async () => {
    // given Alice and Bob share a 10 euro expense
    apiMock.mockResolvedValue({});
    renderForm();
    await userEvent.click(screen.getByRole("checkbox", { name: "Alice" }));
    await fill("Dinner", "10");
    await choose("Exact amounts");

    // when they type 6 and 4
    await type("Amount for Alice", "6");
    await type("Amount for Bob", "4");

    // then nothing is left and the typed amounts are sent
    expect(screen.getByText("Remaining: 0,00 €")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));
    const body = postedBody();
    expect(body.splitStrategy).toBe("EXACT");
    expect(body.participants).toEqual([
      { userId: "u1", value: "6.00" },
      { userId: "u2", value: "4.00" },
    ]);
  });

  it("aPercentageSplitPreviewsTheSharesAndPostsThePercentages", async () => {
    // given three members share 10 euro
    apiMock.mockResolvedValue({});
    renderForm();
    await userEvent.click(screen.getByRole("button", { name: "Select everyone" }));
    await fill("Dinner", "10");
    await choose("Percentages");

    // when they type 33,33 / 33,33 / 33,34
    await type("Percentage for Alice", "33,33");
    await type("Percentage for Bob", "33,33");
    await type("Percentage for Carol", "33,34");

    // then the shares are previewed, the sum is 100 % and the percentages are sent
    const rows = within(screen.getByRole("list", { name: /shares/i })).getAllByRole("listitem");
    expect(rows.map((r) => r.textContent)).toEqual(["Alice3,33 €", "Bob3,33 €", "Carol3,34 €"]);
    expect(screen.getByText("Sum: 100,00 %")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));
    const body = postedBody();
    expect(body.splitStrategy).toBe("PERCENTAGE");
    expect(body.participants.map((p: { value: string }) => p.value)).toEqual(["33.33", "33.33", "33.34"]);
  });

  it("aPercentageSplitThatIsNot100PercentShowsNoEuroPreview", async () => {
    // given
    renderForm();
    await userEvent.click(screen.getByRole("checkbox", { name: "Alice" }));
    await fill("Dinner", "10");
    await choose("Percentages");

    // when
    await type("Percentage for Alice", "60");
    await type("Percentage for Bob", "30");

    // then
    expect(screen.getByText("Sum: 90,00 %")).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: /shares/i })).not.toBeInTheDocument();
  });

  it("amountsThatDoNotAddUpShowAMessageAndSendNothing", async () => {
    // given exact amounts 6 and 3 for 10 euro
    renderForm();
    await userEvent.click(screen.getByRole("checkbox", { name: "Alice" }));
    await fill("Dinner", "10");
    await choose("Exact amounts");
    await type("Amount for Alice", "6");
    await type("Amount for Bob", "3");

    // when
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent("The amounts must add up to 10,00 €.");
    expect(apiMock).not.toHaveBeenCalled();
  });

  it("percentagesThatDoNotAddUpShowAMessageAndSendNothing", async () => {
    // given
    renderForm();
    await userEvent.click(screen.getByRole("checkbox", { name: "Alice" }));
    await fill("Dinner", "10");
    await choose("Percentages");
    await type("Percentage for Alice", "60");
    await type("Percentage for Bob", "30");

    // when
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent("The percentages must add up to 100 %.");
    expect(apiMock).not.toHaveBeenCalled();
  });

  it("aTypedValueThatIsNotANumberShowsAMessageAndSendsNothing", async () => {
    // given
    renderForm();
    await fill("Dinner", "10");
    await choose("Exact amounts");
    await type("Amount for Bob", "1.000,50");

    // when
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent("Please enter a valid amount for Bob.");
    expect(apiMock).not.toHaveBeenCalled();
  });

  it("aMalformedPercentageShowsAMessageAndSendsNothing", async () => {
    // given Bob's percentage box holds three decimals (an empty box is stopped by the browser: required)
    renderForm();
    await fill("Dinner", "10");
    await choose("Percentages");
    await type("Percentage for Bob", "33,333");

    // when
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent("Please enter a valid percentage for Bob.");
    expect(apiMock).not.toHaveBeenCalled();
  });

  it("anUntickedMemberNeitherCountsNorIsSent", async () => {
    // given Alice is ticked and both typed an amount
    apiMock.mockResolvedValue({});
    renderForm();
    await userEvent.click(screen.getByRole("checkbox", { name: "Alice" }));
    await fill("Dinner", "10");
    await choose("Exact amounts");
    await type("Amount for Alice", "4");
    await type("Amount for Bob", "10");
    expect(screen.getByText("Remaining: -4,00 €")).toBeInTheDocument();

    // when Alice is unticked
    await userEvent.click(screen.getByRole("checkbox", { name: "Alice" }));

    // then only Bob counts and only Bob is sent
    expect(screen.getByText("Remaining: 0,00 €")).toBeInTheDocument();
    expect(screen.queryByLabelText("Amount for Alice")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));
    expect(postedBody().participants).toEqual([{ userId: "u2", value: "10.00" }]);
  });

  it("switchingTheModeClearsTheTypedValues", async () => {
    // given
    renderForm();
    await choose("Exact amounts");
    await type("Amount for Bob", "6");

    // when
    await choose("Percentages");

    // then
    expect(screen.getByLabelText("Percentage for Bob")).toHaveValue("");
    await choose("Exact amounts");
    expect(screen.getByLabelText("Amount for Bob")).toHaveValue("");
  });

  it.each([
    ["SHARES_NOT_TOTAL", "The amounts must add up to the total."],
    ["PERCENTAGES_NOT_100", "The percentages must add up to 100 %."],
  ])("theSplitSumErrorCode%sIsShownAsMessage", async (code, message) => {
    apiMock.mockRejectedValue(new ApiError(422, code, "raw"));
    renderForm();

    await fill("Taxi", "10");
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
  });

  it("rejectsInvalidAmountWithoutCallingTheServer", async () => {
    renderForm();

    await fill("Taxi", "1.000,50");
    await userEvent.click(screen.getByRole("button", { name: /save expense/i }));

    expect(screen.getByRole("alert")).toHaveTextContent(/valid amount/i);
    expect(apiMock).not.toHaveBeenCalled();
  });
});

describe("ExpenseList", () => {
  /** An expense split equally between Alice, Bob and Carol; the server's shares give the remainder cent to Alice. */
  function expense(id: string, title: string, totalAmount: string, date: string, paidBy: string) {
    const third = amountToCents(totalAmount) / 3;
    const base = Math.floor(third);
    const first = amountToCents(totalAmount) - 2 * base;
    return {
      id,
      title,
      totalAmount,
      date,
      paidBy,
      recordedBy: paidBy,
      splitStrategy: "EQUAL" as const,
      status: "ACTIVE" as const,
      shares: [
        { participantId: "u1", owedShare: centsToAmount(first) },
        { participantId: "u2", owedShare: centsToAmount(base) },
        { participantId: "u3", owedShare: centsToAmount(base) },
      ],
    };
  }

  it("groupsTheExpensesByDayNewestFirstAsTheServerSendsThem", () => {
    // when Bob looks at three expenses on two days
    render(
      <ExpenseList
        members={members}
        currentUserId="u2"
        expenses={[
          expense("e3", "Taxi", "10.00", "2027-07-19", "u1"),
          expense("e2", "Dinner", "180.00", "2027-07-19", "u2"),
          expense("e1", "Hotel", "900.00", "2027-07-17", "u1"),
        ]}
      />,
    );

    // then each day is a section with its expenses
    const monday = screen.getByRole("region", { name: "Monday 19 Jul" });
    expect(within(monday).getAllByRole("listitem").map((li) => li.textContent)).toEqual([
      expect.stringContaining("Taxi"),
      expect.stringContaining("Dinner"),
    ]);
    expect(within(screen.getByRole("region", { name: "Saturday 17 Jul" })).getByRole("listitem")).toHaveTextContent("Hotel");
  });

  it("saysWhoPaidHowItIsSplitAndWhatItMeansForTheViewer", () => {
    // when Bob looks at a dinner he paid and a taxi Alice paid
    render(
      <ExpenseList
        members={members}
        currentUserId="u2"
        expenses={[expense("e2", "Dinner", "180.00", "2027-07-19", "u2"), expense("e3", "Taxi", "10.00", "2027-07-19", "u1")]}
      />,
    );

    // then the amounts and his part are the server's, with a sign
    const [dinner, taxi] = screen.getAllByRole("listitem");
    expect(dinner).toHaveTextContent("You paid · equally between 3");
    expect(dinner).toHaveTextContent("180,00 €");
    expect(dinner).toHaveTextContent("your part +120,00 €");
    expect(taxi).toHaveTextContent("Alice paid · equally between 3");
    expect(taxi).toHaveTextContent("your part -3,33 €");
  });

  it("namesAPayerWhoLeftAsLeft", () => {
    // when Bob looks at a taxi that David paid before he left the group
    render(
      <ExpenseList
        members={members}
        formerMembers={[{ userId: "u4", displayName: "David", role: "MEMBER" }]}
        currentUserId="u2"
        expenses={[expense("e4", "Taxi", "30.00", "2027-07-19", "u4")]}
      />,
    );

    // then (R13)
    expect(screen.getByRole("listitem")).toHaveTextContent("David (left) paid");
  });

  it("showsEmptyState", () => {
    render(<ExpenseList members={members} currentUserId="u2" expenses={[]} />);

    expect(screen.getByText(/no expenses yet/i)).toBeInTheDocument();
  });
});
