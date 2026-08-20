# B2BGearVia

B2BGearVia는 한 조직이 자체 서버에서 운영하는 B2B 온프레미스 협업 플랫폼입니다. 공개 회원가입이나 결제 중심의 SaaS 운영 대신, 회사 관리자가 사용자와 접근 정책을 관리하는 단일 서버·단일 조직 환경을 대상으로 합니다.

## 주요 기능

- 그룹, 팀원, 역할 기반 권한
- 업무 요청, 승인, 담당자, 상태 흐름
- 체크리스트, 댓글, 멘션, 캘린더, 알림
- 프로젝트, 프로젝트 이슈, 긴급 이슈
- 그룹 채팅, 자료 공유, 문서 업로드
- 대시보드, 기본 리포트, PDF 출력
- 로컬 계정, JWT 세션, 관리자 MFA
- 선택형 OpenAI 기반 AI 비서와 AI 리포트

## 지원 환경

- Ubuntu Server 24.04 LTS x86_64
- Docker Engine
- Docker Compose v2
- MySQL 8.4 컨테이너
- 단일 서버, 단일 회사
- HTTPS 443 접속

운영 데이터베이스 이름은 `b2bgearvia`입니다. MySQL 3306과 백엔드 8081은 Docker 내부 네트워크에서만 사용하며, 웹 컨테이너만 호스트의 443 포트를 공개합니다.

## 기술 구성

| 영역 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot, Spring Security, JPA, Flyway |
| Frontend | React, TypeScript, Vite |
| Database | MySQL 8.4 |
| Runtime | Docker Compose, Nginx, systemd |
| Authentication | Local account, JWT, refresh session, ADMIN MFA |

## 저장소 구조

```text
backend/       Spring Boot API, Flyway schema, backend tests
frontend/      React web application
docs/contracts/ AI report JSON Schema contracts used by backend tests
infra/b2b/     B2B Docker Compose, Nginx, runtime configuration example
installer/     Optional integration configuration scripts
```

## 관리자 매뉴얼

설치 준비, 시스템 구성, 환경설정, 관리자 온보딩과 운영 점검은 [B2BGearVia 온프레미스 구축 및 관리자 가이드](./B2BGearVia_B2B_설치운영_매뉴얼.pdf)를 참고하세요.

## VirtualBox 테스트 빠른 시작

1. Ubuntu Server 24.04 VM을 만들고 네트워크 어댑터 1은 NAT, 어댑터 2는 Host-Only로 설정합니다.
2. VM의 홈 디렉터리에서 저장소를 클론하고 자동 설치기를 실행합니다.

```bash
git clone https://github.com/HO-0219/GearViaB2B_Version.git
cd GearViaB2B_Version
sudo ./installer/install-virtualbox.sh
```

설치기는 다음 작업을 순서대로 자동 처리합니다.

- Docker Engine과 Docker Compose v2 확인 및 설치
- VirtualBox Host-Only IP 감지
- 운영 파일을 `/opt/b2bgearvia`에 배치
- JWT, MFA, MySQL 비밀값 생성
- VirtualBox 테스트용 자체서명 TLS 인증서 생성
- 최초 관리자 계정 생성
- 백엔드와 프론트엔드 이미지 빌드
- 서비스 실행 및 준비 상태 확인

설치가 끝나면 `admin / admin`으로 로그인합니다. 최초 로그인 시 비밀번호 변경 화면으로 이동합니다. 관리자 정보 파일은 `/opt/b2bgearvia/config/initial-admin.txt`에 생성되며 비밀번호 변경 후 삭제하세요.

Host-Only IP 자동 감지가 맞지 않으면 주소만 지정해 다시 실행할 수 있습니다. 기존 DB와 설정은 삭제하거나 덮어쓰지 않습니다.

```bash
sudo B2B_VM_IP=192.168.56.101 ./installer/install-virtualbox.sh
```

OpenAI API 키는 설치 필수값이 아닙니다. `disabled (no key file)`은 AI 기능이 비활성 상태라는 뜻이며 일반 협업 기능은 그대로 실행됩니다. 자체서명 인증서는 VirtualBox 테스트에서만 사용하세요.

설치 작업을 실행하지 않고 계획만 확인하려면 `B2B_DRY_RUN=true ./installer/install-virtualbox.sh`를 사용하세요. 수동 설치와 운영 점검 절차는 PDF 관리자 매뉴얼을 참고하세요.

VirtualBox 테스트 DB는 `b2bgearvia`로 자동 생성되며 MySQL 관리자 계정은 `root`, 비밀번호는 `gearvia`입니다. 애플리케이션은 보안을 위해 자동 생성된 별도 `b2bgearvia` 계정을 사용합니다. MySQL 포트는 호스트에 공개되지 않습니다.

### VirtualBox 완전 삭제 후 재설치

다음 명령은 컨테이너, 애플리케이션 이미지, MySQL DB, 업로드 파일과 `/opt/b2bgearvia`를 모두 삭제합니다. Docker Engine과 홈 디렉터리의 Git 클론은 유지됩니다.

```bash
sudo ./installer/uninstall-virtualbox.sh
sudo ./installer/install-virtualbox.sh
```

삭제 전에 `DELETE`를 직접 입력해야 합니다. 삭제 대상만 미리 확인하려면 다음 명령을 사용하세요.

```bash
./installer/uninstall-virtualbox.sh --dry-run
```

## 로컬 빌드와 테스트

### Backend

```bash
cd backend
./mvnw test
```

### Frontend

```bash
cd frontend
npm ci
npm run build
```

### Compose 설정 검증

`infra/b2b/runtime.env.example`을 `infra/b2b/runtime.env`로 복사하고 모든 예시 값을 실제 운영 값으로 교체한 다음 검증합니다.

```bash
docker compose \
  --env-file infra/b2b/runtime.env \
  -f infra/b2b/compose.yml config
```

정식 서버 기동에는 실제 컨테이너 이미지 digest와 TLS 인증서가 필요합니다.

## 보안 주의사항

- `runtime.env`, `ai.env`, TLS 개인키와 관리자 초기 비밀번호를 Git에 커밋하지 마세요.
- `ADMIN_ALLOWED_IPS`는 실제 사내망 또는 VPN CIDR로 제한하세요.
- 호스트에 MySQL 3306과 백엔드 8081을 공개하지 마세요.
- 모든 관리자 계정에 MFA를 적용하세요.
- 운영 DB를 테스트 초기화 대상으로 사용하지 마세요.

## AI 기능

OpenAI API 키가 없으면 AI 기능만 비활성화되고 일반 협업 기능은 계속 사용할 수 있습니다. AI를 활성화하면 사용자가 선택한 업무 맥락이 외부 AI 제공자에게 전송될 수 있으므로 조직의 개인정보 및 보안정책을 먼저 확인하세요.

## 공개 범위

이 저장소에는 애플리케이션 소스, B2B 배포 구성, 선택형 통합 스크립트, README와 관리자 매뉴얼만 포함합니다. 비밀정보, 인증서, 로컬 실행 데이터, 빌드 산출물과 내부 작업 문서는 포함하지 않습니다.
