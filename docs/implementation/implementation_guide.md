# Implementation Guide
## Step-by-Step Development Guide

**Version:** 3.5.2  
**Date:** April 6, 2026  
**Difficulty:** Intermediate-Advanced

---

## 1. Development Environment Setup

### 1.1 Prerequisites

**Hardware:**
- 8GB+ RAM (16GB recommended)
- 50GB free disk space
- x86_64 or ARM64 processor

**Software:**
- macOS 12+ or Ubuntu 22.04+ or Windows 11+
- Android Studio Ladybug (2024.2.1) or newer

### 1.2 Install Rust Toolchain

```bash
# Install Rust via rustup
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source $HOME/.cargo/env

# Install required targets
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add x86_64-linux-android
rustup target add i686-linux-android

# Install cargo-ndk for cross-compilation
cargo install cargo-ndk

# Verify installation
rustc --version  # Should be 1.70+
cargo --version
```

### 1.3 Install Android NDK

```bash
# Via Android Studio SDK Manager
# Tools → SDK Manager → SDK Tools → NDK (Side by side)
# Select version 25.2.9519653

# Or via command line
sdkmanager "ndk;25.2.9519653"

# Set environment variables
export ANDROID_SDK_ROOT=$HOME/Library/Android/sdk  # macOS
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/25.2.9519653
export PATH=$PATH:$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin
```

### 1.4 System Dependencies

**macOS:**
```bash
# Install Homebrew if not present
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install dependencies
brew install cmake perl pkg-config llvm
```

**Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install -y build-essential cmake perl pkg-config libclang-dev musl-tools git
```

**Windows:**
- Install Visual Studio 2022 with C++ tools
- Install LLVM
- Use WSL2 for Rust builds (recommended)

---

## 2. Project Structure

### 2.1 Directory Layout

```
rust-app/
├── rust-engine/                    # Rust core library
│   ├── Cargo.toml
│   ├── build.rs
│   └── src/
│       ├── lib.rs                 # Main library entry
│       ├── http_client.rs         # HTTP client with impersonation
│       ├── scraper_engine.rs      # HTML scraping logic
│       ├── booker_engine.rs       # Booking automation
│       ├── config.rs              # Configuration structs
│       ├── agent.udl              # UniFFI interface definition
│       └── bin/
│           └── uniffi-bindgen.rs  # Bindings generator
│
├── android-app/                    # Android application
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle.kts
│       └── src/
│           └── main/
│               ├── AndroidManifest.xml
│               ├── java/           # Kotlin source
│               │   └── com/apptcheck/agent/
│               │       ├── MainActivity.kt
│               │       ├── ui/
│               │       │   ├── screens/
│               │       │   ├── components/
│               │       │   └── theme/
│               │       ├── viewmodel/
│               │       └── data/
│               └── jniLibs/        # Compiled Rust libs (auto-generated)
│
└── docs/                          # Documentation
```

### 2.2 Create Project

```bash
# Create root directory
mkdir rust-app && cd rust-app

# Initialize Rust library
cargo new --lib rust-engine
cd rust-engine

# Add required dependencies to Cargo.toml
# (See Architecture Spec for full Cargo.toml)

cd ..

# Create Android project
# File → New → New Project → Empty Compose Activity
# Name: android-app
# Package: com.apptcheck.agent
# Language: Kotlin
# Minimum SDK: API 26
```

---

## 3. Rust Core Implementation

### 3.1 Cargo.toml Configuration

```toml
[package]
name = "rust-engine"
version = "3.5.2"
edition = "2021"

[lib]
crate-type = ["cdylib", "staticlib"]
name = "rust_engine"

[[bin]]
name = "uniffi-bindgen"
path = "src/bin/uniffi-bindgen.rs"
required-features = ["uniffi/cli"]

[dependencies]
tokio = { version = "1.43", features = ["full", "rt", "macros"] }
futures = "0.3"
wreq = { version = "6.0.0-rc.28", features = ["boringssl", "socks5", "gzip", "brotli", "prefix-symbols"] }
wreq-util = { version = "3.0.0-rc.10", features = ["emulation"] }
uniffi = { version = "0.30.0", features = ["tokio", "cli"] }
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"
chrono = { version = "0.4.40", features = ["serde"] }
scraper = "0.22"
uuid = { version = "1.16", features = ["v4"] }
rand = "0.9"
tracing = "0.1"
anyhow = "1.0"
thiserror = "2.0"
regex = "1.11"

[build-dependencies]
uniffi = { version = "0.30.0", features = ["build"] }

[profile.release]
opt-level = "s"
lto = true
strip = true
codegen-units = 1
panic = "abort"
```

### 3.2 Build Script (build.rs)

```rust
fn main() {
    uniffi::generate_scaffolding("src/agent.udl").unwrap();
}
```

### 3.3 UniFFI Interface (agent.udl)

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

### 3.4 Core Library (lib.rs)

See Architecture Spec for full implementation. Key points:

1. **State Management:**
```rust
pub struct BookingAgent {
    state: Arc<RwLock<AgentInternalState>>,
    cancel_tx: Arc<RwLock<Option<oneshot::Sender<()>>>>,
}
```

2. **Async Export:**
```rust
#[uniffi::export]
impl BookingAgent {
    pub async fn start_strike(&self, config_json: String, run_id: String) 
        -> Result<bool, EngineError> { ... }
}
```

3. **Cancellation:**
```rust
pub fn stop(&self) {
    let mut tx_lock = self.cancel_tx.blocking_write();
    if let Some(tx) = tx_lock.take() {
        let _ = tx.send(());
    }
}
```

### 3.5 HTTP Client (http_client.rs)

```rust
use wreq::Client;
use wreq_util::Emulation;
use anyhow::Result;
use std::time::Duration;
use rand::seq::SliceRandom;

pub struct MimicClient {
    pub inner: Client,
    pub profile_name: String,
}

impl MimicClient {
    pub fn new(timeout_ms: u64) -> Result<Self> {
        let profiles = vec![
            (Emulation::Chrome123, "Chrome 123"),
            (Emulation::Safari17_2_1, "Safari 17.2.1"),
            (Emulation::Edge122, "Edge 122"),
            (Emulation::Chrome120, "Chrome 120"),
            (Emulation::Firefox128, "Firefox 128"),
        ];

        let mut rng = rand::thread_rng();
        let (emulation, name) = profiles.choose(&mut rng).unwrap();

        let client = Client::builder()
            .emulation(*emulation)
            .timeout(Duration::from_millis(timeout_ms))
            .cookie_store(true)
            .gzip(true)
            .brotli(true)
            .build()?;

        Ok(Self {
            inner: client,
            profile_name: name.to_string(),
        })
    }
}
```

### 3.6 Build Rust for Android

```bash
cd rust-engine

# Build for all architectures
cargo ndk     -t armeabi-v7a     -t arm64-v8a     -t x86     -t x86_64     -p 25.2.9519653     -o ../android-app/app/src/main/jniLibs     build --release

# Verify outputs
ls -la ../android-app/app/src/main/jniLibs/
# Should show: armeabi-v7a/, arm64-v8a/, x86/, x86_64/
```

---

## 4. Android Implementation

### 4.1 Project build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "8.8.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.mozilla.rust-android-gradle.rust-android") version "0.9.6" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
```

### 4.2 App build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.mozilla.rust-android-gradle.rust-android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.apptcheck.agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.apptcheck.agent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "3.5.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

val rustExt = extensions.getByType(org.mozilla.rustandroidgradle.rust.RustExtension::class.java)
rustExt.setModule("../../rust-engine")
rustExt.setLibname("rust_engine")
rustExt.setTargets(listOf("arm", "arm64", "x86", "x86_64"))
rustExt.setProfile("release")

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("net.java.dev.jna:jna:5.16.0@aar")
}
```

### 4.3 Generate UniFFI Bindings

```bash
cd rust-engine

# Generate Kotlin bindings
cargo run --bin uniffi-bindgen generate src/agent.udl --language kotlin --out-dir ../android-app/app/src/main/java/com/apptcheck/agent/

# This creates: agent.kt
```

### 4.4 Kotlin Wrapper Class

```kotlin
package com.apptcheck.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class BookingAgentWrapper {
    private val agent = BookingAgent()

    suspend fun getStatus(): EngineStatus = withContext(Dispatchers.IO) {
        agent.getStatus()
    }

    suspend fun startStrike(config: AppConfig, runId: String): Boolean = 
        withContext(Dispatchers.IO) {
            val configJson = Json.encodeToString(config)
            agent.startStrike(configJson, runId)
        }

    fun stop() {
        agent.stop()
    }
}
```

### 4.5 ViewModel Implementation

```kotlin
package com.apptcheck.agent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptcheck.agent.BookingAgentWrapper
import com.apptcheck.agent.data.model.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BookingViewModel : ViewModel() {
    private val agent = BookingAgentWrapper()

    private val _uiState = MutableStateFlow(BookingUiState())
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    fun startStrike(config: AppConfig, runId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true)

            try {
                val success = agent.startStrike(config, runId)
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    lastResult = if (success) "Success" else "No availability"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    error = e.message
                )
            }
        }
    }

    fun stop() {
        agent.stop()
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    override fun onCleared() {
        super.onCleared()
        agent.stop()
    }
}

data class BookingUiState(
    val isRunning: Boolean = false,
    val lastResult: String = "",
    val error: String? = null
)
```

### 4.6 Compose UI Screen

```kotlin
package com.apptcheck.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apptcheck.agent.viewmodel.BookingViewModel

@Composable
fun HomeScreen(
    viewModel: BookingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Agent Status",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.isRunning) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Running...")
                } else {
                    Text("Ready")
                }

                uiState.lastResult.takeIf { it.isNotEmpty() }?.let {
                    Text("Last result: $it")
                }

                uiState.error?.let {
                    Text(
                        text = "Error: $it",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Control Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { 
                    // Start with sample config
                    viewModel.startStrike(sampleConfig, "run-001")
                },
                enabled = !uiState.isRunning
            ) {
                Text("Start Strike")
            }

            Button(
                onClick = { viewModel.stop() },
                enabled = uiState.isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop")
            }
        }
    }
}
```

---

## 5. Testing

### 5.1 Unit Tests (Rust)

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_agent_creation() {
        let agent = BookingAgent::new();
        let status = agent.get_status().await;
        assert!(!status.is_running);
    }

    #[tokio::test]
    async fn test_cancellation() {
        let agent = BookingAgent::new();

        let agent_ref = Arc::new(agent);
        let agent_clone = agent_ref.clone();

        let handle = tokio::spawn(async move {
            // Long-running operation
            tokio::time::sleep(Duration::from_secs(10)).await;
        });

        // Cancel immediately
        agent_clone.stop();

        // Should complete quickly due to cancellation
        let result = tokio::time::timeout(
            Duration::from_secs(1),
            handle
        ).await;

        assert!(result.is_ok());
    }
}
```

### 5.2 Integration Tests

```kotlin
@Test
fun testBookingAgentWrapper() = runTest {
    val wrapper = BookingAgentWrapper()
    val status = wrapper.getStatus()

    assertNotNull(status)
    assertFalse(status.isRunning)
}
```

---

## 6. Debugging

### 6.1 Rust Debugging

```bash
# Build with debug symbols
cargo build --target aarch64-linux-android

# Use adb to debug
adb logcat -s "RustStdout"
```

### 6.2 Android Debugging

```kotlin
// Add logging
Log.d("BookingAgent", "Status: ${status.isRunning}")

// Use Timber for better logging
Timber.d("Starting strike with config: $config")
```

---

## 7. Performance Optimization

### 7.1 Rust Optimizations

- Use `release` profile with `opt-level = "s"` (size optimization)
- Enable LTO (Link Time Optimization)
- Strip symbols in release builds

### 7.2 Android Optimizations

- Use R8 code shrinking
- Enable resource shrinking
- Profile startup with Jetpack Macrobenchmark

---

**Next Document:** [Testing Strategy](../06-testing/testing-strategy.md)
