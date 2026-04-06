plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.mozilla.rust-android-gradle.rust-android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.apptcheck.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.apptcheck.agent"
        minSdk = 26 
        targetSdk = 34
        versionCode = 1
        versionName = "3.2.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// FIX: Using explicit extension configuration to avoid "Unresolved reference 'rust'"
// and using backticks or explicit this to avoid the 'module' collision.
extensions.configure<org.mozilla.rustandroidgradle.rust.RustContext> {
    // Escaping 'module' with backticks prevents collision with DependencyHandler.module
    this.`module` = "../../rust-engine"
    this.libname = "rust_engine"
    this.targets = listOf("arm", "arm64", "x86", "x86_64")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    
    // Standard string notation to avoid deprecation warnings
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}

// Ensure Rust builds before JNI/Java compilation starts
tasks.whenTaskAdded {
    if (name == "javaPreCompileDebug" || name == "javaPreCompileRelease") {
        dependsOn("cargoBuild")
    }
}
