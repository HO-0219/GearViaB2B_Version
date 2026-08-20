import { request, requestBlob, saveBlob, serviceUrl } from './client';

export type ChatChannel = {
  id: number; groupId: number; name: string; type: 'GENERAL' | 'TOPIC';
  projectId?: number; projectName?: string; issueNodeId?: number; issueNodeTitle?: string;
  createdAt: string; retentionDays: number; canCreateChannels: boolean;
};
export type ChatMessage = {
  id: number; channelId: number; type: 'TEXT' | 'FILE' | 'IMAGE'; content?: string;
  senderMemberId: number; senderNickname: string; senderProfileImageUrl?: string;
  originalFilename?: string; contentType?: string; sizeBytes?: number; contentUrl?: string; createdAt: string;
};
export type ChatMessagePage = { items: ChatMessage[]; nextBeforeId?: number; retentionDays: number };

export const chatApi = {
  channels: (groupId: number) => request<ChatChannel[]>(`/groups/${groupId}/chat/channels`, {}, true),
  createChannel: (groupId: number, name: string, projectId?: number, issueNodeId?: number) =>
    request<ChatChannel>(`/groups/${groupId}/chat/channels`, {
      method: 'POST', body: JSON.stringify({ name, projectId, issueNodeId }),
    }, true),
  history: (channelId: number, beforeId?: number) => request<ChatMessagePage>(
    `/chat/channels/${channelId}/messages${beforeId ? `?beforeId=${beforeId}` : ''}`, {}, true),
  send: (channelId: number, content: string) => request<ChatMessage>(`/chat/channels/${channelId}/messages`, {
    method: 'POST', body: JSON.stringify({ content }),
  }, true),
  upload: (channelId: number, file: File, caption?: string) => {
    const body = new FormData(); body.append('file', file); if (caption?.trim()) body.append('caption', caption.trim());
    return request<ChatMessage>(`/chat/channels/${channelId}/attachments`, { method: 'POST', body }, true);
  },
  socketTicket: () => request<{ ticket: string; expiresInSeconds: number }>('/chat/socket-tickets', {
    method: 'POST',
  }, true),
  attachmentBlob: (message: ChatMessage) => requestBlob(
    message.contentUrl ?? `/chat/messages/${message.id}/content`, message.originalFilename ?? 'download'),
  download: async (message: ChatMessage) => {
    const result = await chatApi.attachmentBlob(message); saveBlob(result.blob, result.filename);
  },
};

export async function openChatSocket() {
  const { ticket } = await chatApi.socketTicket();
  const endpoint = new URL(serviceUrl('/ws/chat'), window.location.origin);
  endpoint.protocol = endpoint.protocol === 'https:' ? 'wss:' : 'ws:';
  return new WebSocket(endpoint, ['chat', `ticket.${ticket}`]);
}
