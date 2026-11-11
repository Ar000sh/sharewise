import { expect, test, type Browser, type Page } from "@playwright/test";

const PASSWORD = "correct-horse";
const LAST_NAME = "Tester";
// Unique per run, so the test can be repeated against the same Keycloak and database.
const RUN = Date.now();

/** Lower case letters and digits only, as the realm demands (3 to 30 characters). */
function usernameOf(firstName: string): string {
  return `${firstName.toLowerCase()}${RUN}`;
}

function emailOf(firstName: string): string {
  return `${firstName.toLowerCase()}-${RUN}@example.com`;
}

/** Fills Keycloak's registration page (default login theme); the display name becomes "<firstName> Tester". */
async function registerOnKeycloak(page: Page, firstName: string) {
  await expect(page).toHaveURL(/\/realms\/sharewise\//);
  await page.getByLabel("Username").fill(usernameOf(firstName));
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
  // Only the recorder is ticked at first: the journey splits every expense between the whole group.
  await page.getByRole("button", { name: "Select everyone" }).click();
  await page.getByRole("button", { name: "Add expense" }).click();
  await expect(page.getByText(title).first()).toBeVisible();
}

test("three friends sign up, share a group, see who owes what and settle up", async ({ browser }) => {
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

  // Bob and Carol sign up first: only existing accounts can be invited.
  const bob = await signUp(browser, "Bob");
  const carol = await signUp(browser, "Carol");

  // Alice invites Bob by username and Carol by email.
  await alice.getByRole("tab", { name: "Members" }).click();
  await alice.getByLabel("Username or email").fill(usernameOf("Bob"));
  await alice.getByRole("button", { name: "Invite" }).click();
  await expect(alice.getByRole("status")).toContainText(`Invitation sent to @${usernameOf("Bob")}`);
  await alice.getByLabel("Username or email").fill(emailOf("Carol"));
  await alice.getByRole("button", { name: "Invite" }).click();
  await expect(alice.getByRole("status")).toContainText(`Invitation sent to @${usernameOf("Carol")}`);

  // Each of them finds the invitation in the bell and accepts it.
  for (const page of [bob, carol]) {
    await page.reload();
    await page.waitForLoadState("networkidle");
    await page.getByRole("button", { name: /inbox/i }).click();
    await expect(page.getByText("Alice Tester invited you to Mallorca 2027")).toBeVisible();
    await page.getByRole("button", { name: /accept invitation/i }).click();
    await expect(page.getByRole("heading", { name: "Mallorca 2027" })).toBeVisible();
  }

  // Alice hears about it: the entries arrive through an event, so she reloads until they are there.
  await expect(async () => {
    await alice.reload();
    await alice.getByRole("button", { name: /inbox/i }).click();
    await expect(alice.getByText("Bob Tester joined Mallorca 2027")).toBeVisible({ timeout: 2000 });
    await expect(alice.getByText("Carol Tester joined Mallorca 2027")).toBeVisible({ timeout: 2000 });
  }).toPass();

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

  // Carol settles up: she reports her whole debt to Alice. It only counts once Alice confirms it (R16).
  await carol.getByRole("tab", { name: /^Settle up/ }).click();
  const carolOwes = carol.getByRole("region", { name: "You owe" });
  await expect(carolOwes).toContainText("360,00 € to Alice Tester");
  await carolOwes.getByRole("button", { name: "I paid" }).click();
  await expect(carolOwes.getByLabel("Amount (€)")).toHaveValue("360.00");
  await carolOwes.getByRole("button", { name: "Send payment" }).click();
  await expect(carolOwes).toContainText("360,00 € awaiting confirmation");

  // Alice finds the payment waiting for her answer in the bell (state, not an event: a reload shows it) and confirms it.
  await alice.reload();
  await alice.getByRole("button", { name: /inbox/i }).click();
  await expect(alice.getByText("Carol Tester marked 360,00 € as paid to you")).toBeVisible();
  await alice.getByRole("button", { name: "Confirm payment from Carol Tester" }).click();
  await expect(alice.getByText("Carol Tester marked 360,00 € as paid to you")).toHaveCount(0);

  // Carol's Settle up tab is still open and was not touched: it asks again by itself (every 10 s), so her debt
  // disappears from it without a reload once the ledger has booked the confirmation.
  await expect(carolOwes).toHaveCount(0);

  // Carol hears about it: the entry arrives through an event, so she reloads until it is there.
  await expect(async () => {
    await carol.reload();
    await carol.getByRole("button", { name: /inbox/i }).click();
    await expect(carol.getByText("Alice Tester confirmed your payment in Mallorca 2027")).toBeVisible({ timeout: 2000 });
  }).toPass();

  // The ledger books the confirmation asynchronously; the plan shrinks to Bob's debt alone.
  // The open tab asks again by itself, so the assertion retries until the booking has arrived; no reload.
  await alice.getByRole("tab", { name: /^Settle up/ }).click();
  const owedToAlice = alice.getByRole("region", { name: "Owed to you" });
  await expect(owedToAlice.getByRole("listitem")).toHaveCount(1);
  await expect(owedToAlice).toContainText("Bob Tester owes 180,00 €");
  await expect(owedToAlice).not.toContainText("Carol Tester");
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
  await page.getByLabel("Username or email").fill(email);
  await page.getByRole("textbox", { name: "Password", exact: true }).fill("not-the-password");
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page.getByText("Invalid username or password")).toBeVisible();

  await page.getByRole("textbox", { name: "Password", exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page.getByText(`Signed in as Dora ${LAST_NAME}`)).toBeVisible();
});
