import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "io.urlscan"
version = "1.0.0"

kotlin {
    // Apply compiler options to all targets
    sourceSets {
        commonMain {
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        publishLibraryVariants("release", "debug")
    }

    // iOS Targets - iPhone, iPad, Simulator
    val iosTargets = listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    )

    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "UrlScanClient"
            isStatic = true
        }
    }

    linuxX64()
    linuxArm64()

    macosX64()
    macosArm64()

    mingwX64()

    js(KotlinJsCompilerType.IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        nodejs()
        binaries.executable()
    }

    sourceSets {
        // Common Source Set - Shared across ALL platforms
        val commonMain by getting {
            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.serialization.json)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.encoding)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                // OkHttp engine for JVM/Android
                implementation(libs.ktor.client.okhttp)
                implementation(libs.ktor.client.logging)

                // Logging
                implementation(libs.slf4j.api)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.logback.classic)
            }
        }

        // Android Source Set - depends on JVM
        androidMain {
            dependsOn(jvmMain)
        }

        // iOS Source Set - Shared across all iOS targets
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        val iosX64Main by getting {
            dependsOn(iosMain)
        }

        val iosArm64Main by getting {
            dependsOn(iosMain)
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }

        val iosTest by creating {
            dependsOn(commonTest)
        }

        val iosX64Test by getting {
            dependsOn(iosTest)
        }

        val iosArm64Test by getting {
            dependsOn(iosTest)
        }

        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest)
        }

        // Native Source Set - Linux, macOS, Windows
        val nativeMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Curl engine for native platforms
                implementation(libs.ktor.client.curl)
            }
        }

        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }

        val linuxArm64Main by getting {
            dependsOn(nativeMain)
        }

        val macosX64Main by getting {
            dependsOn(nativeMain)
        }

        val macosArm64Main by getting {
            dependsOn(nativeMain)
        }

        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }

        val jsMain by getting {
            dependencies {
                // JS engine for browser and Node.js
                implementation(libs.ktor.client.js)
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

android {
    namespace = "io.urlscan.client"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = false
    }
}


// Task to print all available targets
tasks.register("printTargets") {
    group = "help"
    description = "Prints all available Kotlin Multiplatform targets"

    doLast {
        println("\n" + "=".repeat(50))
        println("Available Kotlin Multiplatform Targets")
        println("=".repeat(50))

        kotlin.targets.forEach { target ->
            println("  ✓ ${target.name.padEnd(25)} - ${target.platformType}")
        }

        println("\n" + "=".repeat(50))
        println("Useful Build Commands")
        println("=".repeat(50))
        println("  ./gradlew build                    # Build all targets")
        println("  ./gradlew test                     # Run all tests")
        println("  ./gradlew jvmJar                   # Build JVM JAR")
        println("  ./gradlew assembleRelease          # Build Android AAR")
        println("  ./gradlew linkReleaseFrameworkIosArm64  # Build iOS Framework")
        println("  ./gradlew linuxX64Binaries         # Build Linux binaries")
        println("  ./gradlew macosX64Binaries         # Build macOS binaries")
        println("  ./gradlew mingwX64Binaries         # Build Windows binaries")
        println("  ./gradlew jsBrowserDistribution    # Build JS for browser")
        println("  ./gradlew jsNodeDistribution       # Build JS for Node.js")
        println("  ./gradlew publishToMavenLocal      # Publish to local Maven")
        println("=".repeat(50) + "\n")
    }
}