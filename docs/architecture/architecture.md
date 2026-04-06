# Architecture Specification
## System Architecture & Technical Design

**Version:** 3.5.2  
**Date:** April 6, 2026  
**Status:** Final  

---

## 1. High-Level Architecture

### 1.1 System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android Application                       │
│  ┌─────────────────────┐          ┌─────────────────────────┐  │
│  │   Kotlin UI Layer   │          │   Rust Engine (Core)    │  │
│  │  ┌───────────────┐  │          │  ┌─────────────────┐    │  │
│  │  │  Jetpack      │  │          │  │ Booking Agent   │    │  │
│  │  │  Compose UI   │◄─┼──JNI────►│  │ (Orchestrator)  │    │  │
│  │  └───────────────┘  │ UniFFI   │  └────────┬────────┘    │  │
│  │  ┌───────────────┐  │          │           │             │  │
│  │  │  ViewModels   │  │          │  ┌────────▼────────┐    │  │
│  │  │  (State Mgmt) │◄─┼──────────┼──┤ HTTP Client     │    │  │
│  │  └───────────────┘  │          │  │ (wreq/Boring)   │    │  │
│  │  ┌───────────────┐  │          │  └────────┬────────┘    │  │
│  │  │  Repositories │  │          │           │             │  │
│  │  │  (Data Layer) │◄─┼──────────┼──┤ Scraper Engine  │    │  │
│  │  └───────────────┘  │          │  │ (CSS/Regex)     │    │  │
│  └─────────────────────┘          │  └────────┬────────┘    │  │
│                                    │           │             │  │
│                                    │  ┌────────▼────────┐    │  │
│                                    │  │ Booker Engine   │    │  │
│                                    │  │ (Form Submit)   │    │  │
│                                    │  └─────────────────┘    │  │
└────────────────────────────────────┴─────────────────────────┘  │
                                   │                              │
                                   ▼                              │
┌─────────────────────────────────────────────────────────────────┐
│                         External Services                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Museum APIs  │  │  NTFY.sh     │  │  Cloudflare/         │  │
│  │ (Web Scraping)│  │ (Alerts)     │  │  Bot Protection      │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Component Breakdown

| Component | Technology | Responsibility |
|-----------|------------|----------------|
| **UI Layer** | Kotlin + Jetpack Compose | User interface, state management |
| **ViewModels** | Kotlin Coroutines | Business logic coordination |
| **Rust Engine** | Rust 1.70+ | Core booking logic, HTTP, scraping |
| **JNI Bridge** | UniFFI 0.30.0 | Rust ↔ Kotlin interoperability |
| **HTTP Client** | wreq 6.0 | Browser impersonation, TLS |
| **Storage** | Android Keystore | Encrypted credential storage |

---

## 2. Rust Core Architecture

### 2.1 Module Structure

```
rust-engine/
├── src/
│   ├── lib.rs                 # Main library entry, UniFFI scaffolding
│   ├── http_client.rs         # MimicClient with wreq/BoringSSL
│   ├── scraper_engine.rs      # HTML parsing & availability extraction
│   ├── booker_engine.rs       # Form submission & booking flow
│   ├── config.rs              # Configuration structs & serialization
│   └── bin/
│       └── uniffi-bindgen.rs  # UniFFI bindings generator
├── Cargo.toml                 # Dependencies & build config
└── build.rs                   # Build script for UDL generation
```

### 2.2 Core Components

#### 2.2.1 BookingAgent (Orchestrator)
```rust
pub struct BookingAgent {
    state: Arc<RwLock<AgentInternalState>>,
    cancel_tx: Arc<RwLock<Option<oneshot::Sender<()>>>>,
}
```

**Responsibilities:**
- State management (running/cancelled)
- High-precision timing coordination
- Multi-phase execution (pre-warm → strike → booking)
- Error handling & recovery

**Concurrency Model:**
- Uses `tokio::sync::RwLock` for state access
- `oneshot::channel` for cancellation signals
- `tokio::select!` for concurrent operation handling

#### 2.2.2 MimicClient (HTTP Layer)
```rust
pub struct MimicClient {
    pub inner: Client,           // wreq Client
    pub profile_name: String,    // Active browser profile
}
```

**Features:**
- Browser fingerprint emulation via `wreq_util::Emulation`
- Cookie persistence across requests
- Automatic compression (gzip/brotli)
- SOCKS5 proxy support

**Security:**
- BoringSSL with `prefix-symbols` to avoid OpenSSL conflicts
- JA3 fingerprint rotation
- TLS 1.3 support

#### 2.2.3 ScraperEngine (Data Extraction)
```rust
pub struct ScraperEngine<'a> {
    pub client: &'a MimicClient,
    pub site: &'a Site,
}
```

**Capabilities:**
- CSS Selector-based parsing (`scraper` crate)
- Regex fallback for resilience
- AJAX request handling
- Multi-month availability checking

#### 2.2.4 BookerEngine (Automation)
```rust
pub struct BookerEngine<'a> {
    pub client: &'a MimicClient,
    pub site: &'a Site,
    pub museum: &'a Museum,
    pub credential: &'a Credential,
}
```

**Workflow:**
1. Navigate to booking URL
2. Detect and handle login forms
3. Extract CSRF/hidden form fields
4. Submit booking form
5. Verify success indicators

---

## 3. Data Architecture

### 3.1 Configuration Schema

```json
{
  "sites": {
    "site_key": {
      "name": "String",
      "baseurl": "URL",
      "availabilityendpoint": "Path",
      "digital": "bool",
      "physical": "bool",
      "location": "String",
      "loginform": {
        "usernamefield": "String",
        "passwordfield": "String",
        "submitbutton": "String",
        "csrfselector": "String"
      },
      "bookingform": {
        "actionurl": "String",
        "emailfield": "String",
        "fields": ["FormFieldConfig"]
      },
      "museums": {
        "museum_slug": {
          "name": "String",
          "slug": "String",
          "museumid": "String"
        }
      }
    }
  },
  "scheduled_runs": [{
    "id": "UUID",
    "sitekey": "String",
    "museumslug": "String",
    "droptime": "ISO8601 DateTime",
    "mode": "booking|alert",
    "credentialid": "String"
  }]
}
```

### 3.2 State Management

#### Rust Core State
```rust
pub struct AgentInternalState {
    pub is_running: bool,
    pub last_log: String,
    pub current_run_id: String,
}
```

#### Android UI State (ViewModel)
```kotlin
data class AgentUiState(
    val isRunning: Boolean = false,
    val lastLog: String = "Ready",
    val currentRunId: String = "",
    val progress: Float = 0f,
    val error: String? = null
)
```

### 3.3 Data Flow

```
User Input → ViewModel → UniFFI Bridge → Rust Engine → HTTP Request
                                              ↓
UI Update ← State Flow ← Callback/Polling ← Response Processing
```

---

## 4. Inter-Process Communication (IPC)

### 4.1 UniFFI Interface

**UDL Definition:**
```idl
namespace agent {
    record EngineStatus {
        boolean is_running;
        string last_log;
        string current_run_id;
    };

    [Error]
    enum EngineError {
        "ConfigError",
        "NetworkError",
        "BookingFailed",
        "InternalError"
    };
};

[TraitInterface]
interface BookingAgent {
    constructor();
    EngineStatus get_status();
    void stop();
    [Throws=EngineError]
    boolean start_strike(string config_json, string run_id);
};
```

### 4.2 JNI Bridge Details

| Aspect | Implementation |
|--------|----------------|
| **Binding Generator** | UniFFI 0.30.0 |
| **Async Support** | Tokio runtime integration |
| **Data Serialization** | JSON for complex objects |
| **Error Handling** | Rust Result → Kotlin Exception |
| **Memory Management** | Arc<RwLock<>> for shared state |

---

## 5. Security Architecture

### 5.1 Threat Model

| Threat | Mitigation |
|--------|------------|
| Credential Theft | Android Keystore + AES-256 |
| MITM Attacks | Certificate Pinning |
| Bot Detection | Browser Impersonation |
| Memory Dump | Rust memory safety |
| Reverse Engineering | ProGuard/R8 obfuscation |

### 5.2 Encryption Strategy

```
Credential Storage:
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Kotlin UI  │────►│ Android      │────►│ Encrypted    │
│   Input      │     │ Keystore     │     │ SharedPrefs  │
└──────────────┘     └──────────────┘     └──────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │ Hardware     │
                    │ Backed Key   │
                    └──────────────┘
```

### 5.3 Network Security

- **TLS**: BoringSSL with modern cipher suites
- **Certificate Pinning**: Optional for known endpoints
- **Proxy Support**: SOCKS5 for anonymization
- **Request Signing**: JA3 fingerprint emulation

---

## 6. Performance Architecture

### 6.1 Concurrency Model

```rust
// Tokio Runtime Configuration
tokio = { version = "1.43", features = ["full", "rt", "macros"] }
```

**Threading Strategy:**
- **Main Thread**: UI updates, state management
- **Tokio Runtime**: Async I/O, HTTP requests
- **Background Thread**: CPU-intensive parsing (optional)

### 6.2 Memory Management

| Component | Strategy |
|-----------|----------|
| HTTP Client | Connection pooling, keep-alive |
| HTML Parsing | Streaming parsing, limited buffer |
| State Updates | Arc<RwLock<>> for minimal locking |
| Cancellation | oneshot channels for clean shutdown |

### 6.3 Caching Strategy

- **TLS Sessions**: Cached for 30s (pre-warm phase)
- **DNS**: TTL-respecting with fallback
- **Cookies**: Persistent jar across requests
- **Configuration**: In-memory with JSON backup

---

## 7. Error Handling & Recovery

### 7.1 Error Hierarchy

```rust
#[derive(Debug, thiserror::Error)]
pub enum EngineError {
    #[error("Configuration error")]
    ConfigError,
    #[error("Network failure")]
    NetworkError,
    #[error("Booking failed")]
    BookingFailed,
    #[error("Internal engine error")]
    InternalError,
}
```

### 7.2 Recovery Strategies

| Error Type | Strategy | Retry |
|------------|----------|-------|
| Network Timeout | Exponential backoff | 3x |
| HTTP 5xx | Immediate retry | 2x |
| HTTP 429 (Rate Limit) | Delay + jitter | 5x |
| Parsing Error | Regex fallback | 1x |
| Auth Failure | Notify user | No retry |

---

## 8. Build Architecture

### 8.1 Cross-Compilation Matrix

| Target | Architecture | NDK Toolchain |
|--------|--------------|---------------|
| aarch64-linux-android | arm64-v8a | aarch64-linux-android21-clang |
| armv7-linux-androideabi | armeabi-v7a | armv7a-linux-androideabi21-clang |
| x86_64-linux-android | x86_64 | x86_64-linux-android21-clang |
| i686-linux-android | x86 | i686-linux-android21-clang |

### 8.2 Build Pipeline

```
Source Code
     │
     ▼
┌──────────────┐
│  cargo-ndk   │◄── Cross-compilation tool
└──────┬───────┘
       │
       ├──► arm64-v8a/librust_engine.so
       ├──► armeabi-v7a/librust_engine.so
       ├──► x86_64/librust_engine.so
       └──► x86/librust_engine.so
       │
       ▼
┌──────────────┐
│    Gradle    │◄── Android build system
└──────┬───────┘
       │
       ▼
    APK/AAB
```

---

## 9. Deployment Architecture

### 9.1 CI/CD Pipeline (GitHub Actions)

See: [CI/CD Documentation](../07-deployment/cicd-pipeline.md)

### 9.2 Distribution Channels

| Channel | Method | Audience |
|---------|--------|----------|
| GitHub Releases | Direct APK download | Power users |
| Google Play | Play Store | General public |
| Firebase App Distribution | Test groups | Beta testers |

---

## 10. Monitoring & Observability

### 10.1 Logging Strategy

```rust
// Structured logging with tracing
tracing::info!("Booking initiated", run_id = %run_id);
tracing::warn!("Retry attempt", attempt = %attempt);
tracing::error!("Booking failed", error = %e);
```

### 10.2 Metrics

- **Success Rate**: Booking success / total attempts
- **Latency**: Request round-trip times
- **Error Rate**: Errors per minute
- **Resource Usage**: Memory, CPU, Battery

---

## 11. Technology Stack

### 11.1 Core Dependencies

| Component | Technology | Version |
|-----------|------------|---------|
| HTTP Client | wreq | 6.0.0-rc.28 |
| TLS/SSL | BoringSSL (via wreq) | Bundled |
| Async Runtime | Tokio | 1.43 |
| HTML Parsing | scraper | 0.22 |
| FFI | UniFFI | 0.30.0 |
| Serialization | serde | 1.0 |

### 11.2 Android Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.1.0 |
| UI Framework | Jetpack Compose | 2025.02.00 |
| Architecture | MVVM | - |
| Dependency Injection | Hilt (optional) | 2.50+ |
| Networking | Rust Core (via JNI) | - |

---

## 12. Appendix

### 12.1 References

- [wreq Documentation](https://docs.rs/wreq/)
- [UniFFI User Guide](https://mozilla.github.io/uniffi-rs/)
- [Android NDK Guide](https://developer.android.com/ndk)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)

### 12.2 Related Documents

- [PRD](../01-product-requirements/prd.md)
- [UI Design](../03-ui-design/ui-specification.md)
- [API Reference](../04-api-reference/api-docs.md)
- [Testing Guide](../06-testing/testing-strategy.md)
