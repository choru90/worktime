import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.spring") version "1.9.20"
    kotlin("plugin.jpa") version "1.9.20"
    jacoco
}

group = "com.worktime"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Database
    runtimeOnly("com.mysql:mysql-connector-j")
    
    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    
    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
    
    // Swagger/OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // Monitoring
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// WT-001: collect coverage evidence before defining enforcement thresholds.
// Report generation is intentionally separate from the normal test task so existing
// CI behavior is unchanged until the baseline is reviewed.
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// WT-002: disposable loopback-only server; H2 stays on the test classpath.
// This task cannot load application-local credentials or connect to a shared DB.
tasks.register<JavaExec>("e2eServer") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.worktime.ApplicationKt")
    args("--server.address=127.0.0.1", "--server.port=8080",
        "--spring.profiles.active=wt002-e2e",
        "--spring.datasource.url=jdbc:h2:mem:wt002_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "--spring.datasource.driver-class-name=org.h2.Driver",
        "--spring.datasource.username=sa", "--spring.datasource.password=",
        "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "--spring.jpa.hibernate.ddl-auto=create-drop", "--spring.flyway.enabled=false",
        "--jwt.secret=wt002-disposable-e2e-signing-key-at-least-32-bytes", "--jwt.expiration=3600000",
        "--spring.jpa.show-sql=false", "--spring.jpa.properties.hibernate.generate_statistics=false",
        "--logging.level.org.hibernate.SQL=WARN", "--logging.level.com.worktime=INFO")
}
