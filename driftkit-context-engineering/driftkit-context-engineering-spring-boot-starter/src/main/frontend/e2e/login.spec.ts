import { test, expect } from '@playwright/test';
import { loginUi } from './_fixtures';

/**
 * Login screen against the live backend. The dev backend is open (no Spring
 * Security), so any credentials are accepted — we assert the happy path and the
 * unauthenticated redirect (a pure frontend router-guard behavior).
 */
test.describe('Login', () => {
  test('unauthenticated visit redirects to the login screen', async ({ page }) => {
    await page.goto('dashboard');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.locator('body')).toContainText('DriftKit');
  });

  test('valid login lands on the dashboard', async ({ page }) => {
    await loginUi(page);
    await expect(page).toHaveURL(/\/dashboard$/);
  });
});
