// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { AdminShell } from './AdminShell';

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

describe('AdminShell MFA setup', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => cleanup());

  test('shows a scannable QR code during MFA setup', async () => {
    render(<MemoryRouter><AdminShell /></MemoryRouter>);

    fireEvent.click(await screen.findByRole('button', { name: 'MFA 설정 시작' }));

    const qrCode = await screen.findByRole('img', { name: 'MFA 설정 QR 코드' });
    expect(qrCode.tagName.toLowerCase()).toBe('svg');
    expect(qrCode.querySelectorAll('path').length).toBeGreaterThan(0);
  });

  test('returns to admin login after confirming the authenticator code', async () => {
    render(<MemoryRouter initialEntries={['/admin']}><Routes>
      <Route path="/admin" element={<AdminShell />} />
      <Route path="/login" element={<p>관리자 재로그인</p>} />
    </Routes></MemoryRouter>);

    fireEvent.click(await screen.findByRole('button', { name: 'MFA 설정 시작' }));
    fireEvent.change(await screen.findByRole('textbox', { name: '6자리 인증 코드' }), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '확인하고 활성화' }));

    expect(await screen.findByText('관리자 재로그인')).toBeTruthy();
  });
});
