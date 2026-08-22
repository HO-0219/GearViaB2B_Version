import { useEffect, useState } from 'react';
import { NavLink, Navigate, Outlet, useNavigate } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { adminApi, AdminMfaSetup, AdminMfaStatus } from '../../api/adminApi';
import { accessToken, errorMessage } from '../../api/client';
import { useLanguage } from '../../app/LanguageContext';

const tabs: { to: string; end?: boolean; label: [string, string] }[] = [
  { to: '/admin', end: true, label: ['운영 현황', 'Overview'] },
  { to: '/admin/users', label: ['사용자 관리', 'Users'] },
  { to: '/admin/reports', label: ['리포트', 'Reports'] },
  { to: '/admin/ai-settings', label: ['AI 설정', 'AI settings'] },
  { to: '/admin/audit-log', label: ['감사 로그', 'Audit log'] },
];

export function AdminShell() {
  const { t, language } = useLanguage();
  const navigate = useNavigate();
  const [mfaStatus, setMfaStatus] = useState<AdminMfaStatus>();
  const [mfaSetup, setMfaSetup] = useState<AdminMfaSetup>();
  const [mfaCode, setMfaCode] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!accessToken.get()) return;
    adminApi.mfaStatus().then((status) => { setMfaStatus(status); setLoading(false); })
      .catch((value) => { setError(errorMessage(value)); setLoading(false); });
  }, []);

  if (!accessToken.get()) return <Navigate to="/login?next=/admin" replace />;
  if (!mfaStatus) return <main className="admin-page admin-loading-page"><header><div><span className="page-eyebrow">RESTRICTED OPERATIONS</span><h1>B2BGearVia Admin</h1></div></header>{loading ? <p className="admin-loading">{t('관리자 보안 상태를 확인하는 중...', 'Checking admin security...')}</p> : error && <p className="error">{error}</p>}</main>;
  if (mfaStatus.enabled && !mfaStatus.sessionVerified) {
    return <Navigate to="/login?next=/admin&adminMfa=required" replace />;
  }

  async function setupMfa() {
    try { setMfaSetup(await adminApi.mfaSetup()); setError(''); }
    catch (value) { setError(errorMessage(value)); }
  }
  async function confirmMfa() {
    try {
      await adminApi.mfaConfirm(mfaCode);
      accessToken.clear();
      navigate('/login?next=/admin&adminMfa=required', { replace: true });
    } catch (value) { setError(errorMessage(value)); }
  }

  if (!mfaStatus.enabled) return <main className="admin-page"><header><div><span className="page-eyebrow">ADMIN SECURITY</span><h1>{t('관리자 MFA 설정', 'Set up admin MFA')}</h1><p>{t('운영 기능을 사용하기 전에 인증 앱 기반 MFA를 활성화해야 합니다.', 'Authenticator MFA is required before using operations features.')}</p></div></header>
    {error && <p className="error">{error}</p>}
    {!mfaStatus.encryptionConfigured ? <p className="error">ADMIN_MFA_ENCRYPTION_KEY_BASE64 {t('설정이 필요합니다.', 'must be configured.')}</p> : !mfaSetup ? <button className="primary" type="button" onClick={setupMfa}>{t('MFA 설정 시작', 'Start MFA setup')}</button> : <section className="admin-panel admin-mfa-setup"><h2>{t('인증 앱에 계정 추가', 'Add account to authenticator')}</h2><p>{t('인증 앱에서 QR 코드를 스캔한 뒤 표시되는 6자리 코드를 입력하세요.', 'Scan the QR code with your authenticator app, then enter the 6-digit code.')}</p><div className="admin-mfa-setup-grid"><div className="admin-mfa-qr"><QRCodeSVG value={mfaSetup.otpauthUri} size={216} level="M" marginSize={4} title={t('MFA 설정 QR 코드', 'MFA setup QR code')} /><strong>{t('인증 앱으로 스캔', 'Scan with authenticator')}</strong><small>Google Authenticator · Microsoft Authenticator · Authy</small></div><div className="admin-mfa-manual"><details><summary>{t('QR 스캔이 어려운 경우 수동 등록', 'Set up manually instead')}</summary><p>{t('인증 앱에 아래 비밀키를 입력하세요.', 'Enter this secret in your authenticator app.')}</p><code className="admin-secret">{mfaSetup.secret}</code><details><summary>otpauth URI</summary><code className="admin-uri">{mfaSetup.otpauthUri}</code></details></details><div className="admin-inline"><input aria-label={t('6자리 인증 코드', '6-digit verification code')} value={mfaCode} inputMode="numeric" autoComplete="one-time-code" onChange={(event) => setMfaCode(event.target.value)} placeholder={t('6자리 코드', '6-digit code')} /><button className="primary" type="button" onClick={confirmMfa}>{t('확인하고 활성화', 'Verify and enable')}</button></div></div></div></section>}
  </main>;

  return <main className="admin-page"><header><div><span className="page-eyebrow">RESTRICTED OPERATIONS</span><h1>B2BGearVia Admin</h1><p>{t('서버 허용 IP·ADMIN 권한·MFA를 모두 통과한 운영 화면입니다.', 'This console requires the server allowlist, ADMIN role, and MFA.')}</p></div><a href="/app">{t('서비스로 돌아가기', 'Back to app')}</a></header>
    <nav className="admin-tabs" aria-label={t('관리자 메뉴', 'Admin sections')}>{tabs.map((tab) => <NavLink key={tab.to} to={tab.to} end={tab.end}>{tab.label[language === 'ko' ? 0 : 1]}</NavLink>)}</nav>
    <Outlet />
  </main>;
}
