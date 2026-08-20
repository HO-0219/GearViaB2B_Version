export const STORAGE_KEYS = {
  refreshSession: 'b2bgearvia-refresh-session',
  refreshLock: 'b2bgearvia-refresh-lock',
  deviceId: 'b2bgearvia-device-id',
  sessionMode: 'b2bgearvia-session-mode',
  language: 'b2bgearvia-language',
} as const;

export const LEGACY_STORAGE_KEYS = {
  refreshSession: 'hasRefreshSession',
  refreshLock: 'b2bgearviaRefreshLock',
} as const;

export function migrateLegacyStorageValue(
  legacyKey: string,
  currentKey: string,
  valid: (value: string) => boolean = () => true,
) {
  const current = localStorage.getItem(currentKey);
  const legacy = localStorage.getItem(legacyKey);
  if (current === null && legacy !== null && valid(legacy)) localStorage.setItem(currentKey, legacy);
  if (legacy !== null) localStorage.removeItem(legacyKey);
}
