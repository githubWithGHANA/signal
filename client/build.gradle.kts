plugins {

    id("org.springframework.boot") version "3.3.3"  // Match Spring Boot version to the one you need
    id("java")
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.quantlab.client"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}


dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.modelmapper:modelmapper:3.2.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation(project(":common"))
    implementation(project(":signal"))
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.apache.poi:poi-ooxml:5.4.0")
    implementation( "io.swagger.core.v3:swagger-annotations:2.1.2")
}

tasks.test {
    useJUnitPlatform()
}
