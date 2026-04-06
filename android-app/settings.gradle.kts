import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// --- FIX FOR GITHUB ACTIONS NDK ERROR ---
// If running on CI, automatically point local.properties to the pre-installed NDK r27.
// This prevents the "NDK is not installed" eager-evaluation crash from the Rust plugin.
if (System.getenv("GITHUB_ACTIONS") == "true") {
    val androidHome = System.getenv("ANDROID_HOME") ?: "/usr/local/lib/android/sdk"
    val ndkPath = "$androidHome/ndk/27.0.12077973" 
    val localProps = file("local.properties")
    
    val props = Properties()
    if (localProps.exists()) {
        localProps.inputStream().use { props.load(it) }
    }
    props.setProperty("sdk.dir", androidHome)
    props.setProperty("ndk.dir", ndkPath)
    localProps.outputStream().use { props.store(it, "Auto-configured by GitHub Actions") }
}
// ----------------------------------------

rootProject.name = "rust-app"
include(":app")
