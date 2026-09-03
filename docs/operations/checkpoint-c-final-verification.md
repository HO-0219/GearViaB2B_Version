# 체크포인트 C 및 최종 로컬 검증 결과

검증 일시: 2026-09-02 21:30 KST (UTC 2026-09-02T12:12:07Z 시작)

검증 대상: `1a21d62`(upstream `feat/gearvia-onprem-checkpoint-a`) 위에 Task 3~10 구현 커밋과
본 검증에서 만든 `V8` 마이그레이션, 그리고 리뷰·실 HW 시험에서 발견해 고친 세 결함
(백엔드 컨테이너 HMAC 키 주입 누락 / 적용기 health probe 레이스 / 인증서 메타데이터 미영속 +
SWITCHED 잡 재조정)을 쌓은 트리.

검증 환경: Windows 11 개발 호스트, Java 21.0.10, Spring Boot 3.3.5, Node/Vitest 4.1.11,
Vite 8.1.5. **Docker 사용 가능** — MySQL Testcontainers(mysql:8.4)가 실제로 실행됨.

## 검증 결과

| 검증 항목 | 결과 | 근거 |
|---|---|---|
| 백엔드 전체 테스트 (`./mvnw test`) | 통과 | 총 503개, 실패 0 / 오류 0 / 건너뜀 0. Testcontainers MySQL(`MySqlFlywayMigrationTest`, `MySqlOperationalIndexTest`) 포함 |
| 프런트엔드 전체 테스트 (`vitest run`) | 통과 | 테스트 파일 7개, 테스트 14개 |
| 프런트엔드 운영 빌드 (`npm run build`) | 경고 포함 통과 | `tsc -b` 통과, 모듈 98개, `dist/assets/index-*.js` 555.20 kB(gzip 159.53 kB), 500 kB 초과 청크 경고 1건 |
| `infra/ubuntu/test-lifecycle-scripts.sh` | 통과 | 미지원 OS/아키텍처 거부, 사전 점검 불변, 재실행 비밀값 보존, 데이터 보존 제거, 확인 문구 완전 삭제 |
| `infra/ubuntu/test-tls-automation.sh` | 통과 | 사설 IPv4 선택, 공개 IP 거부, SAN(IP·호스트·localhost·127.0.0.1), 재실행 시 키 재사용, 파일 권한 |
| `infra/ubuntu/test-image-selection.sh` | 통과 | 번들 로드 → 로컬 재사용 → 소스 빌드/pull 순서, 이미지 ID 상태 기록, 이미지 부재 시 중단 |
| `infra/ubuntu/test-host-apply.sh` | 통과 | 경로 탈출 requestId 거부, HMAC 불일치 거부, 인증서/키 불일치 거부, 헬스 실패 시 활성 인증서 롤백, 성공 시 후보 설치 + ISO-8601 만료일 기록, 재기동 직후 미준비 web 은 롤백 대신 재시도 |
| `infra/ubuntu/test-release-bundle.sh` | 통과 | 번들 필수 파일 존재, `bash -n`, `runtime.env.example` 키 = 설치기 생성 키 정확히 일치, 자리표시자 다이제스트 없음 |
| `infra/ubuntu/test-line-endings.sh` | 통과 | 설치기 관련 쉘 스크립트 커밋 blob LF, `.gitattributes` `eol=lf` 고정 |
| `infra/b2b/test-virtualbox-config.sh` | 통과 | VirtualBox/B2B 병합 Compose 유효, `pull_policy: never` 2건, `/var/lib/gearvia/control` 마운트 |
| `infra/b2b/test-mcp-proxy-config.sh` | 통과 | `/mcp`, 런타임 설정 및 신뢰 프록시 연결 확인 |
| `git diff --check` | 통과 | 공백 오류 없음 |

## 최종 검증에서 발견하고 고친 결함

1. **`V7` 스키마 버전 핀** — `V7__create_deployment_settings.sql`(Task 4)이 스키마를 7로
   올렸으나 `MySqlFlywayMigrationTest`가 현재 버전을 `"6"`으로 하드코딩하고 있었다. Docker가
   있어 이 검사가 실제로 실행되며 드러남. 테스트를 `"8"`로 갱신하고 `deployment_settings`
   테이블·컬럼 검증을 추가.
2. **`mcp_personal_tokens.token_hash` 타입 불일치 (선재 결함)** — `V5`(upstream `c53c9c8`)가
   `CHAR(64)`로 생성했으나 `McpPersonalToken` 엔티티는 JPA `String(length 64)` → Hibernate
   `validate`는 `VARCHAR(64)`를 기대한다. `spring.jpa.hibernate.ddl-auto=validate`인 운영
   구성에서 애플리케이션 컨텍스트 기동이 실패한다. 이전에는 Docker 부재로 해당 검사가
   건너뛰어져 잡히지 않았다. 전방 마이그레이션 `V8__align_mcp_token_hash_type.sql`으로
   `MODIFY token_hash VARCHAR(64) NOT NULL` 적용.

`V8` 적용 후 `MySqlFlywayMigrationTest`가 실제 MySQL 8.4 스키마 위에서 전체 애플리케이션
컨텍스트를 `validate`로 기동하는 데 성공한다(Flyway V1~V8, `deployment_settings` 단일 행 CHECK
제약 포함).

## 실 Ubuntu 24.04 VM 시험 — 수행함

VirtualBox `GearVia-rec`(클린 Ubuntu 24.04.4 LTS)에서 브랜치 번들을 클론해 실행.

- 무인자 설치(소스 이미지 빌드) → 4컨테이너 healthy(`init-data` exited 0) →
  `curl --cacert /etc/gearvia/tls/ca.crt https://127.0.0.1/api/v1/health/ready` → `{"status":"UP"}` 200
- `uninstall` → `/etc/gearvia` 전체(활성 TLS·host-apply.key) 삭제, 데이터 볼륨
  `b2bgearvia-mysql-data`·`b2bgearvia-uploads` 보존, gearvia systemd 유닛 전무
- **호스트 적용기 롤백** — 다른 CA 로 서명된 인증서를 적용 → `HEALTH_CHECK_FAILED` →
  활성 인증서 sha256 불변으로 복원 → 사이트 200. (가짜 docker 아닌 실 `docker compose` 재생성)
- **적용기 재시도** — `--force-recreate web` 직후 첫 probe 실패 → 재시도 후 성공 →
  `code=OK`, `certificateNotAfter` = ISO-8601 (`date -u -d` 정규화)

## 실행하지 않은 검증 (통과로 표시하지 않음)

- **관리자 도메인·SSL 교체 흐름 브라우저 e2e** — 온보딩 → 인증서 업로드 → 연결 테스트 →
  적용 → 진행 폴링 → `COMPLETED`/`ROLLED_BACK` 은 브라우저로 미실행. 적용 경로는 호스트
  적용기 레이어 + 단위/API 테스트로만 검증.
- **MCP 프로토콜 실 e2e** — `MCP_ENABLED=false` 기본. 단위/API 테스트만 실행.
  `McpNetworkPolicy` 는 정적 `MCP_ALLOWED_ORIGINS` 를 읽으며 공개 URL 변경을 따라가지 않는다.
- **사내 LLM 실연동**, **측정된 용량(UNMEASURED)** — 이전 체크포인트와 동일하게 미수행.
- **ShellCheck** — Windows 호스트에 미설치. `bash -n`은 통과. 출시 CI에서 Ubuntu ShellCheck 필요.
- **프런트엔드 500 kB 초과 청크** — 빌드 실패는 아니나, 지연 시간이 큰 WAN 배포 전 경로 단위
  코드 분할 효과를 측정해야 한다.

## 검증 명령어

```bash
cd backend && ./mvnw test
cd frontend && npx vitest run && npm run build
bash infra/ubuntu/test-lifecycle-scripts.sh
bash infra/ubuntu/test-tls-automation.sh
bash infra/ubuntu/test-image-selection.sh
bash infra/ubuntu/test-host-apply.sh
bash infra/ubuntu/test-release-bundle.sh
bash infra/ubuntu/test-line-endings.sh
bash infra/b2b/test-virtualbox-config.sh
bash infra/b2b/test-mcp-proxy-config.sh
git diff --check
```
