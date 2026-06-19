import { type APIRequestContext, expect } from '@playwright/test';
import { BACKEND, DEV_CREDENTIALS } from './_fixtures';

/**
 * Direct REST helpers for test isolation: create fixtures fast and clean them up
 * deterministically, hitting the live backend straight (bypassing the Vite proxy).
 * Every create returns an id; pair each with the matching delete in test teardown.
 */

const HEADERS = { Authorization: `Basic ${DEV_CREDENTIALS}`, 'Content-Type': 'application/json' };

/** Unique suffix so parallel/repeat runs never collide on names. */
export function uniq(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e4)}`;
}

// ---- Prompts ----

export async function apiCreatePrompt(
  api: APIRequestContext,
  method: string,
  message = 'Reply with exactly one word: PONG',
  extra: Record<string, unknown> = {},
): Promise<{ id: string; method: string }> {
  const res = await api.post(`${BACKEND}/data/v1.0/admin/prompt/`, {
    headers: HEADERS,
    data: { method, message, state: 'CURRENT', language: 'GENERAL', ...extra },
  });
  expect(res.ok(), `create prompt failed: ${res.status()}`).toBeTruthy();
  const body = await res.json();
  return { id: body.data.id, method: body.data.method };
}

export async function apiDeletePrompt(api: APIRequestContext, id: string): Promise<void> {
  await api.delete(`${BACKEND}/data/v1.0/admin/prompt/${id}`, { headers: HEADERS });
}

/** Delete every prompt version with the given method (cleanup for UI-created prompts). */
export async function apiDeletePromptByMethod(api: APIRequestContext, method: string): Promise<void> {
  const res = await api.get(`${BACKEND}/data/v1.0/admin/prompt/`, { headers: HEADERS });
  if (!res.ok()) return;
  const body = await res.json();
  const matches = (body.data as Array<{ id: string; method: string }>).filter((p) => p.method === method);
  for (const p of matches) {
    await apiDeletePrompt(api, p.id);
  }
}

// ---- Chats ----
// NB: there is no chat-delete endpoint (DELETE returns 404/405), so chats created
// for tests persist. Create them via API (avoids the native name prompt() in the UI).

export async function apiCreateChat(api: APIRequestContext, name: string): Promise<string> {
  const res = await api.post(`${BACKEND}/data/v1.0/admin/llm/chat`, {
    headers: HEADERS,
    data: { name },
  });
  expect(res.ok(), `create chat failed: ${res.status()}`).toBeTruthy();
  return (await res.json()).data.chatId;
}

// ---- Test sets ----

export async function apiCreateTestSet(
  api: APIRequestContext,
  name: string,
  description = 'e2e',
): Promise<{ id: string; name: string }> {
  const res = await api.post(`${BACKEND}/data/v1.0/admin/test-sets`, {
    headers: HEADERS,
    data: { name, description },
  });
  expect(res.ok(), `create test-set failed: ${res.status()}`).toBeTruthy();
  const body = await res.json();
  return { id: body.id, name: body.name };
}

export async function apiDeleteTestSet(api: APIRequestContext, id: string): Promise<void> {
  await api.delete(`${BACKEND}/data/v1.0/admin/test-sets/${id}`, { headers: HEADERS });
}

/** Delete every test set with the given name (cleanup for UI-created sets). */
export async function apiDeleteTestSetByName(api: APIRequestContext, name: string): Promise<void> {
  const res = await api.get(`${BACKEND}/data/v1.0/admin/test-sets`, { headers: HEADERS });
  if (!res.ok()) return;
  const list = (await res.json()) as Array<{ id: string; name: string }>;
  for (const ts of list.filter((t) => t.name === name)) {
    await apiDeleteTestSet(api, ts.id);
  }
}

/** Create an evaluation run for a test set; returns the run id. */
export async function apiCreateRun(api: APIRequestContext, testSetId: string): Promise<string> {
  const res = await api.post(`${BACKEND}/data/v1.0/admin/test-sets/${testSetId}/runs`, {
    headers: HEADERS,
    data: {},
  });
  expect(res.ok(), `create run failed: ${res.status()}`).toBeTruthy();
  return (await res.json()).data.id;
}
