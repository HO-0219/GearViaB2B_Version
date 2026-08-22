import { useEffect, useState } from 'react';
import { adminApi, AdminReportDelivery, AdminReportDownload } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';
import { AdminTable, formatTime, StatusBadge } from '../AdminShared';

export function AdminReportsPage() {
  const { t } = useLanguage();
  const [reportDownloads, setReportDownloads] = useState<AdminReportDownload[]>([]);
  const [reportDeliveries, setReportDeliveries] = useState<AdminReportDelivery[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([adminApi.reportDownloads(), adminApi.reportDeliveries()])
      .then(([downloadPage, deliveryPage]) => { setReportDownloads(downloadPage.items); setReportDeliveries(deliveryPage.items); })
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="admin-loading">{t('리포트 데이터를 불러오는 중...', 'Loading report data...')}</p>;
  return <>
    {error && <p className="error">{error}</p>}
    <AdminTable title={t('리포트 다운로드 현황', 'Report downloads')} emptyLabel={t('리포트 다운로드 기록이 없습니다.', 'No report downloads to display.')} headers={['ID', t('그룹', 'Group'), t('요청 사용자', 'Requested by'), t('범위', 'Scope'), t('기간', 'Period'), t('다운로드 시간', 'Downloaded')]} rows={reportDownloads.map((item) => [item.id, item.groupName, item.requestedByUserId, item.scope, item.periodType, formatTime(item.createdAt)])} />
    <AdminTable title={t('예약 리포트 발송 현황', 'Scheduled report deliveries')} emptyLabel={t('예약 리포트 발송 기록이 없습니다.', 'No scheduled report deliveries to display.')} headers={['ID', t('그룹', 'Group'), t('기간', 'Period'), t('언어', 'Language'), t('상태', 'Status'), t('재시도', 'Retries'), t('오류', 'Error'), t('최근 시도', 'Last attempt'), t('다음 재시도', 'Next retry'), t('발송 완료', 'Sent')]} rows={reportDeliveries.map((item) => [item.id, item.groupName, item.periodType, item.language, <StatusBadge value={item.status} />, item.retryCount, <span className="admin-message-cell" title={item.errorCode}>{item.errorCode ?? '-'}</span>, formatTime(item.lastAttemptAt), formatTime(item.nextRetryAt), formatTime(item.sentAt)])} />
  </>;
}
