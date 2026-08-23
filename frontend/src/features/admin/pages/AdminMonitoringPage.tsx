import { useEffect, useState } from 'react';
import { adminApi, AdminAiUsageTotals, AdminCapacity, AdminMetric, AdminMonitoring } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';
import { AdminTable } from '../AdminShared';

export function AdminMonitoringPage() {
  const { t } = useLanguage();
  const [monitoring, setMonitoring] = useState<AdminMonitoring>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    adminApi.monitoring().then(setMonitoring)
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="admin-loading">{t('모니터링 데이터를 불러오는 중...', 'Loading monitoring data...')}</p>;
  return <>
    {error && <p className="error">{error}</p>}
    {monitoring && <>
      <section className="admin-panel admin-monitoring-panel">
        <div className="admin-panel-heading"><div><h2>{t('시스템 사용량', 'System usage')}</h2><p>{t('현재 서버의 CPU, 메모리 및 업로드 저장소 상태입니다.', 'Current CPU, memory, and upload-storage status for this server.')}</p></div></div>
        <div className="admin-monitoring-cards">
          <MetricCard title="CPU" value={monitoring.system.cpu} unavailable={t('CPU를 사용할 수 없습니다.', 'CPU unavailable')} />
          <CapacityCard title={t('메모리', 'Memory')} value={monitoring.system.memory} unavailable={t('메모리를 사용할 수 없습니다.', 'Memory unavailable')} />
          <CapacityCard title={t('저장소', 'Storage')} value={monitoring.system.storage} unavailable={t('저장소를 사용할 수 없습니다.', 'Storage unavailable')}
            detail={`${t('저장소 제공자', 'Storage provider')}: ${monitoring.system.storage.provider}`} />
        </div>
      </section>
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
