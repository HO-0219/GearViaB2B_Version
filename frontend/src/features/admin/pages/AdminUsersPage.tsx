import { FormEvent, useEffect, useState } from 'react';
import { adminApi, AdminUser } from '../../../api/adminApi';
import { errorMessage } from '../../../api/client';
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
    <AdminTable title={t('사용자', 'Users')} emptyLabel={t('표시할 사용자가 없습니다.', 'No users to display.')} headers={['ID', t('이름', 'Name'), t('이메일', 'Email'), t('상태', 'Status'), t('작업', 'Action')]} rows={users.map((user) => [user.id, user.nickname, user.maskedEmail, <StatusBadge value={user.status} />, <button className={`admin-action ${user.status === 'ACTIVE' ? 'danger' : ''}`} type="button" onClick={() => status(user)}>{user.status === 'ACTIVE' ? t('정지', 'Suspend') : t('복구', 'Activate')}</button>])} />
  </>;
}
