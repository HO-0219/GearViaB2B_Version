import { FormEvent, useEffect, useState } from 'react';
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom';
import { accessToken, errorMessage } from '../../../api/client';
import { groupApi, GroupResponse } from '../../../api/groupApi';
import { taskApi, TaskPriority, TaskResponse } from '../../../api/taskApi';
import { AppNavigation, Modal } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { ChecklistDraftField, cleanChecklistDraft } from '../components/ChecklistDraftField';
import { subscribeToLiveUpdates } from '../../../app/liveUpdates';
import { projectApi, ProjectResponse } from '../../../api/projectApi';
import { ProjectIssue, projectIssueApi } from '../../../api/projectIssueApi';

const statusLabels: Record<TaskResponse['status'], [string, string]> = {
  REQUESTED: ['승인 대기', 'Pending approval'], TODO: ['할 일', 'To do'], IN_PROGRESS: ['진행 중', 'In progress'], ON_HOLD: ['보류', 'On hold'],
  COMPLETED: ['완료', 'Completed'], REJECTED: ['반려', 'Rejected'], CANCELLED: ['취소', 'Cancelled'],
};
const priorityLabels: Record<TaskPriority, [string, string]> = {
  LOW: ['낮음', 'Low'], NORMAL: ['보통', 'Normal'], HIGH: ['높음', 'High'], URGENT: ['긴급', 'Urgent'],
};

export function TasksPage() {
  const { t, language } = useLanguage();
  const label = (value: [string, string]) => value[language === 'ko' ? 0 : 1];
  const groupId = Number(useParams().groupId);
  const [searchParams] = useSearchParams();
  const [group, setGroup] = useState<GroupResponse>();
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<TaskPriority>('NORMAL');
  const [dueAt, setDueAt] = useState('');
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [topics, setTopics] = useState<ProjectIssue[]>([]);
  const [projectId, setProjectId] = useState(searchParams.get('projectId') ?? '');
  const [topicId, setTopicId] = useState(searchParams.get('topicId') ?? '');
  const [checklistItems, setChecklistItems] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [claimingId, setClaimingId] = useState<number>();
  const [filters, setFilters] = useState({ query: '', status: '', priority: '', projectId: '', assignment: 'ALL', due: 'ALL' });
  const [filtering, setFiltering] = useState(false);

  async function applyFilters(next = filters) {
    setFilters(next); setFiltering(true);
    try { setTasks(await taskApi.list(groupId, { ...next, projectId: next.projectId ? Number(next.projectId) : undefined })); setError(''); }
    catch (caught) { setError(errorMessage(caught)); } finally { setFiltering(false); }
  }

  useEffect(() => {
    if (!Number.isInteger(groupId) || groupId < 1) {
      setError(t('올바르지 않은 그룹 주소입니다.', 'This group address is invalid.'));
      setLoading(false);
      return;
    }
    Promise.all([groupApi.get(groupId), taskApi.list(groupId), projectApi.list(groupId)])
      .then(([groupValue, taskValues, projectValues]) => {
        setGroup(groupValue); setTasks(taskValues);
        setProjects(projectValues.filter((value) => value.status !== 'ARCHIVED'));
        if (searchParams.get('create') === '1') setShowCreate(true);
      })
      .catch((caught) => setError(errorMessage(caught)))
      .finally(() => setLoading(false));
  }, [groupId]);

  useEffect(() => {
    if (!projectId) { setTopics([]); setTopicId(''); return; }
    projectIssueApi.list(Number(projectId)).then((values) => {
      const available = values.filter((value) => value.level === 'MAJOR' && !value.archivedAt);
      setTopics(available);
      setTopicId((current) => available.some((value) => String(value.id) === current) ? current : '');
    }).catch((caught) => setError(errorMessage(caught)));
  }, [projectId]);

  useEffect(() => subscribeToLiveUpdates((update) => {
    if (update.groupId !== groupId) return;
    taskApi.list(groupId).then(setTasks).catch(() => undefined);
  }), [groupId]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      const checklist = cleanChecklistDraft(checklistItems);
      const created = await taskApi.create(groupId, {
        title, description: description || undefined, priority, dueAt: dueAt || undefined,
        projectId: projectId ? Number(projectId) : undefined,
        projectTopicId: topicId ? Number(topicId) : undefined,
        checklistItems: checklist.length > 0 ? checklist : undefined,
      });
      setTasks((current) => [created, ...current]);
      setTitle('');
      setDescription('');
      setPriority('NORMAL');
      setDueAt('');
      setProjectId(''); setTopicId('');
      setChecklistItems([]);
      setShowCreate(false);
      window.dispatchEvent(new Event('notifications:refresh'));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSaving(false);
    }
  }

  async function claim(event: React.MouseEvent, task: TaskResponse) {
    event.preventDefault();
    event.stopPropagation();
    setClaimingId(task.id);
    setError('');
    try {
      const updated = await taskApi.claim(task.id, task.version);
      setTasks((current) => current.map((value) => value.id === updated.id ? updated : value));
      window.dispatchEvent(new Event('notifications:refresh'));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setClaimingId(undefined);
    }
  }

  if (!accessToken.get()) return <Navigate to={`/login?next=${encodeURIComponent(`/groups/${groupId}/tasks`)}`} replace />;
  if (loading) return <main className="center-page">{t('업무를 불러오는 중...', 'Loading tasks...')}</main>;
  const sortedTasks = [...tasks].sort((left, right) => {
    if (left.dueAt && right.dueAt) return left.dueAt.localeCompare(right.dueAt);
    if (left.dueAt) return -1; if (right.dueAt) return 1;
    return right.createdAt.localeCompare(left.createdAt);
  });
  return <><AppNavigation /><main className="tasks-page app-page">
    <header className="tasks-header">
      <div><Link to={`/groups/${groupId}`}>← {t('그룹으로', 'Back to group')}</Link><h1>{group?.name ?? t('그룹', 'Group')} {t('업무', 'Tasks')}</h1><p>{group?.type === 'PERSONAL' ? t('등록 즉시 할 일로 시작합니다.', 'New tasks start in To do immediately.') : t('새 업무는 승인 대기 상태로 시작합니다.', 'New tasks start pending approval.')}</p></div>
      <button className="primary create-action" type="button" onClick={() => setShowCreate(true)}><span aria-hidden="true">＋</span> {t('업무 만들기', 'Create task')}</button>
    </header>
    {error && <p className="error tasks-error">{error}</p>}
    <section className="tasks-layout tasks-layout-single">
      <section className="task-list-card">
        <h2>{t('업무 목록', 'Task list')} <small>{tasks.length}</small></h2>
        <form className="task-filters" onSubmit={event => { event.preventDefault(); void applyFilters(); }}><input aria-label={t('업무 검색', 'Search tasks')} placeholder={t('제목·설명 검색', 'Search title or description')} value={filters.query} onChange={e => setFilters(v => ({ ...v, query: e.target.value }))} /><select aria-label={t('상태', 'Status')} value={filters.status} onChange={e => void applyFilters({ ...filters, status: e.target.value })}><option value="">{t('모든 상태', 'All statuses')}</option>{Object.entries(statusLabels).map(([value, text]) => <option key={value} value={value}>{label(text)}</option>)}</select><select aria-label={t('우선순위', 'Priority')} value={filters.priority} onChange={e => void applyFilters({ ...filters, priority: e.target.value })}><option value="">{t('모든 우선순위', 'All priorities')}</option>{Object.entries(priorityLabels).map(([value, text]) => <option key={value} value={value}>{label(text)}</option>)}</select><select aria-label={t('프로젝트', 'Project')} value={filters.projectId} onChange={e => void applyFilters({ ...filters, projectId: e.target.value })}><option value="">{t('모든 프로젝트', 'All projects')}</option>{projects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}</select><select aria-label={t('담당', 'Assignment')} value={filters.assignment} onChange={e => void applyFilters({ ...filters, assignment: e.target.value })}><option value="ALL">{t('전체 담당', 'All assignments')}</option><option value="MINE">{t('내 업무', 'My tasks')}</option><option value="UNASSIGNED">{t('미배정', 'Unassigned')}</option></select><select aria-label={t('마감', 'Due')} value={filters.due} onChange={e => void applyFilters({ ...filters, due: e.target.value })}><option value="ALL">{t('모든 마감', 'All due dates')}</option><option value="OVERDUE">{t('지연', 'Overdue')}</option><option value="DUE_SOON">{t('7일 이내', 'Due in 7 days')}</option></select><button className="secondary" disabled={filtering}>{t('검색', 'Search')}</button><button type="button" className="secondary" onClick={() => void applyFilters({ query: '', status: '', priority: '', projectId: '', assignment: 'ALL', due: 'ALL' })}>{t('초기화', 'Reset')}</button></form>
        {tasks.length === 0 && <p className="empty-state">{t('첫 업무를 등록해 보세요.', 'Create your first task.')}</p>}
        <div className="task-list">{sortedTasks.map((task) => <article className="task-item" key={task.id}>
          <Link className="task-item-main" to={`/tasks/${task.id}`}>
            <div className="task-item-top"><span className={`task-status status-${task.status.toLowerCase()}`}>{label(statusLabels[task.status])}</span><span className={`task-priority priority-${task.priority.toLowerCase()}`}>{label(priorityLabels[task.priority])}</span>{task.projectName && <span className="task-project-context">{task.projectName}{task.projectTopicTitle ? ` · ${task.projectTopicTitle}` : ''}</span>}{task.delayed && <span className="task-delayed">{t('지연', 'Overdue')}</span>}</div>
            <strong>{task.title}</strong>
            <p>{task.description || t('설명 없음', 'No description')}</p>
            <div className="task-date-row"><span><b>{t('등록', 'Created')}</b>{formatDate(task.createdAt, language)}</span><span className={task.delayed ? 'overdue' : ''}><b>{t('마감', 'Due')}</b>{task.dueAt ? formatDate(task.dueAt, language) : t('미정', 'Not set')}</span></div>
          </Link>
          <div className="task-item-actions"><Link to={`/tasks/${task.id}`}>{t('업무 상세 보기', 'View task details')} →</Link>
            {task.status === 'TODO' && !task.assigneeMemberId && group?.type === 'TEAM' &&
              <button type="button" className="task-claim-button" disabled={claimingId === task.id}
                onClick={(event) => claim(event, task)}>
                {claimingId === task.id ? t('선택 중...', 'Claiming...') : t('내가 담당하기', 'Assign to me')}
              </button>}
          </div>
        </article>)}</div>
      </section>
      {showCreate && <Modal title={t('새 업무 만들기', 'Create a task')} description={t(`${group?.name ?? '그룹'}에 새로운 업무를 추가합니다.`, `Add a new task to ${group?.name ?? 'this group'}.`)} onClose={() => setShowCreate(false)}><form className="form modal-form" onSubmit={create}>
        {group?.type === 'TEAM' && projects.length > 0 && <div className="task-project-fields"><label className="field"><span>{t('프로젝트', 'Project')}</span><select required value={projectId} onChange={(event) => { setProjectId(event.target.value); setTopicId(''); }}><option value="">{t('프로젝트 선택', 'Select a project')}</option>{projects.map((project) => <option value={project.id} key={project.id}>{project.name}</option>)}</select></label><label className="field"><span>{t('주제', 'Topic')}</span><select value={topicId} disabled={!projectId} onChange={(event) => setTopicId(event.target.value)}><option value="">{topics.length > 0 ? t('주제 선택 (선택)', 'Select a topic (optional)') : t('등록된 주제 없음', 'No topics yet')}</option>{topics.map((topic) => <option value={topic.id} key={topic.id}>{topic.title}</option>)}</select></label></div>}
        <label className="field"><span>{t('제목', 'Title')}</span><input required maxLength={120} value={title} onChange={(event) => setTitle(event.target.value)} placeholder={t('예: 발표 자료 초안 작성', 'e.g. Draft presentation slides')} /></label>
        <label className="field"><span>{t('설명 (선택)', 'Description (optional)')}</span><textarea maxLength={5000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
        <label className="field"><span>{t('우선순위', 'Priority')}</span><select value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}>{Object.entries(priorityLabels).map(([value, valueLabel]) => <option value={value} key={value}>{label(valueLabel)}</option>)}</select></label>
        <label className="field"><span>{t('마감 날짜·시간 (선택)', 'Due date and time (optional)')}</span><input type="datetime-local" value={dueAt} onChange={(event) => setDueAt(event.target.value)} /><small className="field-help">{t('시간이 필요한 업무는 시각까지 지정할 수 있습니다.', 'Add a specific time when the task requires one.')}</small></label>
        <ChecklistDraftField items={checklistItems} onChange={setChecklistItems} disabled={saving} />
        <div className="modal-actions"><button className="secondary" type="button" onClick={() => setShowCreate(false)}>{t('취소', 'Cancel')}</button><button className="primary" disabled={saving}>{saving ? t('등록 중...', 'Creating...') : t('업무 만들기', 'Create task')}</button></div>
      </form></Modal>}
    </section>
  </main></>;
}

function formatDate(value: string, language: 'ko' | 'en') {
  return new Intl.DateTimeFormat(language === 'ko' ? 'ko-KR' : 'en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
