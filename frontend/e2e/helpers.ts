import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const PASSWORD = "E2e-Passw0rd-Strong";

// Every test uses its own address, so tests never depend on each other
export function uniqueEmail(label = "user"): string {
  return `e2e-${label}-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}@example.com`;
}

export function isoDaysAgo(days: number): string {
  return new Date(Date.now() - days * 86_400_000).toISOString().replace(/\.\d{3}Z$/, "Z");
}

export interface NewReading {
  type: string;
  value: number;
  secondaryValue?: number;
  unit?: string;
  measuredAt: string;
  note?: string;
}

// Fast setup that skips the screens: used when signing up is not what the test is about
export async function registerViaApi(request: APIRequestContext, email = uniqueEmail(), password = PASSWORD) {
  const response = await request.post("/api/v1/auth/register", { data: { email, password, consent: true } });
  expect(response.ok(), "registering through the API").toBeTruthy();
  const body = (await response.json()) as { token: string };
  return { email, password, token: body.token };
}

export async function createReadingViaApi(request: APIRequestContext, token: string, reading: NewReading) {
  const response = await request.post("/api/v1/vitals", {
    headers: { Authorization: `Bearer ${token}` },
    data: reading,
  });
  expect(response.status(), "creating a reading through the API").toBe(201);
}

export async function loginViaUi(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible();
}
// A dropdown inside its own label gets a name that also contains the selected option ("Type Blood pressure"),
// so an exact match fails. Matching the start of the label works for dropdowns and everything else.
export function labelled(page: Page, text: string) {
  return page.getByLabel(new RegExp(`^${text}`));
}