import { FormEvent, useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { accessToken, errorMessage } from '../../../api/client';
import { groupApi, GroupFeaturePolicy, GroupResponse, MemberResponse } from '../../../api/groupApi';
import { projectApi, ProjectResponse, ProjectStatus } from '../../../api/projectApi';
import { AppNavigation, Modal } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { ProjectIssue, projectIssueApi } from '../../../api/projectIssueApi';
import { taskApi, TaskResponse } from '../../../api/taskApi';

const statusLabels: Record<ProjectStatus, [string, string]> = {
  PLANNED: ['계획', 'Planned'], ACTIVE: ['진행 중', 'Active'], ON_HOLD: ['보류', 'On hold'],
  COMPLETED: ['완료', 'Completed'], ARCHIVED: ['보관됨', 'Archived'],
};

export function ProjectsPage() {
  const { t, language } = useLanguage();
  const groupId = Number(useParams().groupId);
  const [group, setGroup] = useState<GroupResponse>();
  const [features, setFeatures] = useState<GroupFeaturePolicy>();
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [topicsByProject, setTopicsByProject] = useState<Record<number, ProjectIssue[]>>({});
  const [expandedProjectId, setExpandedProjectId] = useState<number>();
  const [editing, setEditing] = useState<ProjectResponse>();
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [leadMemberId, setLeadMemberId] = useState('');
  const [status, setStatus] = useState<ProjectStatus>('PLANNED');
  const [startDate, setStartDate] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!Number.isInteger(groupId) || groupId < 1) { setLoading(false); return; }
    Promise.all([groupApi.get(groupId), groupApi.features(groupId), groupApi.members(groupId), projectApi.list(groupId), taskApi.list(groupId)])
      .then(([groupValue, featureValue, memberValues, projectValues, taskValues]) => {
        setGroup(groupValue); setFeatures(featureValue); setMembers(memberValues); setProjects(projectValues); setTasks(taskValues);
      }).catch((value) => setError(errorMessage(value))).finally(() => setLoading(false));
  }, [groupId]);

  function openCreate() {
    setEditing(undefined); setName(''); setDescription(''); setLeadMemberId(''); setStatus('PLANNED');
    setStartDate(''); setDueDate(''); setError(''); setShowForm(true);
  }

  function openEdit(project: ProjectResponse) {
    setEditing(project); setName(project.name); setDescription(project.description ?? '');
    setLeadMemberId(project.leadMemberId?.toString() ?? ''); setStatus(project.status);
    setStartDate(project.startDate ?? ''); setDueDate(project.dueDate ?? ''); setError(''); setShowForm(true);
  }

  async function save(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError('');
    const input = { name: name.trim(), description: description.trim() || undefined,
      leadMemberId: leadMemberId ? Number(leadMemberId) : undefined,
      startDate: startDate || undefined, dueDate: dueDate || undefined };
    try {
      if (editing) {
        const updated = await projectApi.update(editing.id, { ...input, status, expectedVersion: editing.version });
        setProjects((current) => current.map((value) => value.id === updated.id ? updated : value));
      } else {
        const created = await projectApi.create(groupId, input);
        setProjects((current) => [created, ...current]);
      }
      setShowForm(false);
    } catch (value) { setError(errorMessage(value)); }
    finally { setSaving(false); }
  }

  async function archive(project: ProjectResponse) {
    if (!window.confirm(t(`‘${project.name}’ 프로젝트를 보관할까요?`, `Archive “${project.name}”?`))) return;
    try {
      await projectApi.archive(project.id, project.version);
      setProjects((current) => current.map((value) => value.id === project.id
        ? { ...value, status: 'ARCHIVED', version: value.version + 1 } : value));
    } catch (value) { setError(errorMessage(value)); }
  }

  async function toggleProject(project: ProjectResponse) {
    if (expandedProjectId === project.id) { setExpandedProjectId(undefined); return; }
    setExpandedProjectId(project.id);
    if (topicsByProject[project.id]) return;
    try {
      const values = await projectIssueApi.list(project.id);
      setTopicsByProject((current) => ({ ...current,
        [project.id]: values.filter((value) => value.level === 'MAJOR' && !value.archivedAt) }));
    } catch (value) { setError(errorMessage(value)); }
  }

  if (!accessToken.get()) return <Navigate to="/login" replace />;
  if (loading) return <main className="center-page">{t('프로젝트를 불러오는 중...', 'Loading projects...')}</main>;
  if (!group || group.type !== 'TEAM') return <Navigate to={`/groups/${groupId}`} replace />;
  const visible = projects.filter((project) => project.status !== 'ARCHIVED');
  const archived = projects.filter((project) => project.status === 'ARCHIVED');
  const label = (value: [string, string]) => value[language === 'ko' ? 0 : 1];
  return <><AppNavigation /><main className="projects-page app-page">
    <header className="projects-header"><div><Link to={`/groups/${groupId}`}>← {t('그룹으로', 'Back to group')}</Link>
      <span className="page-eyebrow">PROJECTS</span><h1>{group.name} {t('프로젝트', 'Projects')}</h1>
      <p>{t('프로젝트를 만든 뒤 주제, 내용, 실행 항목을 한 흐름으로 정리합니다.', 'Create a project, then organize topics, details, and action items in one flow.')}</p></div>
      {group.role === 'LEADER' && <button className="primary" type="button" onClick={openCreate}>＋ {t('프로젝트 만들기', 'Create project')}</button>}
    </header>
    {features && <section className="project-plan-summary">
      <div><span>{t('회사 정책', 'Company policy')}</span><strong>{features.projectEnabled ? t('프로젝트 사용', 'Projects enabled') : t('프로젝트 미사용', 'Projects off')}</strong></div>
      <div><span>{t('채팅 보관', 'Chat retention')}</span><strong>{t(`${features.messageRetentionDays}일`, `${features.messageRetentionDays} days`)}</strong></div>
      <div><span>{t('채팅 채널', 'Chat channels')}</span><strong>{features.multipleChatChannels ? t(`최대 ${features.chatChannelLimit}개`, `Up to ${features.chatChannelLimit}`) : t('그룹 공용 1개', '1 group channel')}</strong></div>
      <div><span>{t('그룹 저장공간', 'Group storage')}</span><strong>{formatBytes(features.storageLimitBytes)}</strong></div>
    </section>}
    {error && <p className="error">{error}</p>}
    <section className="project-list-card"><div className="project-list-heading"><h2>{t('진행 프로젝트', 'Current projects')}</h2><strong>{visible.length}</strong></div>
      {visible.length === 0 ? <p className="empty-state">{t('아직 프로젝트가 없습니다. 첫 프로젝트를 만들어 협업 흐름을 시작하세요.', 'No projects yet. Create one to begin the collaboration flow.')}</p>
        : <div className="project-table-wrap"><div className="project-table" role="table"><div className="project-table-head" role="row"><span>{t('프로젝트 제목','Project')}</span><span>{t('설명','Description')}</span><span>{t('현황','Status')}</span><span>{t('총괄 담당자','Lead')}</span><span>{t('시작일','Start')}</span><span>{t('종료일','End')}</span><span /></div>{visible.map((project) => { const projectTasks=tasks.filter(task=>task.projectId===project.id); const complete=projectTasks.filter(task=>task.status==='COMPLETED').length; const progress=projectTasks.length?Math.round(complete*100/projectTasks.length):0; const expanded=expandedProjectId===project.id; const topics=topicsByProject[project.id]; return <div className={`project-table-group${expanded?' expanded':''}`} key={project.id}><div className="project-table-row" role="row"><button className="project-expand-button" type="button" aria-expanded={expanded} onClick={()=>toggleProject(project)}><i>{expanded?'⌄':'›'}</i><strong>{project.name}</strong></button><span className="project-table-description">{project.description||t('설명 없음','No description')}</span><span><b className={`project-status status-${project.status.toLowerCase()}`}>{label(statusLabels[project.status])}</b><small>{t(`진행률 ${progress}%`,`Progress ${progress}%`)}</small></span><span>{project.leadNickname??t('미지정','Unassigned')}</span><time>{project.startDate??'—'}</time><time>{project.dueDate??'—'}</time><div className="project-row-actions"><Link className="primary button-link" to={`/projects/${project.id}/flow`}>{t('관리','Manage')}</Link>{project.canManage&&<button className="ghost" type="button" onClick={()=>openEdit(project)}>{t('수정','Edit')}</button>}</div></div>{expanded&&<div className="project-expanded-topics"><header><div><strong>{t('프로젝트 주제','Project topics')}</strong><small>{t('주제를 누르면 해당 프로젝트 관리 화면에서 업무를 확인합니다.','Open a topic in the project workspace to review its tasks.')}</small></div><div><Link className="secondary button-link" to={`/groups/${groupId}/tasks?projectId=${project.id}&create=1`}>＋ {t('프로젝트 업무','Project task')}</Link><Link className="primary button-link" to={`/projects/${project.id}/flow`}>＋ {t('주제 추가·관리','Add/manage topics')}</Link></div></header>{!topics?<p>{t('주제 목록을 불러오는 중...','Loading topics...')}</p>:topics.length===0?<p>{t('등록된 주제가 없습니다. 프로젝트 관리에서 첫 주제를 추가해 주세요.','No topics yet. Add the first topic in project management.')}</p>:<div>{topics.map(topic=>{const topicTasks=projectTasks.filter(task=>task.projectTopicId===topic.id);const topicDone=topicTasks.filter(task=>task.status==='COMPLETED').length;return <Link to={`/projects/${project.id}/flow`} key={topic.id}><span><strong>{topic.title}</strong><small>{topic.assigneeNickname??t('담당자 미지정','Unassigned')}</small></span><span>{t(`업무 ${topicTasks.length} · 완료 ${topicDone}`,`${topicTasks.length} tasks · ${topicDone} done`)}</span><progress value={topicTasks.length?topicDone/topicTasks.length*100:0} max={100}/></Link>})}</div>}</div>}</div>})}</div></div>}
    </section>
    {archived.length > 0 && <details className="archived-projects"><summary>{t(`보관된 프로젝트 ${archived.length}개`, `${archived.length} archived projects`)}</summary><ul>{archived.map((project) => <li key={project.id}><Link to={`/projects/${project.id}/flow`}>{project.name}</Link></li>)}</ul></details>}
    {showForm && <Modal title={editing ? t('프로젝트 수정', 'Edit project') : t('새 프로젝트', 'New project')} onClose={() => setShowForm(false)}>
      <form className="form modal-form project-editor-form" onSubmit={save}>
        <label className="field"><span>{t('프로젝트 이름', 'Project name')}</span><input autoFocus required maxLength={120} value={name} onChange={(event) => setName(event.target.value)} /></label>
        <label className="field"><span>{t('설명', 'Description')}</span><textarea maxLength={5000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
        <label className="field"><span>{t('프로젝트 리더', 'Project lead')}</span><select value={leadMemberId} onChange={(event) => setLeadMemberId(event.target.value)}><option value="">{t('미지정', 'Unassigned')}</option>{members.filter((member) => member.status === 'ACTIVE').map((member) => <option value={member.id} key={member.id}>{member.nickname}</option>)}</select></label>
        {editing && <label className="field"><span>{t('상태', 'Status')}</span><select value={status} onChange={(event) => setStatus(event.target.value as ProjectStatus)}>{Object.entries(statusLabels).filter(([value]) => value !== 'ARCHIVED').map(([value, text]) => <option value={value} key={value}>{label(text)}</option>)}</select></label>}
        <div className="project-date-fields"><label className="field"><span>{t('시작일', 'Start date')}</span><input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} /></label><label className="field"><span>{t('종료일', 'Due date')}</span><input type="date" min={startDate || undefined} value={dueDate} onChange={(event) => setDueDate(event.target.value)} /></label></div>
        <div className="modal-actions">{editing && <button className="danger" type="button" disabled={saving} onClick={() => { setShowForm(false); void archive(editing); }}>{t('프로젝트 보관', 'Archive project')}</button>}<button className="secondary" type="button" onClick={() => setShowForm(false)}>{t('취소', 'Cancel')}</button><button className="primary" disabled={saving}>{saving ? t('저장 중...', 'Saving...') : t('저장', 'Save')}</button></div>
      </form></Modal>}
  </main></>;
}

function formatBytes(value: number) {
  if (value >= 1024 ** 3) return `${Math.round(value / 1024 ** 3)} GB`;
  return `${Math.round(value / 1024 ** 2)} MB`;
}
