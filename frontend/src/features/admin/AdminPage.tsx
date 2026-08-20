import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { adminApi, AdminAudit, AdminGroup, AdminMfaSetup, AdminMfaStatus, AdminOverview, AdminReportDelivery, AdminReportDownload, AdminUser } from '../../api/adminApi';
import { accessToken, errorMessage } from '../../api/client';
import { useLanguage } from '../../app/LanguageContext';

export function AdminPage() {
  const { t } = useLanguage();
  const [overview, setOverview] = useState<AdminOverview>();
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [groups, setGroups] = useState<AdminGroup[]>([]);
  const [reportDownloads, setReportDownloads] = useState<AdminReportDownload[]>([]);
  const [reportDeliveries, setReportDeliveries] = useState<AdminReportDelivery[]>([]);
  const [auditLogs, setAuditLogs] = useState<AdminAudit[]>([]);
  const [mfaStatus, setMfaStatus] = useState<AdminMfaStatus>();
  const [mfaSetup, setMfaSetup] = useState<AdminMfaSetup>();
  const [mfaCode, setMfaCode] = useState('');
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const load = () => {
    setLoading(true);
    return Promise.all([adminApi.overview(), adminApi.users(), adminApi.groups(), adminApi.reportDownloads(), adminApi.reportDeliveries(), adminApi.auditLogs()])
    .then(([summary, userPage, groupPage, downloadPage, deliveryPage, auditPage]) => {
      setOverview(summary); setUsers(userPage.items); setGroups(groupPage.items);
      setReportDownloads(downloadPage.items); setReportDeliveries(deliveryPage.items);
      setAuditLogs(auditPage.items);
    }).finally(() => setLoading(false));
  };
  useEffect(() => {
    if (!accessToken.get()) return;
    adminApi.mfaStatus().then((status) => {
      setMfaStatus(status);
      if (status.enabled && status.sessionVerified) return load();
      setLoading(false);
    }).catch((value) => { setError(errorMessage(value)); setLoading(false); });
  }, []);
  if (!accessToken.get()) return <Navigate to="/login?next=/admin" replace />;
  if (!mfaStatus) return <main className="admin-page admin-loading-page"><header><div><span className="page-eyebrow">RESTRICTED OPERATIONS</span><h1>B2BGearVia Admin</h1></div></header>{loading ? <p className="admin-loading">{t('관리자 보안 상태를 확인하는 중...', 'Checking admin security...')}</p> : error && <p className="error">{error}</p>}</main>;
  if (mfaStatus?.enabled && !mfaStatus.sessionVerified && recoveryCodes.length === 0) {
    return <Navigate to="/login?next=/admin&adminMfa=required" replace />;
  }
  async function setupMfa() {
    try { setMfaSetup(await adminApi.mfaSetup()); setError(''); }
    catch (value) { setError(errorMessage(value)); }
  }
  async function confirmMfa() {
    try {
      const result = await adminApi.mfaConfirm(mfaCode);
      setRecoveryCodes(result.recoveryCodes); setMessage(t('MFA가 활성화됐습니다. 복구 코드를 안전한 곳에 저장하세요.', 'MFA is enabled. Store the recovery codes securely.'));
    } catch (value) { setError(errorMessage(value)); }
  }
  function loginAgain() {
    accessToken.clear();
    window.location.assign('/login?next=/admin&adminMfa=required');
  }
  async function status(user: AdminUser) {
    try {
      const updated = await adminApi.userStatus(user.id, user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE');
      setUsers((current) => current.map((value) => value.id === updated.id ? updated : value));
    } catch (value) { setError(errorMessage(value)); }
  }
  if (mfaStatus && !mfaStatus.enabled) return <main className="admin-page"><header><div><span className="page-eyebrow">ADMIN SECURITY</span><h1>{t('관리자 MFA 설정', 'Set up admin MFA')}</h1><p>{t('운영 기능을 사용하기 전에 인증 앱 기반 MFA를 활성화해야 합니다.', 'Authenticator MFA is required before using operations features.')}</p></div></header>
    {error && <p className="error">{error}</p>}
    {!mfaStatus.encryptionConfigured ? <p className="error">ADMIN_MFA_ENCRYPTION_KEY_BASE64 {t('설정이 필요합니다.', 'must be configured.')}</p> : !mfaSetup ? <button className="primary" type="button" onClick={setupMfa}>{t('MFA 설정 시작', 'Start MFA setup')}</button> : <section className="admin-panel"><h2>{t('인증 앱에 계정 추가', 'Add account to authenticator')}</h2><p>{t('아래 비밀키를 인증 앱에 직접 입력하거나 otpauth URI를 사용하세요.', 'Enter this secret in your authenticator or use the otpauth URI.')}</p><code className="admin-secret">{mfaSetup.secret}</code><details><summary>otpauth URI</summary><code className="admin-uri">{mfaSetup.otpauthUri}</code></details><div className="admin-inline"><input value={mfaCode} inputMode="numeric" autoComplete="one-time-code" onChange={(event) => setMfaCode(event.target.value)} placeholder={t('6자리 코드', '6-digit code')} /><button className="primary" type="button" onClick={confirmMfa}>{t('확인하고 활성화', 'Verify and enable')}</button></div></section>}
    {recoveryCodes.length > 0 && <section className="admin-panel"><h2>{t('일회용 복구 코드', 'One-time recovery codes')}</h2><p>{t('이 화면을 닫으면 다시 표시하지 않습니다.', 'These codes will not be shown again.')}</p><div className="recovery-codes">{recoveryCodes.map((code) => <code key={code}>{code}</code>)}</div><button className="primary" type="button" onClick={loginAgain}>{t('저장 완료 · 다시 로그인', 'Saved · log in again')}</button></section>}
  </main>;
  if (loading && !overview) return <main className="admin-page admin-loading-page"><header><div><span className="page-eyebrow">RESTRICTED OPERATIONS</span><h1>B2BGearVia Admin</h1></div></header><p className="admin-loading">{t('운영 데이터를 불러오는 중...', 'Loading operations data...')}</p></main>;
  return <main className="admin-page"><header><div><span className="page-eyebrow">RESTRICTED OPERATIONS</span><h1>B2BGearVia Admin</h1><p>{t('서버 허용 IP·ADMIN 권한·MFA를 모두 통과한 운영 화면입니다.', 'This console requires the server allowlist, ADMIN role, and MFA.')}</p></div><a href="/app">{t('서비스로 돌아가기', 'Back to app')}</a></header>
    {error && <p className="error">{error}</p>}{message && <p className="success-message">{message}</p>}
    {overview && <section className="admin-stats" aria-label={t('운영 현황 요약', 'Operations overview')}>
      {[
        [t('전체 사용자', 'Users'), overview.users],
        [t('활성 사용자', 'Active users'), overview.activeUsers],
        [t('정지 사용자', 'Suspended users'), overview.suspendedUsers],
        [t('전체 그룹', 'Groups'), overview.groups],
        [t('팀 그룹', 'Team groups'), overview.teamGroups],
        [t('리포트 다운로드', 'Report downloads'), overview.reportDownloads],
        [t('리포트 발송', 'Report deliveries'), overview.reportDeliveries],
        [t('리포트 발송 실패', 'Failed deliveries'), overview.failedReportDeliveries],
      ].map(([label, value]) => <article key={label}><span>{label}</span><strong>{value}</strong></article>)}
    </section>}
    <AdminTable title={t('사용자', 'Users')} emptyLabel={t('표시할 사용자가 없습니다.', 'No users to display.')} headers={['ID', t('계정', 'Account'), t('이메일', 'Email'), t('상태', 'Status'), t('작업', 'Action')]} rows={users.map((user) => [user.id, user.username, user.maskedEmail, <StatusBadge value={user.status} />, <button className={`admin-action ${user.status === 'ACTIVE' ? 'danger' : ''}`} type="button" onClick={() => status(user)}>{user.status === 'ACTIVE' ? t('정지', 'Suspend') : t('복구', 'Activate')}</button>])} />
    <AdminTable title={t('그룹 현황', 'Group status')} emptyLabel={t('표시할 그룹이 없습니다.', 'No groups to display.')} headers={['ID', t('그룹', 'Group'), t('유형', 'Type'), t('활성 멤버', 'Active members'), t('리포트 예약', 'Report schedule'), t('생성일', 'Created')]} rows={groups.map((group) => [group.id, group.name, group.type, group.activeMembers, <StatusBadge value={group.reportScheduleActive ? t('사용', 'Active') : t('미사용', 'Off')} />, formatTime(group.createdAt)])} />
    <AdminTable title={t('리포트 다운로드 현황', 'Report downloads')} emptyLabel={t('리포트 다운로드 기록이 없습니다.', 'No report downloads to display.')} headers={['ID', t('그룹', 'Group'), t('요청 사용자', 'Requested by'), t('범위', 'Scope'), t('기간', 'Period'), t('다운로드 시간', 'Downloaded')]} rows={reportDownloads.map((item) => [item.id, item.groupName, item.requestedByUserId, item.scope, item.periodType, formatTime(item.createdAt)])} />
    <AdminTable title={t('예약 리포트 발송 현황', 'Scheduled report deliveries')} emptyLabel={t('예약 리포트 발송 기록이 없습니다.', 'No scheduled report deliveries to display.')} headers={['ID', t('그룹', 'Group'), t('기간', 'Period'), t('언어', 'Language'), t('상태', 'Status'), t('재시도', 'Retries'), t('오류', 'Error'), t('최근 시도', 'Last attempt'), t('다음 재시도', 'Next retry'), t('발송 완료', 'Sent')]} rows={reportDeliveries.map((item) => [item.id, item.groupName, item.periodType, item.language, <StatusBadge value={item.status} />, item.retryCount, <span className="admin-message-cell" title={item.errorCode}>{item.errorCode ?? '-'}</span>, formatTime(item.lastAttemptAt), formatTime(item.nextRetryAt), formatTime(item.sentAt)])} />
    <AdminTable title={t('운영 감사로그', 'Operations audit log')} emptyLabel={t('감사 로그가 없습니다.', 'No audit logs to display.')} headers={[t('시간', 'Time'), t('운영자', 'Actor'), 'HTTP', t('경로', 'Path'), t('결과', 'Result'), 'IP']} rows={auditLogs.map((item) => [item.occurredAt.slice(0, 16), item.actorUserId ?? '-', `${item.method} ${item.status}`, <span className="admin-path-cell" title={item.path}>{item.path}</span>, <StatusBadge value={item.outcome} />, item.ipAddress ?? '-'])} />
  </main>;
}
function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}
function StatusBadge({ value }: { value: string }) {
  const { language } = useLanguage();
  const normalized = value.toUpperCase();
  const labels: Record<string, [string, string]> = {
    ACTIVE: ['활성', 'Active'], SUCCESS: ['성공', 'Success'], SUCCEEDED: ['성공', 'Succeeded'],
    SENT: ['발송 완료', 'Sent'], FAILED: ['실패', 'Failed'], FAILURE: ['실패', 'Failure'],
    SUSPENDED: ['정지', 'Suspended'], CANCELED: ['취소', 'Canceled'], CANCELLED: ['취소', 'Cancelled'],
    ERROR: ['오류', 'Error'], PENDING: ['대기', 'Pending'],
    RETRYING: ['재시도 중', 'Retrying'], WAITING: ['대기', 'Waiting'],
    OFF: ['미사용', 'Off'], INACTIVE: ['비활성', 'Inactive'],
  };
  const tone = ['ACTIVE', 'SUCCESS', 'SUCCEEDED', 'SENT', '완료', '사용'].includes(normalized)
    ? 'success'
    : ['FAILED', 'FAILURE', 'SUSPENDED', 'CANCELED', 'CANCELLED', 'ERROR', '실패', '정지'].includes(normalized)
      ? 'danger'
      : ['PENDING', 'RETRYING', 'WAITING', '대기'].includes(normalized)
        ? 'warning'
        : 'neutral';
  return <span className={`admin-status admin-status--${tone}`}>{labels[normalized]?.[language === 'ko' ? 0 : 1] ?? (value || '-')}</span>;
}
function AdminTable({ title, headers, rows, emptyLabel }: { title: string; headers: string[]; rows: ReactNode[][]; emptyLabel: string }) {
  return <section className="admin-panel admin-table-panel">
    <div className="admin-panel-heading"><h2>{title}</h2><span className="admin-count">{rows.length}</span></div>
    <div className="admin-table-wrap">
      <table>
        <thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead>
        <tbody>{rows.length > 0
          ? rows.map((row, index) => <tr key={index}>{row.map((cell, cellIndex) => <td data-label={headers[cellIndex]} key={cellIndex}>{cell}</td>)}</tr>)
          : <tr><td className="admin-empty" colSpan={headers.length}>{emptyLabel}</td></tr>}
        </tbody>
      </table>
    </div>
  </section>;
}
