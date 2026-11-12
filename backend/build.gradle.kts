plugins {
	java
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependency.management)
	alias(libs.plugins.spotless)
	checkstyle
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
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	implementation(libs.springdoc.openapi.starter.webmvc.scalar)
	implementation(platform(libs.aws.sdk.bom))
	// 동기 클라이언트만 쓰므로 비동기 HTTP 클라이언트(netty)는 넣지 않는다.
	implementation(libs.aws.sdk.sesv2) { exclude(group = "software.amazon.awssdk", module = "netty-nio-client") }
	implementation("org.flywaydb:flyway-database-postgresql")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	// 우리 코드가 부르는 API가 없어 runtimeOnly다. actuator가 클래스패스에서 감지해
	// /actuator/prometheus를 만든다. 노출 범위는 application.properties가 정한다 (MS2-168).
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation(libs.archunit.junit6)
}

spotless {
	java {
		googleJavaFormat(libs.versions.google.java.format.get())
		formatAnnotations()
	}
}

checkstyle {
	toolVersion = libs.versions.checkstyle.get()
	maxWarnings = 0
}

tasks.withType<Test> {
	useJUnitPlatform()
	// 캐시된 컨텍스트가 쌓여 기본값 512MB로는 전체 시험의 힙 여유가 없다
	maxHeapSize = "1g"
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	systemProperty("spring.profiles.active", "local")
}

val openApiGenerated = layout.buildDirectory.file("openapi/openapi.yaml")
val openApiSnapshot = layout.projectDirectory.file("openapi.yaml")

tasks.test {
	systemProperty("meterengine.openapi.snapshot", openApiGenerated.get().asFile.absolutePath)
	systemProperty("springdoc.writer-with-order-by-keys", "true")
	systemProperty("springdoc.cache.disabled", "true")

	outputs.file(openApiGenerated)
}

val copyOpenApiSnapshot =
	tasks.register("copyOpenApiSnapshot") {
		description = "생성된 OpenAPI 문서를 커밋 대상 위치로 옮긴다"
		dependsOn(tasks.test)
		outputs.upToDateWhen { false }
		doLast {
			val generated = openApiGenerated.get().asFile
			if (generated.exists()) {
				generated.copyTo(openApiSnapshot.asFile, overwrite = true)
			}
		}
	}

tasks.named("build") { dependsOn(copyOpenApiSnapshot) }
