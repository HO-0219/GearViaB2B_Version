import { useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { accessToken, errorMessage } from '../../../api/client';
import { groupApi, GroupResponse } from '../../../api/groupApi';
import { projectApi, ProjectResponse } from '../../../api/projectApi';
import { ProjectIssue, projectIssueApi } from '../../../api/projectIssueApi';
import { AppNavigation } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { ResourcePanel } from '../../resource/ResourcePanel';
import { ProjectFileSystem } from '../../project/components/ProjectFileSystem';

type Selection = { kind: 'default' } | { kind: 'project'; id: number };

export function GroupFileSystemPage() {
  const { t } = useLanguage();
  const groupId = Number(useParams().groupId);
  const [group, setGroup] = useState<GroupResponse>();
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [selection, setSelection] = useState<Selection>({ kind: 'default' });
  const [projectDetail, setProjectDetail] = useState<ProjectResponse>();
  const [projectNodes, setProjectNodes] = useState<ProjectIssue[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!Number.isInteger(groupId) || groupId < 1) { setLoading(false); return; }
    Promise.all([groupApi.get(groupId), projectApi.list(groupId)])
      .then(([groupValue, projectValues]) => {
        setGroup(groupValue);
        setProjects(projectValues.filter((project) => project.status !== 'ARCHIVED'));
      }).catch((value) => setError(errorMessage(value))).finally(() => setLoading(false));
  }, [groupId]);

  useEffect(() => {
    if (selection.kind !== 'project') { setProjectDetail(undefined); setProjectNodes([]); return; }
    let active = true;
    Promise.all([projectApi.get(selection.id), projectIssueApi.list(selection.id, true)])
      .then(([project, nodes]) => { if (active) { setProjectDetail(project); setProjectNodes(nodes); } })
      .catch((value) => setError(errorMessage(value)));
    return () => { active = false; };
  }, [selection]);

  if (!accessToken.get()) return <Navigate to="/login" replace />;
  if (loading) return <main className="center-page">{t('파일을 불러오는 중...', 'Loading files...')}</main>;
  if (!group || group.type !== 'TEAM') return <Navigate to={`/groups/${groupId}`} replace />;

  return <><AppNavigation /><main className="group-files-page app-page">
    <header className="projects-header"><div><Link to={`/groups/${groupId}`}>← {t('그룹으로', 'Back to group')}</Link>
      <span className="page-eyebrow">GROUP FILES</span><h1>{group.name} {t('파일 시스템', 'File system')}</h1>
      <p>{t('기본 자료함과 프로젝트별 파일을 한 곳에서 탐색합니다.', 'Browse the default folder and every project’s files in one tree.')}</p></div>
    </header>
    {error && <p className="error">{error}</p>}
    <section className="project-files group-file-system">
      <div className="project-file-layout">
        <nav className="project-folder-tree" aria-label={t('그룹 폴더', 'Group folders')}>
          <div className="group-file-root">📁 {group.name}</div>
          <div className="folder-branch">
            <button className={selection.kind === 'default' ? 'selected' : ''} type="button"
              onClick={() => setSelection({ kind: 'default' })}>📁 {t('기본 자료함', 'Default folder')}</button>
          </div>
          {projects.map((project) => <div className="folder-branch" key={project.id}>
            <button className={selection.kind === 'project' && selection.id === project.id ? 'selected' : ''} type="button"
              onClick={() => setSelection({ kind: 'project', id: project.id })}>📁 {project.name}</button>
          </div>)}
          {projects.length === 0 && <p className="group-file-tree-empty">{t('아직 프로젝트가 없습니다.', 'No projects yet.')}</p>}
        </nav>
        <div className="project-file-content group-file-content">
          {selection.kind === 'default'
            ? <ResourcePanel groupId={groupId} />
            : projectDetail
              ? <ProjectFileSystem project={projectDetail} nodes={projectNodes} />
              : <p className="project-files-empty">{t('프로젝트 파일을 불러오는 중...', 'Loading project files...')}</p>}
        </div>
      </div>
    </section>
  </main></>;
}
