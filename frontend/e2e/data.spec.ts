import { promises as fs } from "node:fs";
import { Buffer } from "node:buffer";
import { expect, test } from "@playwright/test";
import { createReadingViaApi, isoDaysAgo, loginViaUi, registerViaApi } from "./helpers";

test("the readings download as CSV and as FHIR", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await createReadingViaApi(request, user.token, { type: "HEART_RATE", value: 72, measuredAt: isoDaysAgo(2), note: "resting" });
  await createReadingViaApi(request, user.token, { type: "BLOOD_PRESSURE", value: 120, secondaryValue: 80, measuredAt: isoDaysAgo(1) });
  await loginViaUi(page, user.email, user.password);

  const [csv] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "Download CSV" }).click()]);
  expect(csv.suggestedFilename()).toBe("vitalsafe-readings.csv");
  const csvText = await fs.readFile(await csv.path(), "utf8");
  expect(csvText.split("\r\n")[0]).toBe("type,value,secondaryValue,unit,measuredAt,note");
  expect(csvText).toContain("HEART_RATE,72,,/min,");
  expect(csvText).toContain("BLOOD_PRESSURE,120,80,mm[Hg],");

  const [fhir] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "Download FHIR" }).click()]);
  expect(fhir.suggestedFilename()).toBe("vitalsafe-fhir-export.json");
  const bundle = JSON.parse(await fs.readFile(await fhir.path(), "utf8")) as { resourceType: string; type: string; entry: unknown[] };
  expect(bundle.resourceType).toBe("Bundle");
  expect(bundle.type).toBe("collection");
  expect(bundle.entry).toHaveLength(3);   // the patient and two observations
});

test("a CSV file can be imported, and importing it again skips the duplicates", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await loginViaUi(page, user.email, user.password);
  const csv = [
    "type,value,secondaryValue,unit,measuredAt,note",
    `HEART_RATE,70,,,${isoDaysAgo(3)},`,
    `HEART_RATE,75,,,${isoDaysAgo(2)},morning`,
    "",
  ].join("\n");
  const file = { name: "readings.csv", mimeType: "text/csv", buffer: Buffer.from(csv) };
  const list = page.locator("ul.readings").first();

  await page.getByLabel("CSV file", { exact: true }).setInputFiles(file);
  await page.getByRole("button", { name: "Import CSV" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Imported 2 readings." })).toBeVisible();
  await expect(list).toContainText("75 beats/min");
  await expect(list).toContainText("morning");

  await page.getByLabel("CSV file", { exact: true }).setInputFiles(file);
  await page.getByRole("button", { name: "Import CSV" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Skipped 2 duplicates." })).toBeVisible();
  await expect(list.locator("li")).toHaveCount(2);
});

test("a CSV file with a bad row imports nothing and says which row", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await loginViaUi(page, user.email, user.password);
  const csv = [
    "type,value,secondaryValue,unit,measuredAt,note",
    `HEART_RATE,70,,,${isoDaysAgo(3)},`,
    `HEART_RATE,5,,,${isoDaysAgo(2)},`,
    "",
  ].join("\n");

  await page.getByLabel("CSV file", { exact: true }).setInputFiles({ name: "bad.csv", mimeType: "text/csv", buffer: Buffer.from(csv) });
  await page.getByRole("button", { name: "Import CSV" }).click();

  await expect(page.getByRole("alert").filter({ hasText: "nothing was imported" })).toBeVisible();
  await expect(page.getByText(/Row 3: value: .*between 20 and 250/)).toBeVisible();
  await expect(page.getByText("No readings yet.")).toBeVisible();
});

test("an exported FHIR Observation can be imported again", async ({ page, request }) => {
  const user = await registerViaApi(request);
  await createReadingViaApi(request, user.token, { type: "HEART_RATE", value: 72, measuredAt: isoDaysAgo(2) });
  const list = await request.get("/api/v1/vitals", { headers: { Authorization: `Bearer ${user.token}` } });
  const id = ((await list.json()) as { items: { id: string }[] }).items[0].id;
  const observation = await request.get(`/api/v1/vitals/${id}/fhir`, { headers: { Authorization: `Bearer ${user.token}` } });
  await loginViaUi(page, user.email, user.password);

  await page.getByLabel("FHIR file", { exact: true }).setInputFiles({
    name: "observation.json",
    mimeType: "application/json",
    buffer: Buffer.from(await observation.text()),
  });
  await page.getByRole("button", { name: "Import FHIR Observation" }).click();

  await expect(page.getByRole("status").filter({ hasText: "Imported 1 reading." })).toBeVisible();
  await expect(page.locator("ul.readings").first().locator("li")).toHaveCount(2);
});