import { request } from './client';

export type CalendarEventType = 'SCHEDULE' | 'MEETING' | 'VACATION' | 'TODO';
export type CalendarItem = {
  source: 'EVENT' | 'TASK_DEADLINE';
  eventId?: number;
  sourceTaskId?: number;
  groupId: number;
  groupName: string;
  groupType: 'PERSONAL' | 'TEAM';
  timezone: string;
  type: CalendarEventType | 'DEADLINE';
  title: string;
  description?: string;
  startAt: string;
  endAt: string;
  startAtUtc: string;
  endAtUtc: string;
  allDay: boolean;
  location?: string;
  createdByMemberId: number;
  version: number;
  createdAt: string;
  updatedAt: string;
  ownerMemberId?: number;
  ownerNickname?: string;
  taskStatus?: string;
  taskDelayed: boolean;
  taskAssigneeMemberId?: number;
  taskAssigneeNickname?: string;
};

export type CalendarEventBody = {
  type: CalendarEventType;
  title: string;
  description?: string;
  startAt: string;
  endAt: string;
  allDay: boolean;
  location?: string;
};

export const calendarApi = {
  list: (from: string, to: string, groupId?: number) => request<{ items: CalendarItem[]; from: string; to: string }>(
    `/calendars/events?from=${from}&to=${to}${groupId ? `&groupId=${groupId}` : ''}`, {}, true,
  ),
  create: (groupId: number, body: CalendarEventBody) => request<CalendarItem>(
    `/groups/${groupId}/calendar-events`, { method: 'POST', body: JSON.stringify(body) }, true,
  ),
  update: (eventId: number, body: Partial<CalendarEventBody> & { expectedVersion: number; clearDescription?: boolean; clearLocation?: boolean }) =>
    request<CalendarItem>(`/calendar-events/${eventId}`, { method: 'PATCH', body: JSON.stringify(body) }, true),
  delete: (eventId: number, expectedVersion: number) => request<void>(
    `/calendar-events/${eventId}?expectedVersion=${expectedVersion}`, { method: 'DELETE' }, true,
  ),
};
