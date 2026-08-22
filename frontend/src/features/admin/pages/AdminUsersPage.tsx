import { FormEvent, useEffect, useState } from 'react';
import { adminApi, AdminUser } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
import { Modal } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { AdminTable, StatusBadge } from '../AdminShared';

export function AdminUsersPage() {
  const { t } = useLanguage();
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [employeeName, setEmployeeName] = useState('');
  const [companyEmail, setCompanyEmail] = useState('');
  const [creatingUser, setCreatingUser] = useState(false);
  const [editingUser, setEditingUser] = useState<AdminUser>();
  const [editNickname, setEditNickname] = useState('');
  const [editSaving, setEditSaving] = useState(false);
  const [withdrawingUser, setWithdrawingUser] = useState<AdminUser>();
  const [withdrawSaving, setWithdrawSaving] = useState(false);
  const [resettingId, setResettingId] = useState<number>();

  useEffect(() => {
    adminApi.users().then((page) => setUsers(page.items))
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setLoading(false));
  }, []);

  async function createEmployee(event: FormEvent) {
    event.preventDefault();
    setCreatingUser(true); setError(''); setMessage('');
    try {
      const result = await adminApi.createUser({ name: employeeName, email: companyEmail, role: 'USER' });
      setUsers((current) => [result.user, ...current]);
      setEmployeeName(''); setCompanyEmail('');
      setMessage(t(`사원 계정을 등록했습니다. 최초 비밀번호: ${result.temporaryPassword}`, `Employee account created. Initial password: ${result.temporaryPassword}`));
    } catch (value) { setError(errorMessage(value)); }
    finally { setCreatingUser(false); }
  }

  async function status(user: AdminUser) {
    try {
      const updated = await adminApi.userStatus(user.id, user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE');
      setUsers((current) => current.map((value) => value.id === updated.id ? updated : value));
    } catch (value) { setError(errorMessage(value)); }
  }

  async function resetPassword(user: AdminUser) {
    setResettingId(user.id); setError(''); setMessage('');
    try {
      const result = await adminApi.resetTemporaryPassword(user.id);
      setUsers((current) => current.map((value) => value.id === result.user.id ? result.user : value));
      setMessage(t(`${user.nickname}님의 비밀번호를 재설정했습니다. 임시 비밀번호: ${result.temporaryPassword}`, `Reset ${user.nickname}'s password. Temporary password: ${result.temporaryPassword}`));
    } catch (value) { setError(errorMessage(value)); }
    finally { setResettingId(undefined); }
  }

  async function saveEdit(event: FormEvent) {
    event.preventDefault();
    if (!editingUser) return;
    setEditSaving(true); setError('');
    try {
      const updated = await adminApi.updateUser(editingUser.id, editNickname.trim());
      setUsers((current) => current.map((value) => value.id === updated.id ? updated : value));
      setEditingUser(undefined);
    } catch (value) { setError(errorMessage(value)); }
    finally { setEditSaving(false); }
  }

  async function confirmWithdraw() {
    if (!withdrawingUser) return;
    setWithdrawSaving(true); setError('');
    try {
      await adminApi.withdrawUser(withdrawingUser.id);
      const page = await adminApi.users();
      setUsers(page.items);
      setWithdrawingUser(undefined);
    } catch (value) { setError(errorMessage(value)); }
    finally { setWithdrawSaving(false); }
  }

  if (loading) return <p className="admin-loading">{t('사용자 데이터를 불러오는 중...', 'Loading user data...')}</p>;
  return <>
    {error && <p className="error">{error}</p>}{message && <p className="success-message">{message}</p>}
    <section className="admin-panel admin-notice-panel">
      <div className="admin-panel-heading"><div><h2>{t('사원 계정 등록', 'Add employee account')}</h2><p>{t('이름과 회사 메일만 입력하면 계정이 생성됩니다. 최초 비밀번호는 user123이며 첫 로그인 후 변경해야 합니다.', 'Enter a name and company email. The initial password is user123 and must be changed after first login.')}</p></div></div>
      <form className="admin-inline" onSubmit={createEmployee}>
        <input aria-label={t('사원 이름', 'Employee name')} placeholder={t('사원 이름', 'Employee name')} value={employeeName} onChange={(event) => setEmployeeName(event.target.value)} required />
        <input aria-label={t('회사 메일', 'Company email')} placeholder="name@company.com" type="email" autoComplete="email" value={companyEmail} onChange={(event) => setCompanyEmail(event.target.value)} required />
        <button className="primary" type="submit" disabled={creatingUser}>{creatingUser ? t('등록 중...', 'Creating...') : t('사원 등록', 'Add employee')}</button>
      </form>
    </section>
    <AdminTable title={t('사용자', 'Users')} emptyLabel={t('표시할 사용자가 없습니다.', 'No users to display.')}
      headers={['ID', t('이름', 'Name'), t('이메일', 'Email'), t('상태', 'Status'), t('작업', 'Action')]}
      rows={users.map((user) => [
        user.id, user.nickname, user.maskedEmail, <StatusBadge value={user.status} />,
        <div className="admin-row-actions">
          {user.status !== 'WITHDRAWN' && <button className={`admin-action ${user.status === 'ACTIVE' ? 'danger' : ''}`} type="button" onClick={() => status(user)}>{user.status === 'ACTIVE' ? t('정지', 'Suspend') : t('복구', 'Activate')}</button>}
          {user.status !== 'WITHDRAWN' && <button className="admin-action" type="button" onClick={() => { setEditingUser(user); setEditNickname(user.nickname); }}>{t('수정', 'Edit')}</button>}
          {user.status !== 'WITHDRAWN' && <button className="admin-action" type="button" disabled={resettingId === user.id} onClick={() => resetPassword(user)}>{resettingId === user.id ? t('재설정 중...', 'Resetting...') : t('비밀번호 재설정', 'Reset password')}</button>}
          {user.status !== 'WITHDRAWN' && <button className="admin-action danger" type="button" onClick={() => setWithdrawingUser(user)}>{t('삭제', 'Delete')}</button>}
        </div>,
      ])} />
    {editingUser && <Modal title={t('사용자 정보 수정', 'Edit user')} description={t(`${editingUser.nickname}님의 표시 이름을 변경합니다.`, `Change the display name for ${editingUser.nickname}.`)} onClose={() => !editSaving && setEditingUser(undefined)}>
      <form className="form modal-form" onSubmit={saveEdit}>
        <label className="field"><span>{t('이름', 'Name')}</span><input autoFocus required maxLength={30} value={editNickname} onChange={(event) => setEditNickname(event.target.value)} /></label>
        {error && <p className="error">{error}</p>}
        <div className="modal-actions"><button className="secondary" type="button" disabled={editSaving} onClick={() => setEditingUser(undefined)}>{t('취소', 'Cancel')}</button><button className="primary" disabled={editSaving || !editNickname.trim()}>{editSaving ? t('저장 중...', 'Saving...') : t('저장', 'Save')}</button></div>
      </form>
    </Modal>}
    {withdrawingUser && <Modal title={t('사용자 삭제', 'Delete user')} description={t(`${withdrawingUser.nickname}님의 계정을 삭제합니다. 계정 정보는 익명화되며 되돌릴 수 없습니다.`, `This deletes ${withdrawingUser.nickname}'s account. The account is anonymized and this cannot be undone.`)} onClose={() => !withdrawSaving && setWithdrawingUser(undefined)}>
      {error && <p className="error">{error}</p>}
      <div className="modal-actions"><button className="secondary" type="button" disabled={withdrawSaving} onClick={() => setWithdrawingUser(undefined)}>{t('취소', 'Cancel')}</button><button className="danger" type="button" disabled={withdrawSaving} onClick={confirmWithdraw}>{withdrawSaving ? t('삭제 중...', 'Deleting...') : t('삭제', 'Delete')}</button></div>
    </Modal>}
  </>;
}
