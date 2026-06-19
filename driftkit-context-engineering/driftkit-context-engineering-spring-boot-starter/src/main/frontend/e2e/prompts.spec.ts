import { test, expect, seedAuth, BACKEND } from './_fixtures';
import { apiCreatePrompt, apiDeletePrompt, apiDeletePromptByMethod, uniq } from './_api';
import type { Page, Locator } from '@playwright/test';

/**
 * Prompts — the core CRUD/versioning screen (live backend). Each test cleans up
 * via the REST API so the suite leaves no residue in Mongo.
 *
 * Method names use no '/' on purpose: a slash makes the UI group the prompt into
 * a folder row instead of showing the full method.
 */

/** The template editor starts in "Preview" mode (showPromptPreview defaults true),
 *  which hides the textarea. Toggle preview off so the template is editable. */
async function templateField(page: Page): Promise<Locator> {
  const tpl = page.getByPlaceholder(/Enter your prompt template/i);
  if (!(await tpl.isVisible().catch(() => false))) {
    await page.locator('.p-togglebutton').first().click();
    await expect(tpl).toBeVisible({ timeout: 5_000 });
  }
  return tpl;
}

test.describe('Prompts', () => {
  test('create a prompt in the editor → it appears in the list', async ({ page, request }) => {
    await seedAuth(page);
    const method = uniq('e2ecreate');

    await page.goto('prompts');
    await page.waitForLoadState('networkidle');

    await page.getByPlaceholder(/Enter prompt method/i).fill(method);
    (await templateField(page)).fill('Reply with one word: PONG');

    const save = page.waitForResponse((r) =>
      r.url().includes('/admin/prompt/') && r.request().method() === 'POST' && r.ok());
    await page.getByRole('button', { name: /save draft/i }).click();
    await save;

    await page.getByPlaceholder(/Search by method or prompt message/i).fill(method);
    await expect(page.locator('body')).toContainText(method);

    await apiDeletePromptByMethod(request, method);
  });

  test('an API-seeded prompt is listed and opens in the editor', async ({ page, request }) => {
    const method = uniq('e2eseed');
    const { id } = await apiCreatePrompt(request, method, 'Seeded template {{x}}');

    try {
      await seedAuth(page);
      await page.goto('prompts');
      await page.waitForLoadState('networkidle');

      await page.getByPlaceholder(/Search by method or prompt message/i).fill(method);
      await expect(page.locator('body')).toContainText(method);

      // Click the row → the editor loads the selected prompt's method.
      await page.getByText(method, { exact: true }).first().click();
      await expect(page.getByPlaceholder(/Enter prompt method/i)).toHaveValue(method);
    } finally {
      await apiDeletePrompt(request, id);
    }
  });

  test('editing the template and saving creates a new version', async ({ page, request }) => {
    const method = uniq('e2eedit');
    const { id } = await apiCreatePrompt(request, method, 'v1 template');

    try {
      await seedAuth(page);
      await page.goto('prompts');
      await page.waitForLoadState('networkidle');
      await page.getByPlaceholder(/Search by method or prompt message/i).fill(method);
      await page.getByText(method, { exact: true }).first().click();

      (await templateField(page)).fill('v2 template, changed');
      const save = page.waitForResponse((r) =>
        r.url().includes('/admin/prompt/') && r.request().method() === 'POST' && r.ok());
      await page.getByRole('button', { name: /save draft/i }).click();
      await save;

      const res = await request.get(`${BACKEND}/data/v1.0/admin/prompt/`);
      const versions = (await res.json()).data.filter((p: { method: string }) => p.method === method);
      expect(versions.length, 'editing should produce a second version').toBeGreaterThanOrEqual(2);
    } finally {
      await apiDeletePromptByMethod(request, method);
      await apiDeletePrompt(request, id).catch(() => {});
    }
  });
});
