plugins {
	java
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependency.management)
	alias(libs.plugins.spotless)
}

group = "com.meterengine"
version = "0.0.1-SNAPSHOT"
description = "MeterEngine metering and billing API server"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation(libs.springdoc.openapi.starter.webmvc.scalar)
	implementation("org.flywaydb:flyway-database-postgresql")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	// 우리 코드가 부르는 API가 없어 runtimeOnly다. actuator가 클래스패스에서 감지해
	// /actuator/prometheus를 만든다. 노출 범위는 application.properties가 정한다 (MS2-168).
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
	java {
		googleJavaFormat(libs.versions.google.java.format.get())
		formatAnnotations()
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// OpenAPI 생성물 (MS2-140).
//
// springdoc은 앱이 떠 있어야 문서를 만들 수 있어서, 생성을 테스트(OpenApiDocumentTest)에 붙였다.
// 그래서 `./gradlew build`가 곧 생성 명령이 된다. 별도 태스크로 떼면 아무도 안 돌리는 것이 기본값이 되고,
// CI가 최신성을 검사하지 않기로 한 이상 낡음을 알아챌 방법이 없어진다.
//
// 두 단계로 나눈 이유가 중요하다. 테스트는 build/ 안에만 쓰고, 커밋 대상 파일은 별도 태스크가 복사한다.
// 커밋 대상 파일을 test의 산출물로 선언하면 이런 일이 난다 (실측):
//   1. `./gradlew test --tests "*SeedDataTest*"` 처럼 필터를 걸면 생성 테스트가 빠진 채로 성공한다
//   2. Gradle이 "산출물이 비어 있는" 캐시 엔트리를 그 필터 키로 저장한다
//   3. 같은 필터로 다시 돌리면 FROM-CACHE로 복원되면서 Gradle이 선언된 산출물을 먼저 지운다
//   4. 커밋된 openapi.yaml이 사라진다
// IntelliJ는 단일 테스트 실행을 Gradle에 `--tests`로 위임하므로, 테스트 하나 돌려 본 사람의 작업 트리에서
// 계약 정본이 삭제된다. git status에 뜨는 것을 신호로 쓰는 이 설계가 거짓 신호에 오염된다.
val openApiGenerated = layout.buildDirectory.file("openapi/openapi.yaml")
val openApiSnapshot = layout.projectDirectory.file("openapi.yaml")

tasks.test {
	// 경로를 넘기는 이유: 테스트의 작업 디렉터리는 실행 주체(Gradle, IDE)마다 달라서 상대 경로를 못 쓴다.
	// 절대 경로가 캐시 키에 들어가 체크아웃 경로가 다른 머신끼리는 test가 캐시 히트하지 않는다. 지금은 원격
	// 캐시가 없어 실질 피해가 없다. 캐시를 공유하게 되면 CommandLineArgumentProvider로 바꾼다.
	systemProperty("meterengine.openapi.snapshot", openApiGenerated.get().asFile.absolutePath)

	// 생성물 전용 설정이라 테스트 JVM에만 준다.
	//
	// application.properties에 넣으면 런타임에도 걸려서 /scalar의 경로 목록과 스키마 목록까지 알파벳순이 된다.
	// 시스템 프로퍼티는 Spring TestContext 캐시 키(설정 클래스, 프로파일, 애노테이션의 properties 등)에
	// 들어가지 않으므로 @SpringBootTest(properties = ...)와 달리 컨텍스트가 늘지 않는다.
	systemProperty("springdoc.writer-with-order-by-keys", "true")
	// 결정성 검사가 실제 모델 재구축을 거치게 한다. 캐시가 켜져 있으면 두 번째 요청이 같은 OpenAPI 객체를
	// 다시 직렬화할 뿐이라, 정작 흔들릴 수 있는 단계(핸들러 순회, 스키마 해석)를 한 번도 안 지난다.
	systemProperty("springdoc.cache.disabled", "true")

	outputs.file(openApiGenerated)
}

// 커밋 대상 파일은 여기서만 쓴다. test에 의존하므로 검증이 실패하면 아예 실행되지 않고, 실패한 빌드가
// git 추적 파일을 건드리는 일이 없다. 캐시나 증분 판정 대상이 아니라서 Gradle이 이 파일을 지울 일도 없다.
val copyOpenApiSnapshot =
	tasks.register("copyOpenApiSnapshot") {
		description = "생성된 OpenAPI 문서를 커밋 대상 위치로 옮긴다"
		dependsOn(tasks.test)
		outputs.upToDateWhen { false }
		doLast {
			val generated = openApiGenerated.get().asFile
			// 필터를 건 실행 등으로 생성 테스트가 안 돌았으면 그냥 둔다. 지우거나 비우지 않는다.
			if (generated.exists()) {
				generated.copyTo(openApiSnapshot.asFile, overwrite = true)
			}
		}
	}

tasks.named("build") { dependsOn(copyOpenApiSnapshot) }
