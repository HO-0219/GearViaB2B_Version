// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

vi.mock('../api/notificationApi', () => ({
  notificationApi: { unsubscribePush: vi.fn(), subscribePush: vi.fn(), pushConfig: vi.fn() },
}));

import { disablePushForCurrentDevice } from './pushNotifications';

describe('disablePushForCurrentDevice', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    (window as unknown as { Notification: unknown }).Notification = {};
    (window as unknown as { PushManager: unknown }).PushManager = {};
    Object.defineProperty(navigator, 'serviceWorker', {
      configurable: true,
      value: { ready: new Promise<never>(() => {}) },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  test('resolves instead of hanging forever when service worker registration never settles', async () => {
    const settled = vi.fn();

    disablePushForCurrentDevice().then(settled);
    await vi.advanceTimersByTimeAsync(3000);

    expect(settled).toHaveBeenCalled();
  });
});
