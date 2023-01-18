plugins {
    java
    kotlin("jvm") version "1.7.21" apply false
    kotlin("plugin.serialization") version "1.7.21" apply false
}

group = "com.github.winteryuki.archtest"
version = "1.0-SNAPSHOT"

subprojects {
    apply {
        plugin("java")
        plugin("kotlin")
        plugin("org.jetbrains.kotlin.plugin.serialization")
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.4.1")
        implementation("org.slf4j:slf4j-api:2.0.3")
        implementation("org.slf4j:slf4j-simple:2.0.3")
        implementation("io.github.microutils:kotlin-logging-jvm:3.0.2")

        testImplementation(kotlin("test"))
        testImplementation(platform("org.junit:junit-bom:5.8.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
    }

    sourceSets {
        main {
            java.setSrcDirs(listOf("src"))
            resources.setSrcDirs(listOf("resources"))
        }
        test {
            java.setSrcDirs(listOf("test"))
            resources.setSrcDirs(listOf("testResources"))
        }
    }

    tasks.test {
        useJUnitPlatform()
    }

    java.toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
    val compileTestKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks

    compileKotlin.kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
    }

    compileTestKotlin.kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
    }
}
