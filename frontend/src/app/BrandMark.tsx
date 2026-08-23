import { BRANDING_LOGO_URL, useBranding } from './useBranding';

export function BrandMark({ className = '' }: { className?: string }) {
  const { organizationName, hasLogo } = useBranding();
  if (hasLogo) return <img className={className} src={BRANDING_LOGO_URL} alt={organizationName} />;
  return <svg
    className={className}
    viewBox="0 0 48 48"
    role="img"
    aria-label={organizationName}
  >
    <path d="M15 8h17a5 5 0 0 1 5 5v22a5 5 0 0 1-5 5H15" />
    <path d="m19 17 4 4 8-9" />
    <path d="M9 30h22" />
    <path d="m26 25 5 5-5 5" />
  </svg>;
}
