import { useEffect, useState } from 'react';
import { adminApi, AdminNotice } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';
import { AdminTable, formatTime, StatusBadge } from '../AdminShared';

export function AdminNoticesPage() {
  const { t } = useLanguage();
  const [notices, setNotices] = useState<AdminNotice[]>([]);
  const [loading, setLoading] = useState(true);
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  function load() {
    return adminApi.notices().then((page) => setNotices(page.items)).catch((value) => setError(errorMessage(value)));
  }

  useEffect(() => { load().finally(() => setLoading(false)); }, []);

  async function send() {
    setSending(true); setError(''); setSuccess('');
    try {
      await adminApi.createNotice({ title: title.trim(), message: message.trim(), scheduledAt });
      setTitle(''); setMessage(''); setScheduledAt('');
      setSuccess(t('공지를 예약했습니다. 예약 시각이 되면 전체 팀장에게 발송됩니다.', 'Notice scheduled. It will be sent to every team leader at the scheduled time.'));
      await load();
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setSending(false);
    }
  }

  async function cancel(id: number) {
    try { await adminApi.cancelNotice(id); await load(); }
    catch (value) { setError(errorMessage(value)); }
  }

  return <>
    <section className="admin-panel admin-notice-panel">
      <div className="admin-panel-heading">
        <div>
          <h2>{t('전체 팀장 공지', 'Notice to all team leaders')}</h2>
          <p>{t('예약한 시각에 활성 상태인 모든 팀 리더에게 알림으로 발송됩니다.', 'Delivered as a notification to every active team leader at the scheduled time.')}</p>
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      {success && <p className="success-message">{success}</p>}
      <label className="field"><span>{t('제목', 'Title')}</span>
        <input value={title} maxLength={160} onChange={(event) => setTitle(event.target.value)} />
      </label>
      <label className="field"><span>{t('내용', 'Content')}</span>
        <textarea value={message} maxLength={2000} onChange={(event) => setMessage(event.target.value)} />
      </label>
      <label className="field"><span>{t('예약 일시', 'Scheduled time')}</span>
        <input type="datetime-local" value={scheduledAt} onChange={(event) => setScheduledAt(event.target.value)} />
      </label>
      <button className="primary" type="button" disabled={sending || !title.trim() || !message.trim() || !scheduledAt}
        onClick={send}>{sending ? t('예약 중...', 'Scheduling...') : t('예약 발송', 'Schedule')}</button>
    </section>
    {!loading && <AdminTable title={t('공지 내역', 'Notice history')}
      emptyLabel={t('발송한 공지가 없습니다.', 'No notices yet.')}
      headers={[t('제목', 'Title'), t('예약 일시', 'Scheduled'), t('상태', 'Status'), t('수신자', 'Recipients'), t('발송 시각', 'Sent at')]}
      rows={notices.map((notice) => [
        notice.title,
        formatTime(notice.scheduledAt),
        <StatusBadge value={notice.status} />,
        notice.recipientCount ?? '-',
        notice.status === 'PENDING'
          ? <button className="secondary" type="button" onClick={() => cancel(notice.id)}>{t('취소', 'Cancel')}</button>
          : formatTime(notice.sentAt),
      ])} />}
  </>;
}
