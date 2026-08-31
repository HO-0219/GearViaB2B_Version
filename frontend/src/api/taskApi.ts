import { request } from './client';

export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export type TaskStatus = 'REQUESTED' | 'TODO' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'REJECTED' | 'CANCELLED';
export type BlockerType = 'DEPENDENCY' | 'DECISION' | 'ACCESS' | 'RESOURCE' | 'TECHNICAL' | 'EXTERNAL' | 'OTHER';
export type BlockerNextActionType = 'FOLLOW_UP' | 'ESCALATE' | 'DECIDE' | 'UNBLOCK_ACCESS' | 'REPLAN' | 'WAIT_EXTERNAL' | 'OTHER';

export type TaskResponse = {
  id: number;
  groupId: number;
  projectId?: number;
  projectName?: string;
  projectTopicId?: number;
  projectTopicTitle?: string;
  requesterMemberId: number;
  approverMemberId?: number;
  assigneeMemberId?: number;
  title: string;
  description?: string;
  priority: TaskPriority;
  status: TaskStatus;
  startAt?: string;
  dueAt?: string;
  completedAt?: string;
  holdReason?: string;
  blockerType?: BlockerType;
  blockerNextActionType?: BlockerNextActionType;
  blockerReviewDate?: string;
  stopReason?: string;
  delayed: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
};

export type TaskAction = 'ACCEPT' | 'REJECT' | 'START' | 'HOLD' | 'RESUME' | 'COMPLETE' | 'REOPEN' | 'CANCEL';

export type TaskHistoryResponse = {
  id: number;
  fromStatus?: TaskStatus;
  toStatus: TaskStatus;
  changedByMemberId: number;
  reason?: string;
  createdAt: string;
};

export type ChecklistItemResponse = {
  id: number;
  taskId: number;
  content: string;
  completed: boolean;
  completedByMemberId?: number;
  completedAt?: string;
  sortOrder: number;
  version: number;
  createdAt: string;
  updatedAt: string;
};

export type ChecklistResponse = {
  items: ChecklistItemResponse[];
  totalCount: number;
  completedCount: number;
  progressPercent?: number;
};

export type CreateTaskRequest = {
  title: string;
  description?: string;
  priority: TaskPriority;
  dueAt?: string;
  projectId?: number;
  projectTopicId?: number;
  checklistItems?: string[];
};

export type UpdateTaskRequest = {
  title?: string;
  description?: string;
  priority?: TaskPriority;
  dueAt?: string;
  clearDueAt?: boolean;
  projectId?: number;
  projectTopicId?: number;
  clearProjectLink?: boolean;
  expectedVersion: number;
};

export type WeeklyObjective = {
  id: number;
  weekStart: string;
  title: string;
  position: number;
  version: number;
};

export type TaskWeeklyObjective = {
  taskId: number;
  weekStart: string;
  objective?: WeeklyObjective;
};
export type AssigneeChangeRequest = { id:number; taskId:number; taskTitle:string; requestedByMemberId:number;
  requestedByNickname:string; proposedAssigneeMemberId:number; proposedAssigneeNickname:string;
  status:'PENDING'|'APPROVED'|'REJECTED'; reason?:string; reviewedByMemberId?:number; reviewNote?:string;
  createdAt:string; reviewedAt?:string; version:number; };

export type TransitionOptions = {
  reason?: string;
  blockerType?: BlockerType;
  blockerNextActionType?: BlockerNextActionType;
  blockerReviewDate?: string;
};

export const taskApi = {
  list: (groupId: number, filters: { query?: string; status?: string; priority?: string; projectId?: number; assignment?: string; due?: string } = {}) => {
    const params = new URLSearchParams(); Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== '') params.set(key, String(value)); });
    return request<TaskResponse[]>(`/groups/${groupId}/tasks${params.size ? `?${params}` : ''}`, {}, true);
  },
  create: (groupId: number, body: CreateTaskRequest) => request<TaskResponse>(`/groups/${groupId}/tasks`, {
    method: 'POST', body: JSON.stringify(body),
  }, true),
  get: (taskId: number) => request<TaskResponse>(`/tasks/${taskId}`, {}, true),
  update: (taskId: number, body: UpdateTaskRequest) => request<TaskResponse>(`/tasks/${taskId}`, {
    method: 'PATCH', body: JSON.stringify(body),
  }, true),
  linkProject: (taskId:number, body:{projectId?:number;projectTopicId?:number;clearProjectLink?:boolean;expectedVersion:number}) => request<TaskResponse>(`/tasks/${taskId}/project-link`,{method:'PATCH',body:JSON.stringify(body)},true),
  delete: (taskId: number, expectedVersion: number) => request<void>(`/tasks/${taskId}?expectedVersion=${expectedVersion}`, {
    method: 'DELETE',
  }, true),
  transition: (
    taskId: number,
    action: TaskAction,
    expectedVersion: number,
    options: TransitionOptions = {},
  ) =>
    request<TaskResponse>(`/tasks/${taskId}/transitions`, {
      method: 'POST', body: JSON.stringify({ action, expectedVersion, ...options }),
    }, true),
  assign: (taskId: number, assigneeMemberId: number, expectedVersion: number) =>
    request<TaskResponse>(`/tasks/${taskId}/assignee`, {
      method: 'PUT', body: JSON.stringify({ assigneeMemberId, expectedVersion }),
    }, true),
  claim: (taskId: number, expectedVersion: number) =>
    request<TaskResponse>(`/tasks/${taskId}/assignee/me`, {
      method: 'PUT', body: JSON.stringify({ expectedVersion }),
    }, true),
  assigneeChangeRequests: (groupId:number) => request<AssigneeChangeRequest[]>(`/groups/${groupId}/assignee-change-requests`,{},true),
  requestAssigneeChange: (taskId:number,assigneeMemberId:number,reason?:string) => request<AssigneeChangeRequest>(`/tasks/${taskId}/assignee-change-requests`,{method:'POST',body:JSON.stringify({assigneeMemberId,reason})},true),
  decideAssigneeChange: (requestId:number,decision:'APPROVE'|'REJECT',expectedVersion:number,note?:string) => request<AssigneeChangeRequest>(`/task-assignee-change-requests/${requestId}/decision`,{method:'POST',body:JSON.stringify({decision,expectedVersion,note})},true),
  histories: (taskId: number) => request<TaskHistoryResponse[]>(`/tasks/${taskId}/histories`, {}, true),
  checklist: (taskId: number) => request<ChecklistResponse>(`/tasks/${taskId}/checklist-items`, {}, true),
  createChecklistItem: (taskId: number, content: string) =>
    request<ChecklistItemResponse>(`/tasks/${taskId}/checklist-items`, {
      method: 'POST', body: JSON.stringify({ content }),
    }, true),
  updateChecklistItem: (itemId: number, body: {
    content?: string; completed?: boolean; sortOrder?: number; expectedVersion: number;
  }) => request<ChecklistItemResponse>(`/checklist-items/${itemId}`, {
    method: 'PATCH', body: JSON.stringify(body),
  }, true),
  deleteChecklistItem: (itemId: number, expectedVersion: number) =>
    request<void>(`/checklist-items/${itemId}?expectedVersion=${expectedVersion}`, {
      method: 'DELETE',
    }, true),
  weeklyObjectives: (groupId: number, weekStart: string) =>
    request<WeeklyObjective[]>(
      `/groups/${groupId}/weekly-objectives?weekStart=${encodeURIComponent(weekStart)}`,
      {},
      true,
    ),
  createWeeklyObjective: (
    groupId: number,
    weekStart: string,
    title: string,
    position: number,
  ) => request<WeeklyObjective>(`/groups/${groupId}/weekly-objectives`, {
    method: 'POST', body: JSON.stringify({ weekStart, title, position }),
  }, true),
  updateWeeklyObjective: (
    objectiveId: number,
    title: string,
    position: number,
    expectedVersion: number,
  ) => request<WeeklyObjective>(`/weekly-objectives/${objectiveId}`, {
    method: 'PATCH', body: JSON.stringify({ title, position, expectedVersion }),
  }, true),
  deleteWeeklyObjective: (objectiveId: number, expectedVersion: number) =>
    request<void>(`/weekly-objectives/${objectiveId}?expectedVersion=${expectedVersion}`, {
      method: 'DELETE',
    }, true),
  taskWeeklyObjective: (taskId: number, weekStart: string) =>
    request<TaskWeeklyObjective>(
      `/tasks/${taskId}/weekly-objective?weekStart=${encodeURIComponent(weekStart)}`,
      {},
      true,
    ),
  linkTaskWeeklyObjective: (
    taskId: number,
    weekStart: string,
    objectiveId?: number,
  ) => request<TaskWeeklyObjective>(`/tasks/${taskId}/weekly-objective`, {
    method: 'PUT', body: JSON.stringify({ weekStart, objectiveId }),
  }, true),
};
