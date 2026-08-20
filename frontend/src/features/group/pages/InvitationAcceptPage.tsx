import { useState } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { groupApi, MemberResponse } from '../../../api/groupApi';
import { accessToken, errorMessage } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';
import { BrandMark } from '../../../app/BrandMark';

export function InvitationAcceptPage() {
  const { t } = useLanguage();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [member, setMember] = useState<MemberResponse>();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  if (!accessToken.get()) {
    const next = `/group-invitations/accept?token=${encodeURIComponent(token)}`;
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />;
  }

  async function accept() {
    setPending(true);
    setError('');
    try {
      setMember(await groupApi.acceptInvitation(token));
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setPending(false);
    }
  }

  return <main className="center-page"><section className="auth-card profile-card invitation-accept-card"><span className="brand-mark"><BrandMark /></span><h1>{t('그룹 초대', 'Group invitation')}</h1>{member ? <div className="success"><strong>{t('그룹에 참여했습니다.', 'You joined the group.')}</strong><p>{t(`${member.nickname}님이 팀원으로 등록되었습니다.`, `${member.nickname} was added as a member.`)}</p><Link className="primary link-button" to="/groups">{t('내 그룹 확인', 'View my groups')}</Link></div> : <><p className="muted">{t('초대 내용을 확인하고 그룹 참여를 수락해 주세요. 이메일로 받은 초대는 해당 이메일 계정으로 로그인해야 합니다.', 'Review the invitation and accept to join. Email invitations require the invited email account.')}</p>{!token && <p className="error">{t('초대 토큰이 없습니다.', 'The invitation token is missing.')}</p>}{error && <p className="error">{error}</p>}<button className="primary invitation-accept-button" disabled={!token || pending} onClick={accept}>{pending ? t('처리 중...', 'Processing...') : t('초대 수락', 'Accept invitation')}</button><Link className="account-link" to="/groups">{t('나중에 하기', 'Maybe later')}</Link></>}</section></main>;
}
