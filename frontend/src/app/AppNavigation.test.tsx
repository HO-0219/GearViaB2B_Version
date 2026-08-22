// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { AppNavigation } from './AppNavigation';

vi.mock('../api/client', () => ({
  accessToken: { get: () => 'access-token' },
  sessionMode: { isDemo: () => false },
}));

vi.mock('../api/notificationApi', () => ({
  notificationApi: { list: vi.fn().mockResolvedValue({ unreadCount: 0 }) },
}));

vi.mock('../api/groupApi', () => ({
  groupApi: { list: vi.fn().mockResolvedValue([]) },
}));

const meMock = vi.fn();
vi.mock('../api/authApi', () => ({
  authApi: { me: () => meMock() },
}));

describe('AppNavigation admin link', () => {
  beforeEach(() => { localStorage.clear(); meMock.mockReset(); });
  afterEach(() => cleanup());

  test('shows the admin link when the signed-in user is an admin', async () => {
    meMock.mockResolvedValue({ role: 'ADMIN' });

    render(<MemoryRouter><AppNavigation /></MemoryRouter>);

    expect(await screen.findByRole('link', { name: /관리자/ })).toBeTruthy();
  });

  test('hides the admin link for a regular user', async () => {
    meMock.mockResolvedValue({ role: 'USER' });

    render(<MemoryRouter><AppNavigation /></MemoryRouter>);
    await screen.findByText('홈');
    await new Promise((resolve) => setTimeout(resolve, 10));

    expect(screen.queryByRole('link', { name: /관리자/ })).toBeNull();
  });
});
