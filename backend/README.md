# backend

미터링 엔진 API 서버. 이벤트 수집, 집계, rating, 인보이스 생성을 담당한다.

## 기술 스택

- Java 25 + Spring Boot 4.1 + Gradle Kotlin DSL. 버전은 `gradle/libs.versions.toml`에서 관리한다
- PostgreSQL 단일 저장소, DB 접근은 Spring Data JPA. 집계는 사전 집계 없이 SQL로 계산한다
- 스키마 마이그레이션: Flyway. 아직 마이그레이션이 없다. 첫 마이그레이션은 `src/main/resources/db/migration/`에 둔다
- API 명세: `openapi.yaml`(구현에서 자동 생성, 아래 "API 문서" 참조). 손으로 쓰는 명세는 없다. 최종 위치는 `docs/document-rules.md`가 정해지면 옮긴다
- API 문서 UI: Scalar. 앱을 띄우면 `/scalar`에 뜬다. 원본 문서는 `/v3/api-docs`(JSON)와 `/v3/api-docs.yaml`이다. Swagger UI는 쓰지 않는다. 두 UI가 같은 문서를 보여줄 이유가 없어 `springdoc-openapi-starter-webmvc-ui` 대신 `-scalar`를 쓴다. 렌더링 JS가 jar에 번들되어 앱이 직접 서빙하므로 CDN을 타지 않고 버전이 의존성에 고정된다
- 테스트: JUnit 5 + AssertJ + Testcontainers. DB가 필요한 테스트는 실제 PostgreSQL 컨테이너로 돌린다
- 코드 포맷: Spotless + google-java-format. CI에서 검사한다

## 실행

Docker Desktop(Compose 포함)과 JDK 25가 필요하다.

```
./gradlew bootRun
```

레포 루트의 `docker-compose.yml`에 정의된 PostgreSQL을 자동으로 띄우고 서버를 시작한다 (spring-boot-docker-compose). IDE에서 main 클래스를 직접 실행해도 되고, working directory가 backend/든 레포 루트든 동작한다 (`backend/compose.yaml`은 루트 정의를 가리키는 include 심이다).

## 빌드와 테스트

```
./gradlew build          # 컴파일 + 포맷 검사 + 테스트 + OpenAPI 생성물 (Docker 필요)
./gradlew spotlessApply  # 포맷 자동 적용
```

## API 문서

`openapi.yaml`이 API 계약의 정본이다. 컨트롤러와 DTO에서 자동 생성되므로 손으로 고치지 않는다.

**컨트롤러나 DTO를 건드렸으면 `openapi.yaml`을 같은 커밋에 넣는다.** `./gradlew build`가 다시 만들어 주니, 빌드 후 `git status`에 이 파일이 떴으면 계약이 바뀐 것이다. 프론트엔드는 백엔드를 띄우지 않고 이 파일로 계약을 읽는다.

생성은 `OpenApiDocumentTest`가 한다. springdoc은 코드를 정적으로 분석하지 않아 앱이 떠 있어야 문서를 만들 수 있고, 그래서 생성 자리가 테스트다. 앱을 띄운 상태에서는 같은 문서를 `/scalar`(UI), `/v3/api-docs`(JSON), `/v3/api-docs.yaml`에서 볼 수 있다.

**CI는 이 파일을 검사하지 않는다.** 커밋된 생성물과 다시 만든 것을 비교해 실패시키는 스텝을 두지 않았다. 문서를 바꾸는 변경 중에 `@Parameter` 문구 수정처럼 알아채기 어려운 것이 많고, springdoc이나 Spring Boot 버전을 올려도 출력이 통째로 달라질 수 있어서, 검사를 넣으면 백엔드를 만지는 PR이 납득하기 어려운 이유로 빨개진다. 대신 생성을 빌드에 붙여 `git status`에 뜨게 했다.

**생성 자체는 CI에서도 돈다.** backend job이 `./gradlew build`를 돌리기 때문이다. 그래서 애노테이션이 잘못돼 문서 생성이 깨지면 CI가 잡는다. 잡지 않는 것은 "커밋된 파일이 낡았는지"뿐이다.

그래서 **갱신을 빠뜨리면 아무것도 실패하지 않는다.** 다음 둘 중 하나가 나오면 CI 검사를 다시 논의한다 (MS2-140).

- 프론트엔드가 이 파일과 실제 응답이 다르다고 보고한다
- PR 리뷰에서 생성물 누락을 지적한 일이 두 번 나온다

## 구조

단일 Gradle 모듈 + 도메인별 패키지 분리로 시작한다. 도메인 패키지 목록은 슬라이스를 진행하며 도출하고, 경계는 코드 리뷰로 지킨다.
