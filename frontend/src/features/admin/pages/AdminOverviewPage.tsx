import { useEffect, useState } from 'react';
import { adminApi, AdminGroup, AdminOverview } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';
import { AdminTable, formatTime, StatusBadge } from '../AdminShared';

export function AdminOverviewPage() {
  const { t } = useLanguage();
  const [overview, setOverview] = useState<AdminOverview>();
  const [groups, setGroups] = useState<AdminGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([adminApi.overview(), adminApi.groups()])
      .then(([summary, groupPage]) => { setOverview(summary); setGroups(groupPage.items); })
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="admin-loading">{t('운영 데이터를 불러오는 중...', 'Loading operations data...')}</p>;
  return <>
    {error && <p className="error">{error}</p>}
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
    <AdminTable title={t('그룹 현황', 'Group status')} emptyLabel={t('표시할 그룹이 없습니다.', 'No groups to display.')} headers={['ID', t('그룹', 'Group'), t('유형', 'Type'), t('활성 멤버', 'Active members'), t('리포트 예약', 'Report schedule'), t('생성일', 'Created')]} rows={groups.map((group) => [group.id, group.name, group.type, group.activeMembers, <StatusBadge value={group.reportScheduleActive ? t('사용', 'Active') : t('미사용', 'Off')} />, formatTime(group.createdAt)])} />
  </>;
}
