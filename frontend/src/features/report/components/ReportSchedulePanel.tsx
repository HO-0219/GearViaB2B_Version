import { FormEvent, useEffect, useState } from 'react';
import { errorMessage } from '../../../api/client';
import { reportApi, ReportSchedule } from '../../../api/reportApi';
import { useLanguage } from '../../../app/LanguageContext';

export function ReportSchedulePanel({ groupId }: { groupId: number }) {
  const { t } = useLanguage();
  const [schedule, setSchedule] = useState<ReportSchedule>();
  const [recipientEmail, setRecipientEmail] = useState('');
  const [editingRecipient, setEditingRecipient] = useState(false);
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    setPending(true);
    reportApi.schedule(groupId)
      .then((value) => {
        setSchedule(value);
        setRecipientEmail(value.recipientEmail);
        setError('');
      })
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setPending(false));
  }, [groupId]);

  async function saveSchedule(event: FormEvent) {
    event.preventDefault();
    if (!schedule) return;
    const nextRecipientEmail = recipientEmail.trim();
    if (nextRecipientEmail !== schedule.recipientEmail
        && !window.confirm(t(`앞으로 리포트를 ${nextRecipientEmail}(으)로 받을까요?`, `Send future reports to ${nextRecipientEmail}?`))) return;
    setPending(true);
    setError('');
    try {
      const updated = await reportApi.updateSchedule(groupId, {
        recipientEmail: nextRecipientEmail,
        weeklyEnabled: schedule.weeklyEnabled,
        weeklyDay: schedule.weeklyDay,
        monthlyEnabled: schedule.monthlyEnabled,
        monthlyDay: schedule.monthlyDay,
        language: schedule.language,
      });
      setSchedule(updated);
      setRecipientEmail(updated.recipientEmail);
      setEditingRecipient(false);
      setMessage(t('리포트 메일 일정을 저장했습니다.', 'Report email schedule saved.'));
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setPending(false);
    }
  }

  if (!schedule) {
    return <section className="report-schedule-panel group-subsection">
      <p>{pending ? t('리포트 일정을 불러오는 중...', 'Loading report schedule...') : t('리포트 일정을 불러오지 못했습니다.', 'Could not load the report schedule.')}</p>
      {error && <p className="error">{error}</p>}
    </section>;
  }

  return <section className="report-schedule-panel group-subsection">
    <form className="report-schedule-form" onSubmit={saveSchedule}><header><div><span className="page-eyebrow">EMAIL REPORT</span><h3>{t('메일 리포트 일정', 'Email report schedule')}</h3><p>{t('원하는 주기와 언어로 팀 업무 요약을 받아보세요.', 'Receive team summaries on your preferred schedule and language.')}</p></div><span className="report-schedule-state">{schedule.weeklyEnabled || schedule.monthlyEnabled ? t('발송 설정됨', 'Scheduled') : t('발송 꺼짐', 'Disabled')}</span></header>
      <label className="report-cycle-toggle"><span><input type="checkbox" checked={schedule.weeklyEnabled} disabled={!schedule.weeklyEligible} onChange={(event) => setSchedule({ ...schedule, weeklyEnabled: event.target.checked })} /><i /></span><strong>{t('주간 리포트', 'Weekly report')}</strong><small>{t('선택한 요일 오전에 발송', 'Delivered in the morning on your chosen day')}</small></label>
      <label className="report-select-field"><span>{t('발송 요일', 'Delivery day')}</span><select value={schedule.weeklyDay ?? 'MONDAY'} disabled={!schedule.weeklyEnabled} onChange={(event) => setSchedule({ ...schedule, weeklyDay: event.target.value })}>{weekdays.map(([value, ko, en]) => <option value={value} key={value}>{t(ko, en)}</option>)}</select></label>
      {!schedule.weeklyEligible && <small>{t(`${schedule.weeklyMinimumDays}일 이상 사용 후 설정할 수 있습니다.`, `Available after ${schedule.weeklyMinimumDays} days of use.`)}</small>}
      <label className="report-cycle-toggle"><span><input type="checkbox" checked={schedule.monthlyEnabled} disabled={!schedule.monthlyEligible} onChange={(event) => setSchedule({ ...schedule, monthlyEnabled: event.target.checked })} /><i /></span><strong>{t('월간 리포트', 'Monthly report')}</strong><small>{t('매월 선택한 날짜에 발송', 'Delivered monthly on your chosen date')}</small></label>
      <label className="report-select-field"><span>{t('발송일', 'Delivery date')}</span><select value={schedule.monthlyDay ?? 1} disabled={!schedule.monthlyEnabled} onChange={(event) => setSchedule({ ...schedule, monthlyDay: Number(event.target.value) })}>{Array.from({ length: 28 }, (_, index) => index + 1).map((day) => <option value={day} key={day}>{t(`${day}일`, `Day ${day}`)}</option>)}</select></label>
      {!schedule.monthlyEligible && <small>{t(`${schedule.monthlyMinimumDays}일 이상 사용 후 설정할 수 있습니다.`, `Available after ${schedule.monthlyMinimumDays} days of use.`)}</small>}
      <label className="report-select-field report-language-field"><span>{t('리포트 언어', 'Report language')}</span><select value={schedule.language} onChange={(event) => setSchedule({ ...schedule, language: event.target.value as ReportSchedule['language'] })}><option value="KO">한국어</option><option value="EN">English</option><option value="BOTH">{t('한글 + 영문', 'Korean + English')}</option></select><small>{t('두 언어를 선택하면 각각 한 부씩 발송합니다.', 'Both sends one copy in each language.')}</small></label>
      <div className={`report-email-field ${editingRecipient ? 'editing' : ''}`}><div><span>{t('수신 이메일', 'Recipient email')}</span>{editingRecipient ? <button type="button" onClick={() => { setRecipientEmail(schedule.recipientEmail); setEditingRecipient(false); }}>{t('취소', 'Cancel')}</button> : <button type="button" onClick={() => setEditingRecipient(true)}>{t('수정', 'Edit')}</button>}</div><label><span aria-hidden="true">@</span><input type="email" value={recipientEmail} required maxLength={255} readOnly={!editingRecipient} onChange={(event) => setRecipientEmail(event.target.value)} /></label><small>{editingRecipient ? t('새 수신 주소를 입력한 뒤 아래 저장 버튼을 눌러주세요.', 'Enter a new recipient and save the schedule below.') : t('실수로 바뀌지 않도록 잠겨 있습니다.', 'Locked to prevent accidental changes.')}</small></div>
      <footer><small>{t('일정 변경은 저장 후 다음 발송부터 적용됩니다.', 'Changes apply from the next delivery after saving.')}</small><button className="primary" disabled={pending}>{pending ? t('저장 중...', 'Saving...') : t('메일 일정 저장', 'Save schedule')}</button></footer>
    </form>{message && <p className="success-message">{message}</p>}{error && <p className="error">{error}</p>}
  </section>;
}

const weekdays = [['MONDAY', '월요일', 'Monday'], ['TUESDAY', '화요일', 'Tuesday'], ['WEDNESDAY', '수요일', 'Wednesday'], ['THURSDAY', '목요일', 'Thursday'], ['FRIDAY', '금요일', 'Friday'], ['SATURDAY', '토요일', 'Saturday'], ['SUNDAY', '일요일', 'Sunday']] as const;
