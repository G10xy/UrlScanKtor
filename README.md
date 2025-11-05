# UrlScanKtor

A **Kotlin Multiplatform** HTTP client library built with **Ktor** for URL scanning and web requests across all major platforms.  
_The project is still in its early stages, aiming to provide a robust and type-safe way to interact with API as per documentation https://docs.urlscan.io/apis/urlscan-openapi._

## 🚀 Features

- **Cross-platform support**: JVM, Android, iOS, Linux, macOS, Windows, and JavaScript
- **Ktor-powered**: Modern HTTP client with coroutines support
- **Type-safe**: Built with Kotlin serialization for JSON handling
- **Lightweight**: Minimal dependencies with platform-optimized engines
- **Production-ready**: Comprehensive logging and error handling

## 📱 Supported Platforms

| Platform | Target | Engine |
|----------|--------|--------|
| **JVM** | Java 21+ | OkHttp |
| **Android** | API 21+ | OkHttp |
| **iOS** | iPhone, iPad, Simulator | Darwin |
| **Linux** | x64, ARM64 | Curl |
| **macOS** | Intel, Apple Silicon | Curl |
| **Windows** | x64 | Curl |
| **JavaScript** | Browser, Node.js | JS Engine |

## 🛠️ Installation 

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.urlscan:UrlScanKtor:1.0.0")
}
```

### Maven

```xml
<dependency>
    <groupId>io.urlscan</groupId>
    <artifactId>UrlScanKtor</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 🔧 Building from Source

### Prerequisites

- **JDK 21** or higher
- **Android SDK** (for Android targets)
- **Xcode** (for iOS targets, macOS only)

### Build Commands

```bash
# Build all targets
./gradlew build

# Run tests
./gradlew test

# Build specific targets
./gradlew jvmJar                    # JVM JAR
./gradlew assembleRelease           # Android AAR
./gradlew linkReleaseFrameworkIosArm64  # iOS Framework
./gradlew linuxX64Binaries          # Linux binaries
./gradlew jsBrowserDistribution     # JavaScript (Browser)

# Print all available targets
./gradlew printTargets

# Clean everything
./gradlew cleanAll
```

## 📋 Requirements

### Development
- Kotlin 2.2.21+
- Gradle 8.0+
- Android Gradle Plugin 8.12.3

### Runtime
- **JVM**: Java 21+
- **Android**: API Level 21+
- **iOS**: iOS 12.0+
- **Native**: Platform-specific requirements

## 🏗️ Architecture

```
UrlScanKtor/
├── commonMain/          # Shared code across all platforms
├── jvmMain/            # JVM and Android specific code
├── iosMain/            # iOS specific code  
├── nativeMain/         # Linux, macOS, Windows code
└── jsMain/             # JavaScript specific code
```

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

