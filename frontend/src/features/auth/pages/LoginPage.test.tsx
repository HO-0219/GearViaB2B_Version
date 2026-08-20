// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { LoginPage } from './LoginPage';

vi.mock('../../../api/client', () => ({
  accessToken: { get: () => null, set: vi.fn(), clear: vi.fn() },
  sessionMode: { isDemo: () => false, clear: vi.fn() },
  errorMessage: (value: unknown) => String(value),
}));

vi.mock('../../../api/authApi', () => ({
  authApi: { login: vi.fn(), logout: vi.fn(), me: vi.fn() },
}));

describe('LoginPage', () => {
  afterEach(() => cleanup());

  test('provides a direct link to the admin page', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    const adminLink = screen.getByRole('link', { name: '관리자 페이지' });
    expect(adminLink.getAttribute('href')).toBe('/admin');
  });
});
