# Ubuntu 설치 및 제거 가이드

## 지원 환경

- x86_64 기반 Ubuntu Server 24.04 LTS
- 승인된 사내 저장소에서 설치한 Docker Engine 및 Docker Compose v2
- 완전한 GearVia 릴리스 번들, TLS 인증서/개인 키, 고정 IP 또는 사내 DNS
- 기본적으로 차단된 인터넷 아웃바운드 연결
- NAS 스토리지를 사용할 경우 `/opt/b2bgearvia/data/nas`에 NAS를 먼저 마운트

기본값인 `COMPOSE_PROFILES=bundled-db`는 패키지에 포함된 MySQL 서버를 실행합니다.
외부 MySQL 8.x 호환 서버를 사용하려면 `COMPOSE_PROFILES`를 비우고 `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`를 설정하십시오. DB URL에는 인증서 및 호스트 이름 검증이
반드시 활성화되어야 합니다. 운영 런타임 파일을 변경하기 전에 제품 내 사전 점검, 공지,
마이그레이션 및 롤백 절차를 모두 완료하십시오.

## 설치 또는 업그레이드

`infra/b2b/runtime.env.example`을 복사해 `runtime.env`를 준비합니다. 모든 자리표시자 값을
실제 운영 값으로 교체하고 파일 권한을 `0600`으로 유지하십시오. 호스트를 변경하지 않고
설정만 검증하려면 다음 명령을 실행합니다.

```bash
sudo ./install_gearvia_ai_agent_ubuntu.sh --dry-run \
  --config /secure/gearvia/runtime.env
```

systemd가 관리하는 Compose 스택을 설치하고 시작하려면 다음 명령을 실행합니다.

```bash
sudo ./install_gearvia_ai_agent_ubuntu.sh \
  --config /secure/gearvia/runtime.env \
  --tls-cert /secure/gearvia/fullchain.pem \
  --tls-key /secure/gearvia/privkey.pem
```

설치 작업은 안전하게 재실행할 수 있습니다. 설정은 `/etc/gearvia/runtime.env`, 배포 파일은
`/opt/b2bgearvia`, 복구 상태는 `/var/lib/gearvia`에 복사됩니다. 설치 프로그램은 원본 설정
파일을 셸 코드로 평가하거나 실행하지 않습니다.

## 제거

기본 제거는 서비스를 중지하고 애플리케이션 설정을 삭제하지만 Docker 데이터베이스 볼륨,
로컬/NAS 파일 및 복구 상태는 보존합니다.

```bash
sudo ./uninstall_gearvia_ai_agent_ubuntu.sh
```

되돌릴 수 없는 GearVia 관리 데이터 삭제에는 아래 두 옵션을 모두 지정해야 합니다.
Docker 명명 볼륨과 외부 마운트 NAS의 파일은 별도로 해당 스토리지 관리자가 처리해야 합니다.

```bash
sudo ./uninstall_gearvia_ai_agent_ubuntu.sh --purge-data --confirm-purge GEARVIA
```

릴리스 패키지를 만들기 전에 `bash infra/ubuntu/test-lifecycle-scripts.sh`를 실행하십시오.
