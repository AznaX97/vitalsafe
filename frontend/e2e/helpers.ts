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
const MAIL_API = "http://127.0.0.1:8125/api/v1";

// Waits for an email to the address to arrive in the test mail server, and returns its text
export async function latestMailBody(request: APIRequestContext, address: string): Promise<string> {
  for (let attempt = 0; attempt < 30; attempt++) {
    const search = await request.get(`${MAIL_API}/search?query=${encodeURIComponent(`to:${address}`)}`);
    const found = (await search.json()) as { messages?: { ID: string }[] };
    if (found.messages && found.messages.length > 0) {
      const message = await request.get(`${MAIL_API}/message/${found.messages[0].ID}`);
      return ((await message.json()) as { Text: string }).Text;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No email arrived for ${address}`);
}

export function resetLinkIn(mailText: string): string {
  const match = /(http:\/\/localhost:5174\/reset-password#token=[A-Za-z0-9_-]+)/.exec(mailText);
  expect(match, "a reset link in the email").not.toBeNull();
  return (match as RegExpExecArray)[1];
}