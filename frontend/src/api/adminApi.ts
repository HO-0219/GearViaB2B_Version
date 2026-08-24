import { request } from './client';

export type AdminUser = { id: number; username: string; maskedEmail: string; nickname: string; role: string; status: string; createdAt: string; lastLoginAt?: string; forcePasswordChange?: boolean };
export type AdminOverview = { users: number; activeUsers: number; suspendedUsers: number; groups: number; teamGroups: number; reportDownloads: number; reportDeliveries: number; failedReportDeliveries: number };
export type AdminGroup = { id: number; name: string; type: string; activeMembers: number; reportScheduleActive: boolean; createdAt: string };
export type AdminReportDownload = { id: number; groupId: number; groupName: string; requestedByUserId: number; scope: string; periodType: string; createdAt: string };
export type AdminReportDelivery = { id: number; groupId: number; groupName: string; periodType: string; language: string; status: string; retryCount: number; errorCode?: string; lastAttemptAt?: string; nextRetryAt?: string; sentAt?: string; createdAt: string };
export type AdminMfaStatus = { enabled: boolean; sessionVerified: boolean; encryptionConfigured: boolean; enabledAt?: string };
export type AdminMfaSetup = { secret: string; otpauthUri: string };
export type AdminAudit = { id: number; actorUserId?: number; method: string; path: string; status: number; outcome: string; ipAddress?: string; requestId?: string; occurredAt: string };
export type AdminAiVerticalStatus = { enabled: boolean; apiKeyConfigured: boolean; maskedApiKey?: string; model: string; baseUrl: string };
export type AdminAiSettingsStatus = { report: AdminAiVerticalStatus; assistant: AdminAiVerticalStatus; supportedModels: string[] };
export type AdminAiConnectionResult = { success: boolean; message: string };
export type AdminAiConnectionTestResponse = { report: AdminAiConnectionResult; assistant: AdminAiConnectionResult };
export type AdminStorageSettings = { provider: string; supportedProviders: string[]; localRootPath: string; localMounted: boolean; nasRootPath: string; nasMounted: boolean };
export type AdminStorageTestResult = { success: boolean; message: string };
export type AdminBranding = { organizationName: string; hasLogo: boolean };
export type AdminNotice = { id: number; title: string; message: string; scheduledAt: string; status: string; recipientCount?: number; createdAt: string; sentAt?: string };
export type AdminTask = { id: number; groupId: number; groupName: string; title: string; status: string; requesterId: number; requesterNickname: string; assigneeId?: number; assigneeNickname?: string; dueAt?: string; holdReason?: string; deletedAt?: string; createdAt: string; updatedAt: string };
export type AdminLoginHistoryEntry = { id: number; username: string; userId?: number; outcome: string; ipAddress?: string; deviceName?: string; occurredAt: string };
export type AdminMetric = { available: boolean; usedPercent?: number | null };
export type AdminCapacity = { available: boolean; usedBytes?: number | null; totalBytes?: number | null; usedPercent?: number | null };
export type AdminAiUsageTotals = { requests: number; failedRequests: number; inputTokens?: number | null; outputTokens?: number | null; totalTokens?: number | null };
export type AdminAiUsageBreakdown = AdminAiUsageTotals & { operation: string; model: string };
export type AdminMonitoring = {
  system: { cpu: AdminMetric; memory: AdminCapacity; storage: AdminCapacity & { provider: string } };
  aiUsage: { timeZone: string; periods: { today: AdminAiUsageTotals; thisMonth: AdminAiUsageTotals; allTime: AdminAiUsageTotals }; breakdown: AdminAiUsageBreakdown[] };
};
export type Page<T> = { items: T[]; page: number; size: number; totalElements: number; totalPages: number };
export const adminApi = {
  overview: () => request<AdminOverview>('/admin/overview', {}, true),
  monitoring: () => request<AdminMonitoring>('/admin/monitoring', {}, true),
  users: () => request<Page<AdminUser>>('/admin/users?size=50', {}, true),
  userStatus: (id: number, status: 'ACTIVE' | 'SUSPENDED') => request<AdminUser>(`/admin/users/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }, true),
  createUser: (body: { email: string; name: string; role?: string }) => request<{ user: AdminUser; temporaryPassword: string }>('/admin/users', { method: 'POST', body: JSON.stringify(body) }, true),
  updateUser: (id: number, nickname: string) => request<AdminUser>(`/admin/users/${id}`, { method: 'PATCH', body: JSON.stringify({ nickname }) }, true),
  withdrawUser: (id: number) => request<void>(`/admin/users/${id}`, { method: 'DELETE' }, true),
  resetTemporaryPassword: (id: number) => request<{ user: AdminUser; temporaryPassword: string }>(`/admin/users/${id}/temporary-password`, { method: 'POST' }, true),
  endSessions: (id: number) => request<void>(`/admin/users/${id}/end-sessions`, { method: 'POST' }, true),
  groups: () => request<Page<AdminGroup>>('/admin/groups?size=50', {}, true),
  reportDownloads: () => request<Page<AdminReportDownload>>('/admin/report-downloads?size=50', {}, true),
  reportDeliveries: () => request<Page<AdminReportDelivery>>('/admin/report-deliveries?size=50', {}, true),
  mfaStatus: () => request<AdminMfaStatus>('/admin/mfa/status', {}, true),
  mfaSetup: () => request<AdminMfaSetup>('/admin/mfa/setup', { method: 'POST' }, true),
  mfaConfirm: (code: string) => request<void>('/admin/mfa/confirm', {
    method: 'POST', body: JSON.stringify({ code }),
  }, true),
  auditLogs: (page = 0) => request<Page<AdminAudit>>(`/admin/audit-logs?page=${page}&size=50`, {}, true),
  aiSettings: () => request<AdminAiSettingsStatus>('/admin/ai-settings', {}, true),
  testAiConnections: () => request<AdminAiConnectionTestResponse>('/admin/ai-settings/test', { method: 'POST' }, true),
  updateAiSettings: (apiKey: string | undefined, reportEnabled: boolean, assistantEnabled: boolean) =>
    request<AdminAiSettingsStatus>('/admin/ai-settings', {
      method: 'PUT', body: JSON.stringify({ apiKey, reportEnabled, assistantEnabled }),
    }, true),
  storageSettings: () => request<AdminStorageSettings>('/admin/storage-settings', {}, true),
  testNasStorage: () => request<AdminStorageTestResult>('/admin/storage-settings/nas/test', { method: 'POST' }, true),
  activateNasStorage: () => request<AdminStorageTestResult>('/admin/storage-settings/nas/activate', { method: 'POST' }, true),
  activateLocalStorage: () => request<AdminStorageSettings>('/admin/storage-settings/local/activate', { method: 'POST' }, true),
  updateBranding: (organizationName: string, logo: File | undefined, removeLogo: boolean) => {
    const body = new FormData();
    body.append('organizationName', organizationName);
    if (logo) body.append('logo', logo);
    body.append('removeLogo', String(removeLogo));
    return request<AdminBranding>('/admin/branding', { method: 'PUT', body }, true);
  },
  notices: () => request<Page<AdminNotice>>('/admin/notices?size=30', {}, true),
  createNotice: (body: { title: string; message: string; scheduledAt: string }) =>
    request<AdminNotice>('/admin/notices', { method: 'POST', body: JSON.stringify(body) }, true),
  cancelNotice: (id: number) => request<void>(`/admin/notices/${id}`, { method: 'DELETE' }, true),
  tasks: () => request<Page<AdminTask>>('/admin/tasks?size=50', {}, true),
  deletedTasks: () => request<AdminTask[]>('/admin/tasks/deleted', {}, true),
  suspendTask: (id: number, reason: string) => request<AdminTask>(`/admin/tasks/${id}/suspend`, { method: 'POST', body: JSON.stringify({ reason }) }, true),
  resumeTask: (id: number) => request<AdminTask>(`/admin/tasks/${id}/resume`, { method: 'POST' }, true),
  deleteTask: (id: number) => request<void>(`/admin/tasks/${id}`, { method: 'DELETE' }, true),
  restoreTask: (id: number) => request<AdminTask>(`/admin/tasks/${id}/restore`, { method: 'POST' }, true),
  loginHistory: () => request<Page<AdminLoginHistoryEntry>>('/admin/login-history?size=50', {}, true),
};
