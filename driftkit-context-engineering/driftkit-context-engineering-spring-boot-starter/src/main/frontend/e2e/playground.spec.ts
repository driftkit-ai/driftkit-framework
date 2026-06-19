import { test, expect, snap, expectNoCrash } from './_fixtures';

/**
 * Playground — the A/B prompt-comparison flow against the LIVE backend.
 * The "executes a real model call" test hits the configured provider
 * (POST /admin/llm/prompt/message/sync → DeepSeek) and asserts a non-empty
 * reply. It is skipped when DEEPSEEK_API_KEY is not set, so the suite stays
 * green without a model key; the render/validation tests need no model.
 */
const HAS_MODEL_KEY = !!process.env.DEEPSEEK_API_KEY;

test.describe('Playground', () => {
  test('renders both panels and the execute controls', async ({ authedPage }, info) => {
    await authedPage.goto('playground');
    await authedPage.waitForLoadState('networkidle');
    await snap(authedPage, info, 'playground-initial');

    await expectNoCrash(authedPage);
    await expect(authedPage.locator('body')).toContainText('Prompt Playground');
    await expect(authedPage.locator('body')).toContainText('Prompt A');
    await expect(authedPage.locator('body')).toContainText('Prompt B');
    await expect(authedPage.getByRole('button', { name: /execute a/i })).toBeVisible();
    await expect(authedPage.getByRole('button', { name: /execute both/i })).toBeVisible();
  });

  test('invalid variables JSON shows an inline error, no request fired', async ({ authedPage }) => {
    let syncCalled = false;
    authedPage.on('request', (r) => {
      if (r.url().includes('/admin/llm/prompt/message/sync')) syncCalled = true;
    });

    await authedPage.goto('playground');
    await authedPage.waitForLoadState('networkidle');

    await authedPage.locator('textarea[placeholder="Enter prompt..."]').first().fill('hi');
    await authedPage.locator('textarea[placeholder=\'{"key": "value"}\']').fill('{ not valid json');
    await authedPage.getByRole('button', { name: /execute a/i }).click();

    await expect(authedPage.locator('body')).toContainText(/invalid json/i);
    expect(syncCalled, 'no model call on invalid input').toBeFalsy();
  });

  test('Dataset Sweep and Pipeline Playground modes render their controls', async ({ authedPage }) => {
    // A full sweep/pipeline run needs a registered pipeline (none in the dev runner),
    // so this asserts the modes render and are wired — not a live pipeline execution.
    await authedPage.goto('playground');
    await authedPage.waitForLoadState('networkidle');

    await expect(authedPage.locator('body')).toContainText('Dataset Sweep');
    await expect(authedPage.getByPlaceholder('Test set ID')).toBeVisible();
    await expect(authedPage.getByRole('button', { name: /run sweep/i })).toBeVisible();

    await expect(authedPage.locator('body')).toContainText('Pipeline Playground');
    await expect(authedPage.getByPlaceholder(/e\.g\. content-moderation/i)).toBeVisible();
  });

  // KNOWN BUG: the sync endpoint (MessageTask) does not surface token counts, so the
  // Playground always shows "Tokens: 0". Encodes the desired behavior; un-fixme once the
  // backend returns promptTokens/completionTokens in the sync response.
  test.fixme('shows non-zero token counts after a model call', async ({ authedPage }) => {
    test.skip(!HAS_MODEL_KEY, 'DEEPSEEK_API_KEY not set');
    await authedPage.goto('playground');
    await authedPage.waitForLoadState('networkidle');
    await authedPage.locator('textarea[placeholder="Enter prompt..."]').first().fill('Say hi');
    await authedPage.getByRole('button', { name: /execute a/i }).click();
    await expect(authedPage.locator('.result-box').first()).toContainText(/Tokens: [1-9]/);
  });

  test('executes a real model call and renders the reply', async ({ authedPage }, info) => {
    test.skip(!HAS_MODEL_KEY, 'DEEPSEEK_API_KEY not set — skipping live model call');

    await authedPage.goto('playground');
    await authedPage.waitForLoadState('networkidle');

    await authedPage.locator('textarea[placeholder="Enter prompt..."]').first()
      .fill('Reply with exactly the single word: PONG');
    await authedPage.getByRole('button', { name: /execute a/i }).click();

    // Real model latency — give it room; assert the result box is non-empty.
    const resultBox = authedPage.locator('.result-box .result-pre').first();
    await expect(resultBox).toBeVisible({ timeout: 60_000 });
    await expect(resultBox).not.toBeEmpty();
    await snap(authedPage, info, 'playground-live-reply');
    await expectNoCrash(authedPage);
  });
});
