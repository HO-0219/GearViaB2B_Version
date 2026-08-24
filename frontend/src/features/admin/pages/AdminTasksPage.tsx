import { FormEvent, useEffect, useState } from 'react';
import { adminApi, AdminTask } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { Modal } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { AdminTable, formatTime, StatusBadge } from '../AdminShared';

export function AdminTasksPage() {
  const { t } = useLanguage();
  const [tasks, setTasks] = useState<AdminTask[]>([]);
  const [deleted, setDeleted] = useState<AdminTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [suspendingTask, setSuspendingTask] = useState<AdminTask>();
  const [suspendReason, setSuspendReason] = useState('');
  const [suspending, setSuspending] = useState(false);
  const [deletingTask, setDeletingTask] = useState<AdminTask>();
  const [deleting, setDeleting] = useState(false);
  const [busyId, setBusyId] = useState<number>();

  function load() {
    return Promise.all([adminApi.tasks(), adminApi.deletedTasks()])
      .then(([page, recentlyDeleted]) => { setTasks(page.items); setDeleted(recentlyDeleted); })
      .catch((value) => setError(errorMessage(value)));
  }

  useEffect(() => { load().finally(() => setLoading(false)); }, []);

  async function resume(task: AdminTask) {
    setBusyId(task.id); setError('');
    try { await adminApi.resumeTask(task.id); await load(); }
    catch (value) { setError(errorMessage(value)); }
    finally { setBusyId(undefined); }
  }

  async function confirmSuspend(event: FormEvent) {
    event.preventDefault();
    if (!suspendingTask) return;
    setSuspending(true); setError('');
    try {
      await adminApi.suspendTask(suspendingTask.id, suspendReason.trim());
      setSuspendingTask(undefined); setSuspendReason('');
      await load();
    } catch (value) { setError(errorMessage(value)); }
    finally { setSuspending(false); }
  }

  async function confirmDelete() {
    if (!deletingTask) return;
    setDeleting(true); setError('');
    try {
      await adminApi.deleteTask(deletingTask.id);
      setDeletingTask(undefined);
      await load();
    } catch (value) { setError(errorMessage(value)); }
    finally { setDeleting(false); }
  }

  async function restore(task: AdminTask) {
    setBusyId(task.id); setError('');
    try { await adminApi.restoreTask(task.id); await load(); }
    catch (value) { setError(errorMessage(value)); }
    finally { setBusyId(undefined); }
  }

  if (loading) return <p className="admin-loading">{t('업무 데이터를 불러오는 중...', 'Loading task data...')}</p>;
  return <>
    {error && <p className="error">{error}</p>}
    <AdminTable title={t('업무', 'Tasks')} emptyLabel={t('표시할 업무가 없습니다.', 'No tasks to display.')}
      headers={['ID', t('그룹', 'Group'), t('제목', 'Title'), t('상태', 'Status'), t('요청자', 'Requester'), t('담당자', 'Assignee'), t('작업', 'Action')]}
      rows={tasks.map((task) => [
        task.id, task.groupName, task.title, <StatusBadge value={task.status} />,
        task.requesterNickname, task.assigneeNickname ?? '-',
        <div className="admin-row-actions">
          {task.status === 'ON_HOLD'
            ? <button className="admin-action" type="button" disabled={busyId === task.id} onClick={() => resume(task)}>{t('재개', 'Resume')}</button>
            : !['COMPLETED', 'REJECTED', 'CANCELLED'].includes(task.status) && <button className="admin-action danger" type="button" onClick={() => { setSuspendingTask(task); setSuspendReason(''); }}>{t('정지', 'Suspend')}</button>}
          <button className="admin-action danger" type="button" onClick={() => setDeletingTask(task)}>{t('삭제', 'Delete')}</button>
        </div>,
      ])} />
    <AdminTable title={t('최근 삭제된 업무', 'Recently deleted tasks')} emptyLabel={t('삭제된 업무가 없습니다.', 'No deleted tasks.')}
      headers={['ID', t('그룹', 'Group'), t('제목', 'Title'), t('삭제 시각', 'Deleted at'), t('작업', 'Action')]}
      rows={deleted.map((task) => [
        task.id, task.groupName, task.title, formatTime(task.deletedAt),
        <button className="admin-action" type="button" disabled={busyId === task.id} onClick={() => restore(task)}>{t('복구', 'Restore')}</button>,
      ])} />
    {suspendingTask && <Modal title={t('업무 정지', 'Suspend task')} description={t(`'${suspendingTask.title}' 업무를 보류(정지) 상태로 전환합니다. 사유는 담당자에게 그대로 표시됩니다.`, `This puts '${suspendingTask.title}' on hold. The reason is shown to the assignee.`)} onClose={() => !suspending && setSuspendingTask(undefined)}>
      <form className="form modal-form" onSubmit={confirmSuspend}>
        <label className="field"><span>{t('정지 사유', 'Reason')}</span><textarea autoFocus required maxLength={500} value={suspendReason} onChange={(event) => setSuspendReason(event.target.value)} /></label>
        {error && <p className="error">{error}</p>}
        <div className="modal-actions"><button className="secondary" type="button" disabled={suspending} onClick={() => setSuspendingTask(undefined)}>{t('취소', 'Cancel')}</button><button className="primary" disabled={suspending || !suspendReason.trim()}>{suspending ? t('처리 중...', 'Processing...') : t('정지', 'Suspend')}</button></div>
      </form>
    </Modal>}
    {deletingTask && <Modal title={t('업무 삭제', 'Delete task')} description={t(`'${deletingTask.title}' 업무를 삭제합니다. 삭제 후에도 복구할 수 있습니다.`, `This deletes '${deletingTask.title}'. It can be restored afterward.`)} onClose={() => !deleting && setDeletingTask(undefined)}>
      {error && <p className="error">{error}</p>}
      <div className="modal-actions"><button className="secondary" type="button" disabled={deleting} onClick={() => setDeletingTask(undefined)}>{t('취소', 'Cancel')}</button><button className="danger" type="button" disabled={deleting} onClick={confirmDelete}>{deleting ? t('삭제 중...', 'Deleting...') : t('삭제', 'Delete')}</button></div>
    </Modal>}
  </>;
}
