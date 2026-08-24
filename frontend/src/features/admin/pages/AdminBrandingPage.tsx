import { useState } from 'react';
import { adminApi } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';
import { BRANDING_LOGO_URL, refreshBranding, useBranding } from '../../../app/useBranding';

export function AdminBrandingPage() {
  const { t } = useLanguage();
  const branding = useBranding();
  const [name, setName] = useState('');
  const [nameTouched, setNameTouched] = useState(false);
  const [logo, setLogo] = useState<File>();
  const [removeLogo, setRemoveLogo] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const currentName = nameTouched ? name : branding.organizationName;

  async function save() {
    setSaving(true); setError(''); setSuccess('');
    try {
      await adminApi.updateBranding(currentName.trim(), logo, removeLogo);
      await refreshBranding();
      setLogo(undefined);
      setRemoveLogo(false);
      setNameTouched(false);
      setSuccess(t('브랜딩 설정을 저장했습니다.', 'Branding settings saved.'));
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setSaving(false);
    }
  }

  return <section className="admin-panel admin-notice-panel">
    <div className="admin-panel-heading">
      <div>
        <h2>{t('로고 · 서비스 이름', 'Logo & service name')}</h2>
        <p>{t('네비게이션, 로그인 화면, 관리자 콘솔 헤더에 표시되는 이름과 로고를 바꿉니다.', 'Change the name and logo shown in navigation, the login screen, and the admin console header.')}</p>
      </div>
    </div>
    {error && <p className="error">{error}</p>}
    {success && <p className="success-message">{success}</p>}
    <div className="admin-branding-preview">
      {branding.hasLogo ? <img src={BRANDING_LOGO_URL} alt={branding.organizationName} /> : <span className="admin-branding-preview-empty">{t('기본 로고 사용 중', 'Using default logo')}</span>}
    </div>
    <label className="field">
      <span>{t('조직/서비스 이름', 'Organization / service name')}</span>
      <input value={currentName} maxLength={80}
        onChange={(event) => { setName(event.target.value); setNameTouched(true); }}
        placeholder="B2BGearVia" />
    </label>
    <label className="field">
      <span>{t('로고 이미지 (PNG/JPG, 2MB 이하)', 'Logo image (PNG/JPG, up to 2MB)')}</span>
      <input type="file" accept="image/png,image/jpeg"
        onChange={(event) => { setLogo(event.target.files?.[0]); setRemoveLogo(false); }} />
    </label>
    {branding.hasLogo && <label className="admin-inline-checkbox">
      <input type="checkbox" checked={removeLogo}
        onChange={(event) => { setRemoveLogo(event.target.checked); if (event.target.checked) setLogo(undefined); }} />
      <span>{t('로고 삭제하고 기본 로고 사용', 'Remove the logo and use the default one')}</span>
    </label>}
    <button className="primary" type="button" disabled={saving} onClick={save}>
      {saving ? t('저장 중...', 'Saving...') : t('저장', 'Save')}
    </button>
  </section>;
}
