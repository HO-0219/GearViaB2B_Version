# B2BGearVia On-Premise Edition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 GearVia와 완전히 분리된 저장소에서, 소규모 회사가 Ubuntu 단일 사내 서버에 실행 파일로 설치하고 Docker로 운영할 수 있는 B2BGearVia를 만든다.

**Architecture:** 고객은 Ubuntu 서버에서 `B2BGearVia-Installer.run`을 실행한다. 설치기는 서버 환경을 검사하고 B2BGearVia의 고정 버전 Docker 이미지를 네트워크로 내려받아 웹, 백엔드, MySQL을 한 서버에 설치한다. 공개 회원가입·소셜 로그인·결제는 제거하고, 회사 관리자 계정과 회사 정책 기반 기능 권한으로 대체한다.

**Tech Stack:** Ubuntu Server 24.04 LTS x86_64, Docker Engine, Docker Compose v2, Nginx, React/Vite, Spring Boot 3.3, Java 21, MySQL 8.4, Bash 기반 `.run` 설치기

**Spec:** 이 문서의 `확정 제품 명세`와 `전환 원칙` 절이 구현 기준이다.

## 원본 스냅샷

- 원본 프로젝트: GearVia (`teamProject`)
- 복사 기준 브랜치: `main`
- 복사 기준 커밋: `8647e981bd7b57930fd485965c33e718ff4462b6`
- 복사 방식: 위 커밋의 Git 추적 파일만 복사
- 복사에서 제외된 항목: 원본 `.git`, `.env`, 로컬 비밀키, DB 데이터, 업로드 데이터, `node_modules`, `target`, `dist`
- 이 복사본에는 원본 Git 이력이 없다. 새 창에서 별도의 비공개 저장소로 초기화한다.

## Global Constraints

- 기존 GearVia 저장소와 배포 파이프라인은 수정하지 않는다.
- B2BGearVia는 반드시 별도 비공개 Git 저장소로 운영한다.
- 최초 인증 대상은 Ubuntu Server 24.04 LTS x86_64 한 종류로 제한한다.
- 한 설치는 한 회사만 사용하는 단일 테넌트이며, 서버 한 대에서 실행한다.
- Docker Engine과 Docker Compose v2는 설치 전에 서버 관리자가 설치한다.
- 설치와 업데이트 시 외부 네트워크의 HTTPS 아웃바운드 연결이 필수다.
- 핵심 협업 기능은 설치 후 외부 네트워크 없이도 동작해야 한다.
- OpenAI 기능, 메일, 웹 푸시, 업데이트 확인은 각각 필요한 외부 네트워크가 있을 때만 동작한다.
- 공개 회원가입, Google/Kakao 로그인, 데모 로그인, 인앱 결제, 무료 체험, 자동결제는 제공하지 않는다.
- OpenAI API 키는 선택사항이며, 키 없이도 설치와 핵심 기능이 정상 동작해야 한다.
- DB 비밀번호, JWT 비밀키, 관리자 MFA 암호화 키는 설치기가 생성한다.
- 모든 비밀값은 Git, 설치 로그, 프로세스 인자, 브라우저 응답에 노출하지 않는다.
- 업데이트는 자동 적용하지 않고 서버 관리자가 명시적으로 승인한다.
- DB 마이그레이션 전에는 자동 백업을 만들고, 실패 시 복원 절차를 안내한다.
- 고객에게는 소스 코드가 아니라 설치기, 서명·체크섬, Docker 이미지, 매뉴얼을 제공한다.

---

## 확정 제품 명세

### 지원 환경

| 항목 | 최초 지원 범위 |
|---|---|
| 운영체제 | Ubuntu Server 24.04 LTS x86_64 |
| 배포 | Docker Engine + Docker Compose v2 |
| 서버 구성 | 단일 서버, 단일 회사 |
| DB | 설치기가 구성하는 MySQL 8.4 컨테이너 |
| 파일 | Docker 영속 볼륨 또는 `/opt/b2bgearvia/data/uploads` |
| 접속 | 사내 DNS 또는 서버 IP의 HTTPS |
| 설치 네트워크 | 외부 HTTPS 연결 필수 |
| 평상시 네트워크 | 핵심 기능은 외부 연결 불필요 |
| AI | 고객 OpenAI API 키가 있을 때만 활성화 |

### 고객 설치 흐름

1. 서버 관리자가 Ubuntu에 Docker Engine과 Docker Compose v2를 설치한다.
2. `B2BGearVia-Installer.run`을 내려받는다.
3. `chmod +x B2BGearVia-Installer.run`을 실행한다.
4. `sudo ./B2BGearVia-Installer.run`을 실행한다.
5. 설치기가 OS, CPU, Docker, Compose, 메모리, 디스크, 포트, 네트워크를 검사한다.
6. 회사명, 접속 주소, 최초 관리자 계정, TLS 방식을 입력한다.
7. 설치기가 DB·JWT·MFA 비밀키를 생성한다.
8. 설치기가 고정 버전 Docker 이미지를 내려받고 다이제스트를 검증한다.
9. MySQL, 백엔드, 웹 컨테이너를 실행하고 Flyway 마이그레이션을 수행한다.
10. 준비 상태 API와 브라우저 접속을 확인한다.
11. 접속 URL, 관리자 ID, 백업 위치, 운영 명령을 출력한다.

### 설치 후 관리 명령

```bash
sudo b2bgearvia status
sudo b2bgearvia start
sudo b2bgearvia stop
sudo b2bgearvia restart
sudo b2bgearvia logs
sudo b2bgearvia backup
sudo b2bgearvia restore /opt/b2bgearvia/backups/b2bgearvia-2026-08-20T010000Z.tar.zst
sudo b2bgearvia configure
sudo b2bgearvia configure-ai
sudo b2bgearvia check-update
sudo b2bgearvia update
```

### 외부 네트워크 요구사항

- 설치 및 업데이트: B2BGearVia 릴리스 다운로드 서버와 이미지 저장소의 TCP 443
- AI 활성화 시: `api.openai.com` 또는 고객이 지정한 호환 API 주소의 TCP 443
- 메일 활성화 시: 고객 SMTP 서버의 지정 포트
- 웹 푸시 활성화 시: 브라우저별 푸시 제공자의 TCP 443
- MySQL 포트 3306은 Docker 내부 네트워크에서만 사용하고 호스트에 공개하지 않는다.
- 외부 사용자가 접근하는 호스트 포트는 기본적으로 443만 허용한다.

---

## 전환 원칙

### 유지할 기능

- 그룹·팀원·역할
- 업무 요청·승인·담당·상태 흐름
- 체크리스트·댓글·멘션
- 캘린더·알림
- 대시보드·기본 리포트·PDF
- 프로젝트·프로젝트 이슈·긴급 이슈
- 채팅·자료·문서 업로드
- JWT 세션, 기기 세션, 로그아웃, 관리자 MFA의 보안 기반
- Flyway 마이그레이션, 상태 점검 API, Docker 헬스체크

### 제거할 기능

- 공개 랜딩 페이지의 SaaS 가입·가격·결제 안내
- 공개 회원가입과 이메일 인증
- Google·Kakao OAuth 가입·로그인
- 공개 아이디 찾기·비밀번호 초기화
- 읽기 전용 데모 로그인과 데모 데이터
- Toss Payments 연동 전체
- 결제수단·결제시도·정기결제·무료체험·해지·미납 처리
- 유료서비스 약관·환불정책·구독 전환 알림
- `FREE`/`PAID` 상태를 이용한 기능 잠금

### 대체할 기능

| 기존 GearVia | B2BGearVia |
|---|---|
| 공개 회원가입 | 관리자가 계정 생성 |
| 소셜 로그인 | 로컬 계정, 후속 버전의 OIDC |
| 결제 승인 | 회사 설치 라이선스 |
| 무료/유료 플랜 | 회사 관리자 기능 정책 |
| 개인 결제 관리자 | 회사 시스템 관리자 |
| 유료 채팅·저장 한도 | 회사 설정의 보존기간·용량 한도 |
| 유료 AI 권한 | AI 키 설정 + 관리자 활성화 |

---

## Task 1: 새 저장소 격리와 기준선 확정

**Files:**
- Review: `.github/workflows/*`
- Review: `GITHUB_ACTIONS_SECRETS_SETUP.txt`
- Create: `README.md`
- Create: `docs/architecture/product-boundary.md`

**Produces:** 기존 GearVia로 푸시하거나 배포할 수 없는 독립 B2BGearVia 저장소

- [ ] 새 창에서 현재 디렉터리가 B2BGearVia 복사본인지 확인한다.
- [ ] 기존 `.github/workflows`를 먼저 비활성화하거나 B2B 전용 워크플로로 교체하기 전까지 원격 저장소에 푸시하지 않는다.
- [ ] `git init -b main`으로 새 Git 저장소를 초기화한다.
- [ ] GitHub에 `B2BGearVia` 비공개 저장소를 만든다.
- [ ] 새 비공개 저장소만 `origin`으로 등록하고 `git remote -v`로 GearVia 원격이 없는지 확인한다.
- [ ] `README.md`에 Ubuntu·Docker·네트워크 필수 조건과 지원하지 않는 환경을 기록한다.
- [ ] 이 문서와 원본 스냅샷을 첫 기준선 커밋에 포함한다.
- [ ] 기준선 태그 `b2b-baseline-8647e98`을 만든다.

**Verification:**

```bash
git remote -v
git status --short
git log -1 --oneline
git tag --list b2b-baseline-8647e98
```

GearVia 원격 주소가 없어야 하며 작업 트리가 깨끗해야 한다.

## Task 2: 제품명과 런타임 네임스페이스 분리

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `frontend/package.json`
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/public/manifest.webmanifest`
- Modify: `frontend/public/sw.js`
- Modify: `frontend/vite.config.ts`
- Modify: `docker-compose.yml`
- Replace: `infra/single-ec2/*`
- Replace: `GITHUB_ACTIONS_SECRETS_SETUP.txt`

**Produces:** `teamProject`와 `totaskflow` 런타임 이름이 남지 않는 B2BGearVia 전용 애플리케이션·DB·볼륨·캐시 이름

- [ ] Maven artifact, Spring application name, 프론트엔드 package name을 B2BGearVia 이름으로 변경한다.
- [ ] DB 기본 이름을 `b2bgearvia`로 통일한다.
- [ ] Docker 프로젝트, 네트워크, 볼륨 이름을 `b2bgearvia-*`로 통일한다.
- [ ] 업로드·설정·백업 경로를 `/opt/b2bgearvia` 아래로 통일한다.
- [ ] 브라우저 localStorage, Web Lock, PWA 캐시 이름을 `b2bgearvia-*`로 통일한다.
- [ ] 기존 `totaskflow.com`, EC2, Certbot 자동 갱신 전용 설정을 제거한다.
- [ ] 사용자 화면의 브랜드는 `B2BGearVia`로, 보고서 파일명은 `b2bgearvia-*`로 통일한다.
- [ ] 문자열 검색으로 이전 런타임 이름이 의도치 않게 남지 않았는지 확인한다.

**Verification:**

```bash
rg -n -i "teamproject|totaskflow" . --glob '!B2B_TRANSITION_PLAN.md' --glob '!frontend/package-lock.json'
./backend/mvnw -f backend/pom.xml test
npm --prefix frontend ci
npm --prefix frontend run build
```

검색 결과는 Java 패키지 이름처럼 의도적으로 유지하기로 기록한 항목만 허용한다.

## Task 3: B2B 전용 운영 설정과 안전 검증

**Files:**
- Replace: `.env.example`
- Replace: `backend/src/main/java/com/teamproject/common/config/ProductionConfigurationValidator.java`
- Replace tests: `backend/src/test/java/com/teamproject/common/config/ProductionConfigurationValidatorTest.java`
- Create: `backend/src/main/java/com/teamproject/common/config/B2bConfigurationValidator.java`
- Create: `backend/src/test/java/com/teamproject/common/config/B2bConfigurationValidatorTest.java`

**Produces:** Google OAuth·Toss·공개 도메인을 요구하지 않고, B2B 필수 비밀키·DB·TLS·저장소만 검증하는 운영 프로필

- [ ] `APP_ENVIRONMENT=b2b-production`을 B2B 운영 프로필로 정의한다.
- [ ] Google OAuth, Toss, 공개 메일을 필수로 요구하는 현재 검증 규칙을 제거한다.
- [ ] MySQL 주소가 Docker 내부 서비스 `mysql:3306`을 사용하도록 검증한다.
- [ ] DB 비밀번호, JWT 비밀키, 관리자 MFA 키의 길이와 기본값 사용 금지를 검증한다.
- [ ] `DEMO_ENABLED=false`, 안전 쿠키, Hibernate `validate`, 로컬 영속 저장소를 강제한다.
- [ ] AI 키가 비어 있을 때는 실패시키지 않고 AI 기능만 비활성화한다.
- [ ] 설정 오류 메시지에는 비밀값 자체가 포함되지 않도록 테스트한다.

**Verification:**

```bash
./backend/mvnw -f backend/pom.xml -Dtest=B2bConfigurationValidatorTest test
```

안전한 B2B 설정은 통과하고 기본 비밀번호·누락된 JWT·노출된 DB 포트 설정은 실패해야 한다.

## Task 4: 결제와 SaaS 구독 제거

**Files:**
- Delete: `backend/src/main/java/com/teamproject/payment/`
- Delete: `backend/src/main/java/com/teamproject/subscription/`
- Delete: `frontend/src/features/payment/`
- Delete: `frontend/src/features/subscription/`
- Delete: `frontend/src/api/paymentApi.ts`
- Delete: `frontend/src/api/subscriptionApi.ts`
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/com/teamproject/admin/application/AdminService.java`
- Modify: `backend/src/main/java/com/teamproject/admin/application/dto/AdminDtos.java`
- Modify: `frontend/src/features/admin/AdminPage.tsx`
- Modify: `frontend/src/api/adminApi.ts`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/app/HomePage.tsx`
- Modify: `frontend/src/app/PwaStatus.tsx`

**Produces:** Toss SDK, 결제 API, 결제 화면, 정기결제 작업, 구독 운영 통계가 런타임과 빌드에서 완전히 제거된 애플리케이션

- [ ] Toss 설정과 결제 암호화 키를 모든 환경설정·Compose·CI에서 제거한다.
- [ ] 결제·구독 컨트롤러, 서비스, 도메인, 저장소, 스케줄러를 제거한다.
- [ ] `/payments` 라우트와 결제수단·구독 UI를 제거한다.
- [ ] 관리자 개요에서 결제·구독 통계를 제거한다.
- [ ] 결제 전환 알림 타입과 문구를 제거한다.
- [ ] 사용자 엔티티의 `paymentCustomerKey`를 B2B 스키마에서 제거한다.
- [ ] Spring Context에 결제·구독 Bean이 남지 않는 테스트를 추가한다.

**Verification:**

```bash
rg -n -i "toss|payment|subscription|paid-terms|refund-policy" backend/src/main frontend/src .env.example infra
./backend/mvnw -f backend/pom.xml test
npm --prefix frontend run build
```

검색 결과는 삭제 사실을 검증하는 테스트나 이 계획 문서 외에는 없어야 한다.

## Task 5: 회사 정책 기반 기능 권한으로 교체

**Files:**
- Replace: `backend/src/main/java/com/teamproject/group/application/GroupFeaturePolicy.java`
- Replace: `backend/src/main/java/com/teamproject/assistant/application/AiAssistantEntitlementService.java`
- Modify: `backend/src/main/java/com/teamproject/report/application/AiWeeklyReportAccessService.java`
- Modify: `backend/src/main/java/com/teamproject/report/application/ReportScheduleService.java`
- Modify: `backend/src/main/java/com/teamproject/chat/application/ChatRetentionCleanup.java`
- Modify: `backend/src/main/java/com/teamproject/group/domain/Group.java`
- Create: `backend/src/main/java/com/teamproject/organization/application/OrganizationFeaturePolicy.java`
- Create: `backend/src/test/java/com/teamproject/organization/OrganizationFeaturePolicyTest.java`

**Produces:** `FREE`/`PAID`가 아니라 회사 설정과 AI 구성 여부로 기능 사용 가능 여부를 판단하는 단일 정책 서비스

- [ ] 프로젝트, 채팅, 파일, 리포트 접근에서 멤버십 플랜 검사를 제거한다.
- [ ] 채팅 채널 수, 보존 기간, 저장공간, 첨부파일 한도를 B2B 환경설정으로 이동한다.
- [ ] AI 접근은 `관리자 활성화`, `API 키 존재`, `사용자 그룹 권한` 세 조건으로 판단한다.
- [ ] AI 미설정 오류를 결제 요구가 아닌 관리자 설정 안내로 변경한다.
- [ ] `MembershipPlan`, `paidStartedAt`, `paidUntil`, `nextBillingAt`이 필요한지 전수 조사하고 B2B 스키마에서 제거한다.
- [ ] 회사 정책을 우회해 API를 직접 호출해도 서버가 동일하게 차단하도록 테스트한다.

**Verification:**

```bash
rg -n "MembershipPlan|paidUntil|PAID|FREE|PAYMENT_REQUIRED" backend/src/main frontend/src
./backend/mvnw -f backend/pom.xml -Dtest=OrganizationFeaturePolicyTest test
```

기능 권한 코드에서 결제·유료 플랜 의존이 없어야 한다.

## Task 6: B2B 로그인과 최초 관리자 부트스트랩

**Files:**
- Delete: `backend/src/main/java/com/teamproject/authentication/application/OAuthLoginService.java`
- Delete: `backend/src/main/java/com/teamproject/authentication/application/OAuthSignupCleanup.java`
- Delete: `backend/src/main/java/com/teamproject/authentication/domain/oauth/`
- Delete: `backend/src/main/java/com/teamproject/authentication/infrastructure/oauth/`
- Delete: `backend/src/main/java/com/teamproject/authentication/presentation/OAuthProviderController.java`
- Delete: `backend/src/main/java/com/teamproject/authentication/presentation/OAuthSignupController.java`
- Delete: `frontend/src/features/auth/pages/OAuthCallbackPage.tsx`
- Delete: `frontend/src/features/auth/pages/OAuthConsentPage.tsx`
- Delete: `frontend/src/features/auth/pages/SignupPage.tsx`
- Modify: `backend/src/main/java/com/teamproject/authorization/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/teamproject/authentication/presentation/SignupController.java`
- Modify: `backend/src/main/java/com/teamproject/authentication/presentation/RecoveryController.java`
- Modify: `backend/src/main/java/com/teamproject/authentication/presentation/SessionController.java`
- Modify: `frontend/src/features/auth/pages/LoginPage.tsx`
- Create: `backend/src/main/java/com/teamproject/installation/BootstrapAdminService.java`
- Create: `backend/src/test/java/com/teamproject/installation/BootstrapAdminServiceTest.java`

**Produces:** 공개 가입 없이 설치 시 한 번만 시스템 관리자를 만들고, 이후 관리자가 사용자 계정을 관리하는 인증 흐름

- [ ] Spring OAuth2 Client 의존성과 OAuth 설정을 제거한다.
- [ ] 공개 회원가입, 이메일 가입 인증, 아이디 찾기, 공개 비밀번호 초기화 API를 닫는다.
- [ ] 로그인 화면에서 회원가입·소셜 로그인·공개 복구 링크를 제거한다.
- [ ] 설치기가 전달한 일회성 비밀 파일로 최초 ADMIN 계정을 생성한다.
- [ ] 사용자 테이블이 비어 있을 때만 부트스트랩을 허용한다.
- [ ] 부트스트랩 성공 후 일회성 비밀 파일을 삭제하고 재사용을 거부한다.
- [ ] 기존 JWT 액세스 토큰, 회전형 리프레시 토큰, 기기 세션, 로그아웃 전체 기능을 유지한다.
- [ ] ADMIN 로그인에는 현재 MFA 흐름을 유지한다.
- [ ] 비상 복구용 로컬 ADMIN 계정은 SSO를 나중에 추가해도 삭제하지 않는다.

**Verification:**

```bash
./backend/mvnw -f backend/pom.xml -Dtest=BootstrapAdminServiceTest,AuthFlowTest,AuthSecurityApiTest test
rg -n -i "oauth2|google|kakao|signup" backend/src/main frontend/src
```

최초 한 번만 ADMIN이 생성되고 공개 가입·OAuth 엔드포인트는 존재하지 않아야 한다.

## Task 7: 회사 관리자 사용자 관리

**Files:**
- Modify: `backend/src/main/java/com/teamproject/admin/presentation/AdminController.java`
- Modify: `backend/src/main/java/com/teamproject/admin/application/AdminService.java`
- Modify: `backend/src/main/java/com/teamproject/admin/application/dto/AdminDtos.java`
- Modify: `frontend/src/features/admin/AdminPage.tsx`
- Modify: `frontend/src/api/adminApi.ts`
- Create: `backend/src/test/java/com/teamproject/admin/B2bUserAdministrationApiTest.java`

**Produces:** ADMIN이 사용자 생성, 초기 비밀번호 발급, 활성화, 정지, 세션 만료를 수행할 수 있는 사내 계정 관리 화면

- [ ] ADMIN만 사용자 계정을 생성할 수 있게 한다.
- [ ] 신규 사용자는 첫 로그인에서 비밀번호를 변경하도록 한다.
- [ ] 관리자가 임시 비밀번호를 재발급하면 기존 세션을 모두 만료시킨다.
- [ ] 사용자 정지·복구·세션 종료를 감사 로그에 남긴다.
- [ ] 관리자가 자기 자신을 정지하거나 마지막 ADMIN을 제거하지 못하도록 한다.
- [ ] 사용자 목록에서 비밀번호 해시와 인증 비밀은 절대 반환하지 않는다.

**Verification:**

```bash
./backend/mvnw -f backend/pom.xml -Dtest=B2bUserAdministrationApiTest test
```

ADMIN 권한, 첫 로그인 비밀번호 변경, 마지막 ADMIN 보호가 모두 검증되어야 한다.

## Task 8: B2B 전용 DB 기준선

**Files:**
- Replace: `backend/src/main/resources/db/migration/`
- Modify: `backend/src/test/java/com/teamproject/migration/MySqlFlywayMigrationTest.java`
- Create: `docs/operations/database-schema.md`

**Produces:** 신규 B2B 설치 전용 Flyway 기준선으로, SaaS 결제·구독·OAuth 테이블과 컬럼이 없는 MySQL 스키마

- [ ] 현재 45개 마이그레이션의 최종 스키마를 검증용 MySQL에 적용한다.
- [ ] B2B에서 유지할 테이블·인덱스·외래키 목록을 확정한다.
- [ ] 결제, 구독, OAuth 가입 요청, 소셜 계정, SaaS 동의 이력의 제거 여부를 도메인 요구사항과 맞춘다.
- [ ] 신규 B2B 설치 전용 `V1__create_b2bgearvia_schema.sql`을 만든다.
- [ ] 이후 변경은 `V2`부터 추가하고 배포된 마이그레이션은 수정하지 않는 규칙을 문서화한다.
- [ ] 빈 MySQL 8.4에서 Flyway 적용 후 Hibernate `validate`가 통과하는 테스트를 만든다.
- [ ] 마이그레이션 재실행이 데이터를 삭제하거나 초기 관리자 계정을 중복 생성하지 않는지 검증한다.

**Verification:**

```bash
./backend/mvnw -f backend/pom.xml -Dtest=MySqlFlywayMigrationTest test
```

빈 DB 설치, 재기동, Hibernate 검증이 모두 통과해야 한다.

## Task 9: OpenAI 선택 설정과 후속 변경

**Files:**
- Modify: `backend/src/main/java/com/teamproject/report/infrastructure/openai/OpenAIConfiguration.java`
- Modify: `backend/src/main/java/com/teamproject/report/infrastructure/openai/OpenAiReportProperties.java`
- Modify: `backend/src/main/java/com/teamproject/assistant/infrastructure/openai/OpenAiAssistantGateway.java`
- Modify: `backend/src/main/resources/application.properties`
- Create: `backend/src/test/java/com/teamproject/assistant/B2bAiConfigurationTest.java`
- Create: `installer/commands/configure-ai.sh`

**Produces:** 키가 없을 때 AI만 비활성화되고, `b2bgearvia configure-ai`로 키를 추가·교체·삭제한 뒤 안전하게 재시작할 수 있는 운영 흐름

- [ ] OpenAI 키 없이 백엔드와 모든 비AI 기능이 기동하는 테스트를 유지한다.
- [ ] AI 비활성 상태의 화면과 API가 결제 대신 관리자 설정을 안내하게 한다.
- [ ] `configure-ai` 명령이 키를 터미널에 표시하지 않고 숨김 입력으로 받게 한다.
- [ ] 키는 권한 `0600`인 서버 설정 파일에 저장하고 Git·로그·백업 기본본에서 제외한다.
- [ ] 설정 변경 후 백엔드를 재시작하고 AI 설정 상태만 마스킹해 확인한다.
- [ ] 키 삭제 시 AI 비서와 AI 리포트가 즉시 비활성화되도록 한다.
- [ ] AI 요청의 `store(false)`와 오류 로그의 응답 본문 비기록 정책을 유지한다.
- [ ] 관리자 매뉴얼에 선택한 그룹의 업무 맥락이 외부 AI 제공자에게 전송될 수 있음을 명시한다.

**Verification:**

```bash
./backend/mvnw -f backend/pom.xml -Dtest=B2bAiConfigurationTest,OpenAiReportConfigurationTest test
```

키 없음, 유효한 설정, 키 삭제 세 상태가 독립적으로 검증되어야 한다.

## Task 10: B2B Docker 운영 스택

**Files:**
- Replace: `infra/single-ec2/compose.yml`
- Replace: `infra/single-ec2/nginx-http.conf.template`
- Replace: `infra/single-ec2/nginx-https.conf.template`
- Replace: `infra/single-ec2/nginx-app-locations.conf`
- Modify: `backend/Dockerfile`
- Modify: `frontend/Dockerfile`
- Create: `infra/b2b/compose.yml`
- Create: `infra/b2b/nginx.conf.template`
- Create: `infra/b2b/runtime.env.example`
- Create: `infra/b2b/systemd/b2bgearvia.service`

**Produces:** 외부에 443만 노출하고 MySQL·백엔드는 내부 네트워크에 격리한 단일 서버 Compose 스택

- [ ] 서비스 이름을 `web`, `backend`, `mysql`, `init-data`로 고정한다.
- [ ] MySQL 3306과 백엔드 8081을 호스트에 publish하지 않는다.
- [ ] 웹 컨테이너만 443을 publish한다.
- [ ] MySQL과 업로드를 명시적 영속 볼륨에 저장한다.
- [ ] 컨테이너를 non-root로 실행하고 읽기 전용 파일시스템 적용 가능 범위를 검토해 적용한다.
- [ ] 각 서비스에 healthcheck, restart policy, 로그 회전, 종료 유예 시간을 설정한다.
- [ ] 이미지 태그가 아니라 릴리스 manifest의 digest로 고정한다.
- [ ] 관리자 API는 별도 공개 포트를 사용하지 않고 같은 HTTPS 경로에서 ADMIN·MFA로 보호한다.
- [ ] `b2bgearvia.service`가 Docker 시작 후 Compose 스택을 복구하도록 한다.

**Verification:**

```bash
docker compose -f infra/b2b/compose.yml config
docker compose -f infra/b2b/compose.yml up -d
docker compose -f infra/b2b/compose.yml ps
curl --fail --silent https://localhost/api/v1/health/ready --insecure
```

모든 컨테이너가 healthy이고 호스트에서 3306·8081이 열리지 않아야 한다.

## Task 11: 실행형 온라인 설치기와 관리 CLI

**Files:**
- Create: `installer/B2BGearVia-Installer.run`
- Create: `installer/lib/preflight.sh`
- Create: `installer/lib/configure.sh`
- Create: `installer/lib/download.sh`
- Create: `installer/lib/install.sh`
- Create: `installer/bin/b2bgearvia`
- Create: `installer/tests/preflight_test.sh`
- Create: `installer/tests/install_smoke_test.sh`

**Produces:** 사용자가 한 실행 파일로 설치하고 `b2bgearvia` 명령으로 운영할 수 있는 Ubuntu 전용 설치기

- [ ] 설치기는 root 권한, Ubuntu 24.04, x86_64, Docker, Compose v2를 검사한다.
- [ ] 최소 요구량을 충족하지 않으면 무엇이 부족한지 한 항목씩 출력하고 설치를 중단한다.
- [ ] 80·443 포트 충돌, 쓰기 불가능한 설치 경로, 부족한 디스크를 사전에 탐지한다.
- [ ] 배포 서버 연결과 TLS 인증서 검증에 실패하면 우회하지 않고 중단한다.
- [ ] 회사명, 접속 주소, 최초 ADMIN ID·이메일·비밀번호, TLS 모드를 대화형으로 입력받는다.
- [ ] 비밀번호는 숨김 입력으로 받고 명령행·프로세스 목록·로그에 남기지 않는다.
- [ ] 설치 경로를 `/opt/b2bgearvia`로 고정하고 설정·데이터·백업·로그·비밀 폴더를 분리한다.
- [ ] DB 비밀번호, JWT 키, MFA 암호화 키를 암호학적 난수로 생성한다.
- [ ] 릴리스 manifest와 이미지 digest를 확인한 뒤 필요한 이미지를 다운로드한다.
- [ ] 설치 중 실패하면 실행한 단계와 복구 명령을 출력한다.
- [ ] 설치 성공 후 `b2bgearvia` CLI를 `/usr/local/bin`에 설치한다.
- [ ] `status`, `start`, `stop`, `restart`, `logs`, `configure`, `configure-ai` 명령을 구현한다.
- [ ] 같은 버전 설치기를 다시 실행해도 데이터와 비밀키를 덮어쓰지 않게 한다.

**Verification:**

```bash
bash installer/tests/preflight_test.sh
bash installer/tests/install_smoke_test.sh
shellcheck installer/lib/*.sh installer/bin/b2bgearvia
```

깨끗한 Ubuntu 24.04 VM에서 설치, 재실행, 중단 후 재시도가 검증되어야 한다.

## Task 12: TLS와 사내 접속

**Files:**
- Create: `installer/lib/tls.sh`
- Create: `docs/operations/tls-and-dns.md`
- Modify: `infra/b2b/nginx.conf.template`

**Produces:** 사내 DNS 인증서 또는 서버 관리자가 제공한 인증서로 HTTPS를 구성하는 두 가지 설치 모드

- [ ] `provided-certificate` 모드는 PEM 인증서와 개인키 경로를 입력받고 일치 여부와 만료일을 검증한다.
- [ ] `internal-self-signed` 모드는 입력한 사내 DNS 또는 IP를 SAN에 포함한 인증서를 생성한다.
- [ ] 자체 서명 모드에서는 브라우저 신뢰 경고와 회사 단말에 인증서를 배포하는 방법을 안내한다.
- [ ] 개인키 파일 권한을 `0600`으로 제한하고 컨테이너에는 읽기 전용으로 마운트한다.
- [ ] HTTPS가 아닌 경우 secure cookie를 끄는 우회 옵션을 제공하지 않는다.
- [ ] 인증서 교체 명령과 Nginx 무중단 reload 절차를 관리 CLI에 추가한다.

**Verification:**

```bash
openssl x509 -in /opt/b2bgearvia/tls/server.crt -noout -subject -issuer -dates -ext subjectAltName
curl --fail --silent https://b2bgearvia.internal/api/v1/health/ready --cacert /opt/b2bgearvia/tls/ca.crt
```

인증서 SAN과 실제 접속 주소가 일치해야 한다.

## Task 13: 백업·복원·업데이트·롤백

**Files:**
- Create: `installer/commands/backup.sh`
- Create: `installer/commands/restore.sh`
- Create: `installer/commands/check-update.sh`
- Create: `installer/commands/update.sh`
- Create: `infra/b2b/systemd/b2bgearvia-backup.service`
- Create: `infra/b2b/systemd/b2bgearvia-backup.timer`
- Create: `installer/tests/backup_restore_test.sh`
- Create: `installer/tests/update_rollback_test.sh`

**Produces:** MySQL·업로드·설정을 일관되게 백업하고 검증한 뒤, 관리자 승인으로만 버전을 올리는 운영 도구

- [ ] 백업은 DB dump, 업로드 파일, 비밀값을 제외한 운영 설정, 설치 버전 manifest를 한 묶음으로 만든다.
- [ ] 백업 파일에 SHA-256 체크섬을 생성한다.
- [ ] 일일 백업과 보존 개수를 systemd timer 설정으로 제공한다.
- [ ] 복원 전 현재 상태를 별도 안전 백업하고 서비스 중단 시간을 명시한다.
- [ ] 복원은 빈 테스트 환경에서 실제로 수행해 로그인·업무·첨부파일을 확인한다.
- [ ] 업데이트 전 현재 버전, 대상 버전, 디스크, 네트워크, DB 백업을 검사한다.
- [ ] 새 이미지를 digest로 내려받고 명시적 확인 후에만 전환한다.
- [ ] 애플리케이션 실패만 발생한 경우 이전 이미지로 되돌린다.
- [ ] DB 마이그레이션 이후 되돌릴 때는 이전 DB dump 복원이 필요하다고 명확히 안내한다.
- [ ] 업데이트 중단 후 다시 실행해도 상태를 잃지 않도록 단계 파일을 기록한다.

**Verification:**

```bash
bash installer/tests/backup_restore_test.sh
bash installer/tests/update_rollback_test.sh
```

백업 후 생성한 신규 데이터가 복원 뒤 사라지고, 백업 당시의 DB와 파일이 동일하게 돌아와야 한다.

## Task 14: 릴리스 다운로드와 공급망 보호

**Files:**
- Create: `.github/workflows/b2b-release.yml`
- Create: `release/manifest.schema.json`
- Create: `release/build-installer.sh`
- Create: `release/generate-checksums.sh`
- Create: `docs/operations/release-process.md`

**Produces:** 버전별 설치기·이미지·manifest·체크섬을 만들고, 설치기가 변조와 버전 불일치를 거부하는 릴리스 파이프라인

- [ ] 백엔드·웹 이미지를 한 릴리스 버전으로 빌드한다.
- [ ] 이미지 digest, 최소 설치기 버전, DB 스키마 버전, 파일 체크섬을 release manifest에 기록한다.
- [ ] Git 태그와 manifest 버전이 다르면 릴리스를 중단한다.
- [ ] 설치기와 manifest에 SHA-256 체크섬을 제공한다.
- [ ] 고객용 다운로드 권한은 소스 저장소 쓰기 권한과 분리한다.
- [ ] 설치기는 manifest에 없는 이미지나 예상 digest와 다른 이미지를 실행하지 않는다.
- [ ] 릴리스 산출물에 SBOM과 오픈소스 고지 파일을 포함한다.
- [ ] 정상 릴리스, 변조된 manifest, 잘못된 digest, 네트워크 중단을 각각 테스트한다.

**Verification:**

```bash
bash release/build-installer.sh
bash release/generate-checksums.sh
sha256sum --check release/checksums.txt
```

체크섬 검증이 성공해야 하며 파일 하나를 변경하면 검증이 실패해야 한다.

## Task 15: 운영 보안과 감사

**Files:**
- Modify: `backend/src/main/java/com/teamproject/admin/application/AdminAuditService.java`
- Modify: `backend/src/main/java/com/teamproject/authorization/config/SecurityAuditFilter.java`
- Modify: `backend/src/main/java/com/teamproject/admin/config/AdminAccessFilter.java`
- Create: `backend/src/test/java/com/teamproject/admin/B2bAuditApiTest.java`
- Create: `docs/operations/security-hardening.md`
- Create: `docs/operations/network-requirements.md`

**Produces:** 관리자 변경, 인증 실패, 사용자 상태 변경, AI 설정 변경, 백업·복원·업데이트 이력을 추적할 수 있는 감사 체계

- [ ] 로그인 성공·실패, 관리자 MFA, 계정 생성·정지·복구, 세션 종료를 기록한다.
- [ ] AI 활성화·비활성화와 키 교체 사실만 기록하고 키 값은 기록하지 않는다.
- [ ] 백업·복원·업데이트는 서버 운영 로그에 실행자·시간·결과·버전을 기록한다.
- [ ] 감사 로그 API는 ADMIN과 MFA를 모두 요구한다.
- [ ] 로그에 비밀번호, 토큰, API 키, 쿠키, 민감한 요청 본문이 없는지 자동 테스트한다.
- [ ] Ubuntu 방화벽, 허용 포트, 외부 목적지, 시간 동기화, 디스크 권한을 문서화한다.
- [ ] 보안 패치 지원기간과 심각도별 배포 기준을 문서화한다.

**Verification:**

```bash
./backend/mvnw -f backend/pom.xml -Dtest=B2bAuditApiTest,SensitiveDtoLoggingTest test
```

민감정보가 로그에 포함되지 않고 모든 관리자 변경에 감사 이벤트가 있어야 한다.

## Task 16: B2B 화면과 문구 정리

**Files:**
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/app/LandingPage.tsx`
- Modify: `frontend/src/app/PublicPages.tsx`
- Modify: `frontend/src/app/AppNavigation.tsx`
- Modify: `frontend/src/features/group/pages/GroupDetailPage.tsx`
- Modify: `frontend/src/features/admin/AdminPage.tsx`

**Produces:** SaaS 가입·가격·구독 문구가 없고 회사 내부 사용에 맞는 로그인·관리·기능 안내 UI

- [ ] 루트 경로는 공개 마케팅 랜딩이 아니라 로그인 또는 앱 홈으로 이동한다.
- [ ] 가격, 무료 체험, 결제, 유료 약관, 환불, 공개 B2B 문의 페이지를 제거한다.
- [ ] 그룹 상세의 구독 탭을 회사 정책 또는 저장·보존 설정 안내로 교체한다.
- [ ] AI 미설정 상태는 회사 관리자에게 문의하라는 문구로 표시한다.
- [ ] 관리자 화면에 시스템 상태, 사용자 수, DB 상태, 저장공간, 백업 시각, 설치 버전을 표시한다.
- [ ] 모바일과 데스크톱에서 로그인·관리·핵심 업무 흐름을 점검한다.

**Verification:**

```bash
rg -n -i "무료 체험|결제수단|자동결제|환불|paid membership|subscription|pricing" frontend/src
npm --prefix frontend run build
```

SaaS 결제·가격 문구가 남지 않고 프론트엔드 빌드가 통과해야 한다.

## Task 17: 매뉴얼과 고객 인수 문서

**Files:**
- Create: `docs/manuals/01-installation.md`
- Create: `docs/manuals/02-administrator.md`
- Create: `docs/manuals/03-user.md`
- Create: `docs/manuals/04-backup-restore.md`
- Create: `docs/manuals/05-update-rollback.md`
- Create: `docs/manuals/06-network-security.md`
- Create: `docs/manuals/07-ai-data-handling.md`
- Create: `docs/manuals/08-troubleshooting.md`
- Create: `docs/manuals/09-uninstallation.md`
- Create: `docs/manuals/10-support-policy.md`

**Produces:** 설치 담당자, 회사 관리자, 일반 사용자가 별도 설명 없이 배포·운영·복구할 수 있는 한국어 문서 세트

- [ ] 설치 전 요구사항에 Ubuntu 24.04 x86_64, Docker Engine, Compose v2, 외부 HTTPS 네트워크를 명시한다.
- [ ] 필요한 CPU·메모리·디스크는 부하 테스트로 검증한 최소값과 권장값을 구분해 적는다.
- [ ] 설치, 최초 로그인, 사용자 생성, MFA, AI 선택 설정을 실제 화면 순서로 작성한다.
- [ ] 방화벽 포트와 외부 통신 목적지를 표로 제공한다.
- [ ] 백업 파일을 서버 밖으로 복제해야 한다는 운영 원칙을 명시한다.
- [ ] 업데이트 전 백업, 업데이트 승인, 장애 시 복원 절차를 작성한다.
- [ ] OpenAI 사용 시 전송되는 데이터 범위와 비활성화 방법을 작성한다.
- [ ] 로그 수집 명령과 민감정보 제거 절차를 작성한다.
- [ ] 제거 시 애플리케이션만 제거할지 데이터까지 제거할지 분리하고 파괴적 명령에는 이중 확인을 요구한다.
- [ ] 지원 범위, 지원하지 않는 환경, 보안 패치 기간을 명시한다.
- [ ] Markdown 원본에서 고객 배포용 PDF를 생성하고 페이지 잘림·깨진 링크를 시각 검수한다.

## Task 18: Ubuntu 실서버 인수 테스트와 첫 릴리스

**Files:**
- Create: `qa/b2b-acceptance-checklist.md`
- Create: `qa/b2b-release-report.md`
- Create: `CHANGELOG.md`

**Produces:** 깨끗한 Ubuntu 서버에서 설치부터 복원까지 검증된 `B2BGearVia 1.0.0` 릴리스

- [ ] 새 Ubuntu 24.04 VM에서 Docker만 설치한 상태로 시작한다.
- [ ] 설치기를 실행해 최초 관리자 로그인까지 완료한다.
- [ ] 사용자 생성, 그룹 생성, 업무 흐름, 댓글, 캘린더, 채팅, 파일, PDF 리포트를 검증한다.
- [ ] OpenAI 키 없이 핵심 기능이 정상 동작하는지 검증한다.
- [ ] OpenAI 키를 나중에 추가하고 AI 비서·AI 리포트를 검증한다.
- [ ] 외부 네트워크를 차단한 뒤 핵심 기능과 기존 데이터 접근을 검증한다.
- [ ] 서버 재부팅 후 컨테이너와 서비스가 자동 복구되는지 검증한다.
- [ ] 백업을 만들고 별도 빈 서버에 복원한다.
- [ ] 이전 시험 버전에서 1.0.0으로 업데이트하고 데이터 보존을 검증한다.
- [ ] 잘못된 인증서, 포트 충돌, 디스크 부족, DB 장애 시 오류 안내를 검증한다.
- [ ] 전체 백엔드 테스트와 프론트엔드 빌드를 실행한다.
- [ ] 설치기·이미지·SBOM·오픈소스 고지·매뉴얼·체크섬을 릴리스 산출물로 묶는다.
- [ ] `B2BGearVia 1.0.0` 태그를 만들기 전에 인수 체크리스트를 두 번째 검토자가 확인한다.

**Final verification:**

```bash
./backend/mvnw -f backend/pom.xml test
npm --prefix frontend ci
npm --prefix frontend run build
docker compose -f infra/b2b/compose.yml config
bash installer/tests/preflight_test.sh
bash installer/tests/install_smoke_test.sh
bash installer/tests/backup_restore_test.sh
bash installer/tests/update_rollback_test.sh
```

모든 명령이 성공하고 Ubuntu 인수 체크리스트의 미완료 항목이 없어야 1.0.0 릴리스를 승인한다.

---

## 후속 버전 범위

다음 기능은 1.0.0 설치형 제품이 안정화된 뒤 별도 설계와 계획으로 진행한다.

- Microsoft Entra ID·Keycloak OIDC 로그인
- LDAP/Active Directory 또는 SAML 로그인
- 외부 MySQL 사용 모드
- 관리자 웹 화면에서 OpenAI 키 변경
- 회사별 오프라인 서명 라이선스
- 다중 서버·고가용성·로드밸런서
- S3 호환 오브젝트 스토리지
- 완전 폐쇄망용 이미지 포함 설치 패키지
- Windows Server 설치기

## 새 창에서의 첫 실행 순서

1. 이 문서의 `Global Constraints`와 `Task 1`을 읽는다.
2. 원본 GearVia 저장소가 아닌 B2BGearVia 디렉터리인지 확인한다.
3. 기존 GitHub Actions가 GearVia 배포를 가리키는지 확인하고 먼저 격리한다.
4. B2BGearVia 비공개 저장소를 초기화한다.
5. `Task 1`부터 한 작업씩 구현하고 각 Task의 검증을 통과한 후 커밋한다.
6. 결제 제거와 인증 변경은 동시에 진행하지 않고 독립적으로 검증한다.
7. 설치기 작업은 B2B 백엔드·프론트엔드가 독립 기동한 뒤 시작한다.
8. 완료를 주장하기 전에 이 문서의 `Final verification`을 새로 실행한다.
