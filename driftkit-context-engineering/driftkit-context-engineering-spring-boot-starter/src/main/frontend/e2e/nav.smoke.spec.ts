import { test, expect, snap, expectNoCrash } from './_fixtures';

/**
 * Smoke: every CORE screen loads under an authenticated session without a JS
 * crash and shows its heading. Advanced screens (Pipelines, Checklists) are
 * covered separately in feature-gate.spec.ts (hidden by default).
 */
const CORE_PAGES = [
  { name: 'Dashboard', path: 'dashboard', marker: /dashboard/i },
  { name: 'Prompts', path: 'prompts', marker: /prompts/i },
  { name: 'Traces', path: 'traces', marker: /traces/i },
  { name: 'Test Sets', path: 'test-sets', marker: /test sets/i },
  { name: 'Eval Runs', path: 'evaluation-runs', marker: /eval/i },
  { name: 'Playground', path: 'playground', marker: /playground/i },
  { name: 'Chat', path: 'chat', marker: /chat/i },
] as const;

test.describe('Smoke — core screens load', () => {
  for (const p of CORE_PAGES) {
    test(`${p.name} loads without crashing`, async ({ authedPage }, info) => {
      const errors: string[] = [];
      authedPage.on('pageerror', (e) => errors.push(e.message));

      await authedPage.goto(p.path);
      await authedPage.waitForLoadState('networkidle');
      await snap(authedPage, info, `smoke-${p.path}`);

      await expectNoCrash(authedPage);
      // The current page title is rendered in the layout header.
      await expect(authedPage.locator('body')).toContainText(p.marker);
      expect(errors, `JS errors on ${p.name}: ${errors.join('; ')}`).toHaveLength(0);
    });
  }
});

test.describe('Smoke — sidebar', () => {
  test('core menu items are visible, advanced are hidden by default', async ({ authedPage }) => {
    await authedPage.goto('dashboard');
    await authedPage.waitForLoadState('networkidle');

    const sidebar = authedPage.locator('nav, aside, .sidebar').first();
    await expect(sidebar).toContainText(/prompts/i);
    await expect(sidebar).toContainText(/playground/i);
    // Advanced, default-off:
    await expect(sidebar).not.toContainText(/pipelines/i);
    await expect(sidebar).not.toContainText(/checklists/i);
  });
});
