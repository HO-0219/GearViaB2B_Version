// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { LanguageProvider } from '../../../app/LanguageContext';
import { mcpApi } from '../../../api/mcpApi';
import { McpTokenPanel } from './McpTokenPanel';

vi.mock('../../../api/mcpApi', () => ({
  mcpApi: { list: vi.fn(), create: vi.fn(), revoke: vi.fn(), endpoint: () => 'https://gearvia.internal/mcp' },
}));

describe('McpTokenPanel', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('b2bgearvia-language', 'en');
    vi.mocked(mcpApi.list).mockReset().mockResolvedValue([]);
    vi.mocked(mcpApi.create).mockReset().mockResolvedValue({
      id: 1, label: 'My Codex', token: 'gv_mcp_once_only', scope: 'READ', expiresAt: '2026-10-02T00:00:00',
    });
    vi.mocked(mcpApi.revoke).mockReset().mockResolvedValue(undefined);
  });
  afterEach(() => cleanup());

  test('shows a newly issued secret once with endpoint guidance', async () => {
    render(<LanguageProvider><McpTokenPanel /></LanguageProvider>);
    expect(await screen.findByText('Personal MCP tokens')).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Token label'), { target: { value: 'My Codex' } });
    fireEvent.click(screen.getByRole('button', { name: 'Issue token' }));

    expect(await screen.findByText('gv_mcp_once_only')).toBeTruthy();
    expect(screen.getByText(/shown only once/i)).toBeTruthy();
    expect(screen.getByText(/\/mcp/)).toBeTruthy();
    expect(mcpApi.create).toHaveBeenCalledWith('My Codex', 30);
  });
});
