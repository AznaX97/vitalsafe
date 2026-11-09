import { expect, test } from "@playwright/test";
import { PASSWORD, createReadingViaApi, isoDaysAgo, loginViaUi, registerViaApi, uniqueEmail } from "./helpers";

test("a new person can register, stay signed in after a reload, log out and log back in", async ({ page }) => {
  const email = uniqueEmail("journey");

  await page.goto("/register");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();
  await expect(page.getByText("No readings yet.")).toBeVisible();

  // The refresh cookie restores the session
  await page.reload();
  await expect(page.getByText(email)).toBeVisible();

  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.reload();
  await expect(page).toHaveURL(/\/login$/);

  await loginViaUi(page, email, PASSWORD);
});

test("registering needs the privacy consent box", async ({ page }) => {
  await page.goto("/register");
  await page.getByLabel("Email", { exact: true }).fill(uniqueEmail("noconsent"));
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);

  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/register$/);
  const valid = await page.getByRole("checkbox").evaluate((box) => (box as HTMLInputElement).validity.valid);
  expect(valid).toBe(false);
});

test("the session cookie is hardened and nothing is kept in browser storage", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await loginViaUi(page, user.email, user.password);

  const cookies = await page.context().cookies();
  const refresh = cookies.find((cookie) => cookie.name === "refresh_token");
  expect(refresh?.httpOnly).toBe(true);
  expect(refresh?.sameSite).toBe("Strict");
  expect(refresh?.path).toBe("/api/v1/auth");

  const storage = await page.evaluate(() => ({ local: window.localStorage.length, session: window.sessionStorage.length }));
  expect(storage).toEqual({ local: 0, session: 0 });
});

test("a wrong password shows an error and keeps the person on the login page", async ({ page, request }) => {
  const user = await registerViaApi(request);

  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(user.email);
  await page.getByLabel("Password", { exact: true }).fill("Wrong-Passw0rd-123");
  await page.getByRole("button", { name: "Log in" }).click();

  await expect(page.getByRole("alert")).toHaveText("Invalid email or password");
  await expect(page).toHaveURL(/\/login$/);
});

test("a logged-out visitor is sent to the login page", async ({ page }) => {
  await page.goto("/");

  await expect(page).toHaveURL(/\/login$/);
});

test("one person never sees another person's readings", async ({ page, request }) => {
  const secret = `secret-${Date.now()}`;
  const alice = await registerViaApi(request, uniqueEmail("alice"));
  await createReadingViaApi(request, alice.token, { type: "HEART_RATE", value: 72, measuredAt: isoDaysAgo(1), note: secret });
  const bob = await registerViaApi(request, uniqueEmail("bob"));

  await loginViaUi(page, bob.email, bob.password);

  await expect(page.getByText("No readings yet.")).toBeVisible();
  await expect(page.getByText(secret)).toHaveCount(0);
});

test("the privacy notice opens from the register form and from the footer", async ({ page, context }) => {
  await page.goto("/register");
  const [notice] = await Promise.all([context.waitForEvent("page"),page.getByRole("link", { name: "privacy notice", exact: true }).click()]);
  await expect(notice.getByRole("heading", { name: "Privacy notice and medical disclaimer" })).toBeVisible();
  await expect(notice.getByText("This is not end-to-end encryption.")).toBeVisible();

  await page.goto("/login");
  await page.getByRole("link", { name: "Privacy notice and medical disclaimer" }).click();
  await expect(page).toHaveURL(/\/privacy$/);
});