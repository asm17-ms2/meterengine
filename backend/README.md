# backend

미터링 엔진 API 서버. 이벤트 수집, 집계, rating, 인보이스 생성을 담당한다.

## 기술 스택

- Java 25 + Spring Boot 4.1 + Gradle Kotlin DSL (ADR 0001). 버전은 `gradle/libs.versions.toml`에서 관리한다
- PostgreSQL 단일 저장소 (ADR 0002), DB 접근은 Spring Data JPA. 집계는 사전 집계 없이 SQL로 계산한다 (ADR 0004)
- 스키마 마이그레이션: Flyway (`src/main/resources/db/migration/`)
- API 명세: spec-first로 시작한다. 명세는 `docs/api/`에 두고(MS2-27에서 작성), springdoc-openapi는 구현 스냅샷을 만들어 명세와 대조하는 용도로 쓴다. 정본 정책은 API 표면별로 다르다: 고객 대면 API는 수기 명세가 계속 정본, 콘솔 내부 API는 구현 완료 후 springdoc 생성물로 정본을 승격한다 (상세는 `docs/api/README.md`)
- API 문서 UI: Scalar. 앱을 띄우면 `/scalar`에 뜬다. 원본 문서는 `/v3/api-docs`(JSON)와 `/v3/api-docs.yaml`이다. Swagger UI는 쓰지 않는다. 두 UI가 같은 문서를 보여줄 이유가 없어 `springdoc-openapi-starter-webmvc-ui` 대신 `-scalar`를 쓴다. 렌더링 JS가 jar에 번들되어 앱이 직접 서빙하므로 CDN을 타지 않고 버전이 의존성에 고정된다
- 테스트: JUnit 5 + AssertJ + Testcontainers. DB 제약 동작을 실제 PostgreSQL로 검증한다
- 코드 포맷: Spotless + google-java-format. CI에서 검사한다

## 실행

Docker Desktop(Compose 포함)과 JDK 25가 필요하다.

```
./gradlew bootRun
```

레포 루트의 `docker-compose.yml`에 정의된 PostgreSQL을 자동으로 띄우고 서버를 시작한다 (spring-boot-docker-compose). IDE에서 main 클래스를 직접 실행해도 되고, working directory가 backend/든 레포 루트든 동작한다 (`backend/compose.yaml`은 루트 정의를 가리키는 include 심이다).

## 빌드와 테스트

```
./gradlew build          # 컴파일 + 포맷 검사 + 테스트 (Docker 필요)
./gradlew spotlessApply  # 포맷 자동 적용
```

## 구조

단일 Gradle 모듈 + 도메인별 패키지 분리로 시작한다 (ADR 0005). 도메인 패키지 목록은 슬라이스를 진행하며 도출하고, 경계는 코드 리뷰로 지킨다.
