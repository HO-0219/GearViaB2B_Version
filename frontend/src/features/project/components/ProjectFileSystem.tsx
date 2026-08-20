import { FormEvent, useEffect, useMemo, useState } from 'react';
import { errorMessage } from '../../../api/client';
import { ProjectDocument, ProjectFileTree, projectDocumentApi } from '../../../api/projectDocumentApi';
import { ProjectResponse } from '../../../api/projectApi';
import { ProjectIssue } from '../../../api/projectIssueApi';
import { Modal } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';

export function ProjectFileSystem({ project, nodes }: { project: ProjectResponse; nodes: ProjectIssue[] }) {
  const { t, language } = useLanguage();
  const [selectedId, setSelectedId] = useState<number>();
  const [data, setData] = useState<ProjectFileTree>();
  const [mode, setMode] = useState<'FILE' | 'LINK'>();
  const [title, setTitle] = useState('');
  const [url, setUrl] = useState('');
  const [file, setFile] = useState<File>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const children = useMemo(() => {
    const values = new Map<number | undefined, ProjectIssue[]>();
    nodes.forEach((node) => values.set(node.parentId, [...(values.get(node.parentId) ?? []), node]));
    return values;
  }, [nodes]);
  const load = () => projectDocumentApi.list(project.id, selectedId).then(setData);
  useEffect(() => { load().catch((value) => setError(errorMessage(value))); }, [project.id, selectedId]);
  useEffect(() => { if (selectedId && !nodes.some((node) => node.id === selectedId)) setSelectedId(undefined); }, [nodes, selectedId]);

  const selected = selectedId ? nodes.find((node) => node.id === selectedId) : undefined;
  const canAdd = project.status !== 'ARCHIVED' && !selected?.archivedAt;
  const items = selectedId ? data?.nodeDocuments ?? [] : data?.rootDocuments ?? [];
  function open(next: 'FILE' | 'LINK') { setMode(next); setTitle(''); setUrl(''); setFile(undefined); setError(''); }
  async function save(event: FormEvent) {
    event.preventDefault(); if (!mode) return; setSaving(true); setError('');
    try {
      if (mode === 'FILE' && file) await projectDocumentApi.upload(project.id, file, title, selectedId);
      if (mode === 'LINK') await projectDocumentApi.addLink(project.id, title.trim(), url.trim(), selectedId);
      setMode(undefined); await load();
    } catch (value) { setError(errorMessage(value)); }
    finally { setSaving(false); }
  }
  async function remove(document: ProjectDocument) {
    if (!window.confirm(t('이 자료를 삭제할까요?', 'Delete this item?'))) return;
    try { await projectDocumentApi.remove(document.id); await load(); }
    catch (value) { setError(errorMessage(value)); }
  }
  const percent = data?.limitBytes ? Math.min(100, Math.round(data.usedBytes * 100 / data.limitBytes)) : 0;
  return <section className="project-files">
    <header><div><span className="page-eyebrow">PROJECT FILES</span><h2>{t('프로젝트 파일 시스템', 'Project file system')}</h2>
      <p>{t('주제, 내용, 실행 항목이 폴더처럼 정리됩니다. 선택한 위치에 파일과 링크를 보관하세요.', 'Topics, details, and action items become folders for files and links.')}</p></div>
      <div className="storage-meter"><div><span>{formatBytes(data?.usedBytes ?? 0)} / {formatBytes(data?.limitBytes ?? 0)}</span><strong>{percent}%</strong></div><progress max={100} value={percent} /></div>
    </header>
    {error && <p className="error">{error}</p>}
    <div className="project-file-layout"><nav className="project-folder-tree" aria-label={t('프로젝트 폴더', 'Project folders')}>
      <button className={!selectedId ? 'selected' : ''} onClick={() => setSelectedId(undefined)}>📁 {project.name}</button>
      {(children.get(undefined) ?? []).map((major) => <FolderBranch key={major.id} node={major} childrenMap={children} selectedId={selectedId} onSelect={setSelectedId} />)}
    </nav><div className="project-file-content">
      <div className="project-file-toolbar"><div><small>{t('현재 위치', 'Location')}</small><strong>{selected ? `${levelLabel(selected.level, language)} / ${selected.title}` : project.name}</strong></div>
        {canAdd && <div><button className="secondary" type="button" onClick={() => open('LINK')}>＋ {t('링크', 'Link')}</button><button className="primary" type="button" onClick={() => open('FILE')}>＋ {t('파일', 'File')}</button></div>}</div>
      {items.length === 0 ? <p className="project-files-empty">{t('이 위치에 등록된 자료가 없습니다.', 'No items in this location.')}</p>
        : <div className="project-document-list">{items.map((item) => <article key={item.id}><span className={`document-icon ${item.type.toLowerCase()}`}>{item.type === 'FILE' ? '▤' : '↗'}</span><div><strong>{item.title}</strong><small>{item.originalFilename ?? item.url} · {item.createdByNickname}{item.sizeBytes ? ` · ${formatBytes(item.sizeBytes)}` : ''}</small></div><div>
          {item.type === 'LINK' ? <a className="secondary" href={item.url} target="_blank" rel="noopener noreferrer">{t('열기', 'Open')}</a> : <button className="secondary" type="button" onClick={() => projectDocumentApi.download(item).catch((value) => setError(errorMessage(value)))}>{t('다운로드', 'Download')}</button>}
          {item.canDelete && <button className="ghost danger-text" type="button" onClick={() => remove(item)}>{t('삭제', 'Delete')}</button>}</div></article>)}</div>}
    </div></div>
    {mode && <Modal title={mode === 'FILE' ? t('파일 올리기', 'Upload file') : t('링크 등록', 'Add link')} onClose={() => setMode(undefined)}><form className="form modal-form" onSubmit={save}>
      <p className="modal-location">📁 {selected?.title ?? project.name}</p>
      <label className="field"><span>{t('자료 제목', 'Title')}</span><input required={mode === 'LINK'} maxLength={160} value={title} onChange={(event) => setTitle(event.target.value)} placeholder={mode === 'FILE' ? t('비우면 파일명을 사용합니다.', 'Uses filename when empty.') : ''} /></label>
      {mode === 'LINK' ? <label className="field"><span>HTTPS URL</span><input required type="url" maxLength={1000} value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://" /></label>
        : <label className={`field project-upload-picker ${file ? 'selected' : ''}`}><span>{t('파일', 'File')}</span><input required type="file" accept=".pdf,.png,.jpg,.jpeg,.gif,.txt,.csv,.docx,.xlsx,.pptx,.zip" onChange={(event) => setFile(event.target.files?.[0])} />{file && <ProjectUploadSelection file={file} />}<small>{t('무료 20MB · 유료 100MB/파일', 'Free 20MB · Paid 100MB/file')}</small></label>}
      <div className="modal-actions"><button className="secondary" type="button" onClick={() => setMode(undefined)}>{t('취소', 'Cancel')}</button><button className="primary" disabled={saving || mode === 'FILE' && !file}>{saving ? t('저장 중...', 'Saving...') : t('저장', 'Save')}</button></div>
    </form></Modal>}
  </section>;
}

function ProjectUploadSelection({ file }: { file: File }) {
  const { t } = useLanguage();
  const [previewUrl, setPreviewUrl] = useState('');
  const image = file.type.startsWith('image/') && /\.(png|jpe?g|gif)$/i.test(file.name);
  useEffect(() => {
    if (!image) { setPreviewUrl(''); return; }
    const url = URL.createObjectURL(file); setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file, image]);
  return <div className="project-upload-selection">{previewUrl ? <img src={previewUrl} alt="" /> : <b aria-hidden="true">▤</b>}<span><strong>{file.name}</strong><small>{formatBytes(file.size)} · {t('선택됨', 'Selected')}</small></span></div>;
}

function FolderBranch({ node, childrenMap, selectedId, onSelect }: {
  node: ProjectIssue; childrenMap: Map<number | undefined, ProjectIssue[]>;
  selectedId?: number; onSelect: (id: number) => void;
}) {
  return <div className={`folder-branch level-${node.level.toLowerCase()}${node.archivedAt ? ' archived' : ''}`}><button className={selectedId === node.id ? 'selected' : ''} onClick={() => onSelect(node.id)}>{node.level === 'ISSUE' ? '📄' : '📁'} {node.title}{node.archivedAt ? ' · 보관됨' : ''}</button>{(childrenMap.get(node.id) ?? []).map((child) => <FolderBranch key={child.id} node={child} childrenMap={childrenMap} selectedId={selectedId} onSelect={onSelect} />)}</div>;
}
function levelLabel(level: ProjectIssue['level'], language: 'ko' | 'en') {
  const labels = { MAJOR: ['주제', 'Topic'], MIDDLE: ['내용', 'Detail'], ISSUE: ['실행 항목', 'Action item'] };
  return labels[level][language === 'ko' ? 0 : 1];
}
function formatBytes(value: number) {
  if (value >= 1024 ** 3) return `${(value / 1024 ** 3).toFixed(1)} GB`;
  if (value >= 1024 ** 2) return `${(value / 1024 ** 2).toFixed(1)} MB`;
  if (value >= 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${value} B`;
}
