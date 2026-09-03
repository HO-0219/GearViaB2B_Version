# 체크포인트 C 및 최종 로컬 검증 결과

검증 일시: 2026-09-02~03 KST

검증 대상: `1a21d62`(upstream `feat/gearvia-onprem-checkpoint-a`) 위에 다음을 쌓은 트리.

- Task 3 Docker 이미지 자동 준비, Task 4 공개 URL DB 영속 + `PublicUrlProvider`,
  Task 5 실행 중 URL 동적 반영(CORS·동일출처·WebSocket·메일 링크), Task 9 운영 문서·패키징
  검증, Task 10 통합 검증, 그리고 설치기 하드닝(비밀번호 16자 입력 검증, 서비스 시작 시간
  제한, 첫 설치 실패 시 정직한 복구).
- `V8__align_mcp_token_hash_type.sql` — 본 검증에서 발견한 선재 결함 수정.
- **관리자 도메인·SSL 관리(Task 6~8: 호스트 적용기, `/api/v1/admin/deployment-settings`
  API·화면)는 제거됨.** 통합 브라우저·비동기 경로가 검증되지 않았고 결함이 반복 발견되어
  이번 배포 범위에서 뺐다. Task 4 `deployment_settings`(V7) 테이블과 `PublicUrlProvider` 는
  남으며, 접속 주소 변경은 `runtime.env` 편집 + TLS 파일 교체 + 서비스 재시작으로 한다.

검증 환경: Windows 11 개발 호스트, Java 21, Spring Boot 3.3.5, Node/Vitest 4.1.11.
**Docker 사용 가능** — MySQL Testcontainers(mysql:8.4) 실제 실행.

## 검증 결과

| 검증 항목 | 결과 | 근거 |
|---|---|---|
| 백엔드 전체 테스트 (`./mvnw test`) | 통과 | 실패 0 / 오류 0 / 건너뜀 0. Testcontainers MySQL(`MySqlFlywayMigrationTest`, `MySqlOperationalIndexTest`) 포함 |
| 프런트엔드 전체 테스트 (`vitest run`) | 통과 | 테스트 파일 6개, 테스트 10개 |
| 프런트엔드 운영 빌드 (`npm run build`) | 경고 포함 통과 | `tsc -b` 통과, 500 kB 초과 청크 경고 1건 |
| `infra/ubuntu/test-lifecycle-scripts.sh` | 통과 | 미지원 OS/아키텍처 거부, 16자 미만 비밀번호 거부, 재실행 비밀값 보존(무효 비밀번호는 재사용 안 함), 데이터 보존 제거, 확인 문구 삭제 |
| `infra/ubuntu/test-tls-automation.sh` | 통과 | 사설 IPv4 선택, 공개 IP 거부, SAN(IP·호스트·localhost·127.0.0.1), 재실행 시 키 재사용, 파일 권한 |
| `infra/ubuntu/test-image-selection.sh` | 통과 | 번들 로드 → 로컬 재사용 → 소스 빌드/pull 순서, 이미지 ID 상태 기록, 이미지 부재 시 중단 |
| `infra/ubuntu/test-release-bundle.sh` | 통과 | 번들 필수 파일 존재, `bash -n`, `runtime.env.example` 키 = 설치기 생성 키 일치, 자리표시자 없음, `b2bgearvia.service` 유한 `TimeoutStartSec` |
| `infra/ubuntu/test-line-endings.sh` | 통과 | 설치기 쉘 스크립트 커밋 blob LF, `.gitattributes` `eol=lf` |
| `infra/b2b/test-virtualbox-config.sh` | 통과 | VirtualBox/B2B 병합 Compose 유효, `pull_policy: never` 2건 |
| `infra/b2b/test-mcp-proxy-config.sh` | 통과 | `/mcp`, 런타임 설정 및 신뢰 프록시 연결 확인 |
| `git diff --check` | 통과 | 공백 오류 없음 |

## 발견하고 고친 결함

1. **`V7` 스키마 버전 핀** — `V7__create_deployment_settings.sql`(Task 4)이 스키마를 7로
   올렸으나 `MySqlFlywayMigrationTest`가 현재 버전을 `"6"`으로 하드코딩. Docker가 있어 이
   검사가 실제로 실행되며 드러남. 테스트 버전 핀을 `"8"`로 갱신.
2. **`mcp_personal_tokens.token_hash` 타입 불일치 (선재 결함)** — `V5`(upstream `c53c9c8`)가
   `CHAR(64)`로 생성했으나 `McpPersonalToken` 엔티티는 JPA `String(length 64)` → Hibernate
   `validate`는 `VARCHAR(64)`를 기대 → `ddl-auto=validate` 운영 구성에서 컨텍스트 기동 실패.
   전방 마이그레이션 `V8__align_mcp_token_hash_type.sql`(`MODIFY token_hash VARCHAR(64) NOT NULL`).

`V8` 적용 후 `MySqlFlywayMigrationTest`가 실제 MySQL 8.4 스키마 위에서 전체 애플리케이션
컨텍스트를 `validate`로 기동하는 데 성공한다(Flyway V1~V8).

## 실 Ubuntu 24.04 VM 시험 — 수행함

VirtualBox `GearVia-rec`(클린 Ubuntu 24.04.4 LTS)에서 브랜치 번들을 클론해 실행.

- 무인자 설치(소스 이미지 빌드) → 4컨테이너 healthy(`init-data` exited 0) →
  `curl --cacert /etc/gearvia/tls/ca.crt https://127.0.0.1/api/v1/health/ready` → `{"status":"UP"}` 200
- `uninstall` → `/etc/gearvia` 삭제(활성 TLS 포함), 데이터 볼륨
  `b2bgearvia-mysql-data`·`b2bgearvia-uploads` 보존, gearvia systemd 유닛 전무
- 16자 미만 DB 비밀번호는 입력 단계에서 즉시 거부(백엔드 `B2bConfigurationValidator` 와 일치)

## 실행하지 않은 검증 (통과로 표시하지 않음)

- **MCP 프로토콜 실 e2e** — `MCP_ENABLED=false` 기본. 단위/API 테스트만 실행.
  `McpNetworkPolicy` 는 정적 `MCP_ALLOWED_ORIGINS` 를 읽으며 공개 URL 변경을 따라가지 않는다.
  설치기 재실행 시 `MCP_ENABLED` 이 `false` 로 되돌아간다(`--enable-mcp` 플래그 없음).
- **사내 LLM 실연동**, **측정된 용량(UNMEASURED)** — 이전 체크포인트와 동일하게 미수행.
- **ShellCheck** — Windows 호스트에 미설치. `bash -n`은 통과. 출시 CI에서 Ubuntu ShellCheck 필요.
- **멀티 NIC 주소 감지** — `gearvia_detect_primary_address` 는 기본 라우트 src IP 를 고르며
  `--address` 재정의가 없다. 클라이언트가 보조 NIC 로 접속하는 서버에서는 인증서 SAN·
  `FRONTEND_URL` 이 틀린다. `ADMIN_ALLOWED_IPS` 도 `192.168.0.0/16` 고정.
- **프런트엔드 500 kB 초과 청크** — 빌드 실패는 아니나 WAN 배포 전 코드 분할 효과 측정 필요.

## 검증 명령어

```bash
cd backend && ./mvnw test
cd frontend && npx vitest run && npm run build
bash infra/ubuntu/test-lifecycle-scripts.sh
bash infra/ubuntu/test-tls-automation.sh
bash infra/ubuntu/test-image-selection.sh
bash infra/ubuntu/test-release-bundle.sh
bash infra/ubuntu/test-line-endings.sh
bash infra/b2b/test-virtualbox-config.sh
bash infra/b2b/test-mcp-proxy-config.sh
git diff --check
```
