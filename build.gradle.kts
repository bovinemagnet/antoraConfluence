plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
}

group = "io.github.bovinemagnet"
version = "0.1.0"

repositories {
    mavenCentral()
}

val okHttpVersion = "4.12.0"
val jacksonVersion = "2.17.2"
val asciidoctorjVersion = "3.0.0"

dependencies {
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("org.asciidoctor:asciidoctorj:$asciidoctorjVersion")

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okHttpVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("antoraConfluence") {
            id = "io.github.bovinemagnet.antora-confluence"
            implementationClass = "io.github.bovinemagnet.antoraconfluence.AntoraConfluencePlugin"
            displayName = "Antora Confluence Plugin"
            description = "Publishes Antora-structured AsciiDoc documentation to Atlassian Confluence"
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
