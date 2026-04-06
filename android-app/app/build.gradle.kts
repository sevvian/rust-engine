//--- START OF FILE rust-app-main/android-app/app/build.gradle.kts ---
import org.mozilla.rustandroidgradle.rust.RustExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.mozilla.rust-android-gradle.rust-android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose") // New for Kotlin 2.0+
}

android {
    namespace = "com.apptcheck.agent"
    compileSdk = 35 // Updated for 2026

    defaultConfig {
        applicationId = "com.apptcheck.agent"
        minSdk = 26 
        targetSdk = 35
        versionCode = 1
        versionName = "3.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // Enabled for 2026 production
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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

    // composeOptions removed: Compiler version now matches Kotlin version automatically

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * Rust Extension Configuration
 * Uses explicit setters for Gradle 9.x compatibility
 */
val rustExt = extensions.getByType(RustExtension::class.java)
rustExt.setModule("../../rust-engine")
rustExt.setLibname("rust_engine")
rustExt.setTargets(listOf("arm", "arm64", "x86", "x86_64"))

dependencies {
    // 2026 BOM Release
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("net.java.dev.jna:jna:5.16.0@aar")
    implementation("androidx.security:security-crypto:1.1.0")
}

// Integration: Ensure Rust builds before Java/Kotlin
tasks.whenTaskAdded {
    if (name == "javaPreCompileDebug" || name == "javaPreCompileRelease") {
        dependsOn("cargoBuild")
    }
}
//--- END OF FILE rust-app-main/android-app/app/build.gradle.kts ---
