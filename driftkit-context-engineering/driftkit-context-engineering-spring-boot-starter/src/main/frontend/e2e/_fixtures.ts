import { test as base, expect, type Page, type TestInfo } from '@playwright/test';

/**
 * Shared E2E helpers — LIVE mode. No mocks: the suite drives the real frontend
 * against a real backend (DriftKitDevRunner :8085 + MongoDB + the configured
 * model provider), with Vite proxying /data → :8085.
 *
 * Bring the stack up first (see scripts/e2e-up.sh): MongoDB on :27017 and the
 * dev server on :8085. _global-setup.ts fails fast if the backend is unreachable.
 *
 * Auth: the dev backend has no Spring Security on the classpath, so any Basic
 * credentials are accepted. The frontend router guard only checks that
 * sessionStorage.credentials exists; we seed it before boot to skip the login UI
 * on every spec (the login screen itself is covered in login.spec.ts).
 */

export const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8085';

/** Dummy Basic-auth blob; the open dev backend accepts anything. */
export const DEV_CREDENTIALS = Buffer.from('admin:admin').toString('base64');

/** Seed credentials in sessionStorage before app boot so the router guard passes. */
export async function seedAuth(page: Page, credentials: string = DEV_CREDENTIALS): Promise<void> {
  await page.addInitScript((creds) => {
    sessionStorage.setItem('credentials', creds as string);
  }, credentials);
}

/** Real UI login (exercises the login screen + probe against the live backend). */
export async function loginUi(page: Page, username = 'admin', password = 'admin'): Promise<void> {
  await page.goto('login');
  await page.locator('#username').fill(username);
  await page.locator('#password input').fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL((url) => !url.pathname.endsWith('/login'), { timeout: 10_000 });
}

/** Enable an advanced feature (e.g. '/pipelines') for the session before boot. */
export async function enableFeature(page: Page, path: string): Promise<void> {
  await page.addInitScript((p) => {
    sessionStorage.setItem(`feature:${p}`, 'true');
  }, path);
}

/** Full-page screenshot attached to the Allure step. */
export async function snap(page: Page, info: TestInfo, label: string): Promise<void> {
  const body = await page.screenshot({ fullPage: true });
  await info.attach(label, { body, contentType: 'image/png' });
}

/** Assert the page rendered without a hard JS/render failure. */
export async function expectNoCrash(page: Page): Promise<void> {
  await expect(page.locator('body')).not.toContainText(/uncaught|application error|cannot read propert/i);
}

/**
 * Test fixture: a pre-authenticated page (credentials seeded) with JS errors
 * captured. `jsErrors` collects pageerror messages so specs can assert the
 * console stayed clean while hitting the real backend.
 */
export const test = base.extend<{ authedPage: Page; jsErrors: string[] }>({
  jsErrors: async ({}, use) => {
    await use([]);
  },
  authedPage: async ({ page, jsErrors }, use) => {
    page.on('pageerror', (e) => jsErrors.push(e.message));
    await seedAuth(page);
    await use(page);
  },
});

export { expect };
