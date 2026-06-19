import { test, expect, seedAuth, enableFeature, expectNoCrash } from './_fixtures';

/**
 * Advanced / optional screens. In the dev runner only the Dictionary backend
 * responds (index/checklist endpoints are 404), so we cover Dictionaries here;
 * Pipelines opt-in is covered in feature-gate.spec.ts.
 */
test.describe('Advanced screens', () => {
  test('Dictionaries (probe-available) shows in the sidebar and loads', async ({ page }) => {
    await seedAuth(page);
    await page.goto('dashboard');
    await page.waitForLoadState('networkidle');

    // Probe (/admin/dictionary/) succeeds in dev → sidebar item appears.
    await expect(page.locator('nav, aside, .sidebar').first()).toContainText(/dictionar/i);

    await page.goto('dictionaries');
    await page.waitForLoadState('networkidle');
    await expectNoCrash(page);
    await expect(page).toHaveURL(/\/dictionaries$/);
  });

  test('Pipelines loads without crashing once enabled', async ({ page }) => {
    await seedAuth(page);
    await enableFeature(page, '/pipelines');

    await page.goto('pipelines');
    await page.waitForLoadState('networkidle');

    await expect(page).toHaveURL(/\/pipelines$/);
    await expectNoCrash(page);
    await expect(page.locator('body')).toContainText(/pipeline/i);
  });
});
