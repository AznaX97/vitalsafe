import { expect, test } from "@playwright/test";
import { PASSWORD, createReadingViaApi, isoDaysAgo, latestMailBody, loginViaUi, registerViaApi, resetLinkIn, uniqueEmail } from "./helpers";

const NEW_PASSWORD = "E2e-New-Passw0rd-Strong";

async function logInWith(page: import("@playwright/test").Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Log in" }).click();
}

test("changing the password signs the person out, and only the new password works", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await loginViaUi(page, user.email, user.password);

  await page.getByRole("link", { name: user.email }).click();
  await expect(page.getByRole("heading", { name: "Your account", exact: true })).toBeVisible();
  await page.getByLabel("Current password", { exact: true }).fill(user.password);
  await page.getByLabel("New password", { exact: true }).fill(NEW_PASSWORD);
  await page.getByLabel("Repeat the new password", { exact: true }).fill(NEW_PASSWORD);
  await page.getByRole("button", { name: "Change password" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("status")).toContainText("Your password was changed");

  await logInWith(page, user.email, user.password);
  await expect(page.getByRole("alert")).toHaveText("Invalid email or password");

  await logInWith(page, user.email, NEW_PASSWORD);
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible();
});

test("a wrong current password shows a message and keeps the person signed in", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await loginViaUi(page, user.email, user.password);
  await page.getByRole("link", { name: user.email }).click();

  await page.getByLabel("Current password", { exact: true }).fill("Not-The-Passw0rd-123");
  await page.getByLabel("New password", { exact: true }).fill(NEW_PASSWORD);
  await page.getByLabel("Repeat the new password", { exact: true }).fill(NEW_PASSWORD);
  await page.getByRole("button", { name: "Change password" }).click();

  await expect(page.getByRole("alert")).toContainText("Current password is incorrect");
  await expect(page.getByRole("heading", { name: "Your account", exact: true })).toBeVisible();
});

test("a forgotten password can be reset with the emailed link, once", async ({ page, request }) => {
  const user = await registerViaApi(request);

  await page.goto("/login");
  await page.getByRole("link", { name: "Forgot your password?" }).click();
  await page.getByLabel("Email", { exact: true }).fill(user.email);
  await page.getByRole("button", { name: "Send reset link" }).click();
  await expect(page.getByRole("status")).toContainText("a reset link is on its way");

  const link = resetLinkIn(await latestMailBody(request, user.email));
  await page.goto(link);
  await expect(page).toHaveURL(/\/reset-password$/);   // the token has been removed from the address
  await page.getByLabel("New password", { exact: true }).fill(NEW_PASSWORD);
  await page.getByLabel("Repeat the new password", { exact: true }).fill(NEW_PASSWORD);
  await page.getByRole("button", { name: "Change password" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("status")).toContainText("Your password was changed");

  await logInWith(page, user.email, user.password);
  await expect(page.getByRole("alert")).toHaveText("Invalid email or password");
  await logInWith(page, user.email, NEW_PASSWORD);
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible();

  // The same link cannot be used a second time
  await page.getByRole("button", { name: "Log out" }).click();
  await page.goto(link);
  await page.getByLabel("New password", { exact: true }).fill("Another-Passw0rd-Strong");
  await page.getByLabel("Repeat the new password", { exact: true }).fill("Another-Passw0rd-Strong");
  await page.getByRole("button", { name: "Change password" }).click();
  await expect(page.getByRole("alert")).toContainText("invalid or has expired");
});

test("asking for a reset for an address with no account looks exactly the same", async ({ page }) => {
  await page.goto("/forgot-password");
  await page.getByLabel("Email", { exact: true }).fill(uniqueEmail("ghost"));
  await page.getByRole("button", { name: "Send reset link" }).click();

  await expect(page.getByRole("status")).toContainText("If that email address has an account, a reset link is on its way");
});

test("a reset link without a token explains itself", async ({ page }) => {
  await page.goto("/reset-password");

  await expect(page.getByRole("heading", { name: "This link is not complete" })).toBeVisible();
});

test("deleting the account removes the login and every reading", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await createReadingViaApi(request, user.token, { type: "HEART_RATE", value: 72, measuredAt: isoDaysAgo(1), note: "to be deleted" });
  await loginViaUi(page, user.email, user.password);
  await expect(page.locator("ul.readings").first()).toContainText("to be deleted");

  await page.getByRole("link", { name: user.email }).click();
  await page.getByLabel("Your password", { exact: true }).fill(user.password);
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Delete my account" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("status")).toContainText("Your account and all its data were deleted");

  await logInWith(page, user.email, user.password);
  await expect(page.getByRole("alert")).toHaveText("Invalid email or password");
  const refresh = await request.post("/api/v1/auth/refresh");
  expect(refresh.status()).toBe(401);
});

test("deleting needs the right password", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await loginViaUi(page, user.email, user.password);
  await page.getByRole("link", { name: user.email }).click();

  await page.getByLabel("Your password", { exact: true }).fill("Not-The-Passw0rd-123");
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Delete my account" }).click();

  await expect(page.getByRole("alert")).toContainText("Password is incorrect");
  await expect(page.getByRole("heading", { name: "Your account", exact: true })).toBeVisible();
  expect(PASSWORD).toBeTruthy();
});