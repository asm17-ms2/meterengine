# k1 넘기기

사용자 요청으로 중간에 멈추고 정리했다. 자세한 판정은 `k1.json`, 요약은 `k1.md`에 있다.

## 끝낸 것

- 판정을 마친 기존 지적: 80건 중 42건. confirmed 21, refuted 10, already-fixed 11(마이그레이션 번호 10건, C448-004), uncertain 0. 추가 검토 새 지적 5건(#448)도 판정했다(confirmed 2, refuted 3).
- push한 수정 브랜치와 마지막 커밋:
  - `claude/s456-k1-442` `5487226` (base `feat/account-permissions` `0245c15`): `9474edc` 로컬 시드 대표관리자(C442-001, G-032 일부, G-003), `5487226` 가입 owner 단언(C442-006)
  - `claude/s456-k1-448` `2eca9ab` (base `feat/request-audit` `4f8e59f`): `7e8a843` 감사 저장 실패 시 응답 유지(C448-003, behavior_change), `48d00e5` 체인 안 방화벽 거절은 400 감사 행 하나(C448-000, behavior_change), `2eca9ab` 정렬 테스트(C448-005)
  - `claude/s456-k1-454` `9e22f5e` (base `feat/auth-rate-limit` `ecf459b`): `5954564` 초대 토큰 가림 테스트(C454-011), `b41df3b` 창 초기화 테스트(C454-009), `5573091` IPv6 판정 테스트(C454-012), `9e22f5e` 가입 한도 테스트(C454-010)
- 하다 만 수정은 없다. 커밋하지 않은 변경도 없다.

## 남은 것

### 판정하지 못한 id (38건)

두 관점 검토 대상(high 또는 security)이라 재현 관점과 피해 관점을 따로 보려 했으나 시작 전에 멈췄다.

- #454 호출 제한 IP(#461 머지 반영 확인 필요): C454-001, C454-004, G-091, G-156
- #454 계정 잠금: C454-002, C454-005
- #454 계정 키 정규화: C454-003, C454-007, C454-008
- #448 감사 행 증가(#459 머지 반영 확인 필요): C448-002, G-092, G-169, G-159
- #448 감사 귀속과 read 스코프: C448-001, G-095
- #442 초대 토큰 로그(#454의 RequestLogFilter 가림과 C454-011 테스트가 위층에서 막는다. #442 단독 머지 구간 판단 필요): C442-003, G-029, G-063
- #442 배포의 admin API 키 주입: C442-010, G-030, G-096, G-160
- #442 비밀번호 변경과 보관 소속 재진입: C442-002, C442-009

한 관점 검토 대상이었는데 결과 전에 멈췄다.

- #442 유지보수: G-010, G-011, G-012, G-013, G-017, G-018, G-038, G-026
- #442 배포와 문서: G-019, G-022, G-034, G-031, G-033, G-187

### confirmed인데 고치지 못한 것

수정안은 `k1.json`의 각 결과 `suggested_fix`에 있다.

- #442: C442-021(감사 부재 단언을 actor_id로), C442-023(NotEmpty 가드가 groups를 보게 하고 빈 consents 400 테스트, DTO 변경이라 openapi.yaml 확인), C442-024(마지막 운영자 잠금 통합 테스트), G-001(DataIntegrityViolationException catch 제거와 register 사전 조회, behavior_change), G-005(403 consent_required를 OpenAPI와 README에), G-006(새 컨트롤러 @ApiResponses), G-008(서비스 안 응답 레코드를 dto로 옮기고 Response 이름), G-009(읽기 메서드 invitations 이름이 naming.md 위반)
- #448: Nk1-002(감사 대상이 경로 변수 이름 목록에 묶임), Nk1-003(RequestAuditService가 AuditLogService 행 생성을 복사)
- #454: G-101(요청 로그 client_address와 한도 IP가 다르고 429 로그가 없다)
- 범위 밖: G-027(경로 /accept 변경은 프론트 호출을 같이 바꿔야 한다), G-101의 프론트 부분

### 추가 검토에서 후보로만 남은 것 (검증 전 10건)

- #448 low `backend/src/main/java/com/meterengine/audit/service/AuditLogQueryService.java:97`: from, to, sort를 문자열로 받아 감사 서비스 안에서 따로 해석해, 형식마다 뜻이 다르고 문서와 다른 목록 API와도 맞지 않는다
- #448 low `backend/src/main/java/com/meterengine/audit/service/RequestAuditService.java:54`: 요청 감사 행이 target_type=http_request에 경로의 리소스 ID를 넣고, 요청 값은 문서에 없는 detail 키에 둔다
- #448 low `backend/README.md:244`: 감사 조회 API, X-Request-ID 헤더, 감사 화면을 더했는데 README 점검 대상 절을 고치지 않았다
- #448 low `backend/src/main/java/com/meterengine/audit/controller/AuditLogController.java:51`: 플랫폼 감사 조회에 도입사 조건이 없어 운영자가 한 도입사의 요청 기록만 골라 볼 수 없다
- #454 medium `deploy/caddy/Caddyfile:9`: 토큰 경로 가림 규칙이 Caddyfile 여섯 줄과 RequestLogFilter에 따로 복사되어 결과가 이미 갈렸다
- #454 medium `deploy/compose.prod.yml:20`: 운영 compose가 한도 설정 8개 중 2개만 넘겨, README대로 Parameter Store에 한도와 창을 넣어도 조용히 무시된다
- #454 medium `deploy/compose.prod.yml:33`: proxy 네트워크 이전 SHA로 롤백하면 새 색이 뜰 때까지 사이트 전체가 끊기고, 옛 색이 못 뜨면 장애가 이어진다
- #454 medium `backend/src/main/java/com/meterengine/account/service/AuthRequestRateLimitService.java:62`: 한도를 넘은 요청도 매번 DB 쓰기와 같은 행 잠금을 거쳐, 한 IP의 폭주가 공용 커넥션 풀을 막는다
- #454 low `backend/src/main/java/com/meterengine/account/controller/SessionController.java:87`: 로그인과 가입의 429만 OpenAPI에 Retry-After 헤더 정의가 없다
- #454 low `backend/src/main/java/com/meterengine/account/service/AuthRequestRateLimitService.java:72`: 로그인과 가입 429를 보는 경보와 지표가 없어 한도 오설정이나 계정 잠금을 운영자가 모른다

- #448과 #454 모두 유지보수 관점과 운영 관점 검토는 끝났고, 위 후보의 반박 검증만 남았다. 검증한 #448 후보 5건은 `k1.json`의 `new_findings`(Nk1-001~005)에 있다.

### 돌리지 못한 검사

- 수정 브랜치 3개의 전체 `./gradlew build`. 수정마다 관련 테스트와 변이 확인만 했다. 컨트롤러와 DTO는 바꾸지 않아 openapi.yaml 변화는 없다.
- 프론트 검사와 deploy/tests는 해당 파일을 바꾸지 않아 돌리지 않았다.

## 이어받을 때 알아야 할 것

- 실행 환경: Maven Central과 Docker Hub가 429를 줬다. `~/.gradle/init.d/mirror.gradle`로 Maven을 Google 미러로 돌렸고, `postgres:18`은 `mirror.gcr.io/library/postgres:18`을 받아 태그를 붙였다. 새 세션에서도 같은 조치가 필요할 수 있다.
- 판정을 바꿀 수 있는 의심
  - C448-003 수정은 기존 테스트 이름이 고정한 의도(감사 실패 예외를 숨기지 않는다)를 뒤집는다. PR 작성자 동의가 필요할 수 있어 behavior_change로 적었다.
  - C448-004는 #459로 수집 요청이 감사에서 빠져 already-fixed로 봤지만, COUNT 구조 자체는 남아 refuted로 볼 여지도 있다.
  - C454-012는 운영 신뢰 프록시가 IPv4뿐이라 사용자 영향이 없다. 테스트 공백만 confirmed다.
  - G-032는 로컬 시드 경로만 고쳤다. 배포 창에 옛 색이 role 없이 넣는 소속은 보정하지 않았다.
  - G-114는 refuted지만 익명 요청 감사 행 증가는 C448-002 계열(판정 전)과 원인이 같다.
- 다른 묶음과 겹치는 파일
  - `RequestAuditFilter.java`: #450(k3)이 REQUEST_ID_ATTRIBUTE를 더한다. #448 수정 브랜치를 위층에 올릴 때 충돌한다.
  - `frontend/src/app/organization-invitations/[token]/actions.ts`: G-027과 k6 G-099, G-179가 같은 파일을 본다.
  - `deploy/compose.prod.yml`: k2 G-157과 #454 후보 2건이 같은 파일을 본다.
- 이 세션의 작업 트리는 `/home/user/wt/` 아래에 있지만 컨테이너가 사라지면 없어진다. 원격 브랜치에서 다시 시작한다.
