import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { activatePwaUpdate, isPwaInstallAvailable, isPwaUpdateAvailable, promptPwaInstall } from './pwa';
import { useLanguage } from './LanguageContext';
import { accessToken, sessionMode } from '../api/client';
import { enablePushNotifications, inspectPushNotifications, PushSetupResult } from './pushNotifications';

export function PwaStatus() {
  const { t } = useLanguage();
  const { pathname } = useLocation();
  const [online, setOnline] = useState(navigator.onLine);
  const [installable, setInstallable] = useState(isPwaInstallAvailable());
  const [updateAvailable, setUpdateAvailable] = useState(isPwaUpdateAvailable());
  const [push, setPush] = useState<PushSetupResult>({ state: 'hidden', consentAgreed: false });
  const [pushPending, setPushPending] = useState(false);

  useEffect(() => {
    const onlineHandler = () => setOnline(true);
    const offlineHandler = () => setOnline(false);
    const installHandler = () => setInstallable(isPwaInstallAvailable());
    const installedHandler = () => setInstallable(false);
    const updateHandler = () => setUpdateAvailable(true);
    window.addEventListener('online', onlineHandler);
    window.addEventListener('offline', offlineHandler);
    window.addEventListener('pwa-install-available', installHandler);
    window.addEventListener('pwa-installed', installedHandler);
    window.addEventListener('pwa-update-available', updateHandler);
    return () => {
      window.removeEventListener('online', onlineHandler);
      window.removeEventListener('offline', offlineHandler);
      window.removeEventListener('pwa-install-available', installHandler);
      window.removeEventListener('pwa-installed', installedHandler);
      window.removeEventListener('pwa-update-available', updateHandler);
    };
  }, []);

  useEffect(() => {
    if (!accessToken.get() || sessionMode.isDemo()) return;
    let active = true;
    inspectPushNotifications()
      .then((result) => { if (active) setPush(result); })
      .catch(() => { if (active) setPush({ state: 'error', consentAgreed: false }); });
    return () => { active = false; };
  }, [pathname]);

  async function allowPush() {
    setPushPending(true);
    try { setPush(await enablePushNotifications()); }
    catch { setPush((current) => ({ ...current, state: 'error' })); }
    finally { setPushPending(false); }
  }

  const corePath = pathname === '/app' || pathname.startsWith('/groups')
    || pathname.startsWith('/tasks') || pathname === '/calendar'
    || pathname === '/notifications' || pathname === '/profile'
    || pathname === '/account' || pathname === '/payments';
  const showPush = push.state === 'prompt' || push.state === 'denied' || push.state === 'error';
  if (!corePath || sessionMode.isDemo() || (online && !installable && !updateAvailable && !showPush)) return null;
  return <aside className={`pwa-status ${online ? '' : 'offline'}`} role="status" aria-live="polite">
    <span>{!online
      ? t('오프라인입니다. 저장된 화면만 볼 수 있으며 조회·변경은 연결 후 가능합니다.', 'You are offline. Reconnect to view or update current data.')
      : updateAvailable ? t('새 버전이 준비되었습니다.', 'A new version is ready.')
        : installable ? t('이 기기에 앱으로 설치할 수 있습니다.', 'You can install this app on this device.')
          : push.state === 'denied' ? t('기기 설정에서 Gearvia 앱의 알림 권한을 허용하면 푸시 알림을 받을 수 있습니다.', 'Allow notifications for the Gearvia app in device settings to receive push alerts.')
            : push.state === 'error' ? t('푸시 알림을 설정하지 못했습니다. 잠시 후 다시 시도해 주세요.', 'Push notifications could not be set up. Please try again shortly.')
              : push.consentAgreed
                ? t('이전에 동의한 업무 알림을 이 기기에서도 푸시로 받아보세요.', 'Receive your previously accepted work alerts as push notifications on this device.')
                : t('알림을 허용하면 마감·멘션 등 업무 알림 수신에 동의하고 이 기기에서 푸시로 받습니다.', 'Allow alerts to consent to work notifications such as deadlines and mentions on this device.')}</span>
    {online && updateAvailable && <button type="button" onClick={activatePwaUpdate}>{t('업데이트', 'Update')}</button>}
    {online && !updateAvailable && installable && <button type="button" onClick={promptPwaInstall}>{t('설치', 'Install')}</button>}
    {online && !updateAvailable && !installable && (push.state === 'prompt' || push.state === 'error')
      && <button type="button" disabled={pushPending} onClick={allowPush}>{pushPending ? t('설정 중...', 'Setting up...') : t('알림 허용', 'Allow alerts')}</button>}
  </aside>;
}
