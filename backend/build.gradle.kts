plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.joblens"
version = "0.0.1-SNAPSHOT"
description = "JobLens AI backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// Reproducible dependency resolution. Regenerate with: ./gradlew resolveAndLockAll --write-locks
dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("org.jsoup:jsoup:1.23.1")
    // Version managed by the Spring Boot BOM. Chosen over the JDK client because it accepts a custom
    // DnsResolver, which is what makes "resolve, validate, then connect to the validated address"
    // possible - the JDK client offers no equivalent hook.
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("com.microsoft.playwright:playwright:1.62.0")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-this-escape", "-Werror"))
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

tasks.named<Test>("test") {
    // Browser tests download a Chromium build and are far slower, so they stay out of the
    // default loop. Run them with ./gradlew browserTest.
    useJUnitPlatform { excludeTags("browser", "provider-eval") }
}

tasks.register<Test>("browserTest") {
    group = "verification"
    description = "Runs the tests that need a real browser."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("browser") }
    shouldRunAfter(tasks.named("test"))
}

// The acceptance suite a candidate AI provider must pass. Excluded from the normal build because
// running it against a real provider sends document content off host and costs money.
tasks.register<Test>("providerEval") {
    group = "verification"
    description = "Evaluates the configured analysis provider against the acceptance fixtures."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("provider-eval") }
    shouldRunAfter(tasks.named("test"))
    testLogging { showStandardStreams = false }
}

// Resolves every lockable configuration so that `--write-locks` produces a complete lockfile set.
tasks.register("resolveAndLockAll") {
    notCompatibleWithConfigurationCache("Filters configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "Run with --write-locks, e.g. ./gradlew resolveAndLockAll --write-locks"
        }
    }
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}
