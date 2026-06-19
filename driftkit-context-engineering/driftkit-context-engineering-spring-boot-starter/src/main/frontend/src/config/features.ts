/**
 * Feature visibility gate — "core vs advanced".
 *
 * Core features are always on. Advanced features are hidden by default (niche or
 * proprietary) and must be explicitly opted in. This is the single source of
 * truth used by BOTH the sidebar (AdminLayout.vue) and the router guard
 * (router/index.ts), so a disabled feature is hidden from the menu AND
 * unreachable by direct URL.
 *
 * An advanced feature is enabled when any of these is true (highest priority first):
 *   1. runtime override:  sessionStorage[`feature:/pipelines`] === 'true'
 *   2. build-time env:     VITE_FEATURE_PIPELINES=true
 *   3. otherwise:          the default below (off)
 *
 * Note: this is independent of the backend "probe" check in AdminLayout. A probe
 * feature that is also advanced (e.g. Checklists) needs BOTH: opted in here AND
 * its backend endpoint reachable.
 */

/** Advanced features and their default-enabled state (all off by default). */
export const ADVANCED_FEATURES: Record<string, boolean> = {
  '/pipelines': false,
  '/checklists': false,
};

function envFlag(path: string): string | undefined {
  // '/pipelines' -> 'VITE_FEATURE_PIPELINES'
  const key = `VITE_FEATURE_${path.replace(/[^a-z]/gi, '').toUpperCase()}`;
  const env = (import.meta as { env?: Record<string, unknown> }).env;
  const value = env ? env[key] : undefined;
  return value === undefined ? undefined : String(value);
}

/**
 * Whether a route/menu path is enabled. Core (non-advanced) paths are always true.
 */
export function isFeatureEnabled(path: string): boolean {
  const matched = Object.keys(ADVANCED_FEATURES).find((p) => path.startsWith(p));
  if (!matched) {
    return true; // core feature
  }
  const override = sessionStorage.getItem(`feature:${matched}`);
  if (override !== null) {
    return override === 'true';
  }
  const env = envFlag(matched);
  if (env !== undefined) {
    return env === 'true';
  }
  return ADVANCED_FEATURES[matched];
}

/** Paths that are gated by the advanced-feature flag (used by the router guard). */
export const ADVANCED_PATHS: string[] = Object.keys(ADVANCED_FEATURES);
