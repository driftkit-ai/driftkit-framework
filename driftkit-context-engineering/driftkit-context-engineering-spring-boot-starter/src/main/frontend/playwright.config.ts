import { defineConfig, devices } from '@playwright/test';

/**
 * E2E config for the Context Engineering UI — LIVE mode (no mocks).
 *
 * The suite drives the real frontend against the real backend
 * (DriftKitDevRunner :8085 + MongoDB + the configured model provider). Bring the
 * stack up first with `./scripts/e2e-up.sh`; _global-setup.ts fails fast if :8085
 * is down. Playwright itself starts only the Vite dev server (webServer below),
 * which proxies /data → :8085. Run locally with: `npm run e2e`.
 *
 * Vite serves under base `/prompt-engineering/` on port 8080 (vite.config.ts).
 * Workers=1: specs share one backend + one Mongo; parallel sessions would race
 * on shared state and the model rate limit.
 */
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/_global-setup.ts',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
    ['allure-playwright', { resultsDir: 'allure-results', detail: true, suiteTitle: 'Context Engineering UI (E2E)' }],
  ],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:8080/prompt-engineering/',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 15_000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:8080/prompt-engineering/',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
