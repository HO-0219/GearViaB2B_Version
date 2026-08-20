import { FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../../../api/authApi';
import { errorMessage } from '../../../api/client';
import { AuthLayout, Field, SubmitButton } from '../components/AuthComponents';
import { useLanguage } from '../../../app/LanguageContext';

function RequestPage({ mode }: { mode: 'username' | 'password' }) {
  const { t } = useLanguage();
  const [email, setEmail] = useState(''); const [pending, setPending] = useState(false); const [done, setDone] = useState(false); const [error, setError] = useState('');
  const isUsername = mode === 'username';
  async function submit(event: FormEvent) { event.preventDefault(); setPending(true); setError(''); try { isUsername ? await authApi.remindUsername(email) : await authApi.requestPasswordReset(email); setDone(true); } catch (e) { setError(errorMessage(e)); } finally { setPending(false); } }
  return <AuthLayout title={isUsername ? t('아이디 찾기', 'Find username') : t('비밀번호 찾기', 'Reset password')} description={t('가입할 때 사용한 이메일을 입력해 주세요.', 'Enter the email address used for your account.')}>{done ? <section className="success"><strong>{t('메일함을 확인해 주세요.', 'Check your inbox.')}</strong><p>{t('가입된 계정이 있다면 안내 메일을 전송했습니다.', 'If an account exists, we sent instructions to that address.')}</p><Link to="/login" className="primary link-button">{t('로그인으로 돌아가기', 'Back to login')}</Link></section> : <form className="form" onSubmit={submit}><Field label={t('이메일', 'Email')} type="email" value={email} onChange={e => setEmail(e.target.value)} required />{error && <p className="error">{error}</p>}<SubmitButton pending={pending}>{isUsername ? t('아이디 안내 받기', 'Send username') : t('재설정 링크 받기', 'Send reset link')}</SubmitButton></form>}</AuthLayout>;
}
export function FindUsernamePage() { return <RequestPage mode="username" />; }
export function ForgotPasswordPage() { return <RequestPage mode="password" />; }

export function ResetPasswordPage() {
  const { t } = useLanguage();
  const [params] = useSearchParams(); const navigate = useNavigate();
  const email = params.get('email') ?? ''; const token = params.get('token') ?? '';
  const [password, setPassword] = useState(''); const [confirm, setConfirm] = useState(''); const [pending, setPending] = useState(false); const [error, setError] = useState('');
  async function submit(event: FormEvent) { event.preventDefault(); if (password !== confirm) return setError(t('비밀번호가 서로 다릅니다.', 'Passwords do not match.')); setPending(true); setError(''); try { await authApi.resetPassword(email, token, password); navigate('/login?reset=success'); } catch (e) { setError(errorMessage(e)); } finally { setPending(false); } }
  return <AuthLayout title={t('새 비밀번호 설정', 'Set a new password')} description={t('앞으로 사용할 비밀번호를 입력해 주세요.', 'Enter the password you want to use from now on.')}><form className="form" onSubmit={submit}><Field label={t('새 비밀번호', 'New password')} type="password" minLength={8} value={password} onChange={e => setPassword(e.target.value)} required /><Field label={t('새 비밀번호 확인', 'Confirm new password')} type="password" value={confirm} onChange={e => setConfirm(e.target.value)} required />{(!email || !token) && <p className="error">{t('유효한 재설정 링크가 아닙니다.', 'This reset link is invalid.')}</p>}{error && <p className="error">{error}</p>}<SubmitButton pending={pending} disabled={!email || !token}>{t('비밀번호 변경', 'Change password')}</SubmitButton></form></AuthLayout>;
}
