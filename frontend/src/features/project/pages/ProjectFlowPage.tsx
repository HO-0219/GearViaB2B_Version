import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { accessToken, errorMessage } from '../../../api/client';
import { groupApi, MemberResponse } from '../../../api/groupApi';
import { projectApi, ProjectResponse } from '../../../api/projectApi';
import {
  IssueImage, IssueLevel, IssueStatus, ProjectIssue, projectIssueApi,
} from '../../../api/projectIssueApi';
import { AppNavigation, Modal } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { ProjectFileSystem } from '../components/ProjectFileSystem';
import { taskApi, TaskResponse } from '../../../api/taskApi';

const statuses: Array<[IssueStatus, string, string]> = [
  ['OPEN', '대기', 'Open'], ['IN_PROGRESS', '진행 중', 'In progress'],
  ['BLOCKED', '막힘', 'Blocked'], ['DONE', '완료', 'Done'],
];
type Editor = { level: IssueLevel; parentId?: number; value?: ProjectIssue };

export function ProjectFlowPage() {
  const { t, language } = useLanguage();
  const projectId = Number(useParams().projectId);
  const [project, setProject] = useState<ProjectResponse>();
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [nodes, setNodes] = useState<ProjectIssue[]>([]);
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [editor, setEditor] = useState<Editor>();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [assignee, setAssignee] = useState('');
  const [status, setStatus] = useState<IssueStatus>('OPEN');
  const [dueDate, setDueDate] = useState('');
  const [checklistDrafts, setChecklistDrafts] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    const projectValue = await projectApi.get(projectId);
    const [memberValues, issueValues, taskValues] = await Promise.all([
      groupApi.members(projectValue.groupId), projectIssueApi.list(projectId, true), taskApi.list(projectValue.groupId),
    ]);
    setProject(projectValue); setMembers(memberValues); setNodes(issueValues);
    setTasks(taskValues.filter((task) => task.projectId === projectId));
  };
  useEffect(() => {
    if (!Number.isInteger(projectId) || projectId < 1) { setLoading(false); return; }
    load().catch((value) => setError(errorMessage(value))).finally(() => setLoading(false));
  }, [projectId]);

  const children = useMemo(() => {
    // API의 루트 parentId는 JSON에서 null이다. undefined로 조회하면 저장된 주제가 있어도
    // 빈 상태가 계속 노출되므로 루트 키를 null로 통일한다.
    const values = new Map<number | null, ProjectIssue[]>();
    nodes.filter((node) => !node.archivedAt)
      .forEach((node) => { const parentId = node.parentId ?? null; values.set(parentId, [...(values.get(parentId) ?? []), node]); });
    return values;
  }, [nodes]);

  function openCreate(level: IssueLevel, parentId?: number) {
    setEditor({ level, parentId }); setTitle(''); setDescription(''); setAssignee('');
    setStatus('OPEN'); setDueDate(''); setError('');
  }
  function openEdit(value: ProjectIssue) {
    setEditor({ level: value.level, parentId: value.parentId, value }); setTitle(value.title);
    setDescription(value.description ?? ''); setAssignee(value.assigneeMemberId?.toString() ?? '');
    setStatus(value.status); setDueDate(value.dueDate ?? ''); setError('');
  }
  async function save(event: FormEvent) {
    event.preventDefault(); if (!editor) return; setSaving(true); setError('');
    const input = { title: title.trim(), description: description.trim() || undefined,
      assigneeMemberId: assignee ? Number(assignee) : undefined, dueDate: dueDate || undefined };
    try {
      const saved = editor.value
        ? await projectIssueApi.update(editor.value.id, { ...input, status, sortOrder: editor.value.sortOrder,
          expectedVersion: editor.value.version })
        : await projectIssueApi.create(projectId, { ...input, level: editor.level, parentId: editor.parentId });
      setNodes((current) => editor.value
        ? current.map((node) => node.id === saved.id ? saved : node) : [...current, saved]);
      setEditor(undefined);
    } catch (value) { setError(errorMessage(value)); }
    finally { setSaving(false); }
  }
  async function archive(node: ProjectIssue) {
    const label = node.level === 'ISSUE' ? t('실행 항목', 'action item') : t('항목과 그 안의 모든 내용', 'item and all nested content');
    if (!window.confirm(t(`‘${node.title}’ ${label}을 보관할까요?`, `Archive “${node.title}” ${label}?`))) return;
    try { await projectIssueApi.archive(node.id, node.version); await load(); }
    catch (value) { setError(errorMessage(value)); }
  }
  async function addChecklist(issue: ProjectIssue) {
    const content = checklistDrafts[issue.id]?.trim(); if (!content) return;
    try {
      const item = await projectIssueApi.createChecklist(issue.id, content);
      patchNode(issue.id, (value) => ({ ...value, checklist: [...value.checklist, item] }));
      setChecklistDrafts((current) => ({ ...current, [issue.id]: '' }));
    } catch (value) { setError(errorMessage(value)); }
  }
  async function toggleChecklist(issue: ProjectIssue, itemId: number, completed: boolean) {
    const item = issue.checklist.find((value) => value.id === itemId); if (!item) return;
    try {
      const updated = await projectIssueApi.updateChecklist(item, completed);
      patchNode(issue.id, (value) => ({ ...value,
        checklist: value.checklist.map((current) => current.id === updated.id ? updated : current) }));
    } catch (value) { setError(errorMessage(value)); }
  }
  async function uploadImage(issue: ProjectIssue, event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]; event.target.value = ''; if (!file) return;
    try {
      const image = await projectIssueApi.uploadImage(issue.id, file);
      patchNode(issue.id, (value) => ({ ...value, images: [...value.images, image] }));
    } catch (value) { setError(errorMessage(value)); }
  }
  async function deleteImage(issue: ProjectIssue, image: IssueImage) {
    if (!window.confirm(t('이 이미지를 삭제할까요?', 'Delete this image?'))) return;
    try {
      await projectIssueApi.deleteImage(image.id);
      patchNode(issue.id, (value) => ({ ...value, images: value.images.filter((item) => item.id !== image.id) }));
    } catch (value) { setError(errorMessage(value)); }
  }
  function patchNode(id: number, change: (value: ProjectIssue) => ProjectIssue) {
    setNodes((current) => current.map((node) => node.id === id ? change(node) : node));
  }

  if (!accessToken.get()) return <Navigate to="/login" replace />;
  if (loading) return <main className="center-page">{t('프로젝트 작업 내용을 불러오는 중...', 'Loading project workspace...')}</main>;
  if (!project) return <Navigate to="/groups" replace />;
  const activeMembers = members.filter((member) => member.status === 'ACTIVE');
  const activeNodes = nodes.filter((node) => !node.archivedAt);
  const archivedNodes = nodes.filter((node) => node.archivedAt);
  const activeTasks = tasks.filter((task) => !['COMPLETED', 'REJECTED', 'CANCELLED'].includes(task.status));
  const completedTasks = tasks.filter((task) => task.status === 'COMPLETED');
  const unclassifiedTasks = tasks.filter((task) => task.projectTopicId == null);
  const completionRate = tasks.length === 0 ? 0 : Math.round(completedTasks.length * 100 / tasks.length);
  const statusLabel = (value: IssueStatus) => {
    const found = statuses.find(([key]) => key === value)!; return language === 'ko' ? found[1] : found[2];
  };
  return <><AppNavigation /><main className="project-flow-page app-page">
    <header className="flow-header"><div><Link to={`/groups/${project.groupId}/projects`}>← {t('프로젝트 목록', 'Projects')}</Link>
      <span className="page-eyebrow">PROJECT WORKSPACE</span><h1>{project.name}</h1>
      <p>{t('프로젝트 안에서 주제를 나누고, 각 주제의 업무와 진행 상황을 한 흐름으로 관리합니다.', 'Organize topics, tasks, and progress in one project flow.')}</p></div>
      <div className="flow-header-actions"><Link className="secondary button-link" to={`/groups/${project.groupId}/tasks?projectId=${project.id}&create=1`}>＋ {t('프로젝트 업무', 'Project task')}</Link>{project.canManageFlow && <button className="primary" type="button" onClick={() => openCreate('MAJOR')}>＋ {t('주제 추가', 'Add topic')}</button>}</div>
    </header>
    {error && <p className="error">{error}</p>}
    <section className="flow-overview" aria-label={t('프로젝트 현황', 'Project overview')}>
      <div><span>{t('주제', 'Topics')}</span><strong>{activeNodes.filter((node) => node.level === 'MAJOR').length}</strong></div>
      <div><span>{t('진행 업무', 'Active tasks')}</span><strong>{activeTasks.length}</strong></div>
      <div><span>{t('연결 업무', 'Linked tasks')}</span><strong>{tasks.length}</strong></div>
      <div><span>{t('완료 업무', 'Completed')}</span><strong>{completedTasks.length}</strong></div>
    </section>
    <section className="project-health-strip"><div><span>{t('전체 진행률', 'Overall progress')}</span><strong>{completionRate}%</strong></div><progress value={completionRate} max={100} /><p>{tasks.length === 0 ? t('첫 업무를 연결하면 프로젝트 진행률이 표시됩니다.', 'Link the first task to start tracking project progress.') : t(`${tasks.length}개 업무 중 ${completedTasks.length}개를 완료했습니다.`, `${completedTasks.length} of ${tasks.length} tasks completed.`)}</p></section>
    {(children.get(null) ?? []).length === 0 ? <section className="flow-empty"><h2>{t('첫 주제를 추가해 주세요', 'Add your first topic')}</h2><p>{t('예: 사용자 기능, 설치 운영, 관리자 기능', 'Examples: User features, Installation ops, Admin features')}</p></section>
      : <div className="flow-major-list">{(children.get(null) ?? []).map((major) => <section className="flow-major" key={major.id}>
        <header><div><span>{t('주제', 'TOPIC')}</span><h2>{major.title}</h2>{major.description && <p>{major.description}</p>}<small className="topic-owner">👤 {t('주제 담당', 'Topic owner')} · {major.assigneeNickname ?? t('미지정', 'Unassigned')}{major.dueDate ? ` · ${major.dueDate}` : ''}</small></div>
          <div className="flow-actions"><Link className="primary button-link" to={`/groups/${project.groupId}/tasks?projectId=${project.id}&topicId=${major.id}&create=1`}>＋ {t('업무 추가', 'Add task')}</Link>{major.canManage && <><button className="ghost" onClick={() => openEdit(major)}>{t('수정', 'Edit')}</button><button className="ghost danger-text" onClick={() => archive(major)}>{t('보관', 'Archive')}</button></>}</div></header>
        <ProjectTaskList title={t('이 주제의 업무', 'Tasks in this topic')} tasks={tasks.filter((task) => task.projectTopicId === major.id)} members={members} language={language} empty={t('아직 연결된 업무가 없습니다. 위 업무 추가를 누르면 이 주제가 자동 선택됩니다.', 'No tasks are linked yet. Add task will preselect this topic.')} />
        {(children.get(major.id) ?? []).length > 0 && <details className="legacy-project-details"><summary>{t(`기존 세부 내용 ${(children.get(major.id) ?? []).length}개`, `${(children.get(major.id) ?? []).length} legacy details`)}</summary><p>{t('기존에 등록한 내용과 실행 항목입니다. 새 업무는 위의 업무 추가를 이용해 주세요.', 'These are existing details and action items. Use Add task above for new work.')}</p><div className="flow-middle-list">{(children.get(major.id) ?? []).map((middle) => <section className="flow-middle" key={middle.id}>
          <header><div><span>{t('내용', 'DETAIL')}</span><h3>{middle.title}</h3></div><div className="flow-actions">
            {project.status !== 'ARCHIVED' && <button className="secondary" onClick={() => openCreate('ISSUE', middle.id)}>＋ {t('실행 항목', 'Action item')}</button>}
            {middle.canManage && <><button className="ghost" onClick={() => openEdit(middle)}>{t('수정', 'Edit')}</button><button className="ghost danger-text" onClick={() => archive(middle)}>{t('보관', 'Archive')}</button></>}
          </div></header>
          {(children.get(middle.id) ?? []).length === 0 ? <p className="flow-inline-empty">{t('실행 항목을 추가해 구체적인 작업을 정리하세요.', 'Add an action item to define the work.')}</p>
            : <div className="flow-issue-grid">{(children.get(middle.id) ?? []).map((issue) => <article className="flow-issue" key={issue.id}>
              <div className="flow-issue-heading"><span className={`issue-status issue-${issue.status.toLowerCase()}`}>{statusLabel(issue.status)}</span><small>#{issue.id}</small></div>
              <h4>{issue.title}</h4><p>{issue.description || t('상세 설명이 없습니다.', 'No description.')}</p>
              <div className="issue-meta"><span>👤 {issue.assigneeNickname ?? t('담당자 미지정', 'Unassigned')}</span><span>📅 {issue.dueDate ?? '—'}</span></div>
              <div className="issue-progress"><span>{t('작업 순서', 'Checklist')}</span><strong>{issue.checklist.filter((item) => item.completed).length}/{issue.checklist.length}</strong></div>
              <div className="issue-checklist">{issue.checklist.map((item) => <label key={item.id}><input type="checkbox" checked={item.completed} disabled={!issue.canManage} onChange={(event) => toggleChecklist(issue, item.id, event.target.checked)} /><span>{item.content}</span></label>)}</div>
              {issue.canManage && <div className="issue-checklist-add"><input value={checklistDrafts[issue.id] ?? ''} maxLength={500} placeholder={t('다음 작업 입력', 'Add next step')} onChange={(event) => setChecklistDrafts((current) => ({ ...current, [issue.id]: event.target.value }))} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); addChecklist(issue); } }} /><button type="button" onClick={() => addChecklist(issue)}>＋</button></div>}
              {issue.images.length > 0 && <div className="issue-images">{issue.images.map((image) => <div key={image.id}><AuthenticatedIssueImage image={image} alt={image.originalFilename} />{image.canDelete && <button type="button" aria-label={t('이미지 삭제', 'Delete image')} onClick={() => deleteImage(issue, image)}>×</button>}</div>)}</div>}
              {issue.canManage && <div className="issue-footer-actions"><label className="secondary file-button">＋ {t('이미지', 'Image')}<input type="file" accept="image/jpeg,image/png,image/gif" onChange={(event) => uploadImage(issue, event)} /></label><button className="ghost" onClick={() => openEdit(issue)}>{t('수정', 'Edit')}</button><button className="ghost danger-text" onClick={() => archive(issue)}>{t('보관', 'Archive')}</button></div>}
            </article>)}</div>}
        </section>)}</div></details>}
      </section>)}</div>}
    {unclassifiedTasks.length > 0 && <section className="unclassified-project-tasks"><header><div><span className="page-eyebrow">NEEDS TOPIC</span><h2>{t('주제 미분류 업무', 'Tasks without a topic')}</h2><p>{t('프로젝트에는 연결됐지만 아직 주제가 정해지지 않은 업무입니다.', 'These tasks belong to the project but do not have a topic yet.')}</p></div><Link className="secondary button-link" to={`/groups/${project.groupId}/tasks`}>{t('업무에서 주제 지정', 'Assign topics')}</Link></header><ProjectTaskList title={t('미분류 업무', 'Unclassified tasks')} tasks={unclassifiedTasks} members={members} language={language} empty="" /></section>}
    <details className="project-support-tools"><summary><span>📁</span><div><strong>{t('프로젝트 파일·링크', 'Project files and links')}</strong><small>{t('필요할 때 열어 파일과 자료를 관리합니다.', 'Open when you need to manage project resources.')}</small></div></summary><ProjectFileSystem project={project} nodes={nodes} /></details>
    {archivedNodes.length > 0 && <details className="archived-projects archived-issues"><summary>{t(`보관된 항목 ${archivedNodes.length}개`, `${archivedNodes.length} archived items`)}</summary>
      <div className="archived-issue-list">{archivedNodes.map((node) => <article key={node.id}><div><strong>{node.title}</strong><small>{levelLabel(node.level, language)} · {node.archivedAt ? new Date(node.archivedAt).toLocaleDateString() : ''}</small></div>
        {node.images.length > 0 && <div className="issue-images">{node.images.map((image) => <div key={image.id}><AuthenticatedIssueImage image={image} alt={image.originalFilename} />{image.canDelete && <button type="button" aria-label={t('이미지 삭제', 'Delete image')} onClick={() => deleteImage(node, image)}>×</button>}</div>)}</div>}</article>)}</div>
      <p>{t('보관된 위치의 파일과 링크는 위 파일 시스템에서 계속 조회하고 정리할 수 있습니다.', 'Files and links in archived locations remain available in the file system above.')}</p>
    </details>}
    {editor && <Modal title={editor.value ? t('항목 수정', 'Edit item') : levelTitle(editor.level, language)} onClose={() => setEditor(undefined)}><form className="form modal-form project-editor-form" onSubmit={save}>
      <label className="field"><span>{t('주제', 'Title')}</span><input autoFocus required maxLength={160} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
      <label className="field"><span>{t('설명', 'Description')}</span><textarea maxLength={10000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
      <label className="field"><span>{editor.level === 'MAJOR' ? t('주제 담당자', 'Topic owner') : t('담당자', 'Assignee')}</span><select value={assignee} onChange={(event) => setAssignee(event.target.value)}><option value="">{t('미지정', 'Unassigned')}</option>{activeMembers.map((member) => <option key={member.id} value={member.id}>{member.nickname}</option>)}</select></label>
      {editor.level === 'ISSUE' && editor.value && <label className="field"><span>{t('상태', 'Status')}</span><select value={status} onChange={(event) => setStatus(event.target.value as IssueStatus)}>{statuses.map(([value, ko, en]) => <option value={value} key={value}>{language === 'ko' ? ko : en}</option>)}</select></label>}
      <label className="field"><span>{t('마감일', 'Due date')}</span><input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} /></label>
      <div className="modal-actions"><button className="secondary" type="button" onClick={() => setEditor(undefined)}>{t('취소', 'Cancel')}</button><button className="primary" disabled={saving}>{saving ? t('저장 중...', 'Saving...') : t('저장', 'Save')}</button></div>
    </form></Modal>}
  </main></>;
}

function levelTitle(level: IssueLevel, language: 'ko' | 'en') {
  const values = { MAJOR: ['새 주제 추가', 'Add a new topic'], MIDDLE: ['새 내용 추가', 'Add new detail'], ISSUE: ['새 실행 항목', 'Add action item'] } as const;
  return values[level][language === 'ko' ? 0 : 1];
}

function levelLabel(level: IssueLevel, language: 'ko' | 'en') {
  const values = { MAJOR: ['주제', 'Topic'], MIDDLE: ['내용', 'Detail'], ISSUE: ['실행 항목', 'Action item'] } as const;
  return values[level][language === 'ko' ? 0 : 1];
}

function taskStatusLabel(status: TaskResponse['status'], language: 'ko' | 'en') {
  const values: Record<TaskResponse['status'], [string, string]> = { REQUESTED: ['승인 대기', 'Pending'], TODO: ['할 일', 'To do'], IN_PROGRESS: ['진행 중', 'In progress'], ON_HOLD: ['보류', 'On hold'], COMPLETED: ['완료', 'Completed'], REJECTED: ['반려', 'Rejected'], CANCELLED: ['취소', 'Cancelled'] };
  return values[status][language === 'ko' ? 0 : 1];
}

function ProjectTaskList({ title, tasks, members, language, empty }: { title: string; tasks: TaskResponse[]; members: MemberResponse[]; language: 'ko' | 'en'; empty: string }) {
  return <div className="project-topic-tasks"><div className="project-topic-task-heading"><strong>{title}</strong><span>{tasks.length}</span></div>{tasks.length === 0 ? <p>{empty}</p> : <div>{tasks.map((task) => <Link to={`/tasks/${task.id}`} key={task.id}><span className={`issue-status issue-${task.status.toLowerCase()}`}>{taskStatusLabel(task.status, language)}</span><strong>{task.title}</strong><small>{task.assigneeMemberId ? members.find((member) => member.id === task.assigneeMemberId)?.nickname ?? (language === 'ko' ? '담당자 확인 필요' : 'Unknown assignee') : language === 'ko' ? '담당자 미지정' : 'Unassigned'}</small></Link>)}</div>}</div>;
}

function AuthenticatedIssueImage({ image, alt }: { image: IssueImage; alt: string }) {
  const [url, setUrl] = useState('');
  useEffect(() => {
    let active = true; let objectUrl = '';
    projectIssueApi.imageBlob(image).then(({ blob }) => {
      if (!active) return; objectUrl = URL.createObjectURL(blob); setUrl(objectUrl);
    }).catch(() => undefined);
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [image.id]);
  return url ? <img src={url} alt={alt} loading="lazy" /> : <span className="image-placeholder" aria-label={alt} />;
}
