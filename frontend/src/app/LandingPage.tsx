import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { BrandMark } from './BrandMark';
import { useLanguage } from './LanguageContext';
import { isPwaInstallAvailable, isRunningStandalone, promptPwaInstall } from './pwa';

export function LandingPage() {
  const { t, language, setLanguage } = useLanguage();
  const [installOpen, setInstallOpen] = useState(false);
  const [installable, setInstallable] = useState(isPwaInstallAvailable());
  const installDialogRef = useRef<HTMLElement>(null);
  const installTitleId = useId();
  const installed = isRunningStandalone();

  useEffect(() => {
    const sync = () => setInstallable(isPwaInstallAvailable());
    window.addEventListener('pwa-install-available', sync);
    window.addEventListener('pwa-installed', sync);
    return () => {
      window.removeEventListener('pwa-install-available', sync);
      window.removeEventListener('pwa-installed', sync);
    };
  }, []);
  useEffect(() => {
    if (!installOpen) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : undefined;
    const overflow = document.body.style.overflow;
    const focusableSelector = 'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])';
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault(); setInstallOpen(false); return;
      }
      if (event.key !== 'Tab' || !installDialogRef.current) return;
      const focusable = Array.from(installDialogRef.current.querySelectorAll<HTMLElement>(focusableSelector));
      if (focusable.length === 0) return;
      const first = focusable[0]; const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first.focus();
      }
    };
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', handleKeyDown);
    const frame = window.requestAnimationFrame(() => installDialogRef.current?.querySelector<HTMLElement>('button')?.focus());
    return () => {
      window.cancelAnimationFrame(frame);
      document.body.style.overflow = overflow;
      document.removeEventListener('keydown', handleKeyDown);
      previousFocus?.focus();
    };
  }, [installOpen]);

  async function install() {
    if (installable) {
      await promptPwaInstall();
      setInstallOpen(false);
      return;
    }
    setInstallOpen(true);
  }

  return <main className="landing-page">
    <header className="landing-nav">
      <Link to="/" className="landing-brand"><BrandMark /><strong>{t('Gearvia', 'Gearvia')}</strong></Link>
      <nav aria-label={t('랜딩 페이지 메뉴', 'Landing navigation')}>
        <Link to="/product">{t('제품', 'Product')}</Link>
        <Link to="/b2b">{t('B2B 솔루션', 'B2B solutions')}</Link>
        <Link to="/pricing">{t('가격', 'Pricing')}</Link>
        <Link to="/contact">{t('문의', 'Contact')}</Link>
        <button type="button" className="landing-language" onClick={() => setLanguage(language === 'ko' ? 'en' : 'ko')}>{language === 'ko' ? 'EN' : '한글'}</button>
        <Link to="/login">{t('로그인', 'Log in')}</Link>
        <Link className="landing-nav-cta" to="/login">{t('무료로 시작', 'Get started')}</Link>
      </nav>
    </header>

    <section className="landing-hero">
      <div className="landing-eyebrow"><span />{t('업무와 팀을 연결하는 협업 관리', 'Connected work for connected teams')}</div>
      <h1>{t('업무 요청부터 승인과 보고까지,', 'From requests and approvals to reports,')}<br /><em>{t('한 흐름으로 끝내세요.', 'finish work in one clear flow.')}</em></h1>
      <p>{t('업무·일정·대화를 한곳에 모으고, AI 비서가 현재 권한 안에서 사용자가 확인한 작업만 안전하게 실행합니다.', 'Bring tasks, schedules, and conversations together. The AI assistant acts only within your permissions and only after your confirmation.')}</p>
      <div className="landing-hero-actions">
        <Link className="landing-primary" to="/login">{t('무료로 시작', 'Get started')} <span>→</span></Link>
        <Link className="landing-demo" to="/demo">{t('실제 화면 보기', 'View the product')} <span>↗</span></Link>
      </div>
      <button className="landing-pwa-hero" type="button" onClick={() => setInstallOpen(true)} disabled={installed}><span aria-hidden="true">↓</span>{installed ? t('Gearvia 앱으로 실행 중', 'Running as the Gearvia app') : t('앱스토어 없이, 이 기기에 Gearvia 추가', 'Add Gearvia to this device—no app store')}</button>
      <small>{t('데모는 실제 시스템과 분리된 읽기 전용 화면입니다.', 'The demo is a read-only experience isolated from the live system.')}</small>
    </section>

    <section className="landing-product" aria-label={t('제품 화면 미리보기', 'Product preview')}>
      <div className="landing-window-bar"><i /><i /><i /><span>Gearvia · PWA</span></div>
      <div className="landing-product-body">
        <aside><div className="mock-brand"><BrandMark />{t('Gearvia', 'Gearvia')}</div><span className="active">⌂ {t('홈', 'Home')}</span><span>♧ {t('그룹', 'Groups')}</span><span>□ {t('캘린더', 'Calendar')}</span><span>♢ {t('알림', 'Alerts')}</span><b>{t('로컬 알파 시연팀', 'Alpha demo team')}</b></aside>
        <div className="mock-dashboard"><header><div><small>TODAY</small><h2>{t('김팀장님, 오늘도 반가워요!', 'Welcome back, Team Lead Kim!')}</h2><p>{t('중요한 일부터 하나씩 시작해 볼까요?', 'Start with what matters most today.')}</p></div><span className="mock-avatar">김</span></header>
          <div className="mock-metrics"><article><small>{t('진행 중', 'In progress')}</small><strong>8</strong><span>↗ 12%</span></article><article><small>{t('완료', 'Completed')}</small><strong>24</strong><span>75%</span></article><article><small>{t('마감 임박', 'Due soon')}</small><strong>3</strong><span className="warning">{t('확인 필요', 'Review')}</span></article></div>
          <div className="mock-columns"><article><h3>{t('내 우선 업무', 'Priority tasks')}</h3><MockTask color="violet" title={t('모바일 화면 최종 점검', 'Final mobile review')} meta={t('오늘 18:00 마감', 'Due today 18:00')} /><MockTask color="blue" title={t('발표 자료 초안 작성', 'Draft presentation')} meta={t('내일 마감', 'Due tomorrow')} /><MockTask color="orange" title={t('외부 피드백 반영', 'Apply external feedback')} meta={t('보류 중', 'On hold')} /></article><article><h3>{t('다가오는 일정', 'Upcoming')}</h3><div className="mock-event"><b>26</b><span><strong>{t('주간 진행 공유', 'Weekly sync')}</strong><small>10:00 · {t('회의실 A', 'Room A')}</small></span></div><div className="mock-event"><b>28</b><span><strong>{t('발표 리허설', 'Presentation rehearsal')}</strong><small>14:00 · Online</small></span></div></article></div>
        </div>
      </div>
    </section>

    <section className="landing-ai-showcase" aria-label={t('AI 비서와 AI 리포트 미리보기', 'AI assistant and report preview')}>
      <header><span className="landing-section-label">AI WORKFLOW</span><h2>{t('업무를 읽고, 다음 행동까지 연결하는 AI.', 'AI that reads the work and connects the next action.')}</h2><p>{t('막연한 답변 대신 팀에 기록된 업무를 바탕으로 정리하고, 실행할 작업은 반드시 확인을 거칩니다.', 'Instead of generic answers, it works from recorded team data and always asks for confirmation before taking action.')}</p></header>
      <div className="landing-ai-products">
        <article className="landing-assistant-preview"><div className="landing-ai-card-head"><span>✦</span><div><small>AI ASSISTANT</small><strong>{t('말로 요청하고, 확인해서 실행', 'Ask in chat, confirm before action')}</strong></div><b>{t('멤버십', 'MEMBERSHIP')}</b></div><div className="landing-ai-chat"><p className="user">{t('이번 주 마감 임박 업무를 정리하고 배포 점검 업무도 만들어줘.', 'Summarize work due this week and create a release-check task.')}</p><div className="assistant"><i>AI</i><p>{t('마감 임박 업무는 3건입니다. 배포 점검 업무는 아래 내용으로 만들 수 있어요.', 'Three tasks are due soon. I can create the release-check task below.')}</p></div><div className="landing-ai-action"><span>{t('실행 전 확인', 'Review before action')}</span><strong>{t('운영 배포 점검 · 긴급 · 오늘 18:00', 'Production release check · Urgent · Today 18:00')}</strong><small>{t('체크리스트 3개 · 담당자 미지정', '3 checklist items · Unassigned')}</small><button type="button" tabIndex={-1}>{t('확인하고 실행', 'Confirm and run')}</button></div></div></article>
        <article className="landing-report-preview"><div className="landing-ai-card-head"><span>↗</span><div><small>AI WEEKLY REPORT</small><strong>{t('숫자 너머의 변화와 위험까지', 'Changes and risks beyond the numbers')}</strong></div><b>R3</b></div><div className="landing-report-summary"><span>{t('7월 4주차 · 그룹 전체', 'July week 4 · Whole group')}</span><h3>{t('출시 준비는 계획대로 진행 중이지만, 결제 검증 업무가 일정 위험을 만들고 있습니다.', 'Launch preparation is on track, but payment verification is creating schedule risk.')}</h3><div><p><small>{t('완료율', 'Completion')}</small><strong>76%</strong><i>+5%p</i></p><p><small>{t('완료 업무', 'Completed')}</small><strong>19</strong><i>+6</i></p><p><small>{t('위험 업무', 'At risk')}</small><strong>3</strong><i className="risk">{t('확인', 'Review')}</i></p></div><ul><li><b>{t('위험', 'RISK')}</b><span>{t('결제 정보 마스킹 검증이 오늘 마감이며 체크리스트 1개가 남았습니다.', 'Payment masking review is due today with one checklist item remaining.')}</span></li><li><b>{t('제안', 'ACTION')}</b><span>{t('오후 배포 전 정품질 담당자의 검증 결과를 먼저 확인하세요.', 'Review the QA owner’s verification result before this afternoon’s release.')}</span></li></ul></div></article>
      </div>
      <footer><span>{t('AI 비서와 AI 리포트는 유료 팀 멤버십에서 활성화됩니다.', 'AI assistant and reports activate with paid team membership.')}</span><div><Link to="/demo">{t('AI 데모 체험', 'Try the AI demo')} →</Link><Link to="/pricing">{t('멤버십 보기', 'View membership')}</Link></div></footer>
    </section>

    <section id="features" className="landing-section">
      <span className="landing-section-label">{t('한곳에서 끝내기', 'Everything connected')}</span>
      <h2>{t('팀의 속도를 늦추는 빈틈을 없애세요.', 'Remove the gaps that slow work down.')}</h2>
      <div className="landing-feature-grid">
        <article><b>01</b><h3>{t('요청부터 담당까지', 'Request to ownership')}</h3><p>{t('팀원이 업무를 제안하고 팀장이 승인하면, 적합한 팀원이 직접 담당 업무를 선택합니다.', 'Members propose work, leads approve it, and the right teammate can claim ownership.')}</p></article>
        <article><b>02</b><h3>{t('상태가 보이는 협업', 'Visible progress')}</h3><p>{t('체크리스트, 댓글, 멘션과 상태 이력이 한 업무 안에 쌓여 맥락을 잃지 않습니다.', 'Checklists, comments, mentions, and history stay attached to the work itself.')}</p></article>
        <article><b>03</b><h3>{t('업무와 일정 연결', 'Tasks meet calendar')}</h3><p>{t('마감 업무와 팀 일정을 같은 캘린더에서 확인하고 중요한 알림을 놓치지 않습니다.', 'See deadlines and team events together, with alerts for what needs attention.')}</p></article>
        <article><b>04</b><h3>{t('확인하고 실행하는 AI 비서', 'AI actions you control')}</h3><p>{t('대화로 업무·댓글·멘션·알림을 요청하고, 권한 검사와 최종 확인을 거쳐 실행합니다.', 'Request tasks, comments, mentions, and alerts in chat, then run them after permission checks and final confirmation.')}</p></article>
      </div>
    </section>

    <section className="landing-name-story" aria-label={t('Gearvia 브랜드 이야기', 'The Gearvia name')}>
      <span>Gear + Via</span>
      <h2>{t('업무와 구성원이 톱니처럼 맞물려,', 'Work and people mesh like gears,')}<br /><em>{t('하나의 흐름으로 움직입니다.', 'moving forward through one connected path.')}</em></h2>
      <p>{t('Gear는 서로 맞물려 움직이는 협업을, Via는 요청부터 실행과 보고까지 이어지는 경로를 뜻합니다.', 'Gear represents collaboration in motion; Via represents the connected path from request to execution and reporting.')}</p>
    </section>

    <section className="landing-proof" aria-label={t('Gearvia 핵심 가치', 'Gearvia key value')}>
      <header><span className="landing-section-label">{t('실제 업무가 움직이는 구조', 'Built for work that moves')}</span><h2>{t('기록만 쌓는 도구가 아니라, 다음 행동을 분명하게.', 'More than stored records—make the next action clear.')}</h2><p>{t('누가 요청했고, 누가 승인하며, 누가 맡아야 하는지 한 흐름 안에서 확인합니다. 일정과 대화도 같은 업무에 연결됩니다.', 'See who requested, who approves, and who owns the next step. Schedules and conversations stay connected to the same work.')}</p></header>
      <div><article><strong>{t('요청과 승인', 'Request & approve')}</strong><p>{t('팀원의 제안을 팀장이 확인하고 반려 사유까지 기록합니다.', 'Leads review proposals and preserve rejection context.')}</p></article><article><strong>{t('직접 담당 선택', 'Claim ownership')}</strong><p>{t('승인된 업무를 진행 가능한 팀원이 직접 맡아 병목을 줄입니다.', 'Available teammates claim approved work and reduce bottlenecks.')}</p></article><article><strong>{t('업무 중심 협업', 'Work-centered context')}</strong><p>{t('체크리스트, 댓글, 멘션과 변경 이력을 업무별로 모읍니다.', 'Keep checklists, comments, mentions, and history per task.')}</p></article><article><strong>{t('일정과 리포트', 'Schedule & reports')}</strong><p>{t('마감과 팀 일정을 함께 보고 기간별 PDF 리포트를 만듭니다.', 'View deadlines with team events and create period-based PDF reports.')}</p></article></div>
    </section>

    <section id="workflow" className="landing-workflow">
      <div><span className="landing-section-label">{t('가볍게 시작하기', 'Start lightly')}</span><h2>{t('설치 없이 시작하고, 필요할 때 앱으로.', 'Start in the browser. Make it an app when ready.')}</h2><p>{t('회원가입 후 바로 브라우저에서 사용할 수 있습니다. 자주 사용한다면 별도 앱스토어 없이 현재 기기에 PWA 앱을 만들 수 있습니다.', 'Use it immediately after signing up. If it becomes part of your routine, add the PWA to your device without an app store.')}</p><button type="button" onClick={() => setInstallOpen(true)} disabled={installed}>{installed ? t('이미 앱으로 사용 중', 'Already installed') : t('이 기기에 APP 만들기', 'Make this an app')}</button></div>
      <ol><li><b>1</b><span><strong>{t('그룹 만들기', 'Create a group')}</strong><small>{t('멤버를 초대하고 역할을 정합니다.', 'Invite members and set roles.')}</small></span></li><li><b>2</b><span><strong>{t('업무 흐름 연결', 'Connect the workflow')}</strong><small>{t('요청·승인·담당·완료를 기록합니다.', 'Track request, approval, ownership, and completion.')}</small></span></li><li><b>3</b><span><strong>{t('한눈에 확인', 'Stay aligned')}</strong><small>{t('대시보드와 캘린더로 지금을 봅니다.', 'See the present in dashboards and calendars.')}</small></span></li></ol>
    </section>

    <section className="landing-confidence">
      <div><span className="landing-section-label">{t('안심하고 시험하기', 'Try it with confidence')}</span><h2>{t('브라우저에서 시작하고, 팀에 맞으면 그대로 이어가세요.', 'Start in the browser and keep going when it fits your team.')}</h2></div>
      <div className="landing-confidence-grid"><article><b>01</b><strong>{t('읽기 전용 데모', 'Read-only demo')}</strong><p>{t('김팀장과 팀원 더미 데이터로 실제 화면과 흐름을 먼저 확인합니다.', 'Explore real screens and flows with manager and teammate sample data.')}</p></article><article><b>02</b><strong>{t('설치 선택권', 'Optional installation')}</strong><p>{t('다운로드 없이 사용하고, 자주 쓰는 기기에만 PWA로 추가합니다.', 'Use it without a download, then add the PWA only on devices you choose.')}</p></article><article><b>03</b><strong>{t('데이터 분리 원칙', 'Data boundaries')}</strong><p>{t('데모 데이터는 브라우저 샘플로만 제공되어 실제 계정·업무·결제 데이터와 섞이지 않습니다.', 'Demo data stays in the browser sample and never mixes with real accounts, work, or payments.')}</p></article></div>
    </section>

    <section className="landing-final-cta"><BrandMark /><h2>{t('팀의 업무를 하나의 흐름으로 연결하세요.', 'Connect your team in one clear workflow.')}</h2><p>{t('요청부터 실행과 보고까지, Gearvia에서 함께 움직입니다.', 'Move together from request to execution and reporting with Gearvia.')}</p><div><Link to="/login">{t('무료로 시작', 'Get started')} →</Link><Link to="/demo">{t('데모 보기', 'View demo')}</Link></div></section>
    <footer><Link to="/" className="landing-brand"><BrandMark /><strong>{t('Gearvia', 'Gearvia')}</strong></Link><nav><Link to="/privacy">{t('개인정보 처리방침', 'Privacy')}</Link><Link to="/terms">{t('이용약관', 'Terms')}</Link><Link to="/paid-terms">{t('유료서비스 약관', 'Paid terms')}</Link><Link to="/refund-policy">{t('환불 정책', 'Refunds')}</Link><Link to="/site-map">{t('사이트맵', 'Site map')}</Link></nav><small>© 2026 Gearvia</small></footer>

    {installOpen && <div className="landing-install-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && setInstallOpen(false)}><section ref={installDialogRef} role="dialog" aria-modal="true" aria-labelledby={installTitleId} tabIndex={-1}><button className="landing-install-close" type="button" onClick={() => setInstallOpen(false)} aria-label={t('닫기', 'Close')}>×</button><span className="landing-install-icon"><BrandMark /></span><h2 id={installTitleId}>{t('Gearvia를 앱으로 설치할까요?', 'Install Gearvia as an app?')}</h2><p>{t('앱스토어 다운로드 없이 홈 화면과 앱 목록에 아이콘을 추가합니다.', 'Add an icon to your home screen and app list without an app store download.')}</p><ul><li>{t('독립된 앱 화면으로 빠르게 실행됩니다.', 'Opens quickly in its own app window.')}</li><li>{t('기본 화면 일부를 저장하지만, 최신 조회와 변경에는 인터넷이 필요합니다.', 'Keeps part of the shell offline; current data and changes still need internet.')}</li><li>{t('알림·카메라 같은 권한은 자동으로 허용되지 않습니다.', 'Notification and camera permissions are not granted automatically.')}</li><li>{t('기기 설정에서 언제든 제거할 수 있습니다.', 'You can remove it anytime in device settings.')}</li></ul>{installable ? <div className="landing-install-actions"><button type="button" onClick={() => setInstallOpen(false)}>{t('나중에', 'Not now')}</button><button className="confirm" type="button" onClick={install}>{t('APP 만들기', 'Install app')}</button></div> : <div className="landing-install-manual"><strong>{t('브라우저 메뉴에서 직접 추가해 주세요.', 'Add it from your browser menu.')}</strong><p>{t('iPhone/iPad: Safari 공유 버튼 → 홈 화면에 추가\nAndroid/PC: 브라우저 메뉴 → 앱 설치 또는 홈 화면에 추가', 'iPhone/iPad: Safari Share → Add to Home Screen\nAndroid/Desktop: Browser menu → Install app or Add to Home Screen')}</p></div>}</section></div>}
  </main>;
}

function MockTask({ color, title, meta }: { color: string; title: string; meta: string }) {
  return <div className="mock-task"><i className={color} /><span><strong>{title}</strong><small>{meta}</small></span><b>•••</b></div>;
}
