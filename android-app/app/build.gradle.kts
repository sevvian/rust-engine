plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.mozilla.rust-android-gradle.rust-android")
    id("org.jetbrains.kotlin.plugin.serialization")
    // ADDED: Compose Compiler Gradle plugin for Kotlin 2.1.0
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.apptcheck.agent"
    // UPDATED: compileSdk to 35 (Android 15)
    compileSdk = 35

    defaultConfig {
        applicationId = "com.apptcheck.agent"
        // UPDATED: minSdk to 23 (Android 6.0) - required by newer AndroidX libraries
        minSdk = 26
        // UPDATED: targetSdk to 35
        targetSdk = 35
        versionCode = 1
        // UPDATED: versionName to match Cargo.toml
        versionName = "3.5.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // UPDATED: Java 17 for better compatibility with Kotlin 2.1.0
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // UPDATED: JVM target 17
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // REMOVED: composeOptions block - no longer needed with Compose Compiler Gradle plugin
    // The plugin handles compiler version automatically

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ADDED: Compose Compiler configuration block (optional)
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
}

/**
 * FIXED: Rust Extension Configuration
 * Uses the correct CargoExtension class from com.nishtahir package
 */
// Configure the Rust extension using the standard DSL
// The plugin adds the 'rust' extension automatically
configure<com.nishtahir.CargoExtension> {
    module = "../../rust-engine"
    libname = "rust_engine"
    targets = listOf("arm", "arm64", "x86", "x86_64")
    profile = "release"
}

dependencies {
    // UPDATED: Compose BOM to 2025.02.00 (stable version compatible with Kotlin 2.1.0)
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)

    // UPDATED: AndroidX core to latest stable
    implementation("androidx.core:core-ktx:1.15.0")
    // UPDATED: Lifecycle to latest stable
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // UPDATED: Activity Compose to 1.10.0
    implementation("androidx.activity:activity-compose:1.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // UPDATED: Navigation Compose to latest stable
    implementation("androidx.navigation:navigation-compose:2.8.6")

    // UPDATED: Kotlinx Serialization to 1.8.0 (compatible with Kotlin 2.1.0)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // UPDATED: Coroutines to 1.10.1
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // UPDATED: JNA to 5.16.0
    implementation("net.java.dev.jna:jna:5.16.0@aar")

    // UPDATED: Security Crypto to stable 1.1.0-alpha06 -> 1.1.0-alpha06 (latest is 1.1.0-alpha06)
    // Keeping as is since 1.1.0-alpha06 is the latest for this specific library
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Debug implementations
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.whenTaskAdded {
    if (name == "javaPreCompileDebug" || name == "javaPreCompileRelease") {
        dependsOn("cargoBuild")
    }
}
