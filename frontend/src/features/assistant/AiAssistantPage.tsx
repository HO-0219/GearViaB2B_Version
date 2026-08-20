import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { assistantApi, type AssistantActionResponse, type AssistantTurn } from '../../api/assistantApi';
import { errorMessage } from '../../api/client';
import { groupApi, type GroupResponse } from '../../api/groupApi';
import { AppNavigation } from '../../app/AppNavigation';
import { useLanguage } from '../../app/LanguageContext';

type ChatItem = AssistantTurn & {
  id: number;
  actionId?: number;
  actionSummary?: string;
  actionResult?: AssistantActionResponse;
};

export function AiAssistantPage() {
  const { t } = useLanguage();
  const [params, setParams] = useSearchParams();
  const [groups, setGroups] = useState<GroupResponse[]>([]);
  const [message, setMessage] = useState('');
  const [items, setItems] = useState<ChatItem[]>([]);
  const [pending, setPending] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const groupId = Number(params.get('groupId') ?? 0);

  useEffect(() => {
    groupApi.list().then((values) => {
      setGroups(values);
      const preferred = values.find((value) => value.type === 'TEAM' && value.membershipPlan === 'PAID') ?? values[0];
      if (!groupId && preferred) setParams({ groupId: String(preferred.id) }, { replace: true });
    }).catch((caught) => setItems([assistantMessage(errorMessage(caught))]));
  }, []);

  useEffect(() => {
    if (!groupId) return;
    const selected = groups.find((value) => value.id === groupId);
    if (!selected || selected.type !== 'TEAM' || selected.membershipPlan !== 'PAID') {
      if (groups.length > 0) setItems([]);
      return;
    }
    setLoadingHistory(true);
    assistantApi.messages(groupId).then((values) => {
      setItems(values.length > 0 ? values.map((value) => ({
        id: value.id, role: value.role, content: value.content,
        actionId: value.actionId, actionSummary: value.actionSummary,
        actionResult: value.actionId && value.actionStatus && value.actionStatus !== 'PENDING'
          ? {
            actionId: value.actionId, status: value.actionStatus,
            message: value.actionStatus === 'COMPLETED'
              ? t('이미 실행한 작업입니다.', 'This action was already completed.')
              : t('실행할 수 없는 작업입니다.', 'This action can no longer be run.'),
          } : undefined,
      })) : [welcomeMessage(t)]);
    }).catch((caught) => setItems([assistantMessage(errorMessage(caught))]))
      .finally(() => setLoadingHistory(false));
  }, [groupId, groups]);

  async function send(event: FormEvent) {
    event.preventDefault();
    const content = message.trim();
    if (!content || !groupId || pending) return;
    const userItem: ChatItem = { id: Date.now(), role: 'user', content };
    setItems((old) => [...old, userItem]);
    setMessage('');
    setPending(true);
    try {
      const response = await assistantApi.chat(groupId, content);
      setItems((old) => [...old, {
        id: Date.now() + 1, role: 'assistant', content: response.message,
        actionId: response.pendingActionId, actionSummary: response.actionSummary,
      }]);
    } catch (caught) {
      setItems((old) => [...old, assistantMessage(errorMessage(caught))]);
    } finally {
      setPending(false);
    }
  }

  async function decide(item: ChatItem, confirm: boolean) {
    if (!item.actionId || pending) return;
    setPending(true);
    try {
      const result = confirm
        ? await assistantApi.confirm(item.actionId)
        : await assistantApi.cancel(item.actionId);
      setItems((old) => old.map((value) => value.id === item.id
        ? { ...value, actionResult: result } : value));
      if (result.selectedGroupId) {
        setParams({ groupId: String(result.selectedGroupId) });
      }
    } catch (caught) {
      setItems((old) => [...old, assistantMessage(errorMessage(caught))]);
    } finally {
      setPending(false);
    }
  }

  async function copyInvite(url: string) {
    await navigator.clipboard.writeText(url);
    setItems((old) => [...old, assistantMessage(t('초대 링크를 복사했습니다.', 'Invite link copied.'))]);
  }

  const selectedGroup = groups.find((group) => group.id === groupId);
  const assistantEnabled = selectedGroup?.type === 'TEAM' && selectedGroup.membershipPlan === 'PAID';

  return <><AppNavigation /><main className="assistant-page app-page">
    <header className="assistant-header"><div><span className="page-eyebrow">AI ASSISTANT</span>
      <h1>{t('업무 비서', 'Work assistant')}</h1>
      <p>{t('팀의 업무 맥락을 읽고, 지금 필요한 다음 행동을 안전하게 제안합니다.',
        'It reads your team context and safely proposes the next action that matters.')}</p>
      {assistantEnabled && <span className="assistant-live-badge"><i />{t('AI 활성 · 실행 준비됨', 'AI enabled · Ready')}</span>}</div>
      <label><span>{t('작업할 그룹', 'Workspace')}</span><select value={groupId || ''} onChange={(event) => {
        setParams({ groupId: event.target.value });
        setItems((old) => [...old, assistantMessage(t('작업 그룹을 변경했습니다.', 'Workspace changed.'))]);
      }}><option value="" disabled>{t('그룹 선택', 'Choose a group')}</option>{groups.map((group) =>
        <option value={group.id} key={group.id}>{group.name}</option>)}</select></label>
    </header>

    {!assistantEnabled && selectedGroup && <section className="assistant-policy-lock"><span aria-hidden="true">✦</span><div><h2>{t('AI 비서는 관리자 정책에서 허용된 팀 기능입니다.', 'AI assistant is available for teams enabled by an admin.')}</h2><p>{t('관리자가 AI 기능을 활성화하면 팀원 모두가 사용할 수 있습니다.', 'It becomes available to the whole team when an admin enables it.')}</p></div>{selectedGroup.role === 'LEADER' ? <Link className="primary" to={`/groups/${selectedGroup.id}?tab=plan`}>{t('AI 설정', 'Open AI settings')}</Link> : <small>{t('팀장에게 AI 기능 활성화를 요청해 주세요.', 'Ask your team leader to enable AI for this team.')}</small>}</section>}
    {assistantEnabled && <div className="assistant-workspace-layout"><section className="assistant-chat" aria-label={t('AI 비서 대화', 'AI assistant chat')}>
      <header className="assistant-chat-heading"><div><span>✦</span><div><strong>{t('B2BGearVia AI', 'B2BGearVia AI')}</strong><small>{t(`${selectedGroup?.name ?? ''}의 업무를 바탕으로 답변합니다.`, `Answers from work in ${selectedGroup?.name ?? ''}.`)}</small></div></div><b>{t('온라인', 'Online')}</b></header>
      <div className="assistant-messages" aria-live="polite">{items.map((item) =>
        <article className={`assistant-message ${item.role}`} key={item.id}>
          <span>{item.role === 'assistant' ? 'AI' : t('나', 'Me')}</span><p>{item.content}</p>
          {item.actionId && item.actionSummary && <div className="assistant-action-card">
            <strong>{item.actionSummary}</strong>
            {!item.actionResult && <div><button type="button" disabled={pending} onClick={() => decide(item, true)}>{t('확인하고 실행', 'Confirm and run')}</button>
              <button type="button" className="secondary" disabled={pending} onClick={() => decide(item, false)}>{t('취소', 'Cancel')}</button></div>}
            {item.actionResult && <div className={`assistant-action-result ${item.actionResult.status.toLowerCase()}`}><p>{item.actionResult.message}</p>
              {item.actionResult.targetUrl && <Link to={item.actionResult.targetUrl}>{t('결과 열기', 'Open result')}</Link>}
              {item.actionResult.inviteUrl && <button type="button" onClick={() => copyInvite(item.actionResult!.inviteUrl!)}>{t('초대 링크 복사', 'Copy invite link')}</button>}</div>}
          </div>}
        </article>)}</div>
      {(pending || loadingHistory) && <p className="assistant-thinking" role="status">{loadingHistory
        ? t('이전 대화를 불러오고 있어요…', 'Loading previous messages…')
        : t('비서가 확인하고 있어요…', 'The assistant is working…')}</p>}
      <div className="assistant-quick-prompts" aria-label={t('빠른 요청', 'Quick prompts')}>
        {[t('이번 주 마감 임박 업무를 정리해줘', 'Summarize work due this week'), t('배포 점검 업무와 체크리스트를 만들어줘', 'Create a release task and checklist'), t('진행 중인 업무의 막힌 지점을 알려줘', 'Find blockers in active work')].map((prompt) => <button type="button" onClick={() => setMessage(prompt)} disabled={pending} key={prompt}>{prompt}</button>)}
      </div>
      <form className="assistant-composer" onSubmit={send}><textarea value={message} maxLength={2000}
        onChange={(event) => setMessage(event.target.value)} disabled={!groupId || pending}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
            event.preventDefault();
            event.currentTarget.form?.requestSubmit();
          }
        }}
        placeholder={t("예: '배포 점검 업무를 만들고 체크리스트에 테스트, 모니터링을 넣어줘'",
          "Example: 'Create a release check task with testing and monitoring checklist items'")} />
        <button type="submit" disabled={!message.trim() || !groupId || pending}>{t('보내기', 'Send')}</button></form>
    </section><aside className="assistant-guide-panel"><span className="assistant-guide-symbol">✦</span><h2>{t('대화가 실제 업무가 되는 방식', 'How chat becomes real work')}</h2><p>{t('AI가 임의로 바꾸지 않습니다. 현재 데이터와 내 권한을 확인한 뒤 실행 전에 한 번 더 보여줍니다.', 'AI never changes work on its own. It checks current data and your permissions, then previews every action before execution.')}</p><ol><li><b>01</b><div><strong>{t('업무 맥락 확인', 'Read context')}</strong><small>{t('선택한 그룹의 업무·댓글·알림을 확인', 'Review tasks, comments, and alerts in this group')}</small></div></li><li><b>02</b><div><strong>{t('권한 검사', 'Check permission')}</strong><small>{t('현재 계정이 할 수 있는 작업만 제안', 'Only propose actions your account can perform')}</small></div></li><li><b>03</b><div><strong>{t('확인 후 실행', 'Confirm and run')}</strong><small>{t('요약을 확인하고 승인한 작업만 반영', 'Apply only the actions you approve')}</small></div></li></ol><div className="assistant-guide-note"><b>{t('안전한 기본값', 'Safe by default')}</b><span>{t('삭제·상태 변경 같은 작업은 대화만으로 즉시 실행되지 않습니다.', 'Actions such as deletion or status changes never run from chat alone.')}</span></div></aside></div>}
  </main></>;
}

function assistantMessage(content: string): ChatItem {
  return { id: Date.now() + Math.random(), role: 'assistant', content };
}

function welcomeMessage(t: (ko: string, en: string) => string): ChatItem {
  return assistantMessage(t(
    '무엇을 도와드릴까요? 업무·댓글·멘션·알림 처리와 작업 그룹 선택을 맡길 수 있어요.',
    'How can I help? I can handle tasks, comments, mentions, notifications, and workspace selection.'));
}
