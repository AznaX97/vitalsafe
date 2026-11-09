import { expect, test } from "@playwright/test";
import { createReadingViaApi, isoDaysAgo, labelled, loginViaUi, registerViaApi } from "./helpers";

test("add, edit and delete a reading", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await loginViaUi(page, user.email, user.password);
  const list = page.locator("ul.readings").first();

await page.getByLabel("Systolic", { exact: true }).fill("120");
await page.getByLabel("Diastolic", { exact: true }).fill("80");
  await page.getByLabel("Note").fill("after a walk");
  await page.getByRole("button", { name: "Save reading" }).click();
  await expect(list).toContainText("120/80 mmHg");
  await expect(list).toContainText("after a walk");

  await list.getByRole("button", { name: "Edit" }).click();
  await expect(page.getByRole("heading", { name: "Edit reading" })).toBeVisible();
  await page.getByLabel("Systolic",  { exact: true }).fill("130");
  await page.getByRole("button", { name: "Save reading" }).click();
  await expect(list).toContainText("130/80 mmHg");

  page.once("dialog", (dialog) => void dialog.accept());
  await list.getByRole("button", { name: "Delete" }).click();
  await expect(page.getByText("No readings yet.")).toBeVisible();
});

test("an impossible value shows the server's message", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await loginViaUi(page, user.email, user.password);

  await labelled(page, "Type").selectOption("HEART_RATE");
  await page.getByLabel("Value", { exact: true }).fill("5");
  await page.getByRole("button", { name: "Save reading" }).click();

  await expect(page.getByRole("alert")).toContainText("between 20 and 250");
});

test("glucose needs a unit to be chosen", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await loginViaUi(page, user.email, user.password);
  const list = page.locator("ul.readings").first();

  await labelled(page, "Type").selectOption("BLOOD_GLUCOSE");
  await page.getByLabel("Value", { exact: true }).fill("5.5");
  await page.getByRole("button", { name: "Save reading" }).click();
  // The browser refuses to submit while the unit is missing
  await expect(page.getByText("No readings yet.")).toBeVisible();

  await labelled(page, "Unit").selectOption("mmol/L");
  await page.getByRole("button", { name: "Save reading" }).click();
  await expect(list).toContainText("5.5 mmol/L");
});

test("the list can be filtered by type and paged", async ({ page, request }) => {
  const user = await registerViaApi(request);
  for (let day = 1; day <= 12; day++) {
    await createReadingViaApi(request, user.token, { type: "HEART_RATE", value: 60 + day, measuredAt: isoDaysAgo(day) });
  }
  await createReadingViaApi(request, user.token, { type: "BODY_WEIGHT", value: 70, measuredAt: isoDaysAgo(1) });
  await loginViaUi(page, user.email, user.password);
  const list = page.locator("ul.readings").first();

  await expect(page.getByText("Page 1 of 2")).toBeVisible();
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page.getByText("Page 2 of 2")).toBeVisible();

  await labelled(page, "Show").selectOption("BODY_WEIGHT");
  await expect(list).toContainText("70 kg");
  await expect(list.locator("li")).toHaveCount(1);
});