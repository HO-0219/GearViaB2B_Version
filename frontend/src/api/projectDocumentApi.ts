import { request, requestBlob, saveBlob } from './client';

export type ProjectDocument = {
  id: number; projectId: number; issueNodeId?: number; type: 'LINK' | 'FILE'; title: string;
  url?: string; originalFilename?: string; contentType?: string; sizeBytes?: number;
  createdByMemberId: number; createdByNickname: string; createdAt: string; canDelete: boolean;
};
export type ProjectFileTree = {
  projectId: number; usedBytes: number; limitBytes: number; remainingBytes: number;
  rootDocuments: ProjectDocument[]; nodeDocuments: ProjectDocument[];
};

export const projectDocumentApi = {
  list: (projectId: number, issueNodeId?: number) => request<ProjectFileTree>(
    `/projects/${projectId}/documents${issueNodeId ? `?issueNodeId=${issueNodeId}` : ''}`, {}, true),
  addLink: (projectId: number, title: string, url: string, issueNodeId?: number) =>
    request<ProjectDocument>(`/projects/${projectId}/documents/links`, {
      method: 'POST', body: JSON.stringify({ title, url, issueNodeId }),
    }, true),
  upload: (projectId: number, file: File, title: string, issueNodeId?: number) => {
    const body = new FormData(); body.append('file', file);
    if (title.trim()) body.append('title', title.trim());
    const query = issueNodeId ? `?issueNodeId=${issueNodeId}` : '';
    return request<ProjectDocument>(`/projects/${projectId}/documents/files${query}`, {
      method: 'POST', body,
    }, true);
  },
  remove: (documentId: number) => request<void>(`/project-documents/${documentId}`, { method: 'DELETE' }, true),
  download: async (document: ProjectDocument) => {
    const result = await requestBlob(`/project-documents/${document.id}/download`,
      document.originalFilename ?? 'download');
    saveBlob(result.blob, result.filename);
  },
};
