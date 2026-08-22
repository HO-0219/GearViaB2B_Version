import { useEffect, useState } from 'react';
import { adminApi, AdminAiConnectionResult, AdminAiSettingsStatus, AdminAiVerticalStatus } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';

export function AdminAiSettingsPage() {
  const { t } = useLanguage();
  const [status, setStatus] = useState<AdminAiSettingsStatus>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [testing, setTesting] = useState(false);
  const [results, setResults] = useState<{ report: AdminAiConnectionResult; assistant: AdminAiConnectionResult }>();

  useEffect(() => {
    adminApi.aiSettings().then(setStatus)
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setLoading(false));
  }, []);

  async function runTest() {
    setTesting(true); setError(''); setResults(undefined);
    try { setResults(await adminApi.testAiConnections()); }
    catch (value) { setError(errorMessage(value)); }
    finally { setTesting(false); }
  }

  if (loading) return <p className="admin-loading">{t('AI 설정을 불러오는 중...', 'Loading AI settings...')}</p>;
  return <>
    {error && <p className="error">{error}</p>}
    <section className="admin-panel admin-notice-panel">
      <div className="admin-panel-heading"><div><h2>{t('AI 연동 상태', 'AI integration status')}</h2><p>{t('API 키와 모델은 서버 관리자가 SSH로 설정합니다. 이 화면은 현재 상태 확인과 연결 테스트만 제공합니다.', 'The API key and model are set by the server administrator over SSH. This screen only shows status and lets you test the connection.')}</p></div></div>
      {status && <div className="ai-settings-grid">
        <VerticalCard title={t('AI 비서', 'AI assistant')} status={status.assistant} testResult={results?.assistant} />
        <VerticalCard title={t('AI 주간 리포트', 'AI weekly report')} status={status.report} testResult={results?.report} />
      </div>}
      <button className="primary" type="button" disabled={testing} onClick={runTest}>{testing ? t('연결 테스트 중...', 'Testing connection...') : t('연결 테스트', 'Test connection')}</button>
      {status && status.supportedModels.length > 0 && <p className="admin-notice-panel-footnote">{t('지원 모델 목록', 'Supported models')}: {status.supportedModels.join(', ')}</p>}
    </section>
  </>;
}

function VerticalCard({ title, status, testResult }: { title: string; status: AdminAiVerticalStatus; testResult?: AdminAiConnectionResult }) {
  const { t } = useLanguage();
  return <article className="ai-settings-card">
    <h3>{title}</h3>
    <dl>
      <div><dt>{t('활성화', 'Enabled')}</dt><dd>{status.enabled ? t('예', 'Yes') : t('아니오', 'No')}</dd></div>
      <div><dt>{t('API 키', 'API key')}</dt><dd>{status.apiKeyConfigured ? status.maskedApiKey : t('미설정', 'Not configured')}</dd></div>
      <div><dt>{t('모델', 'Model')}</dt><dd>{status.model || '-'}</dd></div>
    </dl>
    {testResult && <p className={testResult.success ? 'success-message' : 'error'}>{testResult.message}</p>}
  </article>;
}
