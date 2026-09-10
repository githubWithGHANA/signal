plugins {
    java
    id("org.springframework.boot") version "3.3.3"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.liquibase.gradle") version "2.2.2"
}

sourceSets {
    main {
        resources.srcDir("src/main/resources")
    }
}

liquibase {
    activities {
        create("main") {
            this.arguments = mapOf(
                "changelogFile" to "src/main/resources/db/changelog/db.changelog-master.yaml",
                "url" to "jdbc:postgresql://localhost:5432/signal_dev",
                "username" to "dev_admin",
                "password" to "bps@1122",
                "driver" to "org.postgresql.Driver",
                "classpath" to "src/main/resources",
                "propertyFile" to "liquibase.properties"
            )
        }
    }
    setProperty("runList", "main")
}

group = "com.quantlab"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("redis.clients:jedis:3.1.0")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("jakarta.servlet:jakarta.servlet-api:5.0.0")
    implementation("org.liquibase:liquibase-core")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.mapstruct:mapstruct:1.5.3.Final")
    liquibaseRuntime("org.liquibase:liquibase-core:4.16.1") // Explicitly add this to liquibaseRuntime
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test") // Use Spring Boot test starter
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.apache.logging.log4j:log4j-core:2.24.1")
    implementation("org.apache.logging.log4j:log4j-api:2.24.1")
    implementation("org.modelmapper:modelmapper:3.2.0")
    implementation("com.zaxxer:HikariCP")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-hibernate6:2.17.2")
    implementation("io.springfox:springfox-boot-starter:3.0.0")
    implementation("io.springfox:springfox-swagger-ui:3.0.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0")
    implementation("io.swagger.core.v3:swagger-core-jakarta:2.2.10")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("com.esotericsoftware:kryo:5.5.0")
    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
    implementation(project(":proto"))
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mail:3.3.4")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("info.picocli:picocli:4.7.6")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0") // Ensure compatibility
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.0")
    implementation("org.json:json:20210307")
    implementation("org.springframework.retry:spring-retry:1.3.4")
    implementation("org.redisson:redisson-spring-boot-starter:3.52.0")

}

tasks.bootRun {
    if (project.hasProperty("profile")) {
        args("--spring.profiles.active=${project.property("profile")}")
    } else {
        args("--spring.profiles.active=dev")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<JavaExec>("generateChangelogs") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("liquibase.integration.commandline.LiquibaseCommandLine")
    args = listOf(
        "--classpath=src/main/resources",
        "--changelog-file=src/main/resources/db/changelog/db.changelog-master.yaml",
        "--url=jdbc:postgresql://localhost:5432/signal_dev",
        "--username=dev_admin",
        "--password=bps@1122",
        "generateChangeLog"
    )
}

// tasks.register<JavaExec>("applyMigrations") { ... }
// tasks.register("generateAndApplyMigrations") { ... }

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
