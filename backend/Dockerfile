# 테스트는 CI가 돌린다. 여기서 돌리지 않는 이유는 정책만이 아니라 물리적 제약이다:
# 이 레포의 테스트는 Testcontainers로 Docker 데몬을 요구하는데, 이미지 빌드 안에는 데몬이 없다.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# 소스보다 훨씬 드물게 바뀌는 빌드 정의를 먼저 복사해 의존성 다운로드 레이어를 캐시한다.
# 소스만 고친 빌드는 이 레이어를 재사용해 다운로드 없이 컴파일부터 시작한다.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
RUN ./gradlew --no-daemon dependencies

COPY src/ src/
RUN ./gradlew --no-daemon bootJar

# 실행 이미지에는 JRE와 jar만 담는다. JDK, Gradle 캐시, 소스가 빠져 크기가 줄고 공격 표면도 준다.
FROM eclipse-temurin:25-jre
WORKDIR /app

# temurin 이미지의 기본 사용자는 root라 명시적으로 내려놓는다.
RUN useradd --system --no-create-home app
USER app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
