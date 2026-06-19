import { test, expect, seedAuth } from './_fixtures';
import { apiCreateChat, uniq } from './_api';

/**
 * Chat — multi-turn conversation against the live backend. The send→reply test
 * hits the real model and is skipped without DEEPSEEK_API_KEY. Chats have no
 * delete endpoint, so the created chat persists (named e2e-*); this is acceptable
 * residue noted in the suite.
 */
const HAS_MODEL_KEY = !!process.env.DEEPSEEK_API_KEY;

test.describe('Chat', () => {
  test('an API-created chat is listed and selectable; composer is ready', async ({ page, request }) => {
    const name = uniq('e2e-chat');
    await apiCreateChat(request, name);

    await seedAuth(page);
    await page.goto('chat');
    await page.waitForLoadState('networkidle');

    const item = page.locator('.chat-item', { hasText: name });
    await expect(item).toBeVisible();
    await item.click();

    await expect(page.getByPlaceholder('Type a message...')).toBeVisible();
    await expect(page.getByRole('button', { name: /send/i })).toBeVisible();
  });

  test('sending a message yields a model reply', async ({ page, request }) => {
    test.skip(!HAS_MODEL_KEY, 'DEEPSEEK_API_KEY not set — skipping live model call');

    const name = uniq('e2e-chat');
    await apiCreateChat(request, name);

    await seedAuth(page);
    await page.goto('chat');
    await page.waitForLoadState('networkidle');
    await page.locator('.chat-item', { hasText: name }).click();

    await page.getByPlaceholder('Type a message...').fill('Reply with exactly the single word: PONG');
    await page.getByRole('button', { name: /send/i }).click();

    // User bubble appears immediately…
    await expect(page.locator('.bubble-user')).toContainText('PONG', { timeout: 10_000 });
    // …then the assistant bubble fills in from polling (real model latency).
    const aiBubble = page.locator('.bubble-ai').last();
    await expect(aiBubble).toBeVisible({ timeout: 60_000 });
    await expect(aiBubble).not.toBeEmpty();
  });
});
