import { request } from '@playwright/test';
import { BACKEND } from './_fixtures';

/**
 * Fail fast (once, before the suite) if the live backend is not reachable —
 * a clear message beats every spec timing out on a dead :8085.
 */
export default async function globalSetup(): Promise<void> {
  const api = await request.newContext();
  try {
    const res = await api.get(`${BACKEND}/data/v1.0/admin/prompt/`, { timeout: 5_000 });
    if (res.status() >= 500) {
      throw new Error(`backend returned ${res.status()}`);
    }
  } catch (e) {
    throw new Error(
      `E2E backend not reachable at ${BACKEND}. Start the stack first:\n` +
      `  ./scripts/e2e-up.sh   (MongoDB on :27017 + DriftKitDevRunner on :8085)\n` +
      `Original error: ${(e as Error).message}`,
    );
  } finally {
    await api.dispose();
  }
}
