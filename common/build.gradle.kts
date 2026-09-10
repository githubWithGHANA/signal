plugins {
    id("org.springframework.boot") version "3.3.3"  // Match Spring Boot version to the one you need
    id("java")
    id("io.spring.dependency-management") version "1.1.6"  // Required for managing Spring dependencies

}

group = "com.quantlab.common"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.modelmapper:modelmapper:3.2.0")
    compileOnly("org.projectlombok:lombok")

    implementation("org.modelmapper:modelmapper:3.0.0")

    annotationProcessor("org.projectlombok:lombok")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-hibernate6:2.17.2")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mail:3.3.4")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-hibernate6:2.17.2")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("io.springfox:springfox-boot-starter:3.0.0")
    implementation("io.springfox:springfox-swagger-ui:3.0.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0")
    implementation("io.swagger.core.v3:swagger-core-jakarta:2.2.10")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
    implementation("org.postgresql:postgresql:42.7.8")
}

tasks.test {
    useJUnitPlatform()
}
