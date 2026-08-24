import { useEffect, useState } from 'react';
import { adminApi, AdminAudit } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';
import { AdminTable, StatusBadge } from '../AdminShared';

export function AdminAuditLogPage() {
  const { t } = useLanguage();
  const [auditLogs, setAuditLogs] = useState<AdminAudit[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    adminApi.auditLogs(page).then((result) => {
      setAuditLogs(result.items);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    })
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setLoading(false));
  }, [page]);

  if (loading) return <p className="admin-loading">{t('감사 로그를 불러오는 중...', 'Loading audit logs...')}</p>;
  return <>
    {error && <p className="error">{error}</p>}
    <AdminTable title={t('운영 감사로그', 'Operations audit log')} emptyLabel={t('감사 로그가 없습니다.', 'No audit logs to display.')}
      headers={[t('시간', 'Time'), t('운영자', 'Actor'), 'HTTP', t('경로', 'Path'), t('결과', 'Result'), 'IP']}
      rows={auditLogs.map((item) => [item.occurredAt.slice(0, 16), item.actorUserId ?? '-', `${item.method} ${item.status}`, <span className="admin-path-cell" title={item.path}>{item.path}</span>, <StatusBadge value={item.outcome} />, item.ipAddress ?? '-'])}
      pagination={{ page, totalPages, totalElements, onPrevious: () => setPage((value) => Math.max(0, value - 1)), onNext: () => setPage((value) => value + 1) }} />
  </>;
}
