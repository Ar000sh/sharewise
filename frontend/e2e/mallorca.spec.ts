import { expect, test, type Browser, type Page } from "@playwright/test";

const PASSWORD = "correct-horse";
// Unique per run, so the test can be repeated against the same database.
const RUN = Date.now();

async function signUp(browser: Browser, name: string): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto("/register");
  await page.getByLabel("Name").fill(name);
  await page.getByLabel("Email").fill(`${name.toLowerCase()}-${RUN}@example.com`);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign up" }).click();
  await expect(page.getByText(`Signed in as ${name}`)).toBeVisible();
  return page;
}

async function addExpense(page: Page, title: string, euros: string) {
  await page.getByLabel("Title").fill(title);
  await page.getByLabel("Amount (€)").fill(euros);
  await page.getByRole("button", { name: "Add expense" }).click();
  await expect(page.getByText(title).first()).toBeVisible();
}

test("three friends sign up, share a group and see who owes what", async ({ browser }) => {
  // Alice signs up, creates the group and an invite link.
  const alice = await signUp(browser, "Alice");
  await alice.getByRole("link", { name: "Create your first group" }).click();
  await alice.getByLabel("Group name").fill("Mallorca 2027");
  await alice.getByRole("button", { name: "Create group" }).click();
  await expect(alice.getByRole("heading", { name: "Mallorca 2027" })).toBeVisible();

  await alice.getByRole("tab", { name: "Members" }).click();
  await alice.getByRole("button", { name: "Invite someone" }).click();
  const inviteLink = await alice.getByLabel("Invite link (valid for 7 days)").inputValue();

  // Bob and Carol open the link without an account: they are sent to sign in, sign up, and land on the join page.
  const others: Page[] = [];
  for (const name of ["Bob", "Carol"]) {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto(inviteLink);
    await expect(page).toHaveURL(/\/login\?next=/);
    await page.getByRole("link", { name: "Create an account" }).click();
    await page.getByLabel("Name").fill(name);
    await page.getByLabel("Email").fill(`${name.toLowerCase()}-${RUN}@example.com`);
    await page.getByLabel("Password").fill(PASSWORD);
    await page.getByRole("button", { name: "Sign up" }).click();
    await page.getByRole("button", { name: "Join group" }).click();
    await expect(page.getByRole("heading", { name: "Mallorca 2027" })).toBeVisible();
    others.push(page);
  }
  const [bob] = others;

  // Alice pays the hotel (900 €, all three), Bob pays the dinner (180 €, all three).
  await alice.reload();
  await expect(alice.getByLabel("Paid by")).toContainText("Carol");
  await addExpense(alice, "Hotel", "900");
  await bob.reload();
  await expect(bob.getByLabel("Paid by")).toContainText("Carol");
  await bob.getByLabel("Paid by").selectOption({ label: "Bob" });
  await addExpense(bob, "Dinner", "180");

  // Balances come through an event, so they may need a moment: the assertions retry.
  await alice.getByRole("tab", { name: "Balances" }).click();
  const balances = alice.getByRole("listitem").filter({ hasText: /€/ });
  await expect(balances.filter({ hasText: "Alice" })).toContainText("+540,00");
  await expect(balances.filter({ hasText: "Bob" })).toContainText("-180,00");
  await expect(balances.filter({ hasText: "Carol" })).toContainText("-360,00");
  await expect(alice.getByText("Sum: 0,00")).toBeVisible();
});

test("wrong password is refused, signed-out visitors are sent to sign in, signing in again works", async ({
  browser,
}) => {
  const page = await signUp(browser, "Dora");
  const email = `dora-${RUN}@example.com`;

  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/login/);

  await page.goto("/");
  await expect(page).toHaveURL(/\/login\?next=/);

  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("not-the-password");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByText("Wrong email or password")).toBeVisible();

  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByText("Signed in as Dora")).toBeVisible();
});
