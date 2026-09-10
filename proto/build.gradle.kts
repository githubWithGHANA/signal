plugins {
    java
    id("org.springframework.boot") version "3.3.3"  // Match Spring Boot version to the one you need
    id("java")
    id("io.spring.dependency-management") version "1.1.6"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.quantlab.proto"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

fun getGrpcPluginArtifact(): String {
    val osName = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()

    return when {
        osName.contains("win") -> "io.grpc:protoc-gen-grpc-java:1.57.0:windows-x86_64@exe"
        osName.contains("mac") -> {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                "io.grpc:protoc-gen-grpc-java:1.57.0:osx-aarch_64@exe"
            } else {
                "io.grpc:protoc-gen-grpc-java:1.57.0:osx-x86_64@exe"
            }
        }
        osName.contains("linux") -> {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                "io.grpc:protoc-gen-grpc-java:1.57.0:linux-aarch_64@exe"
            } else {
                "io.grpc:protoc-gen-grpc-java:1.57.0:linux-x86_64@exe"
            }
        }
        else -> throw GradleException("Unsupported OS/architecture: $osName, $arch")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.12"
    }
    plugins {
        create("grpc") {
            artifact = getGrpcPluginArtifact()
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.3.0:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
                create("grpckt")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("proto/xts")
            srcDir("proto/tr")
        }
    }
}



dependencies {
    implementation("io.grpc:grpc-protobuf:1.30.0")
    implementation("io.grpc:grpc-stub:1.40.1")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("com.google.protobuf:protobuf-java:3.21.7")
}

tasks.named("compileJava") {
    dependsOn("generateProto") // Ensures proto generation happens before compilation
}

tasks.test {
    useJUnitPlatform()
}
