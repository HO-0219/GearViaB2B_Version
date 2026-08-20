import { request } from './client';

export type UserProfile = {
  userId: number;
  username: string;
  email: string;
  name: string;
  nickname: string;
  phoneNumber?: string;
  profileImageUrl?: string;
  status: string;
  systemRole: string;
  createdAt: string;
  updatedAt: string;
};

export const userApi = {
  profile: () => request<UserProfile>('/users/me', {}, true),
  updateProfile: (body: { nickname: string; phoneNumber?: string; profileImageUrl?: string }) =>
    request<UserProfile>('/users/me', { method: 'PATCH', body: JSON.stringify(body) }, true),
  uploadProfileImage: (file: File) => {
    const body = new FormData();
    body.append('file', file);
    return request<UserProfile>('/users/me/image', { method: 'POST', body }, true);
  },
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/users/me/password', {
      method: 'PUT', body: JSON.stringify({ currentPassword, newPassword }),
    }, true),
  withdraw: (currentPassword: string) =>
    request<void>('/users/me', { method: 'DELETE', body: JSON.stringify({ currentPassword }) }, true),
};
