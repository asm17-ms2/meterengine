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

// 스냅샷 생성은 test 태스크만의 일이다. 아래 설정을 withType<Test>에 두면 나중에 Test 태스크가
// 늘었을 때 여러 태스크가 같은 파일을 산출물로 선언하게 되고, Gradle이 "출력 경로를 독점하지
// 못한다"며 해당 태스크의 빌드 캐시를 꺼버린다.
tasks.named<Test>("test") {
	// 경로를 한 곳에서만 정한다. 테스트에 알려주는 위치와 Gradle이 감시하는 위치가 어긋나면
	// 아래 산출물 등록이 무의미해지는데, 그 어긋남은 아무 에러 없이 조용히 생긴다.
	val openApiSnapshot = layout.projectDirectory.file("../docs/api/generated/openapi.yaml")

	// OpenApiSnapshotTest가 레포 루트 아래에 생성물을 쓴다. 실행 위치(IDE/Gradle)에 따라 경로가 달라지지 않도록 절대 경로로 넘긴다.
	systemProperty("meterengine.openapi.snapshot", openApiSnapshot.asFile.absolutePath)

	// 생성물을 test의 산출물로 등록한다. 등록하지 않으면 소스가 그대로일 때 test가 UP-TO-DATE로
	// 건너뛰어져 생성물이 다시 만들어지지 않고, 그 상태에서는 yaml만 바뀐 커밋(머지 충돌 해소 등)이
	// CI 최신성 검사를 그대로 통과한다. 등록하면 파일이 바뀐 것만으로 태스크가 다시 실행되고,
	// 빌드 캐시가 적중할 때도 캐시에서 복원된다 (PR #14 리뷰).
	outputs.file(openApiSnapshot)
}
