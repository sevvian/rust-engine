//--- START OF FILE rust-app-main/android-app/build.gradle.kts ---
// Top-level build file for 2026 Rust-Android Stack
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.mozilla.rust-android-gradle.rust-android") version "0.9.6" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
//--- END OF FILE rust-app-main/android-app/build.gradle.kts ---
