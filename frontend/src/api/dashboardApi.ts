import { CalendarItem } from './calendarApi';
import { request } from './client';
import { NotificationResponse } from './notificationApi';

export type DashboardTask = { id: number; groupId: number; groupName: string; title: string; projectId?: number; projectName?: string; projectTopicId?: number; projectTopicTitle?: string; status: string; priority: string; dueAt?: string; delayed: boolean; createdAt: string; startAt?: string; completedAt?: string; assigneeMemberId?: number; assigneeNickname?: string };
export type PersonalDashboard = {
  generatedAt: string; todayDueCount: number; delayedCount: number; inProgressCount: number; unreadNotificationCount: number;
  priorityTasks: DashboardTask[];
  groups: { groupId: number; groupName: string; groupType: string; assignedCount: number; completedCount: number; activeCount: number; delayedCount: number }[];
  upcomingItems: CalendarItem[];
  unreadNotifications: NotificationResponse[];
};
export type StatusCounts = { requested: number; todo: number; inProgress: number; onHold: number; completed: number; rejected: number; cancelled: number; delayed: number };
export type GroupDashboard = {
  generatedAt: string; groupId: number; groupName: string; timezone: string; visibility: string;
  periodFrom: string; periodTo: string; totalCount: number; statuses: StatusCounts; workflowProgressPercent?: number;
  periodCreatedCount: number; periodCompletedCount: number; periodCompletionRatePercent?: number;
  completedWithDueDateCount: number; onTimeCompletedCount: number; onTimeRatePercent?: number; averageCompletionHours?: number;
  members: { memberId: number; userId: number; nickname: string; role: string; assignedCount: number; activeCount: number; completedCount: number; delayedCount: number; onTimeRatePercent?: number }[];
  riskTasks: DashboardTask[];
  periodTasks: DashboardTask[];
  statusTasks: DashboardTask[];
  calendarItems: CalendarItem[];
};
export type MemberReport = {
  groupId: number; groupName: string; memberId: number; periodFrom: string; periodTo: string;
  tasks: DashboardTask[];
};

export const dashboardApi = {
  personal: () => request<PersonalDashboard>('/dashboard/me', {}, true),
  group: (groupId: number, from?: string, to?: string) => request<GroupDashboard>(
    `/groups/${groupId}/dashboard${from && to ? `?from=${from}&to=${to}` : ''}`, {}, true,
  ),
  memberReport: (groupId: number, from: string, to: string) => request<MemberReport>(
    `/groups/${groupId}/reports/me?from=${from}&to=${to}`, {}, true,
  ),
};
