import { test, expect, expectNoCrash } from './_fixtures';

/**
 * Dashboard — metrics sections render and the date-range control is interactive
 * (against the live backend, with whatever data Mongo holds).
 */
test.describe('Dashboard', () => {
  test('renders the metrics sections', async ({ authedPage }) => {
    await authedPage.goto('dashboard');
    await authedPage.waitForLoadState('networkidle');

    await expectNoCrash(authedPage);
    await expect(authedPage.locator('body')).toContainText('Date Range');
    await expect(authedPage.locator('body')).toContainText(/Latency Percentiles/i);
    await expect(authedPage.locator('body')).toContainText(/Success|Error/i);
  });

  test('changing the date range stays stable', async ({ authedPage }) => {
    await authedPage.goto('dashboard');
    await authedPage.waitForLoadState('networkidle');

    // The Date Range SelectButton offers several windows; pick a different one.
    const option = authedPage.locator('.p-selectbutton').first().getByText(/1W|7|Week|24|1D|Day/i).first();
    if (await option.isVisible().catch(() => false)) {
      await option.click();
      await authedPage.waitForLoadState('networkidle');
    }
    await expectNoCrash(authedPage);
    await expect(authedPage.getByRole('button', { name: /reset/i })).toBeVisible();
  });
});
