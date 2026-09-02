// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { LanguageProvider } from '../../../app/LanguageContext';
import { adminApi, AdminAiSettingsStatus } from '../../../api/adminApi';
import { AdminAiSettingsPage } from './AdminAiSettingsPage';

vi.mock('../../../api/adminApi', () => ({
  adminApi: { aiSettings: vi.fn(), testAiConnections: vi.fn(), updateAiSettings: vi.fn() },
}));

const status: AdminAiSettingsStatus = {
  provider: 'INTERNAL_OPENAI_COMPATIBLE', baseUrl: 'https://10.0.0.8/v1', chatModel: 'chat-v1',
  embeddingModel: 'embed-v1', requestTimeoutSeconds: 30, externalAllowed: false,
  supportedModels: [],
  report: { enabled: true, apiKeyConfigured: false, model: 'chat-v1', baseUrl: 'https://10.0.0.8/v1' },
  assistant: { enabled: true, apiKeyConfigured: false, model: 'chat-v1', baseUrl: 'https://10.0.0.8/v1' },
};

describe('AdminAiSettingsPage', () => {
  beforeEach(() => {
    localStorage.setItem('b2bgearvia-language', 'en');
    vi.mocked(adminApi.aiSettings).mockResolvedValue(status);
    vi.mocked(adminApi.updateAiSettings).mockResolvedValue(status);
    vi.mocked(adminApi.testAiConnections).mockResolvedValue({
      chat: { success: true, message: 'chat ok' }, embedding: { success: false, message: 'embedding failed' },
    });
  });
  afterEach(() => { cleanup(); vi.clearAllMocks(); });

  test('saves internal routing fields and shows separate chat and embedding probes', async () => {
    render(<LanguageProvider><AdminAiSettingsPage /></LanguageProvider>);
    expect(await screen.findByDisplayValue('https://10.0.0.8/v1')).toBeTruthy();

    fireEvent.change(screen.getByDisplayValue('chat-v1'), { target: { value: 'chat-v2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(adminApi.updateAiSettings).toHaveBeenCalledWith(expect.objectContaining({
      provider: 'INTERNAL_OPENAI_COMPATIBLE', chatModel: 'chat-v2', embeddingModel: 'embed-v1', externalAllowed: false,
    }));

    fireEvent.click(screen.getByRole('button', { name: 'Test connection' }));
    expect(await screen.findAllByText('chat ok')).toHaveLength(2);
    expect(await screen.findByText(/embedding failed/)).toBeTruthy();
  });
});
