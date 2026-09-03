# 접속 주소 자동 감지 결함 수정 및 검증 결과

검증 일시: 2026-09-03 KST

대상: `e17fae5`(upstream `feat/gearvia-onprem-checkpoint-a` tip) 위에 다음 수정을 쌓은 트리.

- `infra/ubuntu/lib/gearvia-tls.sh` — `gearvia_detect_primary_address`, `gearvia_issue_server_cert`
- `infra/ubuntu/test-tls-automation.sh` — 회귀 테스트 추가

## 배경 (증상)

데모 재촬영용 VirtualBox VM(`GearVia-rec`, host-only `192.168.56.102`)에서
`install_gearvia_ai_agent_ubuntu.sh` 로 설치한 뒤, 브라우저·Playwright 의 모든
`/api/**` 변경 요청(로그인 `POST` 포함)이 **HTTP 403 "Invalid CORS request"** 로
거절됨. `GET`·`/healthz` 는 정상.

## 근본 원인 1 — VirtualBox NAT 주소를 접속 주소로 감지

`gearvia_detect_primary_address` 가 후보 IP 목록에서 **첫 번째 사설 IPv4** 를 그대로
채택한다. VirtualBox 게스트는 NIC1=NAT(`enp0s3`, `10.0.2.15/24`),
NIC2=host-only(`enp0s8`, `192.168.56.102/24`) 구성이고, **기본 라우트는 항상 NAT
쪽**(`default via 10.0.2.2 dev enp0s3`)이라 `ip -4 route get` 이 `10.0.2.15` 를
맨 앞에 내놓는다. `10.0.2.15` 는 `10.0.0.0/8` 이므로 즉시 반환되고 host-only 주소는
검사조차 되지 않는다.

이 값이 `gearvia_write_runtime_env` 를 거쳐 `/etc/gearvia/runtime.env` 의
`DOMAIN_NAME` · `FRONTEND_URL` · `ADMIN_FRONTEND_URL` · `MCP_ALLOWED_ORIGINS` 에
`https://10.0.2.15` 로 박힌다. Spring CORS 허용 목록이 여기서 만들어지므로, 실제
접속 주소인 `https://192.168.56.102` 에서 온 `Origin` 이 목록에 없어 모든 변경
요청이 403 이 된다. `SERVER_FORWARD_HEADERS_STRATEGY=none`(Admin·MCP 가 raw socket
peer 를 검증해야 해서 의도된 값)이라 `X-Forwarded-*` 로 복구되는 경로도 없다.
서버 인증서 SAN 도 `IP:10.0.2.15` 만 담겨 이름 불일치가 함께 발생한다.

`10.0.2.15` 는 게스트 내부에서만 도달 가능하고 호스트·LAN 에서는 접근 불가하므로
접속 주소로는 절대 부적합하다.

## 근본 원인 2 — 인증서 재사용 가드가 무력

`gearvia_issue_server_cert` 는 기존 인증서를 재사용할지 판단할 때
`openssl x509 -noout -checkip <addr>` 의 **종료 코드**를 본다. 이 명령은 주소가
일치하지 않아도 항상 `0` 으로 끝나고 결과는 stdout 텍스트로만 알린다
(`IP <addr> does NOT match certificate`). 따라서 두 파일이 존재하기만 하면 가드가
항상 "재사용" 으로 판정하여, 접속 주소가 바뀌어 설치기를 재실행해도 낡은 인증서를
그대로 유지한다.

## 수정

### `gearvia_detect_primary_address`
- `GEARVIA_PUBLIC_ADDRESS=<사설 IPv4>` 환경 변수로 명시 지정 시 그 값을 우선한다.
- `scope global` 후보에서 컨테이너·브리지 인터페이스(`docker0`, `br-*`, `veth*`,
  `virbr*`)를 제외한다.
- `10.0.2.*`(VirtualBox NAT 고정 대역)는 다른 사설 주소가 하나도 없을 때만
  최후 fallback 으로 채택한다.

### `gearvia_issue_server_cert`
- `gearvia_cert_covers()` 헬퍼 신설 — `-checkip` / `-checkhost` 의 **출력 문자열**
  (`does match certificate`)로 판정한다. 주소가 바뀌면 인증서를 실제로 재발급한다.

## 추가 테스트 (`infra/ubuntu/test-tls-automation.sh`)

| 케이스 | 기대 |
|---|---|
| 후보 `10.0.2.15 10.0.2.15/24 192.168.56.102/24 172.17.0.1/16` (NAT·docker 우선 순서) | `192.168.56.102` 선택 |
| 후보 `10.0.2.15/24` 단독 | `10.0.2.15` fallback 허용 |
| `GEARVIA_PUBLIC_ADDRESS=192.168.7.7` + 후보 `10.0.2.15` | `192.168.7.7` |
| 인증서 재발급을 다른 주소로 호출 | SAN 이 새 주소로 교체, 옛 주소 SAN 제거 |
| 기존 케이스(공개 IP 거부, SAN 구성, 키 재사용, 파일 권한) | 회귀 없음 |

## 검증 결과

### 로컬 (Windows 개발 호스트, Git Bash)

| 항목 | 결과 |
|---|---|
| `infra/ubuntu/test-tls-automation.sh` | 통과 |
| `infra/ubuntu/test-lifecycle-scripts.sh` | 통과 |
| `infra/ubuntu/test-line-endings.sh` | 통과 |
| `infra/ubuntu/test-image-selection.sh` | 통과 |
| `infra/ubuntu/test-release-bundle.sh` | 통과 |
| `infra/b2b/test-virtualbox-config.sh`, `infra/b2b/test-mcp-proxy-config.sh` | 통과 |

### 실환경 (촬영용 VM `GearVia-rec`, `192.168.56.102`, Ubuntu 24.04)

패치한 `gearvia-tls.sh` 를 반영한 뒤 `install_gearvia_ai_agent_ubuntu.sh` 재실행
(시크릿·데이터 볼륨 보존).

| 항목 | 수정 전 | 수정 후 |
|---|---|---|
| `gearvia_detect_primary_address` | `10.0.2.15` | `192.168.56.102` |
| `runtime.env` 의 `DOMAIN_NAME`·`FRONTEND_URL`·`ADMIN_FRONTEND_URL`·`MCP_ALLOWED_ORIGINS` | `…10.0.2.15` | `…192.168.56.102` |
| 서버 인증서 SAN | `IP:10.0.2.15` | `IP:192.168.56.102` (재발급 확인) |
| `POST /api/v1/auth/login` (`Origin: https://192.168.56.102`) | **403** `Invalid CORS request` | **200** |
| 교차 출처(`Origin: https://evil.example`) | 403 | 403 (유지) |
| `GET /healthz` | 200 | 200 |
| MCP 개인 토큰 조회 | — | 성공 |
| 설치기 재실행 | — | idempotent (변경 없음) |

## 운영 노트

- 신규 설치는 위 수정으로 host-only / LAN 주소를 정상 감지한다.
- 다중 NIC·비표준 라우팅 환경에서는
  `sudo GEARVIA_PUBLIC_ADDRESS=<접속 주소> ./install_gearvia_ai_agent_ubuntu.sh`
  로 접속 주소를 명시한다.
- 접속 주소를 바꿀 때는 여전히 `runtime.env` 편집 + 서비스 재시작이 필요하다
  (관리자 Domain·SSL 화면은 `7f25ac2` 에서 제거됨). 설치기를 재실행하면
  `GEARVIA_PUBLIC_ADDRESS` 또는 자동 감지 결과에 맞춰 `runtime.env` 와 인증서가
  함께 갱신된다.
