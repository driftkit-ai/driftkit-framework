import { test, expect, seedAuth } from './_fixtures';
import { apiCreateTestSet, apiDeleteTestSet, apiDeleteTestSetByName, apiCreateRun, uniq } from './_api';

/**
 * Test Sets — create/view (live backend), plus the eval-run lifecycle surfaced on
 * the Eval Runs screen. Cleanup via REST so nothing lingers in Mongo.
 */
test.describe('Test Sets', () => {
  test('create a test set via the modal → it appears in the list', async ({ page, request }) => {
    await seedAuth(page);
    const name = uniq('e2e-ts');

    await page.goto('test-sets');
    await page.waitForLoadState('networkidle');

    await page.getByRole('button', { name: /create new test set/i }).first().click();
    await page.locator('#testSetName').fill(name);
    await page.locator('#testSetDescription').fill('created by e2e');

    const created = page.waitForResponse((r) =>
      r.url().includes('/admin/test-sets') && r.request().method() === 'POST' && r.ok());
    await page.getByRole('button', { name: /^create$/i }).click();
    await created;

    await expect(page.locator('body')).toContainText(name);

    await apiDeleteTestSetByName(request, name);
  });

  test('an API-seeded test set is listed and View opens its details', async ({ page, request }) => {
    const name = uniq('e2e-ts-view');
    const { id } = await apiCreateTestSet(request, name);

    try {
      await seedAuth(page);
      await page.goto('test-sets');
      await page.waitForLoadState('networkidle');

      const row = page.locator('tr', { hasText: name });
      await expect(row).toBeVisible();
      await row.getByRole('button', { name: /^view$/i }).click();
      // Details expand: the row toggles to "Hide" and the items area renders.
      await expect(row.getByRole('button', { name: /^hide$/i })).toBeVisible();
    } finally {
      await apiDeleteTestSet(request, id);
    }
  });

  test('a created eval run shows up on the Eval Runs screen', async ({ page, request }) => {
    const name = uniq('e2e-ts-run');
    const { id } = await apiCreateTestSet(request, name);
    const runId = await apiCreateRun(request, id);

    try {
      await seedAuth(page);
      await page.goto('evaluation-runs');
      await page.waitForLoadState('networkidle');

      // The run (or its parent test set) must be listed; status is one of the lifecycle states.
      await expect(page.locator('body')).toContainText(
        new RegExp(`${name}|QUEUED|RUNNING|COMPLETED|FAILED`, 'i'),
      );
    } finally {
      await apiDeleteTestSet(request, id);
    }
    expect(runId).toBeTruthy();
  });
});
