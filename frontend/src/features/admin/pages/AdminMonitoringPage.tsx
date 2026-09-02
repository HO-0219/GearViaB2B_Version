import { useEffect, useState } from 'react';
import { adminApi, AdminAiUsageTotals, AdminCapacity, AdminMetric, AdminMonitoring, AdminStorageSettings } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';
import { AdminTable } from '../AdminShared';

export function AdminMonitoringPage() {
  const { t } = useLanguage();
  const [monitoring, setMonitoring] = useState<AdminMonitoring>();
  const [storage, setStorage] = useState<AdminStorageSettings>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [switching, setSwitching] = useState(false);
  const [switchResult, setSwitchResult] = useState<{ success: boolean; message: string }>();

  function loadStorage() {
    return adminApi.storageSettings().then(setStorage);
  }

  useEffect(() => {
    Promise.all([adminApi.monitoring(), loadStorage()])
      .then(([monitoringValue]) => setMonitoring(monitoringValue))
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setLoading(false));
  }, []);

  async function activateNas() {
    setSwitching(true); setSwitchResult(undefined); setError('');
    try {
      const result = await adminApi.activateNasStorage();
      setSwitchResult(result);
      await loadStorage();
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setSwitching(false);
    }
  }

  async function activateLocal() {
    setSwitching(true); setSwitchResult(undefined); setError('');
    try {
      await adminApi.activateLocalStorage();
      await loadStorage();
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setSwitching(false);
    }
  }

  if (loading) return <p className="admin-loading">{t('모니터링 데이터를 불러오는 중...', 'Loading monitoring data...')}</p>;
  return <>
    {error && <p className="error">{error}</p>}
    {monitoring && <>
      <section className="admin-panel admin-monitoring-panel">
        <div className="admin-panel-heading"><div><h2>{t('운영 런타임', 'Operational runtime')}</h2><p>{t('현재 인스턴스와 제한된 데이터베이스 풀 상태입니다.', 'Current instance and bounded database-pool status.')}</p></div></div>
        <div className="admin-monitoring-cards">
          <article className="admin-monitoring-card"><span>{t('인스턴스', 'Instance')}</span><strong>{monitoring.runtime.instanceId}</strong><small>{t('업무 조회 상한', 'Task query cap')}: {monitoring.runtime.maxTaskResults}</small></article>
          <article className="admin-monitoring-card"><span>{t('데이터베이스 풀', 'Database pool')}</span><strong>{monitoring.databasePool.available ? `${monitoring.databasePool.active} / ${monitoring.databasePool.maximum}` : t('사용할 수 없음', 'Unavailable')}</strong><small>{percent(monitoring.databasePool.usedPercent)} · {t('유휴', 'idle')} {monitoring.databasePool.idle}</small></article>
          <article className="admin-monitoring-card"><span>{t('활성 경보', 'Active alerts')}</span><strong>{monitoring.alerts.length}</strong><small>{monitoring.alerts.filter((value) => value.severity === 'CRITICAL').length} {t('심각', 'critical')}</small></article>
        </div>
      </section>
      <section className="admin-panel admin-monitoring-panel">
        <div className="admin-panel-heading"><div><h2>{t('시스템 사용량', 'System usage')}</h2><p>{t('현재 서버의 CPU, 메모리 및 업로드 저장소 상태입니다.', 'Current CPU, memory, and upload-storage status for this server.')}</p></div></div>
        <div className="admin-monitoring-cards">
          <MetricCard title="CPU" value={monitoring.system.cpu} unavailable={t('CPU를 사용할 수 없습니다.', 'CPU unavailable')} />
          <CapacityCard title={t('메모리', 'Memory')} value={monitoring.system.memory} unavailable={t('메모리를 사용할 수 없습니다.', 'Memory unavailable')} />
          <CapacityCard title={t('저장소', 'Storage')} value={monitoring.system.storage} unavailable={t('저장소를 사용할 수 없습니다.', 'Storage unavailable')}
            detail={`${t('저장소 제공자', 'Storage provider')}: ${monitoring.system.storage.provider}`} />
        </div>
      </section>
      <AdminTable title={t('의존성 준비 상태', 'Dependency readiness')} emptyLabel={t('의존성 정보가 없습니다.', 'No dependency data.')}
        headers={[t('이름', 'Name'), t('상태', 'Status')]}
        rows={monitoring.dependencies.map((item) => [item.name, item.status])} />
      <AdminTable title={t('작업 실행기', 'Workload executors')} emptyLabel={t('실행기 정보가 없습니다.', 'No executor data.')}
        headers={[t('이름', 'Name'), t('활성', 'Active'), t('풀', 'Pool'), t('큐', 'Queue'), t('거부', 'Rejected'), t('완료', 'Completed')]}
        rows={monitoring.executors.map((item) => [item.name, item.active, `${item.poolSize} / ${item.maxSize}`,
          `${item.queueSize} / ${item.queueCapacity} (${percent(item.queueUsedPercent)})`, item.rejected, item.completed])} />
      <AdminTable title={t('활성 경보', 'Active alerts')} emptyLabel={t('활성 경보가 없습니다.', 'No active alerts.')}
        headers={[t('코드', 'Code'), t('심각도', 'Severity'), t('대상', 'Subject'), t('사용률', 'Usage')]}
        rows={monitoring.alerts.map((item) => [item.code, item.severity, item.subject ?? '—', percent(item.usedPercent)])} />
      {storage && <section className="admin-panel admin-notice-panel">
        <div className="admin-panel-heading"><div><h2>{t('스토리지 연동 설정', 'Storage integration')}</h2><p>{t('NAS/사내 스토리지는 호스트에 미리 마운트해 둔 상태여야 합니다. 연결 테스트가 성공해야만 전환되고, 실패하면 현재 방식이 그대로 유지됩니다.', 'The NAS/company storage share must already be mounted on the host. Switching only happens if the connection test succeeds — on failure the current provider stays active.')}</p></div></div>
        <dl className="admin-storage-settings">
          <div><dt>{t('현재 방식', 'Active provider')}</dt><dd>{providerLabel(storage.provider, t)}</dd></div>
          <div><dt>{t('로컬 경로', 'Local path')}</dt><dd>{storage.localRootPath || '-'} · <span className={storage.localMounted ? 'success-message' : 'error'}>{storage.localMounted ? t('정상', 'Healthy') : t('연결 안 됨', 'Unavailable')}</span></dd></div>
          <div><dt>{t('NAS 경로', 'NAS path')}</dt><dd>{storage.nasRootPath || '-'} · <span className={storage.nasMounted ? 'success-message' : 'error'}>{storage.nasMounted ? t('정상', 'Healthy') : t('연결 안 됨', 'Unavailable')}</span></dd></div>
        </dl>
        <p className="admin-notice-panel-footnote">{t('지원되는 연동 방식', 'Supported providers')}: {storage.supportedProviders.map((value) => providerLabel(value, t)).join(', ')}</p>
        <div className="admin-storage-actions">
          <button className="primary" type="button" disabled={switching || storage.provider === 'nas_mount'} onClick={activateNas}>
            {switching ? t('연결 테스트 중...', 'Testing connection...') : t('NAS 연결 테스트 및 전환', 'Test NAS connection and switch')}
          </button>
          <button className="secondary" type="button" disabled={switching || storage.provider === 'local'} onClick={activateLocal}>
            {t('로컬로 되돌리기', 'Revert to local')}
          </button>
        </div>
        {switchResult && <p className={switchResult.success ? 'success-message' : 'error'}>{switchResult.message}</p>}
      </section>}
      <AdminTable title={t('AI 사용량', 'AI usage')} emptyLabel={t('AI 호출 기록이 없습니다.', 'No AI requests recorded.')}
        headers={[t('기간', 'Period'), t('호출', 'Requests'), t('실패', 'Failed'), t('입력 토큰', 'Input tokens'), t('출력 토큰', 'Output tokens'), t('총 토큰', 'Total tokens')]}
        rows={[
          [t('오늘', 'Today'), ...totalCells(monitoring.aiUsage.periods.today)],
          [t('이번 달', 'This month'), ...totalCells(monitoring.aiUsage.periods.thisMonth)],
          [t('전체 기간', 'All time'), ...totalCells(monitoring.aiUsage.periods.allTime)],
        ]} />
      <AdminTable title={t('AI 호출 세부', 'AI request breakdown')} emptyLabel={t('AI 호출 기록이 없습니다.', 'No AI requests recorded.')}
        headers={[t('작업', 'Operation'), t('모델', 'Model'), t('호출', 'Requests'), t('실패', 'Failed'), t('입력 토큰', 'Input tokens'), t('출력 토큰', 'Output tokens'), t('총 토큰', 'Total tokens')]}
        rows={monitoring.aiUsage.breakdown.map((item) => [operationLabel(item.operation, t), item.model, ...totalCells(item)])} />
      <p className="admin-monitoring-footnote">{t('AI 사용량 기간 기준 시간대', 'AI usage time zone')}: {monitoring.aiUsage.timeZone}</p>
    </>}
  </>;
}

function MetricCard({ title, value, unavailable }: { title: string; value: AdminMetric; unavailable: string }) {
  return <article className="admin-monitoring-card"><span>{title}</span>
    <strong>{value.available ? percent(value.usedPercent) : unavailable}</strong>
    {value.available && <small>{title} {value.usedPercent == null ? '—' : percent(value.usedPercent)}</small>}
  </article>;
}

function CapacityCard({ title, value, unavailable, detail }: { title: string; value: AdminCapacity; unavailable: string; detail?: string }) {
  return <article className="admin-monitoring-card"><span>{title}</span>
    <strong>{value.available ? `${bytes(value.usedBytes)} / ${bytes(value.totalBytes)}` : unavailable}</strong>
    {value.available && <small>{percent(value.usedPercent)} {detail ? `· ${detail}` : ''}</small>}
    {!value.available && detail && <small>{detail}</small>}
  </article>;
}

function totalCells(value: AdminAiUsageTotals): (number | string)[] {
  return [value.requests, value.failedRequests, tokens(value.inputTokens), tokens(value.outputTokens), tokens(value.totalTokens)];
}

function providerLabel(provider: string, t: (ko: string, en: string) => string) {
  if (provider.toLowerCase() === 'local') return t('로컬 디스크', 'Local disk');
  if (provider.toLowerCase() === 'nas_mount') return t('NAS/사내 스토리지', 'NAS / company storage');
  return provider;
}

function operationLabel(operation: string, t: (ko: string, en: string) => string) {
  if (operation === 'ASSISTANT_RESPONSE') return t('AI 비서 응답', 'Assistant response');
  if (operation === 'DOCUMENT_EMBEDDING') return t('문서 임베딩', 'Document embedding');
  if (operation === 'WEEKLY_REPORT') return t('주간 리포트', 'Weekly report');
  return operation;
}

function percent(value?: number | null) {
  return value == null ? '—' : `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(value)}%`;
}

function tokens(value?: number | null) {
  return value == null ? '—' : new Intl.NumberFormat().format(value);
}

function bytes(value?: number | null) {
  if (value == null) return '—';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let amount = value;
  let index = 0;
  while (amount >= 1024 && index < units.length - 1) { amount /= 1024; index += 1; }
  return `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(amount)} ${units[index]}`;
}
