import { request } from './client';

export type ProjectStatus = 'PLANNED' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'ARCHIVED';
export type ProjectResponse = {
  id: number; groupId: number; name: string; description?: string; status: ProjectStatus;
  leadMemberId?: number; leadNickname?: string; createdByMemberId: number; createdByNickname: string;
  startDate?: string; dueDate?: string; version: number; createdAt: string; updatedAt: string;
  canManage: boolean; canManageFlow: boolean;
};
export type ProjectInput = {
  name: string; description?: string; leadMemberId?: number; startDate?: string; dueDate?: string;
};

export const projectApi = {
  list: (groupId: number) => request<ProjectResponse[]>(`/groups/${groupId}/projects`, {}, true),
  create: (groupId: number, body: ProjectInput) => request<ProjectResponse>(`/groups/${groupId}/projects`, {
    method: 'POST', body: JSON.stringify(body),
  }, true),
  get: (projectId: number) => request<ProjectResponse>(`/projects/${projectId}`, {}, true),
  update: (projectId: number, body: ProjectInput & { status: ProjectStatus; expectedVersion: number }) =>
    request<ProjectResponse>(`/projects/${projectId}`, { method: 'PUT', body: JSON.stringify(body) }, true),
  archive: (projectId: number, expectedVersion: number) => request<void>(
    `/projects/${projectId}?expectedVersion=${expectedVersion}`, { method: 'DELETE' }, true,
  ),
};
