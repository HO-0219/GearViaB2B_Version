import type { ReactNode } from 'react';
import { useLanguage } from '../../app/LanguageContext';

export function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

export function StatusBadge({ value }: { value: string }) {
  const { language } = useLanguage();
  const normalized = value.toUpperCase();
  const labels: Record<string, [string, string]> = {
    ACTIVE: ['활성', 'Active'], SUCCESS: ['성공', 'Success'], SUCCEEDED: ['성공', 'Succeeded'],
    SENT: ['발송 완료', 'Sent'], FAILED: ['실패', 'Failed'], FAILURE: ['실패', 'Failure'],
    SUSPENDED: ['정지', 'Suspended'], CANCELED: ['취소', 'Canceled'], CANCELLED: ['취소', 'Cancelled'],
    ERROR: ['오류', 'Error'], PENDING: ['대기', 'Pending'],
    RETRYING: ['재시도 중', 'Retrying'], WAITING: ['대기', 'Waiting'],
    OFF: ['미사용', 'Off'], INACTIVE: ['비활성', 'Inactive'],
  };
  const tone = ['ACTIVE', 'SUCCESS', 'SUCCEEDED', 'SENT', '완료', '사용'].includes(normalized)
    ? 'success'
    : ['FAILED', 'FAILURE', 'SUSPENDED', 'CANCELED', 'CANCELLED', 'ERROR', '실패', '정지'].includes(normalized)
      ? 'danger'
      : ['PENDING', 'RETRYING', 'WAITING', '대기'].includes(normalized)
        ? 'warning'
        : 'neutral';
  return <span className={`admin-status admin-status--${tone}`}>{labels[normalized]?.[language === 'ko' ? 0 : 1] ?? (value || '-')}</span>;
}

export function AdminTable({ title, headers, rows, emptyLabel }: { title: string; headers: string[]; rows: ReactNode[][]; emptyLabel: string }) {
  return <section className="admin-panel admin-table-panel">
    <div className="admin-panel-heading"><h2>{title}</h2><span className="admin-count">{rows.length}</span></div>
    <div className="admin-table-wrap">
      <table>
        <thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead>
        <tbody>{rows.length > 0
          ? rows.map((row, index) => <tr key={index}>{row.map((cell, cellIndex) => <td data-label={headers[cellIndex]} key={cellIndex}>{cell}</td>)}</tr>)
          : <tr><td className="admin-empty" colSpan={headers.length}>{emptyLabel}</td></tr>}
        </tbody>
      </table>
    </div>
  </section>;
}
