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
      runtime: { instanceId: 'test-instance', maxTaskResults: 1000 },
      databasePool: { available: false, active: 0, idle: 0, total: 0, maximum: 20 },
      dependencies: [], executors: [], alerts: [],
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

  test('renders instance dependency pool executor and critical alert telemetry', async () => {
    vi.mocked(adminApi.monitoring).mockResolvedValueOnce({
      system: {
        cpu: { available: true, usedPercent: 25 },
        memory: { available: true, usedBytes: 600, totalBytes: 800, usedPercent: 75 },
        storage: { available: true, usedBytes: 400, totalBytes: 1000, usedPercent: 40, provider: 'local' },
      },
      aiUsage: {
        timeZone: 'Asia/Seoul',
        periods: { today: totals(2), thisMonth: totals(5), allTime: totals(8) },
        breakdown: [],
      },
      runtime: { instanceId: 'backend-1', maxTaskResults: 1000 },
      databasePool: { available: true, active: 18, idle: 2, total: 20, maximum: 20, usedPercent: 90 },
      dependencies: [{ name: 'database', status: 'UP' }, { name: 'storage', status: 'UP' }],
      executors: [{ name: 'document-index', active: 2, poolSize: 2, maxSize: 2,
        queueSize: 95, queueCapacity: 100, queueUsedPercent: 95, completed: 50, rejected: 3 }],
      alerts: [{ code: 'DATABASE_POOL_CRITICAL', severity: 'CRITICAL', usedPercent: 90 }],
    });

    render(<LanguageProvider><AdminMonitoringPage /></LanguageProvider>);

    expect(await screen.findByText('backend-1')).toBeTruthy();
    expect(screen.getByText('DATABASE_POOL_CRITICAL')).toBeTruthy();
    expect(screen.getByText('document-index')).toBeTruthy();
    expect(screen.getByText('database')).toBeTruthy();
  });
});
