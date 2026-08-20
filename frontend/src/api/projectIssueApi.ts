import { request, requestBlob } from './client';

export type IssueLevel = 'MAJOR' | 'MIDDLE' | 'ISSUE';
export type IssueStatus = 'OPEN' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE';
export type IssueChecklist = {
  id: number; content: string; completed: boolean; completedByMemberId?: number; completedAt?: string;
  sortOrder: number; version: number; createdAt: string; updatedAt: string;
};
export type IssueImage = {
  id: number; originalFilename: string; contentType: string; sizeBytes: number;
  uploadedByMemberId: number; uploadedByNickname: string; sortOrder: number; createdAt: string;
  contentUrl: string; canDelete: boolean;
};
export type ProjectIssue = {
  id: number; projectId: number; parentId?: number; level: IssueLevel; title: string; description?: string;
  status: IssueStatus; assigneeMemberId?: number; assigneeNickname?: string;
  createdByMemberId: number; createdByNickname: string; sortOrder: number; dueDate?: string;
  version: number; createdAt: string; updatedAt: string; canManage: boolean;
  archivedAt?: string;
  checklist: IssueChecklist[]; images: IssueImage[];
};
export type IssueInput = {
  level: IssueLevel; parentId?: number; title: string; description?: string;
  assigneeMemberId?: number; sortOrder?: number; dueDate?: string;
};

export const projectIssueApi = {
  list: (projectId: number, includeArchived = false) => request<ProjectIssue[]>(
    `/projects/${projectId}/issues${includeArchived ? '?includeArchived=true' : ''}`, {}, true),
  create: (projectId: number, body: IssueInput) => request<ProjectIssue>(`/projects/${projectId}/issues`, {
    method: 'POST', body: JSON.stringify(body),
  }, true),
  update: (issueId: number, body: Omit<IssueInput, 'level' | 'parentId'> & {
    status: IssueStatus; expectedVersion: number;
  }) => request<ProjectIssue>(`/project-issues/${issueId}`, { method: 'PUT', body: JSON.stringify(body) }, true),
  archive: (issueId: number, expectedVersion: number) => request<void>(
    `/project-issues/${issueId}?expectedVersion=${expectedVersion}`, { method: 'DELETE' }, true),
  createChecklist: (issueId: number, content: string) => request<IssueChecklist>(
    `/project-issues/${issueId}/checklist`, { method: 'POST', body: JSON.stringify({ content }) }, true),
  updateChecklist: (item: IssueChecklist, completed: boolean) => request<IssueChecklist>(
    `/project-issue-checklist/${item.id}`, {
      method: 'PUT', body: JSON.stringify({ completed, expectedVersion: item.version }),
    }, true),
  deleteChecklist: (item: IssueChecklist) => request<void>(
    `/project-issue-checklist/${item.id}?expectedVersion=${item.version}`, { method: 'DELETE' }, true),
  uploadImage: (issueId: number, file: File) => {
    const form = new FormData(); form.append('file', file);
    return request<IssueImage>(`/project-issues/${issueId}/images`, { method: 'POST', body: form }, true);
  },
  imageBlob: (image: IssueImage) => requestBlob(image.contentUrl, image.originalFilename),
  deleteImage: (imageId: number) => request<void>(`/project-issue-images/${imageId}`, { method: 'DELETE' }, true),
};
