plugins {
    id("org.springframework.boot") version "3.3.3" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("java")
}

allprojects {
    group = "com.quantlab"
    version = "0.0.1-SNAPSHOT"

    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    repositories {
        mavenCentral()
    }
}


subprojects {
    plugins.apply("java")

    group = "com.quantlab"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.1")
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.apache.commons:commons-lang3:3.12.0")


    }

    tasks.test {
        useJUnitPlatform()
    }

    tasks.register<Wrapper>("wrapper") {
        gradleVersion = "7.2"
    }

}
