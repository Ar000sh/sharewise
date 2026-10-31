import { expect, test, type Browser, type Page } from "@playwright/test";

const PASSWORD = "correct-horse";
const LAST_NAME = "Tester";
// Unique per run, so the test can be repeated against the same Keycloak and database.
const RUN = Date.now();

function emailOf(firstName: string): string {
  return `${firstName.toLowerCase()}-${RUN}@example.com`;
}

/** Fills Keycloak's registration page (default login theme); the display name becomes "<firstName> Tester". */
async function registerOnKeycloak(page: Page, firstName: string) {
  await expect(page).toHaveURL(/\/realms\/sharewise\//);
  await page.getByLabel("First name").fill(firstName);
  await page.getByLabel("Last name").fill(LAST_NAME);
  await page.getByLabel("Email").fill(emailOf(firstName));
  await page.getByRole("textbox", { name: "Password", exact: true }).fill(PASSWORD);
  await page.getByRole("textbox", { name: "Confirm password" }).fill(PASSWORD);
  await page.getByRole("button", { name: "Register" }).click();
}

async function signUp(browser: Browser, firstName: string): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto("/login");
  await page.getByRole("link", { name: "Create an account" }).click();
  await registerOnKeycloak(page, firstName);
  await expect(page.getByText(`Signed in as ${firstName} ${LAST_NAME}`)).toBeVisible();
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

  // The session is one httpOnly cookie and must stay well under the 4096 bytes a browser keeps per cookie (F4).
  const cookies = await alice.context().cookies();
  const session = cookies.find((c) => c.name === "sw_session");
  expect(session?.httpOnly).toBe(true);
  expect(session!.name.length + session!.value.length).toBeLessThan(4096);

  await alice.getByRole("link", { name: "Create your first group" }).click();
  await alice.getByLabel("Group name").fill("Mallorca 2027");
  await alice.getByRole("button", { name: "Create group" }).click();
  await expect(alice.getByRole("heading", { name: "Mallorca 2027" })).toBeVisible();

  await alice.getByRole("tab", { name: "Members" }).click();
  await alice.getByRole("button", { name: "Invite someone" }).click();
  const inviteLink = await alice.getByLabel("Invite link (valid for 7 days)").inputValue();

  // Bob and Carol open the link without an account: they are sent to sign in, register on Keycloak, and land on the
  // join page.
  const others: Page[] = [];
  for (const name of ["Bob", "Carol"]) {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto(inviteLink);
    await expect(page).toHaveURL(/\/login\?next=/);
    await page.getByRole("link", { name: "Create an account" }).click();
    await registerOnKeycloak(page, name);
    // The page is server-rendered: wait until React has hydrated, or the click lands on a button without handler.
    await page.waitForLoadState("networkidle");
    await page.getByRole("button", { name: "Join group" }).click();
    await expect(page.getByRole("heading", { name: "Mallorca 2027" })).toBeVisible();
    others.push(page);
  }
  const [bob] = others;

  // Alice pays the hotel (900 €, all three), Bob pays the dinner (180 €, all three).
  await alice.reload();
  await expect(alice.getByLabel("Paid by")).toContainText("Carol Tester");
  await addExpense(alice, "Hotel", "900");
  await bob.reload();
  await expect(bob.getByLabel("Paid by")).toContainText("Carol Tester");
  // Nobody has to pick themselves: the form already says who is recording.
  await expect(bob.getByLabel("Paid by").locator("option:checked")).toHaveText("Bob Tester");
  await addExpense(bob, "Dinner", "180");

  // Balances come through an event, so they may need a moment: the assertions retry.
  await alice.getByRole("tab", { name: "Balances" }).click();
  const balances = alice.getByRole("listitem").filter({ hasText: /€/ });
  await expect(balances.filter({ hasText: "Alice Tester" })).toContainText("+540,00");
  await expect(balances.filter({ hasText: "Bob Tester" })).toContainText("-180,00");
  await expect(balances.filter({ hasText: "Carol Tester" })).toContainText("-360,00");
  await expect(alice.getByText("Sum: 0,00")).toBeVisible();
});

test("wrong password is refused, signed-out visitors are sent to sign in, signing in again works", async ({
  browser,
}) => {
  const page = await signUp(browser, "Dora");
  const email = emailOf("Dora");

  // Signing out ends the Keycloak session too and comes back to the app's sign-in page.
  await page.getByRole("button", { name: "Sign out" }).click();
  // Without an id_token_hint (F4) Keycloak asks once before it ends the session.
  await page.getByRole("button", { name: "Logout" }).click();
  await expect(page).toHaveURL(/\/login/);

  await page.goto("/");
  await expect(page).toHaveURL(/\/login\?next=/);

  await page.getByRole("link", { name: "Sign in" }).click();
  await page.getByLabel("Email").fill(email);
  await page.getByRole("textbox", { name: "Password", exact: true }).fill("not-the-password");
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page.getByText("Invalid username or password")).toBeVisible();

  await page.getByRole("textbox", { name: "Password", exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page.getByText(`Signed in as Dora ${LAST_NAME}`)).toBeVisible();
});
