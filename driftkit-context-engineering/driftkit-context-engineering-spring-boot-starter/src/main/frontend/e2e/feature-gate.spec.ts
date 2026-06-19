import { test, expect, seedAuth, enableFeature, expectNoCrash, snap } from './_fixtures';

/**
 * The "core vs advanced" feature gate (src/config/features.ts):
 * advanced features are hidden from the sidebar AND unreachable by direct URL,
 * unless explicitly opted in (sessionStorage `feature:/path` or VITE_FEATURE_*).
 */
test.describe('Feature gate — advanced off by default', () => {
  test('direct URL to a disabled advanced screen redirects to dashboard', async ({ page }) => {
    await seedAuth(page);

    await page.goto('pipelines');
    await page.waitForLoadState('networkidle');

    // Router guard sends a disabled feature back to dashboard.
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('checklists (proprietary) is unreachable by default', async ({ page }) => {
    await seedAuth(page);

    await page.goto('checklists');
    await page.waitForLoadState('networkidle');

    await expect(page).toHaveURL(/\/dashboard$/);
  });
});

test.describe('Feature gate — opt-in enables the feature', () => {
  test('enabling Pipelines shows it in the sidebar and lets the route load', async ({ page }, info) => {
    await seedAuth(page);
    await enableFeature(page, '/pipelines');

    await page.goto('pipelines');
    await page.waitForLoadState('networkidle');
    await snap(page, info, 'pipelines-enabled');

    // Not redirected away…
    await expect(page).toHaveURL(/\/pipelines$/);
    await expectNoCrash(page);
    // …and now present in the sidebar.
    await expect(page.locator('nav, aside, .sidebar').first()).toContainText(/pipelines/i);
  });
});
