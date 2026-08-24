import { useEffect, useState } from 'react';
import { adminApi, AdminLoginHistoryEntry } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';
import { AdminTable, formatTime, StatusBadge } from '../AdminShared';

export function AdminLoginHistoryPage() {
  const { t } = useLanguage();
  const [entries, setEntries] = useState<AdminLoginHistoryEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    adminApi.loginHistory().then((page) => setEntries(page.items))
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="admin-loading">{t('로그인 이력을 불러오는 중...', 'Loading login history...')}</p>;
  return <>
    {error && <p className="error">{error}</p>}
    <AdminTable title={t('로그인 이력', 'Login history')}
      emptyLabel={t('로그인 이력이 없습니다.', 'No login attempts recorded.')}
      headers={[t('계정', 'Account'), t('결과', 'Outcome'), t('IP', 'IP'), t('기기', 'Device'), t('시각', 'Time')]}
      rows={entries.map((entry) => [
        entry.username, <StatusBadge value={entry.outcome} />, entry.ipAddress ?? '-',
        entry.deviceName ?? '-', formatTime(entry.occurredAt),
      ])} />
  </>;
}
