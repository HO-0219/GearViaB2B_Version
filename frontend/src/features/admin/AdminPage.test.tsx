// @vitest-environment jsdom

import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { AdminPage } from './AdminPage';

vi.mock('../../api/client', () => ({
  accessToken: { get: () => 'access-token', clear: vi.fn() },
  errorMessage: (value: unknown) => String(value),
}));

vi.mock('../../api/adminApi', () => ({
  adminApi: {
    mfaStatus: vi.fn().mockResolvedValue({ enabled: false, sessionVerified: false, encryptionConfigured: true }),
    mfaSetup: vi.fn().mockResolvedValue({
      secret: 'ABC123',
      otpauthUri: 'otpauth://totp/B2BGearVia%3Aadmin?secret=ABC123&issuer=B2BGearVia',
    }),
    mfaConfirm: vi.fn(),
  },
}));

describe('AdminPage MFA setup', () => {
  beforeEach(() => localStorage.clear());

  test('shows a scannable QR code during MFA setup', async () => {
    render(<MemoryRouter><AdminPage /></MemoryRouter>);

    fireEvent.click(await screen.findByRole('button', { name: 'MFA 설정 시작' }));

    const qrCode = await screen.findByRole('img', { name: 'MFA 설정 QR 코드' });
    expect(qrCode.tagName.toLowerCase()).toBe('svg');
    expect(qrCode.querySelectorAll('path').length).toBeGreaterThan(0);
  });
});
