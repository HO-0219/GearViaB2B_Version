# Ubuntu 설치 및 제거 가이드

## 지원 환경

- x86_64 기반 Ubuntu Server 24.04 LTS
- 승인된 사내 저장소에서 설치한 Docker Engine 및 Docker Compose v2
- 완전한 GearVia 릴리스 번들 및 고정 사설 IPv4 네트워크
- 기본적으로 차단된 인터넷 아웃바운드 연결
- NAS 스토리지를 사용할 경우 `/opt/b2bgearvia/data/nas`에 NAS를 먼저 마운트

기본값인 `COMPOSE_PROFILES=bundled-db`는 패키지에 포함된 MySQL 서버를 실행합니다.
외부 MySQL 8.x 호환 서버를 사용하려면 `COMPOSE_PROFILES`를 비우고 `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`를 설정하십시오. DB URL에는 인증서 및 호스트 이름 검증이
반드시 활성화되어야 합니다. 운영 런타임 파일을 변경하기 전에 제품 내 사전 점검, 공지,
마이그레이션 및 롤백 절차를 모두 완료하십시오.

## 설치 또는 업그레이드

설치기는 기본 경로의 사설 IPv4 주소와 호스트 이름을 감지하고 root 전용 런타임 설정,
로컬 CA와 HTTPS 서버 인증서를 자동 생성합니다. 대화형 설치에서 필요한 입력은 번들 MySQL
애플리케이션 비밀번호 하나뿐입니다. 호스트를 변경하지 않고 사전 점검하려면 실행합니다.

```bash
sudo ./install_gearvia_ai_agent_ubuntu.sh --dry-run
```

systemd가 관리하는 Compose 스택을 설치하고 시작하려면 다음 명령을 실행합니다.

```bash
sudo ./install_gearvia_ai_agent_ubuntu.sh
```

무인 설치에서는 root만 읽을 수 있는 절대 경로의 비밀번호 파일을 사용합니다.

```bash
sudo ./install_gearvia_ai_agent_ubuntu.sh --db-password-file /secure/mysql-app-password
```

설치 작업은 안전하게 재실행할 수 있습니다. 설정은 `/etc/gearvia/runtime.env`, 배포 파일은
`/opt/b2bgearvia`, 인증서는 `/etc/gearvia/tls`, 복구 상태는 `/var/lib/gearvia`에 저장됩니다.
접속 주소는 감지된 사설 IP의 `https://<사설-IP>`이며, 클라이언트에는
`/etc/gearvia/tls/ca.crt`를 신뢰할 수 있는 CA로 배포해야 합니다.

이미지는 `infra/images/<이름>.tar` 번들 로드, 로컬 이미지 재사용, 애플리케이션 소스 빌드
순서로 준비합니다. MySQL 8.4와 BusyBox 1.37은 로컬에 없을 때만 가져오므로 폐쇄망에서는
해당 tar 번들을 릴리스에 포함하십시오. 적용된 이미지 ID는 `/var/lib/gearvia/install-state.env`에
기록됩니다.

`runtime.env`, JWT·MFA·MySQL 비밀값은 설치기가 생성하며 재실행 시 보존됩니다.
`infra/b2b/runtime.env.example`은 생성되는 키 목록 확인용이며 직접 복사하지 않습니다.
접속 주소(`FRONTEND_URL`)와 서버 인증서를 바꾸려면 `runtime.env`를 편집하고
`/etc/gearvia/tls/{fullchain,privkey}.pem`을 교체한 뒤 `sudo systemctl restart b2bgearvia`
하십시오.

## 제거

기본 제거는 서비스를 중지하고 활성 설정, TLS 키/인증서를 삭제하지만 Docker 데이터베이스
볼륨, 로컬/NAS 파일 및 DB 비밀번호 전용 복구 상태는 보존합니다.

```bash
sudo ./uninstall_gearvia_ai_agent_ubuntu.sh
```

되돌릴 수 없는 GearVia 관리 데이터 삭제에는 아래 두 옵션을 모두 지정해야 합니다.
Docker 명명 볼륨과 외부 마운트 NAS의 파일은 별도로 해당 스토리지 관리자가 처리해야 합니다.

```bash
sudo ./uninstall_gearvia_ai_agent_ubuntu.sh --purge-data --confirm-purge GEARVIA
```

릴리스 패키지를 만들기 전에 다음 집중 검증을 모두 실행하십시오.

```bash
bash infra/ubuntu/test-lifecycle-scripts.sh
bash infra/ubuntu/test-tls-automation.sh
bash infra/ubuntu/test-image-selection.sh
bash infra/ubuntu/test-line-endings.sh
bash infra/ubuntu/test-release-bundle.sh
bash infra/b2b/test-virtualbox-config.sh
```
