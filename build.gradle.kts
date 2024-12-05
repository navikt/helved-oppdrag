plugins {
    kotlin("jvm") version "2.1.0"
    id("io.ktor.plugin") version "3.0.1" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    tasks {
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        }

        withType<Test> {
            useJUnitPlatform()
        }

        sourceSets {
            main {
                kotlin.srcDir("main")
                resources.srcDirs("main", "migrations")
            }
            test {
                kotlin.srcDir("test")
                resources.srcDir("test")
            }
        }
    }
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }

    kotlin {
        jvmToolchain(21)
    }
}
