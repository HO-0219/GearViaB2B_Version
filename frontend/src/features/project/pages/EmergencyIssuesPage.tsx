import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { accessToken, errorMessage } from '../../../api/client';
import { emergencyIssueApi, EmergencyIssue } from '../../../api/emergencyIssueApi';
import { projectApi, ProjectResponse } from '../../../api/projectApi';
import { AppNavigation, Modal } from '../../../app/AppNavigation';
import { AuthenticatedImage } from '../../../app/AuthenticatedImage';
import { useLanguage } from '../../../app/LanguageContext';

type IssueFilter = 'OPEN' | 'RESOLVED';

export function EmergencyIssuesPage() {
  const groupId = Number(useParams().groupId);
  const { t, language } = useLanguage();
  const [issues, setIssues] = useState<EmergencyIssue[]>([]);
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [filter, setFilter] = useState<IssueFilter>('OPEN');
  const [showCreate, setShowCreate] = useState(false);
  const [projectId, setProjectId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [audience, setAudience] = useState<EmergencyIssue['audience']>('PROJECT_PARTICIPANTS');
  const [image, setImage] = useState<File>();
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const preview = useMemo(() => image ? URL.createObjectURL(image) : '', [image]);
  const openIssues = issues.filter((issue) => issue.status === 'OPEN');
  const resolvedIssues = issues.filter((issue) => issue.status === 'RESOLVED');
  const visibleIssues = filter === 'OPEN' ? openIssues : resolvedIssues;

  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview);
  }, [preview]);

  async function refresh() {
    const [issueValues, projectValues] = await Promise.all([
      emergencyIssueApi.list(groupId), projectApi.list(groupId),
    ]);
    const activeProjects = projectValues.filter((project) => project.status !== 'ARCHIVED');
    setIssues(issueValues);
    setProjects(activeProjects);
    setProjectId((current) => current || (activeProjects[0] ? String(activeProjects[0].id) : ''));
  }

  useEffect(() => {
    if (!Number.isInteger(groupId) || groupId < 1) {
      setLoading(false);
      return;
    }
    refresh().catch((caught) => setError(errorMessage(caught))).finally(() => setLoading(false));
  }, [groupId]);

  function openCreate() {
    setTitle('');
    setDescription('');
    setAudience('PROJECT_PARTICIPANTS');
    setImage(undefined);
    setError('');
    setProjectId(projects[0] ? String(projects[0].id) : '');
    setShowCreate(true);
  }

  function closeCreate() {
    if (pending) return;
    setShowCreate(false);
    setImage(undefined);
    setError('');
  }

  async function create(event: FormEvent) {
    event.preventDefault();
    if (!projectId) return;
    setPending(true);
    setError('');
    try {
      let created = await emergencyIssueApi.create(groupId, {
        projectId: Number(projectId), title: title.trim(),
        description: description.trim() || undefined, audience,
      });
      if (image) created = await emergencyIssueApi.uploadImage(created.id, image);
      setIssues((current) => [created, ...current]);
      setFilter('OPEN');
      setShowCreate(false);
      setImage(undefined);
      window.dispatchEvent(new Event('notifications:refresh'));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  async function toggle(issue: EmergencyIssue) {
    setPending(true);
    setError('');
    try {
      const updated = await emergencyIssueApi.status(
        issue.id, issue.status === 'OPEN' ? 'RESOLVED' : 'OPEN', issue.version,
      );
      setIssues((current) => current.map((value) => value.id === updated.id ? updated : value));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  if (!accessToken.get()) {
    return <Navigate to={`/login?next=${encodeURIComponent(`/groups/${groupId}/emergency-issues`)}`} replace />;
  }
  if (loading) return <main className="center-page">{t('긴급 이슈를 불러오는 중...', 'Loading urgent issues...')}</main>;

  return <><AppNavigation /><main className="app-page emergency-page">
    <header className="emergency-page-header"><div><span className="page-eyebrow">URGENT CONTROL</span>
      <h1>{t('긴급 이슈 관리', 'Urgent issue management')}</h1>
      <p>{t('대응이 필요한 문제와 해결된 기록을 구분해 확인합니다.', 'Review active incidents separately from resolved records.')}</p></div>
      <div className="emergency-header-actions"><Link className="secondary button-link" to={`/groups/${groupId}/dashboard`}>← {t('대시보드', 'Dashboard')}</Link>
        <button className="danger" type="button" onClick={openCreate}>＋ {t('긴급 이슈 추가', 'Add urgent issue')}</button></div>
    </header>
    {error && !showCreate && <p className="error">{error}</p>}
    <section className="emergency-list"><div className="emergency-list-heading"><div><h2>{t('긴급 이슈 목록', 'Urgent issues')}</h2>
      <p>{t('상태를 선택해 필요한 이슈만 확인하세요.', 'Choose a status to focus the list.')}</p></div>
      <div className="emergency-status-tabs" role="tablist" aria-label={t('긴급 이슈 상태', 'Urgent issue status')}>
        <button type="button" role="tab" aria-selected={filter === 'OPEN'} className={filter === 'OPEN' ? 'active open' : ''} onClick={() => setFilter('OPEN')}>
          <span>{t('미해결', 'Open')}</span><strong>{openIssues.length}</strong>
        </button>
        <button type="button" role="tab" aria-selected={filter === 'RESOLVED'} className={filter === 'RESOLVED' ? 'active resolved' : ''} onClick={() => setFilter('RESOLVED')}>
          <span>{t('해결', 'Resolved')}</span><strong>{resolvedIssues.length}</strong>
        </button>
      </div></div>
      {visibleIssues.length === 0 ? <div className="emergency-empty"><span>{filter === 'OPEN' ? '✓' : '○'}</span><strong>{filter === 'OPEN'
        ? t('현재 미해결 긴급 이슈가 없습니다.', 'There are no open urgent issues.')
        : t('아직 해결된 긴급 이슈가 없습니다.', 'There are no resolved urgent issues.')}</strong>
        <p>{filter === 'OPEN'
          ? t('새 문제가 발생하면 긴급 이슈 추가로 바로 공유하세요.', 'Use Add urgent issue when a new problem needs immediate attention.')
          : t('미해결 이슈를 해결 처리하면 이곳에 기록됩니다.', 'Resolved open issues will be recorded here.')}</p></div>
        : visibleIssues.map((issue) => <article className={`emergency-card ${issue.status.toLowerCase()}`} key={issue.id}>
          {issue.imageUrl && <AuthenticatedImage src={issue.imageUrl} alt="" />}
          <div><header><span>{issue.status === 'OPEN' ? t('대응 필요', 'Action required') : t('해결됨', 'Resolved')}</span>
            <small>{issue.audience === 'WHOLE_TEAM' ? t('팀 전체 알림', 'Whole team') : t('프로젝트 참여자 알림', 'Project participants')}</small></header>
            <h3>{issue.title}</h3><Link to={`/projects/${issue.projectId}/flow`}>{issue.projectName}</Link>
            {issue.description && <p>{issue.description}</p>}
            <footer><small>{issue.createdByNickname} · {new Intl.DateTimeFormat(language === 'ko' ? 'ko-KR' : 'en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(issue.createdAt))}</small>
              {issue.canManage && <button className={issue.status === 'OPEN' ? 'primary' : 'secondary'} type="button" disabled={pending} onClick={() => toggle(issue)}>
                {issue.status === 'OPEN' ? t('해결 처리', 'Mark resolved') : t('다시 열기', 'Reopen')}
              </button>}</footer></div>
        </article>)}
    </section>
    {showCreate && <Modal title={t('긴급 이슈 추가', 'Add urgent issue')} onClose={closeCreate}>
      <form className="form modal-form emergency-create-form" onSubmit={create}>
        <p className="emergency-modal-guide">{t('즉시 공유가 필요한 문제를 등록하면 선택한 대상에게 알림을 보냅니다.', 'Publish a problem that needs immediate attention and notify the selected audience.')}</p>
        {projects.length === 0 && <div className="emergency-project-required"><strong>{t('먼저 프로젝트가 필요합니다.', 'Create a project first.')}</strong>
          <Link to={`/groups/${groupId}/projects`}>{t('프로젝트 만들기', 'Create project')} →</Link></div>}
        <div className="form-row"><label className="field"><span>{t('프로젝트', 'Project')}</span>
          <select required value={projectId} onChange={(event) => setProjectId(event.target.value)}><option value="">{t('프로젝트 선택', 'Select project')}</option>
            {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</select></label>
          <label className="field"><span>{t('알림 대상', 'Audience')}</span>
            <select value={audience} onChange={(event) => setAudience(event.target.value as EmergencyIssue['audience'])}>
              <option value="PROJECT_PARTICIPANTS">{t('프로젝트 참여 인원', 'Project participants')}</option>
              <option value="WHOLE_TEAM">{t('팀 전체', 'Whole team')}</option>
            </select></label></div>
        <label className="field"><span>{t('긴급 이슈 제목', 'Issue title')}</span>
          <input autoFocus required maxLength={160} value={title} onChange={(event) => setTitle(event.target.value)} placeholder={t('무슨 문제가 발생했나요?', 'What happened?')} /></label>
        <label className="field"><span>{t('상세 내용', 'Details')}</span>
          <textarea maxLength={5000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
        <label className="emergency-image-picker"><input type="file" accept="image/jpeg,image/png,image/gif" onChange={(event) => setImage(event.target.files?.[0])} />
          {preview ? <img src={preview} alt={t('첨부 이미지 미리보기', 'Image preview')} />
            : <span><b>＋ {t('현장 이미지 추가', 'Add an image')}</b><small>JPG, PNG, GIF · 5MB</small></span>}</label>
        {error && <p className="error">{error}</p>}
        <div className="modal-actions"><button className="secondary" type="button" disabled={pending} onClick={closeCreate}>{t('취소', 'Cancel')}</button>
          <button className="danger" disabled={pending || !title.trim() || !projectId}>{pending ? t('알리는 중...', 'Sending...') : t('등록하고 알림 보내기', 'Publish and alert')}</button></div>
      </form>
    </Modal>}
  </main></>;
}
