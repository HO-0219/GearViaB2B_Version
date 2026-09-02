# GearVia 개인 MCP 연결 가이드

## 배포 및 네트워크 경계

`https://<internal-gearvia-host>/mcp`는 설정된 사내망 또는 VPN에서 GearVia HTTPS 리버스
프록시를 통해서만 노출해야 합니다. `MCP_ENABLED=true`로 설정하고 허용할 CIDR을
`MCP_ALLOWED_CIDRS`에 지정하십시오. `MCP_TRUSTED_PROXIES`에는 리버스 프록시 컨테이너 또는
네트워크만 등록해야 합니다. 백엔드 포트나 MCP 엔드포인트를 인터넷에 직접 공개하면 안 됩니다.

Nginx는 `X-Forwarded-For` 헤더를 추가하지 않고 덮어씁니다. GearVia는 설정된 프록시 CIDR에서
들어온 헤더만 신뢰합니다. 패키지의 백엔드는 `SERVER_FORWARD_HEADERS_STRATEGY=none`으로
설정되어 있으므로 이 정책이 적용되기 전에 서블릿 컨테이너가 소켓 접속자 주소를 바꾸지 않습니다.

각 사용자는 마이페이지에서 읽기 전용 개인 토큰을 발급합니다. 평문 토큰은 발급 시 한 번만
표시되며 GearVia에는 SHA-256 해시만 저장됩니다. 토큰을 폐기하거나 사용자 계정을 정지하면
다음 요청부터 즉시 차단됩니다.

도구 응답에는 항목 수와 바이트 수 제한이 적용되며, 모든 최종 도구 실행 결과는 감사 로그에
기록됩니다. 현재 단일 노드 릴리스의 요청 속도 및 동시 실행 제한은 JVM 로컬 방식입니다.
백엔드 노드를 두 대 이상으로 늘리기 전에 Redis 또는 MySQL 임대 카운터 같은 공유 제한 방식으로
교체해야 합니다. 그렇지 않으면 클라이언트가 노드마다 설정된 허용량을 각각 사용할 수 있습니다.

## Codex CLI 연결

한 번만 표시되는 토큰을 로컬 환경 변수에 저장한 뒤 Streamable HTTP 엔드포인트를 등록합니다.

```bash
export GEARVIA_MCP_TOKEN='paste-the-one-time-token'
codex mcp add gearvia --url https://gearvia.internal/mcp \
  --bearer-token-env-var GEARVIA_MCP_TOKEN
codex mcp get gearvia
```

현재 Codex CLI는 Streamable HTTP용 `--url`과 토큰을 `config.toml`에 직접 기록하지 않는
`--bearer-token-env-var` 옵션을 제공합니다.

## Claude Code 연결

Claude Code는 Authorization 헤더를 사용하는 원격 HTTP MCP 서버를 지원합니다. 공유 프로젝트
파일에 토큰이 남지 않도록 사용자 범위 JSON 설정에서 환경 변수 확장 방식을 사용하십시오.

```bash
export GEARVIA_MCP_TOKEN='paste-the-one-time-token'
claude mcp add-json gearvia \
  '{"type":"http","url":"https://gearvia.internal/mcp","headers":{"Authorization":"Bearer ${GEARVIA_MCP_TOKEN}"}}' \
  --scope user
claude mcp get gearvia
```

## 최초 제공 도구

- `gearvia_list_groups`: 토큰 소유자가 조회할 수 있는 그룹 목록
- `gearvia_list_tasks`: 그룹 소속 권한을 확인한 뒤 반환하는 제한된 작업 목록
- `gearvia_get_task`: 그룹 소속 권한을 확인한 뒤 반환하는 단일 작업

첫 릴리스에서는 의도적으로 읽기 전용 도구만 제공합니다. SQL, 셸 명령, 임의 HTTP 요청,
파일시스템 경로 또는 데이터베이스 직접 접근은 제공하지 않습니다.

## 프로토콜 및 보안 참고 자료

- MCP Streamable HTTP 전송 방식: <https://modelcontextprotocol.io/specification/2025-06-18/basic/transports>
- MCP 도구 스키마 및 호출: <https://modelcontextprotocol.io/specification/2025-06-18/server/tools>
- Claude Code 원격 MCP 설정: <https://docs.anthropic.com/en/docs/claude-code/mcp>
