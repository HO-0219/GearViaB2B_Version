import { useEffect, useState } from 'react';
import { adminApi, AdminMailSettings } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';

export function AdminMailSettingsPage() {
  const { t } = useLanguage();
  const [status, setStatus] = useState<AdminMailSettings>();
  const [form, setForm] = useState({ host: '', port: 587, username: '', password: '', smtpAuth: true, starttls: true, fromAddress: '', enabled: false });
  const [clearPassword, setClearPassword] = useState(false);
  const [busy, setBusy] = useState<'save' | 'test'>(); const [message, setMessage] = useState(''); const [error, setError] = useState('');
  function apply(value: AdminMailSettings) { setStatus(value); setForm(current => ({ ...current, host: value.host, port: value.port, username: value.username ?? '', password: '', smtpAuth: value.smtpAuth, starttls: value.starttls, fromAddress: value.fromAddress, enabled: value.enabled })); }
  useEffect(() => { adminApi.mailSettings().then(apply).catch(v => setError(errorMessage(v))); }, []);
  const body = () => ({ ...form, username: form.username || undefined, password: clearPassword ? '' : (form.password || undefined) });
  async function save() { setBusy('save'); setError(''); setMessage(''); try { apply(await adminApi.updateMailSettings({ ...body(), enabled: form.enabled })); setClearPassword(false); setMessage(t('SMTP 설정을 저장했습니다.', 'SMTP settings saved.')); } catch (v) { setError(errorMessage(v)); } finally { setBusy(undefined); } }
  async function test() { setBusy('test'); setError(''); setMessage(''); try { const result = await adminApi.testMailSettings(body()); result.success ? setMessage(result.message) : setError(result.message); } catch (v) { setError(errorMessage(v)); } finally { setBusy(undefined); } }
  const field = <K extends keyof typeof form>(key: K, value: typeof form[K]) => setForm(current => ({ ...current, [key]: value }));
  if (!status) return <p className="admin-loading">{t('SMTP 설정을 불러오는 중...', 'Loading SMTP settings...')}</p>;
  return <section className="admin-panel admin-mail-settings"><div className="admin-panel-heading"><div><h2>{t('SMTP 설정', 'SMTP settings')}</h2><p>{t('저장된 설정은 즉시 적용되며 배포 환경 설정보다 우선합니다.', 'Saved settings apply immediately and override deployment defaults.')}</p></div><span>{status.source === 'DATABASE' ? 'DB' : 'ENV'}</span></div>
    {error && <p className="error">{error}</p>}<div className="admin-mail-grid"><label className="field"><span>{t('호스트', 'Host')}</span><input value={form.host} onChange={e => field('host', e.target.value)} /></label><label className="field"><span>{t('포트', 'Port')}</span><input type="number" min="1" max="65535" value={form.port} onChange={e => field('port', Number(e.target.value))} /></label><label className="field"><span>{t('사용자명', 'Username')}</span><input value={form.username} onChange={e => field('username', e.target.value)} /></label><label className="field"><span>{t('비밀번호', 'Password')}</span><input type="password" disabled={clearPassword} value={form.password} placeholder={status.passwordConfigured ? t('저장된 비밀번호 유지', 'Keep saved password') : t('미설정', 'Not configured')} onChange={e => field('password', e.target.value)} /></label><label className="field admin-mail-wide"><span>{t('발신 주소', 'From address')}</span><input type="email" value={form.fromAddress} onChange={e => field('fromAddress', e.target.value)} /></label></div>
    <div className="admin-mail-options"><label><input type="checkbox" checked={form.smtpAuth} onChange={e => field('smtpAuth', e.target.checked)} /> SMTP AUTH</label><label><input type="checkbox" checked={form.starttls} onChange={e => field('starttls', e.target.checked)} /> STARTTLS</label><label><input type="checkbox" checked={form.enabled} onChange={e => field('enabled', e.target.checked)} /> {t('메일 발송 활성화', 'Enable mail delivery')}</label><label><input type="checkbox" checked={clearPassword} onChange={e => setClearPassword(e.target.checked)} /> {t('저장된 비밀번호 삭제', 'Clear saved password')}</label></div><div className="admin-inline"><button className="secondary" disabled={!!busy} onClick={test}>{busy === 'test' ? t('테스트 중...', 'Testing...') : t('연결 테스트', 'Test connection')}</button><button className="primary" disabled={!!busy} onClick={save}>{busy === 'save' ? t('저장 중...', 'Saving...') : t('저장', 'Save')}</button></div>{message && <p className="success-message">{message}</p>}
  </section>;
}
