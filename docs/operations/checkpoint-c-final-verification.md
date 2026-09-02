# 체크포인트 C 및 최종 로컬 검증 결과

검증 일시: 2026-09-02 KST

검증 대상 커밋: `9faa57f` 및 본 검증 결과 문서 변경분

검증 환경: Windows 개발 호스트, Java 21.0.10, Spring Boot 3.3.5, Node/Vite 로컬 도구 체인

## 검증 결과

| 검증 항목 | 결과 | 근거 |
|---|---|---|
| 백엔드 전체 테스트 | 로컬 통과 | 총 488개, 실패 0개, 오류 0개, 건너뜀 2개, 56.220초 |
| 프런트엔드 테스트 | 통과 | 테스트 파일 6개, 테스트 10개, 1.44초 |
| 프런트엔드 운영 빌드 | 경고 포함 통과 | 모듈 97개, 기본 JS 551.04 kB(압축 시 158.48 kB) |
| Ubuntu 수명 주기 모의 검증 | 통과 | 미지원 OS 거부, 사전 점검, 재실행, 데이터 보존 제거, 확인 문구 기반 완전 삭제 |
| 용량 시험 설정 검증 | 통과 | 정상 템플릿 승인 및 동시 사용자 수 0인 비정상 설정 거부 |
| Bash 구문 검사 | 통과 | 신규 수명 주기 및 용량 시험 스크립트 전체가 `bash -n` 통과 |
| Compose 설정 검증 | 통과 | VirtualBox/B2B 병합 설정 유효 |
| MCP 리버스 프록시 계약 | 통과 | `/mcp`, 런타임 설정 및 신뢰 프록시 연결 확인 |
| 비밀정보 패턴 점검 | 통과 | 추적 중인 변경 사항에서 생성된 MCP 토큰이나 실제 공급자 인증정보 없음 |

## 출시 전 남은 필수 검증

- Docker Desktop 또는 Docker 데몬을 사용할 수 없어 MySQL Testcontainers 검사 2개를 건너뛰었습니다.
  출시 전에 초기 상태의 MySQL 8.x 호환 인스턴스에서 Flyway V1~V6, 스키마 검증 및
  인덱스 실행 계획 검사를 수행해야 합니다.
- OpenAI 호환 사내 엔드포인트가 제공되지 않아 실제 연동을 검증하지 못했습니다.
  사용자에게 기능을 활성화하기 전에 선택한 사내 LLM으로 채팅, 구조화된 Responses/도구 호출,
  임베딩을 모두 시험해야 합니다.
- 용량은 아직 **측정 전(UNMEASURED)**입니다. 검증되지 않은 동시 사용자 수를 문서에 게시하지
  않았습니다. 운영 환경과 동등한 단일 노드 장비에서 `infra/capacity/run-capacity-smoke.sh`를
  실행하고 모든 기준을 통과한 경우에만 `docs/operations/capacity-results-template.md`를 작성하십시오.
- Windows 검증 호스트에 ShellCheck가 설치되어 있지 않았습니다. Bash 구문 및 동작 검증은
  통과했지만, 출시 CI에서는 Ubuntu 환경의 ShellCheck를 실행해야 합니다.
- 프런트엔드 빌드에서 기존 500 kB 초과 청크 경고가 발생합니다. 빌드 실패는 아니지만,
  지연 시간이 큰 WAN 환경에 배포하기 전에 경로 단위 코드 분할 효과를 측정해야 합니다.

## 검증 명령어

```bash
cd backend && ./mvnw test
cd frontend && npm test -- --run && npm run build
infra/ubuntu/test-lifecycle-scripts.sh
infra/capacity/test-capacity-config.sh
bash -n install_gearvia_ai_agent_ubuntu.sh uninstall_gearvia_ai_agent_ubuntu.sh \
  infra/ubuntu/lib/gearvia-common.sh infra/ubuntu/test-lifecycle-scripts.sh \
  infra/capacity/run-capacity-smoke.sh infra/capacity/test-capacity-config.sh
infra/b2b/test-virtualbox-config.sh
```
