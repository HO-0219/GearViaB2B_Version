// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { LanguageProvider } from '../../../app/LanguageContext';
import { adminApi } from '../../../api/adminApi';
import { AdminMonitoringPage } from './AdminMonitoringPage';

vi.mock('../../../api/client', () => ({
  errorMessage: (value: { message?: string }) => value.message ?? 'Request failed',
}));

vi.mock('../../../api/adminApi', () => ({
  adminApi: { monitoring: vi.fn(), storageSettings: vi.fn() },
}));

const totals = (requests: number) => ({
  requests,
  failedRequests: 1,
  inputTokens: 10,
  outputTokens: 4,
  totalTokens: 14,
});

describe('AdminMonitoringPage', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('b2bgearvia-language', 'en');
    vi.mocked(adminApi.monitoring).mockReset();
    vi.mocked(adminApi.storageSettings).mockReset();
    vi.mocked(adminApi.storageSettings).mockResolvedValue({
      provider: 'local', supportedProviders: ['local', 'nas_mount'],
      localRootPath: '/opt/b2bgearvia/data/uploads', localMounted: true,
      nasRootPath: '/opt/b2bgearvia/data/nas', nasMounted: false,
    });
  });
  afterEach(() => cleanup());

  test('renders unavailable storage without hiding AI totals', async () => {
    vi.mocked(adminApi.monitoring).mockResolvedValueOnce({
      system: {
        cpu: { available: true, usedPercent: 25 },
        memory: { available: true, usedBytes: 600, totalBytes: 800, usedPercent: 75 },
        storage: { available: false, provider: 'nas_mount' },
      },
      aiUsage: {
        timeZone: 'Asia/Seoul',
        periods: { today: totals(2), thisMonth: totals(5), allTime: totals(8) },
        breakdown: [{ operation: 'ASSISTANT_RESPONSE', model: 'gpt-5.6-luna', ...totals(8) }],
      },
    });

    render(<LanguageProvider><AdminMonitoringPage /></LanguageProvider>);

    expect(await screen.findByText('AI usage')).toBeTruthy();
    expect(screen.getByText('Storage unavailable')).toBeTruthy();
    expect(screen.getByText('gpt-5.6-luna')).toBeTruthy();
    expect(adminApi.monitoring).toHaveBeenCalledTimes(1);
  });

  test('shows the existing request error message when monitoring cannot load', async () => {
    vi.mocked(adminApi.monitoring).mockRejectedValueOnce({ message: 'Monitoring data failed' });

    render(<LanguageProvider><AdminMonitoringPage /></LanguageProvider>);

    expect(await screen.findByText('Monitoring data failed')).toBeTruthy();
  });
});
