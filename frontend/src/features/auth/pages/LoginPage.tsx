import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../../../api/authApi';
import { accessToken, ApiError, errorMessage, sessionMode } from '../../../api/client';
import { AuthLayout, Field, SubmitButton } from '../components/AuthComponents';
import { useLanguage } from '../../../app/LanguageContext';

export function LoginPage() {
  const { t } = useLanguage();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = params.get('next');
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [showMfa, setShowMfa] = useState(params.get('adminMfa') === 'required');
  const [pending, setPending] = useState(sessionMode.isDemo());
  const [error, setError] = useState('');
  useEffect(() => {
    if (accessToken.get()) {
      navigate(loginDestination(next), { replace: true });
      return;
    }
    if (sessionMode.isDemo()) {
      authApi.logout().catch(() => undefined).finally(() => {
        accessToken.clear();
        sessionMode.clear();
        setPending(false);
      });
    }
  }, [navigate, next]);

  async function submit(event: FormEvent) {
    event.preventDefault(); setPending(true); setError('');
    try {
      const tokens = await authApi.login(identifier, password, mfaCode);
      accessToken.set(tokens.accessToken, tokens.expiresIn);
      sessionMode.clear();
      const me = await authApi.me();
      navigate(me.passwordChangeRequired ? '/account' : loginDestination(next), { replace: true });
    } catch (caught) {
      if ((caught as ApiError)?.code === 'ADMIN_MFA_REQUIRED') setShowMfa(true);
      setError(errorMessage(caught));
    }
    finally { setPending(false); }
  }

  return <AuthLayout title={t('로그인', 'Log in')} description={t('회사에서 등록한 계정으로 접속하세요.', 'Sign in with the account provided by your company.')}>
    <form onSubmit={submit} className="form"><Field label={t('회사 메일 또는 관리자 ID', 'Company email or admin ID')} value={identifier} onChange={e => setIdentifier(e.target.value)} autoComplete="username" required /><Field label={t('비밀번호', 'Password')} type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required />
      {showMfa && <Field label={t('관리자 MFA 코드', 'Admin MFA code')} value={mfaCode} onChange={e => setMfaCode(e.target.value)} autoComplete="one-time-code" required />}
      {error && <p className="error">{error}</p>}<SubmitButton pending={pending}>{t('로그인', 'Log in')}</SubmitButton>
    </form>
    <Link className="auth-admin-link" to="/admin">{t('관리자 페이지', 'Admin console')}</Link>
  </AuthLayout>;
}

function loginDestination(next: string | null) {
  if (!next?.startsWith('/') || next.startsWith('//')) return '/app';
  if (next === '/login' || next.startsWith('/oauth/')) return '/app';
  return next;
}
