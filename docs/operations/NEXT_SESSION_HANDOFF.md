# GearVia 설치 자동화 다음 작업 인계

이 파일을 읽은 다음 아래 범위만 이어서 작업한다. 이전 대화 전체를 다시고 확장하지 않는다.

## 저장소와 브랜치

- 원격: `https://github.com/HO-0219/GearViaB2B_Version.git`
- 작업 위치: worktree `sixgill`, 브랜치 `study734/fetch-upstream-onprem-checkpoint`
  (upstream `feat/gearvia-onprem-checkpoint-a` 를 fast-forward 한 것 + 아래 커밋 2개)
- 기준 커밋: `9466607 build: pin installer shell scripts to LF endings`
- 구현 계획: `docs/superpowers/plans/2026-09-02-automated-ubuntu-install-domain-tls.md`
- 설계: `docs/superpowers/specs/2026-09-02-automated-ubuntu-install-domain-tls-design.md`

> 주의: 이 두 커밋(`d1b8afc`, `9466607`)은 아직 `upstream/feat/gearvia-onprem-checkpoint-a`
> (`1a21d62`)에 없다. 다음 세션은 `feat/gearvia-onprem-checkpoint-a` 가 아니라 위 브랜치를
> 기준으로 삼거나, 먼저 두 커밋을 해당 브랜치로 반영해야 한다.

## 사용자 최종 지시

내일 오전 내부 PWA 시연이 우선이다. 관리자 도메인 변경, 백엔드 동적 URL, 관리자 API/UI는
구현하지 않는다. 설치 스크립트 완성도만 빠르게 올린다. 백엔드·프런트엔드 전체 테스트를
반복하지 말고 Bash/Compose 집중 검증만 수행한다.

## 완료된 작업

### Task 1 — 커밋 `0f9a8c8`

- `sudo ./install_gearvia_ai_agent_ubuntu.sh` 무인자 설치 흐름
- MySQL 앱 비밀번호 숨김 입력 또는 `--db-password-file`
- JWT, 관리자 MFA 키, MySQL root 비밀번호 자동 생성
- `/etc/gearvia/runtime.env` 자동 생성 및 재실행 시 비밀값 보존
- 설정 파일을 `source`/`eval`하지 않는 고정 키 파서
- Ubuntu 수명 주기 집중 테스트 통과

### Task 2 — 커밋 `d5bdebc`

- 사설 IPv4 주소 자동 감지 (`gearvia_detect_primary_address`), 공개 IP 후보 거부
- 호스트 이름 감지 (`hostname -f`)
- 로컬 CA 및 서버 인증서 자동 생성 (`infra/ubuntu/lib/gearvia-tls.sh`)
- SAN 에 감지 IP, 호스트 이름, `localhost`, `127.0.0.1` 포함
- 키 `0600` / 인증서 `0644`, 재실행 시 유효한 키·인증서 재사용
- Compose 는 `/etc/gearvia/tls` 고정 경로를 읽기 전용 마운트

### Task 3 — 커밋 `1a21d62`, `d1b8afc`

- `infra/ubuntu/lib/gearvia-images.sh`: `gearvia_prepare_image` 로 번들 로드
  (`infra/images/<이름>.tar`) → 로컬 이미지 재사용 → 소스 빌드(Dockerfile) 또는
  레지스트리 pull 순서로 준비
- MySQL `mysql:8.4`, 초기화 `busybox:1.37` 자동 준비
- `gearvia_record_image_state` 로 각 이미지의 `*_IMAGE_REF` / `*_IMAGE_ID` 를
  `/var/lib/gearvia/install-state.env` 에 기록. 이미지 없으면 빈 ID 대신 중단
- 빌드·pull 실패 시 stderr 진단 출력 유지
- 제거: 활성 `runtime.env` 와 `/etc/gearvia/tls` 삭제
- 기본 제거: `/opt/b2bgearvia/data` (업로드/NAS), DB 볼륨, `recovery/database.env`
  (`0600`) 보존
- 완전 제거(`--purge-data --confirm-purge GEARVIA`): 관리 데이터 + 복구 상태까지 삭제
- 한국어 설치 문서(`docs/operations/ubuntu-installation.md`)를 무인자 명령 + 집중 검증
  목록에 맞게 수정
- 신규 테스트 `infra/ubuntu/test-image-selection.sh`

### 라인 엔딩 고정 — 커밋 `9466607`

- `.gitattributes` 에 `*.sh text eol=lf` + 루트 인스톨러 2개 고정
- `.gitignore` 허용목록에 `!.gitattributes`
- 신규 테스트 `infra/ubuntu/test-line-endings.sh` (커밋 blob + eol 속성 검증)
- 커밋된 blob 은 이미 LF 였음. 이 변경은 `core.autocrlf` 재유입 방지 목적

## 남은 작업

인계서가 정의한 설치 자동화 범위(Task 2+3, 라인 엔딩)는 **코드/스크립트 레벨 완료**.
다음만 남음:

- 실제 Ubuntu 24.04 + Docker + systemd 호스트에서 `GEARVIA_SKIP_RUNTIME` 없이
  `sudo ./install_gearvia_ai_agent_ubuntu.sh` 1회 엔드투엔드 실행 검증
  (`docker compose config`, `docker load/build/pull`, `systemctl enable --now`,
  readiness curl 경로는 어떤 자동 테스트도 타지 않음)
- 위 두 커밋을 `feat/gearvia-onprem-checkpoint-a` 로 반영 또는 그 결정

## 금지 범위

- `deployment_settings` DB 테이블
- 관리자 도메인·SSL 페이지
- 동적 CORS/WebSocket URL 변경
- 호스트 적용 API와 관리자 공지 흐름
- 백엔드 또는 프런트엔드 전체 테스트 반복

## 집중 검증 명령

Windows 개발 호스트에서는 WSL `bash.exe` 대신 Git Bash를 사용한다.

```powershell
& 'C:\Program Files\Git\bin\bash.exe' infra/ubuntu/test-lifecycle-scripts.sh
& 'C:\Program Files\Git\bin\bash.exe' infra/ubuntu/test-tls-automation.sh
& 'C:\Program Files\Git\bin\bash.exe' infra/ubuntu/test-image-selection.sh
& 'C:\Program Files\Git\bin\bash.exe' infra/ubuntu/test-line-endings.sh
& 'C:\Program Files\Git\bin\bash.exe' infra/b2b/test-virtualbox-config.sh
git diff --check
```

마지막 실행 결과: 5종 전부 통과, `git diff --check` 클린 (기준 커밋 `9466607`).

## 완료 조건

Ubuntu 24.04 서버에서 별도 `.env`, TLS 파일, 이미지 식별자 없이 다음 명령으로 설치를 시작할
수 있고, 사용자는 MySQL 앱 비밀번호만 입력한다.

```bash
sudo ./install_gearvia_ai_agent_ubuntu.sh
```
