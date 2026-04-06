# Booking Agent

Android application for automated museum booking, built with Rust and Kotlin.

## Version: 3.5.2

**Release Date:** April 6, 2026

---

## Overview

Booking Agent is a high-performance Android application that automates the booking process for museum tickets. It combines the speed and safety of Rust with the native Android experience of Kotlin and Jetpack Compose.

### Key Features

- **High-Performance Rust Core**: Native booking engine with browser impersonation
- **Modern Android UI**: Built with Jetpack Compose and Material Design 3
- **Automated Booking**: Scheduled strikes with precise timing
- **Multi-Site Support**: Configurable for multiple booking platforms
- **Secure Storage**: Android Keystore integration for credentials
- **Real-time Status**: Live updates during booking operations

---

## Architecture

```
┌─────────────────────────────────────────┐
│           Android Application            │
│  ┌─────────────┐      ┌──────────────┐  │
│  │   Kotlin    │      │  Rust Engine │  │
│  │  Compose UI │◄────►│   (Core)     │  │
│  │  ViewModel  │ JNI  │  - HTTP      │  │
│  └─────────────┘ UniFFI│  - Scraper   │  │
│                       │  - Booker    │  │
│                       └──────────────┘  │
└─────────────────────────────────────────┘
```

See [Architecture Specification](docs/architecture/architecture.md) for detailed documentation.

---

## Quick Start

### Prerequisites

- **JDK 17+**
- **Android Studio Ladybug (2024.2.1)+**
- **Rust 1.70+** with Android targets
- **Android NDK r27**

### Build Instructions

```bash
# Clone repository
git clone https://github.com/YOUR_USERNAME/booking-agent.git
cd booking-agent

# Build Rust libraries for Android
cd rust-engine
cargo ndk -t armeabi-v7a -t arm64-v8a -t x86 -t x86_64 \
  -o ../android-app/app/src/main/jniLibs build --release

# Build Android app
cd ../android-app
./gradlew assembleDebug
```

APK will be available at: `android-app/app/build/outputs/apk/debug/`

---

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/architecture/architecture.md) | System design and technical specifications |
| [Implementation Guide](docs/implementation/implementation_guide.md) | Step-by-step development instructions |
| [Deployment Guide](docs/deployment/deployment.md) | Build, release, and distribution |
| [API Reference](docs/api-reference/api-docs.md) | Rust core API documentation |
| [UI Specification](docs/ui-design/ui-specification.md) | User interface design |
| [Testing Plan](docs/testing/testing-plan.md) | Testing strategy and procedures |
| [Product Requirements](docs/product_requirement/prd.md) | Feature specifications |
| [Release Guide](RELEASE_GUIDE.md) | GitHub releases workflow |

---

## Project Structure

```
booking-agent/
├── rust-engine/              # Rust core library
│   ├── Cargo.toml
│   ├── src/
│   │   ├── lib.rs           # Main entry point
│   │   ├── http_client.rs   # HTTP client with impersonation
│   │   ├── scraper_engine.rs # HTML parsing
│   │   ├── booker_engine.rs # Booking automation
│   │   └── config.rs        # Configuration
│   └── build.rs             # UniFFI build script
│
├── android-app/              # Android application
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── java/        # Kotlin source
│   │       └── jniLibs/     # Compiled Rust libs
│   └── gradle/
│
├── docs/                     # Documentation
│   ├── architecture/
│   ├── implementation/
│   ├── deployment/
│   └── ...
│
└── .github/workflows/        # CI/CD pipelines
    ├── release.yml          # Continuous integration
    ├── build-release.yml    # Release automation
    └── test.yml             # Test suite
```

---

## Technology Stack

### Rust Core

| Component | Technology | Version |
|-----------|------------|---------|
| HTTP Client | wreq | 6.0.0-rc.28 |
| TLS/SSL | BoringSSL | Bundled |
| Async Runtime | Tokio | 1.43 |
| HTML Parsing | scraper | 0.22 |
| FFI | UniFFI | 0.30.0 |
| Serialization | serde | 1.0 |

### Android App

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.1.0 |
| UI Framework | Jetpack Compose | 2025.02.00 |
| Minimum SDK | API 26 (Android 8.0) | - |
| Target SDK | API 35 (Android 15) | - |

---

## CI/CD

### Workflows

1. **release.yml** - Runs on push to main and PRs
   - Builds debug APK
   - Uploads as artifact

2. **build-release.yml** - Runs on version tags (v*)
   - Builds release and debug APKs
   - Creates GitHub Release automatically

3. **test.yml** - Runs on push and PRs
   - Rust tests, formatting, and linting
   - Android unit tests and lint checks

### Automated Releases

```bash
# Tag a release
git tag v3.5.2
git push origin v3.5.2
```

This triggers automatic build and GitHub Release creation.

See [RELEASE_GUIDE.md](RELEASE_GUIDE.md) for details.

---

## License

[Specify your license here]

---

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `cargo test` and `./gradlew test`
5. Submit a pull request

---

## Support

- **Issues**: Open an issue on GitHub
- **Documentation**: See `/docs` directory
- **Releases**: Check [GitHub Releases](https://github.com/YOUR_USERNAME/booking-agent/releases)

---

## Acknowledgments

- Built with [Rust](https://www.rust-lang.org/)
- Android UI with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- FFI via [UniFFI](https://mozilla.github.io/uniffi-rs/)
- HTTP client using [wreq](https://github.com/0x676e67/wreq)
