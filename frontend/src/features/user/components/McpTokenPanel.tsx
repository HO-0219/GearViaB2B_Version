import { FormEvent, useEffect, useState } from 'react';
import { errorMessage } from '../../../api/client';
import { mcpApi, McpToken } from '../../../api/mcpApi';
import { useLanguage } from '../../../app/LanguageContext';

export function McpTokenPanel() {
  const { t } = useLanguage();
  const [tokens, setTokens] = useState<McpToken[]>([]);
  const [label, setLabel] = useState('');
  const [secret, setSecret] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { mcpApi.list().then(setTokens).catch(value => setError(errorMessage(value))); }, []);

  async function issue(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setSecret('');
    try {
      const created = await mcpApi.create(label.trim(), 30);
      setSecret(created.token ?? '');
      setTokens(current => [{ ...created, token: null }, ...current]);
      setLabel('');
    } catch (value) { setError(errorMessage(value)); }
    finally { setBusy(false); }
  }

  async function revoke(id: number) {
    setBusy(true); setError('');
    try { await mcpApi.revoke(id); setTokens(current => current.filter(token => token.id !== id)); }
    catch (value) { setError(errorMessage(value)); }
    finally { setBusy(false); }
  }

  return <section className="profile-card-new mcp-token-panel">
    <div><span className="page-eyebrow">AGENT ACCESS</span><h2>{t('개인 MCP 토큰', 'Personal MCP tokens')}</h2>
      <p>{t('Codex 또는 Claude에서 GearVia 업무를 안전하게 조회할 수 있습니다.', 'Let Codex or Claude securely read your GearVia work.')}</p></div>
    <p><strong>{t('MCP 주소', 'MCP endpoint')}:</strong> <code>{mcpApi.endpoint()}</code></p>
    <form className="form" onSubmit={issue}>
      <label className="field"><span>{t('토큰 이름', 'Token label')}</span>
        <input aria-label={t('토큰 이름', 'Token label')} value={label} maxLength={60} required
          placeholder="My Codex" onChange={event => setLabel(event.target.value)} /></label>
      <button className="primary" disabled={busy}>{t('토큰 발급', 'Issue token')}</button>
    </form>
    {secret && <div className="mcp-token-secret" role="status">
      <strong>{t('지금 복사하세요. 이 토큰은 한 번만 표시됩니다.', 'Copy now. This token is shown only once.')}</strong>
      <code>{secret}</code>
    </div>}
    {error && <p className="error">{error}</p>}
    <div className="mcp-token-list">{tokens.map(token => <article key={token.id}>
      <div><strong>{token.label}</strong><small>{token.scope} · {t('만료', 'Expires')} {new Date(token.expiresAt).toLocaleDateString()}</small></div>
      <button type="button" disabled={busy || Boolean(token.revokedAt)} onClick={() => revoke(token.id)}>{t('폐기', 'Revoke')}</button>
    </article>)}</div>
  </section>;
}
