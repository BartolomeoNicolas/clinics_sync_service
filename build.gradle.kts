plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.bartolomeo.clinics"
version = "0.0.1-SNAPSHOT"
description = "Servicio de catálogo y sincronización · clinics"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // --- web y validación -------------------------------------------------
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // --- persistencia -----------------------------------------------------
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // En Boot 4 el driver ya NO viene por el starter de JPA: hay que declararlo.
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.liquibase:liquibase-core")

    // --- integración con la cátedra ---------------------------------------
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // --- seguridad --------------------------------------------------------
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // --- observabilidad y docs -------------------------------------------
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // --- tests ------------------------------------------------------------
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:2.0.5")
    testImplementation("org.testcontainers:postgresql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.testcontainers:kafka:2.0.5")
    testImplementation("org.testcontainers:testcontainers-kafka:2.0.5")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}