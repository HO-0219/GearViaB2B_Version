import { request } from './client';

export type CommentResponse = {
  id: number;
  taskId: number;
  authorMemberId: number;
  authorNickname: string;
  content: string;
  deleted: boolean;
  recordLocked: boolean;
  mentions: Array<{ id: number; memberId: number; userId: number; nickname: string }>;
  version: number;
  createdAt: string;
  updatedAt?: string;
  deletedAt?: string;
};

export const commentApi = {
  list: (taskId: number) => request<CommentResponse[]>(`/tasks/${taskId}/comments`, {}, true),
  create: (taskId: number, content: string, mentionedMemberIds: number[]) => request<CommentResponse>(`/tasks/${taskId}/comments`, {
    method: 'POST', body: JSON.stringify({ content, mentionedMemberIds }),
  }, true),
  update: (commentId: number, content: string, mentionedMemberIds: number[], expectedVersion: number) =>
    request<CommentResponse>(`/comments/${commentId}`, {
      method: 'PATCH', body: JSON.stringify({ content, mentionedMemberIds, expectedVersion }),
    }, true),
  delete: (commentId: number, expectedVersion: number) =>
    request<void>(`/comments/${commentId}?expectedVersion=${expectedVersion}`, {
      method: 'DELETE',
    }, true),
};
