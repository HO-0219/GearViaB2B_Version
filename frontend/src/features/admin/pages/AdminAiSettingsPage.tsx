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
  const [saving, setSaving] = useState(false);
  const [results, setResults] = useState<{ chat: AdminAiConnectionResult; embedding: AdminAiConnectionResult }>();
  const [apiKeyInput, setApiKeyInput] = useState('');
  const [clearKey, setClearKey] = useState(false);
  const [reportEnabled, setReportEnabled] = useState(false);
  const [assistantEnabled, setAssistantEnabled] = useState(false);
  const [saveMessage, setSaveMessage] = useState('');
  const [provider, setProvider] = useState<'OPENAI' | 'INTERNAL_OPENAI_COMPATIBLE'>('INTERNAL_OPENAI_COMPATIBLE');
  const [baseUrl, setBaseUrl] = useState('');
  const [chatModel, setChatModel] = useState('');
  const [embeddingModel, setEmbeddingModel] = useState('');
  const [requestTimeoutSeconds, setRequestTimeoutSeconds] = useState(30);
  const [externalAllowed, setExternalAllowed] = useState(false);

  function applyStatus(value: AdminAiSettingsStatus) {
    setStatus(value);
    setReportEnabled(value.report.enabled);
    setAssistantEnabled(value.assistant.enabled);
    setProvider(value.provider);
    setBaseUrl(value.baseUrl);
    setChatModel(value.chatModel);
    setEmbeddingModel(value.embeddingModel);
    setRequestTimeoutSeconds(value.requestTimeoutSeconds);
    setExternalAllowed(value.externalAllowed);
  }

  useEffect(() => {
    adminApi.aiSettings().then(applyStatus)
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setLoading(false));
  }, []);

  async function runTest() {
    setTesting(true); setError(''); setResults(undefined);
    try { setResults(await adminApi.testAiConnections()); }
    catch (value) { setError(errorMessage(value)); }
    finally { setTesting(false); }
  }

  async function save() {
    setSaving(true); setError(''); setSaveMessage('');
    try {
      const apiKey = clearKey ? '' : (apiKeyInput.trim() ? apiKeyInput.trim() : undefined);
      const updated = await adminApi.updateAiSettings({ apiKey, reportEnabled, assistantEnabled,
        provider, baseUrl, chatModel, embeddingModel, requestTimeoutSeconds, externalAllowed });
      applyStatus(updated);
      setApiKeyInput(''); setClearKey(false);
      setSaveMessage(t('저장했습니다.', 'Saved.'));
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <p className="admin-loading">{t('AI 설정을 불러오는 중...', 'Loading AI settings...')}</p>;
  return <>
    {error && <p className="error">{error}</p>}
    <section className="admin-panel admin-notice-panel">
      <div className="admin-panel-heading"><div><h2>{t('AI 연동 상태', 'AI integration status')}</h2><p>{t('OpenAI 또는 사내 OpenAI 호환 LLM을 선택할 수 있습니다.', 'Choose OpenAI or an internal OpenAI-compatible LLM.')}</p></div></div>
      {status && <div className="ai-settings-grid">
        <VerticalCard title={t('AI 비서', 'AI assistant')} status={status.assistant} testResult={results?.chat} />
        <VerticalCard title={t('AI 주간 리포트', 'AI weekly report')} status={status.report} testResult={results?.chat} />
      </div>}
      {results?.embedding && <p className={results.embedding.success ? 'success-message' : 'error'}>
        {t('문서 임베딩', 'Document embedding')}: {results.embedding.message}
      </p>}
      <button className="primary" type="button" disabled={testing} onClick={runTest}>{testing ? t('연결 테스트 중...', 'Testing connection...') : t('연결 테스트', 'Test connection')}</button>
      {status && status.supportedModels.length > 0 && <p className="admin-notice-panel-footnote">{t('지원 모델 목록', 'Supported models')}: {status.supportedModels.join(', ')}</p>}
    </section>
    <section className="admin-panel admin-notice-panel">
      <div className="admin-panel-heading"><div><h2>{t('AI 설정 변경', 'Change AI settings')}</h2></div></div>
      <label className="field"><span>{t('AI 공급자', 'AI provider')}</span>
        <select value={provider} onChange={(event) => setProvider(event.target.value as typeof provider)}>
          <option value="INTERNAL_OPENAI_COMPATIBLE">{t('사내 OpenAI 호환 LLM', 'Internal OpenAI-compatible LLM')}</option>
          <option value="OPENAI">OpenAI</option>
        </select>
      </label>
      <label className="field"><span>{t('서버 URL', 'Server URL')}</span>
        <input value={baseUrl} placeholder="http://llm.internal:8000/v1" onChange={(event) => setBaseUrl(event.target.value)} />
      </label>
      <label className="field"><span>{t('채팅 모델', 'Chat model')}</span>
        <input value={chatModel} placeholder="company-chat-model" onChange={(event) => setChatModel(event.target.value)} />
      </label>
      <label className="field"><span>{t('임베딩 모델', 'Embedding model')}</span>
        <input value={embeddingModel} placeholder="company-embedding-model" onChange={(event) => setEmbeddingModel(event.target.value)} />
      </label>
      <label className="field"><span>{t('요청 제한 시간(초)', 'Request timeout (seconds)')}</span>
        <input type="number" min="1" max="120" value={requestTimeoutSeconds} onChange={(event) => setRequestTimeoutSeconds(Number(event.target.value))} />
      </label>
      {provider === 'OPENAI' && <label className="admin-inline-checkbox">
        <input type="checkbox" checked={externalAllowed} onChange={(event) => setExternalAllowed(event.target.checked)} />
        <span>{t('외부 인터넷 연결 허용(VPN/방화벽 정책 필요)', 'Allow external internet access (VPN/firewall policy required)')}</span>
      </label>}
      <label className="field">
        <span>{t('API 키', 'API key')}</span>
        <input type="password" value={apiKeyInput} disabled={clearKey}
          placeholder={status?.report.apiKeyConfigured ? status.report.maskedApiKey : t('설정된 키 없음', 'No key configured')}
          onChange={(event) => setApiKeyInput(event.target.value)} />
      </label>
      <label className="admin-inline-checkbox">
        <input type="checkbox" checked={clearKey} onChange={(event) => setClearKey(event.target.checked)} />
        <span>{t('키 삭제', 'Clear the key')}</span>
      </label>
      <label className="admin-inline-checkbox">
        <input type="checkbox" checked={assistantEnabled} onChange={(event) => setAssistantEnabled(event.target.checked)} />
        <span>{t('AI 비서 활성화', 'Enable AI assistant')}</span>
      </label>
      <label className="admin-inline-checkbox">
        <input type="checkbox" checked={reportEnabled} onChange={(event) => setReportEnabled(event.target.checked)} />
        <span>{t('AI 주간 리포트 활성화', 'Enable AI weekly report')}</span>
      </label>
      <button className="primary" type="button" disabled={saving} onClick={save}>
        {saving ? t('저장 중...', 'Saving...') : t('저장', 'Save')}
      </button>
      {saveMessage && <p className="success-message">{saveMessage}</p>}
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
