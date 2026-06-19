import { test, expect, seedAuth, expectNoCrash } from './_fixtures';

/**
 * Error resilience: when a backend call fails (500), the screen must degrade
 * gracefully — no white screen, no uncaught exception. We override a single
 * endpoint per test (everything else stays live).
 */
test.describe('Error states', () => {
  test('Prompts survives a 500 from the list endpoint', async ({ page }) => {
    await seedAuth(page);
    await page.route('**/data/v1.0/admin/prompt/**', (route) =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"error":"boom"}' }));

    await page.goto('prompts');
    await page.waitForLoadState('networkidle');

    await expectNoCrash(page);
    // The editor shell still renders even though the list failed to load.
    await expect(page.getByPlaceholder(/Enter prompt method/i)).toBeVisible();
  });

  test('Traces survives a 500 from the analytics endpoint', async ({ page }) => {
    await seedAuth(page);
    await page.route('**/data/v1.0/analytics/**', (route) =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"error":"boom"}' }));

    await page.goto('traces');
    await page.waitForLoadState('networkidle');

    await expectNoCrash(page);
    // Filter controls still render.
    await expect(page.getByPlaceholder('Message ID')).toBeVisible();
  });
});
