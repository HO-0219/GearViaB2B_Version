# GearVia Ubuntu 자동 설치 및 도메인·SSL 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** MySQL 애플리케이션 비밀번호만 입력하면 단일 명령으로 설치되고, 도메인과 SSL을 관리자 페이지에서 안전하게 시험·적용·복구할 수 있는 Ubuntu 온프레미스 배포를 만든다.

**Architecture:** 설치기는 root 전용 런타임 설정과 로컬 CA/서버 인증서를 생성하고 이미지 번들 로드·로컬 재사용·소스 빌드를 순서대로 선택한다. 애플리케이션은 공개 URL을 DB 단일 설정에서 동적으로 읽고, 권한이 제한된 파일 요청과 systemd 호스트 적용 도우미를 통해서만 인증서와 프록시를 변경한다.

**Tech Stack:** Bash, OpenSSL, Docker Compose v2, systemd, Java 21/Spring Boot 3.3.5, MySQL 8.x/Flyway, React/TypeScript/Vitest

**Spec:** `docs/superpowers/specs/2026-09-02-automated-ubuntu-install-domain-tls-design.md`

## 전역 제약

- 지원 호스트는 x86_64 Ubuntu Server 24.04 LTS다.
- 사용자가 직접 `runtime.env`, JWT 비밀값, MFA 암호화 키, TLS 파일 또는 이미지 식별자를 준비하지 않는다.
- 대화형 설치에서 사용자가 입력하는 필수값은 번들 MySQL 애플리케이션 비밀번호 하나다.
- 비밀번호 자동화 입력은 root가 읽을 수 있는 절대 경로의 `--db-password-file`만 허용한다.
- MySQL은 8.x 호환 서버만 지원한다.
- 인터넷 아웃바운드는 기본 차단하며 폐쇄망은 `infra/images/*.tar` 이미지 번들을 사용한다.
- 백엔드 컨테이너에 Docker 소켓과 root 권한을 제공하지 않는다.
- 외부 NAS 파일은 설치 및 삭제 스크립트가 삭제하지 않는다.
- 각 작업은 실패 테스트 확인, 최소 구현, 통과 확인, 독립 커밋 순서를 지킨다.

---

### Task 1: 무인자 설치 입력과 런타임 설정 생성

**Files:**
- Modify: `install_gearvia_ai_agent_ubuntu.sh`
- Modify: `infra/ubuntu/lib/gearvia-common.sh`
- Modify: `infra/ubuntu/test-lifecycle-scripts.sh`

**Interfaces:**
- Produces: `gearvia_generate_secret <bytes>`, `gearvia_read_db_password <optional-file>`, `gearvia_write_runtime_env <target> <public-url> <db-password>`
- Produces: CLI `install_gearvia_ai_agent_ubuntu.sh [--dry-run] [--db-password-file /absolute/file]`

- [ ] **Step 1: 무인자 설치 실패 테스트 작성**

```bash
password_file="$tmp_root/db-password"
printf '%s' 'LocalDbPassword-2026!' > "$password_file"
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 \
  "$installer" --db-password-file "$password_file"
grep -Eq '^JWT_SECRET=.{43,}$' "$tmp_root/etc/gearvia/runtime.env"
grep -Eq '^ADMIN_MFA_ENCRYPTION_KEY_BASE64=.{43,}$' "$tmp_root/etc/gearvia/runtime.env"
grep -Fq 'MYSQL_APP_PASSWORD=LocalDbPassword-2026!' "$tmp_root/etc/gearvia/runtime.env"
```

- [ ] **Step 2: 테스트가 기존 `--config` 필수 오류로 실패하는지 확인**

Run: `bash infra/ubuntu/test-lifecycle-scripts.sh`

Expected: FAIL with `--config must name a readable absolute file`.

- [ ] **Step 3: root 전용 설정 생성 구현**

```bash
gearvia_generate_secret() { openssl rand -base64 "$1" | tr -d '\n'; }
gearvia_write_kv() { printf '%s=%s\n' "$1" "$2" >> "$3"; }
```

설정 값은 배열로 조립해 후보 파일에 `0600`으로 기록하고, 기존 `/etc/gearvia/runtime.env`가
있으면 DB/JWT/MFA 비밀값을 재사용한다. 설정 파일을 `source`하거나 `eval`하지 않는다.

- [ ] **Step 4: 수명 주기 테스트 통과 확인**

Run: `bash infra/ubuntu/test-lifecycle-scripts.sh`

Expected: `Ubuntu lifecycle script tests passed`.

- [ ] **Step 5: 커밋**

```bash
git add install_gearvia_ai_agent_ubuntu.sh infra/ubuntu/lib/gearvia-common.sh infra/ubuntu/test-lifecycle-scripts.sh
git commit -m "feat: generate Ubuntu runtime configuration"
```

### Task 2: 초기 주소·로컬 CA·서버 인증서 자동 생성

**Files:**
- Create: `infra/ubuntu/lib/gearvia-tls.sh`
- Create: `infra/ubuntu/test-tls-automation.sh`
- Modify: `install_gearvia_ai_agent_ubuntu.sh`
- Modify: `infra/b2b/compose.yml`

**Interfaces:**
- Produces: `gearvia_detect_primary_address`, `gearvia_generate_local_ca <dir>`, `gearvia_issue_server_cert <dir> <address> <hostname>`
- Produces: `/etc/gearvia/tls/ca.crt`, `fullchain.pem`, `privkey.pem`

- [ ] **Step 1: 인증서 SAN과 재실행 보존 실패 테스트 작성**

```bash
gearvia_issue_server_cert "$tmp_root/tls" "10.20.30.40" "gearvia-node"
openssl x509 -in "$tmp_root/tls/fullchain.pem" -noout -ext subjectAltName | grep -F 'IP Address:10.20.30.40'
openssl x509 -in "$tmp_root/tls/fullchain.pem" -noout -ext subjectAltName | grep -F 'DNS:gearvia-node'
openssl pkey -in "$tmp_root/tls/privkey.pem" -pubout -outform pem | sha256sum > "$tmp_root/key.before"
gearvia_issue_server_cert "$tmp_root/tls" "10.20.30.40" "gearvia-node"
openssl pkey -in "$tmp_root/tls/privkey.pem" -pubout -outform pem | sha256sum | diff - "$tmp_root/key.before"
GEARVIA_TEST_IP_CANDIDATES='' gearvia_detect_primary_address 2>"$tmp_root/address.error" && exit 1
grep -Fq '사설 IPv4 주소를 감지하지 못했습니다' "$tmp_root/address.error"
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `bash infra/ubuntu/test-tls-automation.sh`

Expected: FAIL because `infra/ubuntu/lib/gearvia-tls.sh` does not exist.

- [ ] **Step 3: OpenSSL 기반 로컬 CA와 SAN 인증서 구현**

```bash
openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 3650 \
  -subj '/CN=GearVia Local CA' -keyout "$tls_dir/ca.key" -out "$tls_dir/ca.crt"
openssl x509 -req -sha256 -days 825 -in "$tls_dir/server.csr" \
  -CA "$tls_dir/ca.crt" -CAkey "$tls_dir/ca.key" -CAcreateserial \
  -extfile "$tls_dir/server.ext" -out "$tls_dir/fullchain.pem"
```

키는 `0600`, 인증서는 `0644`로 설정하고 Compose TLS 마운트는 `/etc/gearvia/tls` 고정 경로를
읽도록 변경한다.

- [ ] **Step 4: 인증서와 Compose 검증 통과 확인**

Run: `bash infra/ubuntu/test-tls-automation.sh && bash infra/b2b/test-virtualbox-config.sh`

Expected: both commands exit 0.

- [ ] **Step 5: 커밋**

```bash
git add install_gearvia_ai_agent_ubuntu.sh infra/ubuntu/lib/gearvia-tls.sh infra/ubuntu/test-tls-automation.sh infra/b2b/compose.yml
git commit -m "feat: generate initial on-prem TLS"
```

### Task 3: Docker 이미지 자동 준비와 제거·재설치

**Files:**
- Create: `infra/ubuntu/lib/gearvia-images.sh`
- Create: `infra/ubuntu/test-image-selection.sh`
- Modify: `install_gearvia_ai_agent_ubuntu.sh`
- Modify: `uninstall_gearvia_ai_agent_ubuntu.sh`
- Modify: `infra/ubuntu/test-lifecycle-scripts.sh`
- Modify: `infra/b2b/compose.yml`
- Modify: `docs/operations/ubuntu-installation.md`

**Interfaces:**
- Produces: `gearvia_prepare_image <logical-name> <tag> <dockerfile>`, `gearvia_record_image_state <state-file>`
- Consumes: Task 1 runtime writer and Task 2 TLS paths

- [ ] **Step 1: 이미지 선택 우선순위와 삭제 실패 테스트 작성**

```bash
GEARVIA_DOCKER_BIN="$fake_docker" gearvia_prepare_image backend b2bgearvia-backend:test backend/Dockerfile
assert_log_order 'load infra/images/backend.tar' 'image inspect b2bgearvia-backend:test' 'build -f backend/Dockerfile'
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$uninstaller"
assert_absent "$tmp_root/etc/gearvia/tls"
assert_file "$tmp_root/var/lib/gearvia/recovery/database.env"
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `bash infra/ubuntu/test-image-selection.sh && bash infra/ubuntu/test-lifecycle-scripts.sh`

Expected: FAIL because image selection and TLS cleanup are not implemented.

- [ ] **Step 3: 번들 로드·재사용·빌드와 안전 제거 구현**

```bash
if [[ -f "$bundle" ]]; then docker load --input "$bundle"; fi
if ! docker image inspect "$tag" >/dev/null 2>&1; then
  docker build --tag "$tag" --file "$dockerfile" "$repo_root"
fi
```

MySQL 기본 이미지는 `mysql:8.4`, 초기화 이미지는 `busybox:1.37`로 설정하고 실제 이미지 ID를
`/var/lib/gearvia/install-state.env`에 기록한다. 기본 제거는 활성 TLS와 설정을 삭제하되 DB 접속
복구 파일만 `0600`으로 보존하며, 완전 삭제는 해당 복구 파일까지 제거한다.

- [ ] **Step 4: 체크포인트 A 통합 검증**

Run: `bash infra/ubuntu/test-image-selection.sh && bash infra/ubuntu/test-tls-automation.sh && bash infra/ubuntu/test-lifecycle-scripts.sh && bash infra/b2b/test-virtualbox-config.sh`

Expected: all commands exit 0 and no test prints `FAIL`.

- [ ] **Step 5: 커밋**

```bash
git add install_gearvia_ai_agent_ubuntu.sh uninstall_gearvia_ai_agent_ubuntu.sh infra/ubuntu infra/b2b/compose.yml docs/operations/ubuntu-installation.md
git commit -m "feat: automate Ubuntu image lifecycle"
```

### Task 4: 공개 URL 영속 설정과 동적 조회

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__create_deployment_settings.sql`
- Create: `backend/src/main/java/com/teamproject/deployment/domain/DeploymentSettings.java`
- Create: `backend/src/main/java/com/teamproject/deployment/domain/DeploymentSettingsRepository.java`
- Create: `backend/src/main/java/com/teamproject/deployment/application/PublicUrlProvider.java`
- Create: `backend/src/test/java/com/teamproject/deployment/PublicUrlProviderTest.java`

**Interfaces:**
- Produces: `URI PublicUrlProvider.current()`, `boolean PublicUrlProvider.isAllowedOrigin(String origin)`
- Table singleton: `deployment_settings(id=1, public_url, certificate_issuer, certificate_not_after, certificate_sans, apply_version, status, updated_at)`

- [ ] **Step 1: 환경값 폴백과 DB 우선순위 실패 테스트 작성**

```java
@Test void databasePublicUrlOverridesBootstrapUrl() {
    repository.save(new DeploymentSettings("https://gearvia.corp"));
    assertThat(provider.current()).isEqualTo(URI.create("https://gearvia.corp"));
    assertThat(provider.isAllowedOrigin("https://gearvia.corp")).isTrue();
    assertThat(provider.isAllowedOrigin("https://evil.example")).isFalse();
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./mvnw -Dtest=PublicUrlProviderTest test`

Expected: FAIL because the deployment package does not exist.

- [ ] **Step 3: V7과 캐시 없는 DB 우선 공급자 구현**

```java
public URI current() {
    return settings.findById(DeploymentSettings.SINGLETON_ID)
            .map(value -> URI.create(value.getPublicUrl()))
            .orElse(bootstrapUrl);
}
```

URL은 HTTPS, 사용자 정보 없음, 쿼리/프래그먼트 없음, 기본 포트 규칙을 검증한다.

- [ ] **Step 4: 단위 시험 통과 확인**

Run: `cd backend && ./mvnw -Dtest=PublicUrlProviderTest test`

Expected: 0 failures and 0 errors.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/resources/db/migration/V7__create_deployment_settings.sql backend/src/main/java/com/teamproject/deployment backend/src/test/java/com/teamproject/deployment
git commit -m "feat: persist deployment public URL"
```

### Task 5: 보안·WebSocket·메일 링크의 동적 URL 전환

**Files:**
- Modify: `backend/src/main/java/com/teamproject/authorization/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/teamproject/authorization/config/SameOriginMutationFilter.java`
- Modify: `backend/src/main/java/com/teamproject/chat/websocket/ChatWebSocketConfiguration.java`
- Modify: `backend/src/main/java/com/teamproject/authentication/application/RecoveryService.java`
- Modify: `backend/src/main/java/com/teamproject/group/application/GroupInvitationService.java`
- Create: `backend/src/test/java/com/teamproject/deployment/DynamicPublicUrlIntegrationTest.java`

**Interfaces:**
- Consumes: `PublicUrlProvider.current()` and `PublicUrlProvider.isAllowedOrigin(String)` from Task 4
- Produces: every URL consumer observes DB changes without application restart

- [ ] **Step 1: 동적 출처와 링크 실패 시험 작성**

```java
settings.save(new DeploymentSettings("https://new.gearvia.corp"));
mockMvc.perform(post("/api/v1/admin/branding").header("Origin", "https://old.gearvia.corp"))
        .andExpect(status().isForbidden());
assertThat(invitationService.createLink(fixtureGroupId())).startsWith("https://new.gearvia.corp/");
```

- [ ] **Step 2: 기존 정적 주입 때문에 실패하는지 확인**

Run: `cd backend && ./mvnw -Dtest=DynamicPublicUrlIntegrationTest test`

Expected: FAIL because services still hold `@Value("${app.frontend-url}")` strings.

- [ ] **Step 3: 모든 소비자를 `PublicUrlProvider`로 교체**

```java
if (origin != null && !publicUrls.isAllowedOrigin(origin)) {
    reject(response, "CROSS_ORIGIN_REQUEST_BLOCKED");
    return;
}
```

CORS 구성은 요청마다 공급자의 현재 URL을 읽는 `CorsConfigurationSource`를 사용하고 WebSocket
핸드셰이크 인터셉터에서 같은 공급자로 Origin을 검증한다.

- [ ] **Step 4: 보안 회귀 시험 통과 확인**

Run: `cd backend && ./mvnw -Dtest=DynamicPublicUrlIntegrationTest,AuthSecurityApiTest,ProductionConfigurationValidatorTest test`

Expected: 0 failures and 0 errors.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/teamproject/authorization backend/src/main/java/com/teamproject/chat backend/src/main/java/com/teamproject/authentication/application/RecoveryService.java backend/src/main/java/com/teamproject/group/application/GroupInvitationService.java backend/src/test/java/com/teamproject/deployment
git commit -m "feat: apply deployment URL dynamically"
```

### Task 6: 권한 제한 호스트 적용 요청과 롤백 도우미

**Files:**
- Create: `infra/ubuntu/gearvia-host-apply.sh`
- Create: `infra/ubuntu/systemd/gearvia-host-apply.service`
- Create: `infra/ubuntu/systemd/gearvia-host-apply.path`
- Create: `infra/ubuntu/test-host-apply.sh`
- Modify: `install_gearvia_ai_agent_ubuntu.sh`
- Modify: `uninstall_gearvia_ai_agent_ubuntu.sh`
- Modify: `infra/b2b/compose.yml`

**Interfaces:**
- Consumes request fields: `requestId`, `publicUrl`, `certificateMode`, fixed candidate filenames, `signature`
- Produces result fields: `requestId`, `status`, `code`, `certificateIssuer`, `certificateNotAfter`, `certificateSans`
- Fixed paths: `/var/lib/gearvia/control/requests`, `/results`, `/candidates`
- Authentication: 설치 시 생성한 `HOST_APPLY_REQUEST_HMAC_KEY`로 정규화된 요청 본문을 HMAC-SHA256 서명

- [ ] **Step 1: 경로 탈출·키 불일치·롤백 실패 시험 작성**

```bash
submit_request '../escape' 'https://gearvia.corp' uploaded
assert_result_code REQUEST_ID_INVALID
submit_request_with_invalid_signature valid-request 'https://gearvia.corp'
assert_result_code REQUEST_SIGNATURE_INVALID
submit_mismatched_certificate valid-request
assert_result_code CERTIFICATE_KEY_MISMATCH
force_health_failure valid-request
assert_same_checksum "$active_cert_before" "$active_cert_after"
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `bash infra/ubuntu/test-host-apply.sh`

Expected: FAIL because the host apply script does not exist.

- [ ] **Step 3: 고정 명령 allowlist와 원자 교체 구현**

```bash
openssl x509 -in "$candidate_cert" -pubkey -noout | openssl pkey -pubin -outform der | sha256sum
openssl pkey -in "$candidate_key" -pubout -outform der | sha256sum
docker compose --env-file "$runtime" -f "$compose" config --quiet
```

요청의 HMAC을 상수 시간 비교로 확인하고 인증서 공개키 해시가 일치한 경우에만 후보를 설치한다.
`docker compose up -d --no-deps --force-recreate web` 후
HTTPS 상태를 확인한다. 실패하면 백업 파일을 복원해 동일 명령으로 웹을 재생성한다.

- [ ] **Step 4: 체크포인트 B 통합 검증**

Run: `bash infra/ubuntu/test-host-apply.sh && bash infra/ubuntu/test-lifecycle-scripts.sh && cd backend && ./mvnw -Dtest=PublicUrlProviderTest,DynamicPublicUrlIntegrationTest test`

Expected: all commands exit 0.

- [ ] **Step 5: 커밋**

```bash
git add infra/ubuntu install_gearvia_ai_agent_ubuntu.sh uninstall_gearvia_ai_agent_ubuntu.sh infra/b2b/compose.yml
git commit -m "feat: add restricted host configuration applier"
```

### Task 7: 도메인·SSL 관리자 API와 변경 작업 상태

**Files:**
- Create: `backend/src/main/java/com/teamproject/deployment/application/DeploymentSettingsService.java`
- Create: `backend/src/main/java/com/teamproject/deployment/presentation/AdminDeploymentSettingsController.java`
- Create: `backend/src/main/java/com/teamproject/deployment/application/dto/DeploymentSettingsDtos.java`
- Create: `backend/src/main/java/com/teamproject/deployment/infrastructure/HostApplyGateway.java`
- Create: `backend/src/test/java/com/teamproject/deployment/AdminDeploymentSettingsApiTest.java`
- Modify: `backend/src/main/java/com/teamproject/operations/domain/InfrastructureChangeJob.java`

**Interfaces:**
- Produces: `GET /api/v1/admin/deployment-settings`
- Produces: `POST /api/v1/admin/deployment-settings/test`
- Produces: `POST /api/v1/admin/deployment-settings/drafts` multipart
- Produces: `POST /api/v1/admin/deployment-settings/{jobId}/apply`
- Produces: `GET /api/v1/admin/deployment-settings/jobs/{jobId}`
- Consumes: root 전용 런타임 파일에서 컨테이너에 읽기 전용 주입된 `HOST_APPLY_REQUEST_HMAC_KEY`

- [ ] **Step 1: 권한·테스트 선행·비밀 비반환 실패 시험 작성**

```java
mockMvc.perform(post("/api/v1/admin/deployment-settings/drafts")
        .file(certFile).file(keyFile).param("publicUrl", "https://gearvia.corp"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.privateKey").doesNotExist());
mockMvc.perform(post("/api/v1/admin/deployment-settings/{id}/apply", untestedJobId))
        .andExpect(status().isConflict());
```

- [ ] **Step 2: API 시험 실패 확인**

Run: `cd backend && ./mvnw -Dtest=AdminDeploymentSettingsApiTest test`

Expected: FAIL with 404 for the new endpoints.

- [ ] **Step 3: 검증·작업·결과 반영 구현**

```java
public enum Type { MYSQL, STORAGE, DOMAIN_TLS }
```

업로드는 크기 제한, PEM 형식, SAN, 만료일, 키 일치를 검사하고 고정 후보 파일명으로 기록한다.
`TEST_SUCCEEDED` 작업만 공지 후 적용하며 결과 파일을 읽어 `COMPLETED` 또는 `ROLLED_BACK`으로
전이한다. `HostApplyGateway`는 정규화된 요청 JSON을 HMAC-SHA256으로 서명하고 호스트 결과가
성공한 뒤에만 `DeploymentSettings`의 공개 URL을 갱신한다. 인증서와 개인 키 본문은 DTO와 감사
로그에서 제외한다.

- [ ] **Step 4: API 및 상태 전이 시험 통과 확인**

Run: `cd backend && ./mvnw -Dtest=AdminDeploymentSettingsApiTest,InfrastructureChangeJobServiceTest test`

Expected: 0 failures and 0 errors.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/teamproject/deployment backend/src/test/java/com/teamproject/deployment backend/src/main/java/com/teamproject/operations/domain/InfrastructureChangeJob.java
git commit -m "feat: add domain and TLS administration API"
```

### Task 8: 전체 공지와 관리자 도메인·SSL 화면

**Files:**
- Create: `frontend/src/features/admin/pages/AdminDeploymentSettingsPage.tsx`
- Create: `frontend/src/features/admin/pages/AdminDeploymentSettingsPage.test.tsx`
- Modify: `frontend/src/api/adminApi.ts`
- Modify: `frontend/src/features/admin/AdminShell.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `backend/src/main/java/com/teamproject/deployment/application/DeploymentSettingsService.java`
- Modify: `backend/src/test/java/com/teamproject/deployment/AdminDeploymentSettingsApiTest.java`

**Interfaces:**
- Consumes: Task 7 administrator endpoints
- Produces: route `/admin/deployment-settings` and unified-section entry

- [ ] **Step 1: 테스트 성공 전 적용 금지와 상태 표시 실패 시험 작성**

```tsx
render(<LanguageProvider><AdminDeploymentSettingsPage /></LanguageProvider>);
expect(await screen.findByText('도메인·SSL 설정')).toBeInTheDocument();
expect(screen.getByRole('button', { name: '적용' })).toBeDisabled();
await userEvent.click(screen.getByRole('button', { name: '연결 테스트' }));
expect(await screen.findByText('예상 중단 시간')).toBeInTheDocument();
```

- [ ] **Step 2: 프런트엔드 시험 실패 확인**

Run: `cd frontend && npm test -- --run AdminDeploymentSettingsPage.test.tsx`

Expected: FAIL because the page does not exist.

- [ ] **Step 3: 관리자 화면과 공지 호출 구현**

```ts
export type DeploymentSettingsStatus = {
  publicUrl: string; certificateIssuer: string; certificateNotAfter: string;
  certificateSans: string[]; status: string; applyVersion: number;
};
```

페이지는 URL, 자체 서명/업로드 모드, 인증서·키 파일, 연결 테스트, 예상 중단 시간 확인,
적용 및 진행 상태를 제공한다. 적용 직전에 기존 공지 서비스를 통해 전체 사용자에게 시스템
중단 알림을 생성하고 SMTP가 활성화된 경우 전체 공지 메일도 발송한다.

- [ ] **Step 4: 프런트엔드와 공지 회귀 시험 통과 확인**

Run: `cd frontend && npm test -- --run AdminDeploymentSettingsPage.test.tsx AdminShell.test.tsx && cd ../backend && ./mvnw -Dtest=AdminDeploymentSettingsApiTest,AdminNoticeServiceTest test`

Expected: all tests pass.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src backend/src/main/java/com/teamproject/deployment backend/src/test/java/com/teamproject/deployment
git commit -m "feat: add deployment settings administrator workflow"
```

### Task 9: 운영 문서와 패키징 검증

**Files:**
- Modify: `docs/operations/ubuntu-installation.md`
- Create: `docs/operations/domain-tls-administration.md`
- Modify: `infra/b2b/runtime.env.example`
- Modify: `infra/b2b/test-virtualbox-config.sh`
- Create: `infra/ubuntu/test-release-bundle.sh`

**Interfaces:**
- Documents: zero-argument install, password-file automation, CA distribution, administrator change and uninstall recovery

- [ ] **Step 1: 릴리스 번들 필수 파일 실패 검사 작성**

```bash
required=(install_gearvia_ai_agent_ubuntu.sh uninstall_gearvia_ai_agent_ubuntu.sh infra/b2b/compose.yml infra/ubuntu/gearvia-host-apply.sh)
for path in "${required[@]}"; do [[ -r "$repo_root/$path" ]] || exit 1; done
scripts=(install_gearvia_ai_agent_ubuntu.sh uninstall_gearvia_ai_agent_ubuntu.sh infra/ubuntu/gearvia-host-apply.sh)
bash -n "${scripts[@]/#/$repo_root/}"
```

- [ ] **Step 2: 검사 실패 확인**

Run: `bash infra/ubuntu/test-release-bundle.sh`

Expected: FAIL until all generated service/helper files are included.

- [ ] **Step 3: 한국어 운영 절차와 생성 설정 예시 정리**

```bash
sudo ./install_gearvia_ai_agent_ubuntu.sh
sudo ./install_gearvia_ai_agent_ubuntu.sh --db-password-file /secure/mysql-app-password
sudo ./uninstall_gearvia_ai_agent_ubuntu.sh
```

`runtime.env.example`은 설치기가 생성하는 키 목록 검증용으로만 유지하고 사용자 복사 절차와
자리표시자 이미지 다이제스트를 제거한다.

- [ ] **Step 4: 패키징 검증 통과 확인**

Run: `bash infra/ubuntu/test-release-bundle.sh && bash infra/b2b/test-virtualbox-config.sh && git diff --check`

Expected: all commands exit 0.

- [ ] **Step 5: 커밋**

```bash
git add docs/operations infra/b2b/runtime.env.example infra/b2b/test-virtualbox-config.sh infra/ubuntu/test-release-bundle.sh
git commit -m "docs: document automated on-prem deployment"
```

### Task 10: 최종 통합 검증과 결과 기록

**Files:**
- Modify: `docs/operations/checkpoint-c-final-verification.md`

**Interfaces:**
- Consumes: Tasks 1~9 complete tree
- Produces: exact commands, counts, skipped external gates and Ubuntu VM acceptance result

- [ ] **Step 1: Bash 및 배포 계약 전체 실행**

Run: `bash infra/ubuntu/test-lifecycle-scripts.sh && bash infra/ubuntu/test-tls-automation.sh && bash infra/ubuntu/test-image-selection.sh && bash infra/ubuntu/test-host-apply.sh && bash infra/ubuntu/test-release-bundle.sh && bash infra/b2b/test-virtualbox-config.sh`

Expected: every command exits 0.

- [ ] **Step 2: 백엔드 전체 시험 실행**

Run: `cd backend && ./mvnw test`

Expected: `BUILD SUCCESS`, 0 failures, 0 errors. Docker가 없으면 MySQL Testcontainers 건너뜀을
최종 문서에 그대로 기록한다.

- [ ] **Step 3: 프런트엔드 전체 시험과 운영 빌드 실행**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: all Vitest tests pass and Vite build exits 0. 청크 경고가 남으면 수치와 함께 기록한다.

- [ ] **Step 4: Ubuntu 24.04 VM 인수 시험 실행**

```bash
sudo ./install_gearvia_ai_agent_ubuntu.sh --db-password-file /secure/mysql-app-password
curl --cacert /etc/gearvia/tls/ca.crt https://127.0.0.1/api/v1/health/ready
sudo ./uninstall_gearvia_ai_agent_ubuntu.sh
test ! -e /etc/gearvia/tls/fullchain.pem
```

Expected: readiness returns success, uninstall removes active TLS, and MySQL/upload volumes remain.

- [ ] **Step 5: 검증 결과 기록 및 최종 커밋**

```bash
git add docs/operations/checkpoint-c-final-verification.md
git commit -m "docs: record automated deployment verification"
```

문서에는 실행 일시, Git 커밋, 시험 수, 실패/건너뜀 수, 이미지 경로, 실제 VM 결과와 수행하지
못한 외부 검증을 분리해 기록한다. 실행하지 않은 시험을 통과로 표시하지 않는다.
