import { useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { GroupsPage } from '../features/group/pages/GroupsPage';
import { GroupDetailPage } from '../features/group/pages/GroupDetailPage';
import { GroupMembersPage } from '../features/group/pages/GroupMembersPage';
import { InvitationAcceptPage } from '../features/group/pages/InvitationAcceptPage';
import { AccountPage } from '../features/user/pages/AccountPage';
import { ProfilePage } from '../features/user/pages/ProfilePage';
import { TasksPage } from '../features/task/pages/TasksPage';
import { TaskDetailPage } from '../features/task/pages/TaskDetailPage';
import { HomePage } from './HomePage';
import { NotificationsPage } from '../features/notification/pages/NotificationsPage';
import { CalendarPage } from '../features/calendar/pages/CalendarPage';
import { GroupDashboardPage } from '../features/dashboard/pages/GroupDashboardPage';
import { PwaStatus } from './PwaStatus';
import { LanguageProvider } from './LanguageContext';
import { useLanguage } from './LanguageContext';
import { PrivacyPage, TermsPage } from './PublicPages';
import { PageMeta } from './PageMeta';
import { SessionKeepAlive } from './SessionKeepAlive';
import { AdminShell } from '../features/admin/AdminShell';
import { AdminOverviewPage } from '../features/admin/pages/AdminOverviewPage';
import { AdminUsersPage } from '../features/admin/pages/AdminUsersPage';
import { AdminReportsPage } from '../features/admin/pages/AdminReportsPage';
import { AdminAuditLogPage } from '../features/admin/pages/AdminAuditLogPage';
import { AdminAiSettingsPage } from '../features/admin/pages/AdminAiSettingsPage';
import { AiAssistantPage } from '../features/assistant/AiAssistantPage';
import { ProjectsPage } from '../features/project/pages/ProjectsPage';
import { ProjectFlowPage } from '../features/project/pages/ProjectFlowPage';
import { EmergencyIssuesPage } from '../features/project/pages/EmergencyIssuesPage';
import { ChatHubPage, ChatPage } from '../features/chat/ChatPage';

export default function App() {
  return <LanguageProvider><BrowserRouter>
    <SkipLink />
    <RouteAnnouncer />
    <PageMeta />
    <SessionKeepAlive />
    <div id="main-content" tabIndex={-1}><Routes>
    <Route path="/" element={<Navigate to="/login" replace />} />
    <Route path="/app" element={<HomePage />} />
    <Route path="/privacy" element={<PrivacyPage />} />
    <Route path="/terms" element={<TermsPage />} />
    <Route path="/profile" element={<ProfilePage />} />
    <Route path="/account" element={<AccountPage />} />
    <Route path="/admin" element={<AdminShell />}>
      <Route index element={<AdminOverviewPage />} />
      <Route path="users" element={<AdminUsersPage />} />
      <Route path="reports" element={<AdminReportsPage />} />
      <Route path="ai-settings" element={<AdminAiSettingsPage />} />
      <Route path="audit-log" element={<AdminAuditLogPage />} />
    </Route>
    <Route path="/groups" element={<GroupsPage />} />
    <Route path="/groups/:groupId" element={<GroupDetailPage />} />
    <Route path="/groups/:groupId/members" element={<GroupMembersPage />} />
    <Route path="/groups/:groupId/tasks" element={<TasksPage />} />
    <Route path="/groups/:groupId/projects" element={<ProjectsPage />} />
    <Route path="/chat" element={<ChatHubPage />} />
    <Route path="/groups/:groupId/chat" element={<ChatPage />} />
    <Route path="/projects/:projectId/flow" element={<ProjectFlowPage />} />
    <Route path="/groups/:groupId/emergency-issues" element={<EmergencyIssuesPage />} />
    <Route path="/tasks/:taskId" element={<TaskDetailPage />} />
    <Route path="/notifications" element={<NotificationsPage />} />
    <Route path="/assistant" element={<AiAssistantPage />} />
    <Route path="/calendar" element={<CalendarPage />} />
    <Route path="/groups/:groupId/dashboard" element={<GroupDashboardPage />} />
    <Route path="/group-invitations/accept" element={<InvitationAcceptPage />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="*" element={<Navigate to="/" replace />} />
    </Routes></div>
    <PwaStatus />
  </BrowserRouter></LanguageProvider>;
}

function SkipLink() {
  const { t } = useLanguage();
  return <a className="skip-link" href="#main-content">{t('본문으로 건너뛰기', 'Skip to main content')}</a>;
}

function RouteAnnouncer() {
  const { language } = useLanguage();
  const location = useLocation();
  const label = pageLabel(location.pathname, language);
  useEffect(() => {
    window.requestAnimationFrame(() => document.getElementById('main-content')?.focus());
  }, [label, location.pathname]);
  return <span className="sr-only" role="status" aria-live="polite">{language === 'ko' ? `${label} 페이지` : `${label} page`}</span>;
}

function pageLabel(pathname: string, language: 'ko' | 'en') {
  if (language === 'en') {
    if (pathname === '/') return 'Log in'; if (pathname === '/app') return 'Dashboard'; if (pathname === '/chat') return 'Chat'; if (pathname === '/calendar') return 'Calendar'; if (pathname === '/notifications') return 'Alerts'; if (pathname === '/assistant') return 'AI assistant';
    if (pathname === '/groups') return 'Groups'; if (pathname === '/profile') return 'Profile'; if (pathname === '/account') return 'Account settings';
    if (pathname === '/admin') return 'Admin overview';
    if (pathname === '/admin/users') return 'Admin users';
    if (pathname === '/admin/reports') return 'Admin reports';
    if (pathname === '/admin/ai-settings') return 'Admin AI settings';
    if (pathname === '/admin/audit-log') return 'Admin audit log';
    if (/\/dashboard$/.test(pathname)) return 'Group dashboard'; if (/\/members$/.test(pathname)) return 'Team members'; if (/\/chat$/.test(pathname)) return 'Group chat'; if (/\/projects$/.test(pathname)) return 'Projects'; if (/^\/projects\/\d+\/flow$/.test(pathname)) return 'Project issue flow'; if (/\/tasks$/.test(pathname)) return 'Tasks'; if (/^\/tasks\//.test(pathname)) return 'Task details';
    if (/^\/groups\/\d+$/.test(pathname)) return 'Group settings'; if (pathname === '/login') return 'Log in';
  }
  if (pathname === '/') return '로그인';
  if (pathname === '/app') return '내 대시보드';
  if (pathname === '/chat') return '채팅';
  if (pathname === '/calendar') return '캘린더';
  if (pathname === '/notifications') return '알림';
  if (pathname === '/assistant') return 'AI 업무 비서';
  if (/^\/groups\/\d+\/dashboard$/.test(pathname)) return '그룹 대시보드';
  if (/^\/groups\/\d+\/members$/.test(pathname)) return '팀원 목록';
  if (/^\/groups\/\d+\/tasks$/.test(pathname)) return '업무 목록';
  if (/^\/groups\/\d+\/projects$/.test(pathname)) return '프로젝트 목록';
  if (/^\/groups\/\d+\/chat$/.test(pathname)) return '그룹 채팅';
  if (/^\/projects\/\d+\/flow$/.test(pathname)) return '프로젝트 작업 내용';
  if (/^\/tasks\/\d+$/.test(pathname)) return '업무 상세';
  if (pathname === '/groups') return '그룹 목록';
  if (/^\/groups\/\d+$/.test(pathname)) return '그룹 상세';
  if (pathname === '/profile') return '프로필';
  if (pathname === '/account') return '계정 설정';
  if (pathname === '/admin') return '운영자 · 현황';
  if (pathname === '/admin/users') return '운영자 · 사용자';
  if (pathname === '/admin/reports') return '운영자 · 리포트';
  if (pathname === '/admin/ai-settings') return '운영자 · AI 설정';
  if (pathname === '/admin/audit-log') return '운영자 · 감사 로그';
  if (pathname === '/login') return '로그인';
  return 'B2BGearVia';
}
