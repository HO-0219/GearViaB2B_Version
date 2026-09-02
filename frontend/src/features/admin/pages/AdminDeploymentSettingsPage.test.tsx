// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { LanguageProvider } from '../../../app/LanguageContext';
import { adminApi } from '../../../api/adminApi';
import { AdminDeploymentSettingsPage } from './AdminDeploymentSettingsPage';

vi.mock('../../../api/adminApi', () => ({
  adminApi: {
    deploymentSettings: vi.fn(),
    createDeploymentDraft: vi.fn(),
    testDeploymentJob: vi.fn(),
    applyDeploymentJob: vi.fn(),
  },
}));

const status = {
  publicUrl: 'https://gearvia.corp',
  certificateIssuer: 'CN=GearVia Local CA',
  certificateNotAfter: '2027-01-01T00:00:00',
  certificateSans: ['gearvia.corp', 'localhost'],
  status: 'ACTIVE',
  applyVersion: 2,
};

const job = (over: Record<string, unknown>) => ({
  jobId: 7, type: 'DOMAIN_TLS', status: 'DRAFT', publicUrl: 'https://new.gearvia.corp',
  progressPercent: 0, verificationSummary: null, failureCode: null, rollbackSummary: null,
  version: 0, ...over,
});

describe('AdminDeploymentSettingsPage', () => {
  beforeEach(() => {
    vi.mocked(adminApi.deploymentSettings).mockResolvedValue(status as never);
    vi.mocked(adminApi.createDeploymentDraft).mockResolvedValue(job({}) as never);
    vi.mocked(adminApi.testDeploymentJob).mockResolvedValue(
      job({ status: 'TEST_SUCCEEDED', progressPercent: 100 }) as never,
    );
    vi.mocked(adminApi.applyDeploymentJob).mockResolvedValue(
      job({ status: 'COMPLETED', progressPercent: 100 }) as never,
    );
  });
  afterEach(() => { cleanup(); vi.clearAllMocks(); });

  test('keeps apply disabled until a connection test succeeds', async () => {
    render(<LanguageProvider><AdminDeploymentSettingsPage /></LanguageProvider>);

    expect(await screen.findByText('도메인·SSL 설정')).toBeTruthy();
    expect((screen.getByRole('button', { name: '적용' }) as HTMLButtonElement).disabled).toBe(true);

    fireEvent.change(screen.getByLabelText('공개 URL'), { target: { value: 'https://new.gearvia.corp' } });
    fireEvent.change(screen.getByLabelText('인증서 파일'), {
      target: { files: [new File(['cert'], 'fullchain.pem', { type: 'text/plain' })] },
    });
    fireEvent.change(screen.getByLabelText('개인 키 파일'), {
      target: { files: [new File(['key'], 'privkey.pem', { type: 'text/plain' })] },
    });

    fireEvent.click(screen.getByRole('button', { name: '연결 테스트' }));

    expect(await screen.findByText('예상 중단 시간')).toBeTruthy();
    expect((screen.getByRole('button', { name: '적용' }) as HTMLButtonElement).disabled).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: '적용' }));
    expect(await screen.findByText('COMPLETED')).toBeTruthy();
    expect(adminApi.applyDeploymentJob).toHaveBeenCalledWith(7);
  });
});
