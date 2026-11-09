import { expect, test, type Page } from "@playwright/test";
import { createReadingViaApi, isoDaysAgo, labelled, loginViaUi, registerViaApi } from "./helpers";

function fact(page: Page, label: RegExp) {
  return page.locator("dt", { hasText: label }).locator("xpath=following-sibling::dd[1]");
}

test("the summary shows statistics and a chart for the chosen kind of reading", async ({ page, request }) => {
  const user = await registerViaApi(request);
  for (const [days, value] of [[3, 60], [2, 70], [1, 80]]) {
    await createReadingViaApi(request, user.token, { type: "HEART_RATE", value, measuredAt: isoDaysAgo(days) });
  }
  await loginViaUi(page, user.email, user.password);

  await labelled(page, "Summary of").selectOption("HEART_RATE");

  await expect(fact(page, /^Readings$/)).toHaveText("3");
  await expect(fact(page, /^Average$/)).toHaveText("70 beats/min");
  await expect(fact(page, /^Lowest$/)).toHaveText("60 beats/min");
  await expect(fact(page, /^Highest$/)).toHaveText("80 beats/min");
  await expect(page.getByRole("img", { name: /average heart rate per day/i })).toBeVisible();
});

test("a range the person sets is used to count readings within, above and below it", async ({ page, request }) => {
  const user = await registerViaApi(request);
  for (const [days, value] of [[3, 60], [2, 70], [1, 80]]) {
    await createReadingViaApi(request, user.token, { type: "HEART_RATE", value, measuredAt: isoDaysAgo(days) });
  }
  await loginViaUi(page, user.email, user.password);
  await labelled(page, "Summary of").selectOption("HEART_RATE");
  await expect(page.getByText("No range is set for this kind of reading")).toBeVisible();

  await labelled(page, "Range for").selectOption("HEART_RATE");
  await page.getByLabel("Low", { exact: true }).fill("65");
  await page.getByLabel("High", { exact: true }).fill("75");
  await page.getByRole("button", { name: "Save range" }).click();

  await expect(fact(page, /^Within your range$/)).toHaveText("1");
  await expect(fact(page, /^Above your range$/)).toHaveText("1");
  await expect(fact(page, /^Below your range$/)).toHaveText("1");
  await expect(fact(page, /^Range you set$/)).toHaveText("65 to 75 beats/min");

  await page.getByRole("button", { name: "Remove range for Heart rate" }).click();
  await expect(page.getByText("No range is set for this kind of reading")).toBeVisible();
  await expect(page.locator("dt", { hasText: /Within your range/ })).toHaveCount(0);
});

test("glucose in two units asks for one unit to be chosen", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await createReadingViaApi(request, user.token, { type: "BLOOD_GLUCOSE", value: 100, unit: "mg/dL", measuredAt: isoDaysAgo(2) });
  await createReadingViaApi(request, user.token, { type: "BLOOD_GLUCOSE", value: 5.5, unit: "mmol/L", measuredAt: isoDaysAgo(1) });
  await loginViaUi(page, user.email, user.password);

  await labelled(page, "Summary of").selectOption("BLOOD_GLUCOSE");
  await expect(page.getByRole("alert")).toContainText("more than one unit");

  await labelled(page, "Summary unit").selectOption("mg/dL");
  await expect(fact(page, /^Average$/)).toHaveText("100 mg/dL");
});