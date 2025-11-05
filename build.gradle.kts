import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.plugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType

// Centralized versions
val kotlinPluginVersion = "2.2.21"          // used only for reference; plugins block keeps literal
val androidGradlePluginVersion = "8.12.3"   // used only for reference; plugins block keeps literal
val ktorVersion = "3.3.0"
val coroutinesVersion = "1.8.0"
val serializationVersion = "1.6.3"
val datetimeVersion = "0.5.0"
val slf4jVersion = "2.0.9"
val junitVersion = "4.13.2"
val logbackVersion = "1.4.14"

plugins {
    kotlin("multiplatform") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("com.android.library") version "8.12.3"
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

    // ========================================
    // JVM Target - Backend services, desktop apps
    // ========================================
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // ========================================
    // Android Target - Mobile apps
    // ========================================
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        publishLibraryVariants("release", "debug")
    }

    // ========================================
    // iOS Targets - iPhone, iPad, Simulator
    // ========================================
    val iosTargets = listOf(
        iosX64(),           // iOS Simulator on Intel Macs
        iosArm64(),         // iOS Device (64-bit ARM)
        iosSimulatorArm64() // iOS Simulator on Apple Silicon
    )

    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "UrlScanClient"
            isStatic = true
        }
    }

    // ========================================
    // Linux Targets - Servers, CLI tools
    // ========================================
    linuxX64()    // Linux x86_64
    linuxArm64()  // Linux ARM

    // ========================================
    // macOS Targets - Desktop apps, CLI tools
    // ========================================
    macosX64()    // macOS Intel
    macosArm64()  // macOS Apple Silicon (M1/M2/M3)

    // ========================================
    // Windows Target - Desktop apps, CLI tools
    // ========================================
    mingwX64()    // Windows 64-bit

    // ========================================
    // JavaScript Target - Web and Node.js
    // ========================================
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

    // ========================================
    // Source Sets Configuration
    // ========================================
    sourceSets {
        // ========================================
        // Common Source Set - Shared across ALL platforms
        // ========================================
        val commonMain by getting {
            dependencies {
                // Ktor Client - HTTP client library
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.ktor:ktor-client-logging:$ktorVersion")
                // Coroutines - Async/await support
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

                // Serialization - JSON parsing
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

                // DateTime - Timestamp handling (optional)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:$datetimeVersion")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        // ========================================
        // JVM Source Set - JVM and Android
        // ========================================
        val jvmMain by getting {
            dependencies {
                // OkHttp engine for JVM/Android
                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
                implementation("io.ktor:ktor-client-logging:$ktorVersion")

                // Logging
                implementation("org.slf4j:slf4j-api:$slf4jVersion")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("junit:junit:$junitVersion")
                implementation("ch.qos.logback:logback-classic:$logbackVersion")
            }
        }

        // ========================================
        // iOS Source Set - Shared across all iOS targets
        // ========================================
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Darwin engine for iOS
                implementation("io.ktor:ktor-client-darwin:$ktorVersion")
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

        // iOS Tests
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

        // ========================================
        // Native Source Set - Linux, macOS, Windows
        // ========================================
        val nativeMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Curl engine for native platforms
                implementation("io.ktor:ktor-client-curl:$ktorVersion")
            }
        }

        // Linux targets
        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }

        val linuxArm64Main by getting {
            dependsOn(nativeMain)
        }

        // macOS targets
        val macosX64Main by getting {
            dependsOn(nativeMain)
        }

        val macosArm64Main by getting {
            dependsOn(nativeMain)
        }

        // Windows target
        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }

        // ========================================
        // JavaScript Source Set
        // ========================================
        val jsMain by getting {
            dependencies {
                // JS engine for browser and Node.js
                implementation("io.ktor:ktor-client-js:$ktorVersion")
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

// ========================================
// Android Library Configuration
// ========================================
android {
    namespace = "io.urlscan.client"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = false
    }
}


// ========================================
// Custom Tasks
// ========================================

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

// Task to clean all build outputs
tasks.register("cleanAll") {
    group = "build"
    description = "Cleans all build outputs including platform-specific builds"

    dependsOn("clean")

    doLast {
        println("✓ All build outputs cleaned")
    }
}

// Task to build and test everything
tasks.register("buildAll") {
    group = "build"
    description = "Builds and tests all targets"

    dependsOn("build", "test")

    doLast {
        println("\n" + "=".repeat(50))
        println("✓ Build completed successfully!")
        println("=".repeat(50))
    }
}