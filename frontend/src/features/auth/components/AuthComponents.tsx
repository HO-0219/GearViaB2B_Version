import { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { BrandMark } from '../../../app/BrandMark';
import { useLanguage } from '../../../app/LanguageContext';

export function AuthLayout({ title, description, children }: {
  title: string; description: string; children: ReactNode;
}) {
  const { t } = useLanguage();
  return <main className="auth-page"><section className="brand"><span className="brand-mark"><BrandMark /></span><p>{t('Gearvia', 'GEARVIA')}</p><h1>{t('업무와 사람을 연결하고,', 'Connect work and people,')}<br />{t('하나의 흐름으로 움직이세요.', 'then move as one.')}</h1><span>{t('요청부터 완료와 리포트까지 연결하는 AI 기반 협업 업무 관리.', 'AI-powered collaboration from requests through completion and reporting.')}</span></section><section className="auth-card"><header><Link to="/" className="mobile-logo"><BrandMark />{t('Gearvia', 'Gearvia')}</Link><h2>{title}</h2><p>{description}</p></header>{children}</section></main>;
}

export function Field({ label, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return <label className="field"><span>{label}</span><input {...props} /></label>;
}

export function SubmitButton({ children, pending, disabled }: {
  children: ReactNode; pending?: boolean; disabled?: boolean;
}) {
  const { t } = useLanguage();
  return <button className="primary" type="submit" disabled={pending || disabled}>
    {pending ? t('처리 중...', 'Processing...') : children}
  </button>;
}
