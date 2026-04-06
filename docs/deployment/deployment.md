# Deployment Guide
## Build, Release & Distribution

**Version:** 3.5.2  
**Date:** April 6, 2026  

---

## 1. Release Checklist

### 1.1 Pre-Release

- [ ] All tests passing
- [ ] Version numbers updated:
  - `rust-engine/Cargo.toml`: `version = "3.5.2"`
  - `android-app/app/build.gradle.kts`: `versionName = "3.5.2"`
- [ ] CHANGELOG.md updated
- [ ] Documentation reviewed
- [ ] Security audit completed
- [ ] Performance benchmarks met

### 1.2 Build Verification

```bash
# 1. Clean build
cd rust-engine && cargo clean
cd ../android-app && ./gradlew clean

# 2. Run full test suite
cd rust-engine && cargo test --all-features
cd ../android-app && ./gradlew test

# 3. Build release artifacts
cd rust-engine
cargo ndk -t armeabi-v7a -t arm64-v8a -t x86 -t x86_64 -o ../android-app/app/src/main/jniLibs build --release

cd ../android-app
./gradlew assembleRelease
```

---

## 2. Signing Configuration

### 2.1 Create Keystore

```bash
# Generate release keystore
keytool -genkey -v     -keystore release.keystore     -alias bookingagent     -keyalg RSA     -keysize 2048     -validity 10000

# Convert to base64 for CI
base64 -i release.keystore -o release.keystore.base64
```

### 2.2 Configure Signing

**android-app/app/build.gradle.kts:**
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("SIGNING_KEYSTORE") ?: "release.keystore")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### 2.3 GitHub Secrets

Add to repository settings:

| Secret | Value |
|--------|-------|
| `SIGNING_KEYSTORE` | Base64-encoded keystore |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

---

## 3. Distribution Channels

### 3.1 GitHub Releases (Primary)

**Automated via GitHub Actions:**

```yaml
- name: Create Release
  uses: softprops/action-gh-release@v1
  with:
    files: |
      android-app/app/build/outputs/apk/release/*.apk
      android-app/app/build/outputs/apk/debug/*.apk
    generate_release_notes: true
```

**Manual Process:**
1. Go to GitHub → Releases → Draft New Release
2. Tag version: `v3.5.2`
3. Upload APK files
4. Add release notes
5. Publish

### 3.2 Google Play Store

**Prerequisites:**
- Google Play Developer account ($25)
- Privacy policy
- App screenshots
- Feature graphic

**Upload Steps:**
1. Build AAB (Android App Bundle):
   ```bash
   ./gradlew bundleRelease
   ```
2. Go to Play Console → Create App
3. Upload AAB to Internal Testing
4. Fill store listing
5. Roll out to production

### 3.3 Firebase App Distribution

**For Beta Testing:**

```bash
# Using Firebase CLI
firebase appdistribution:distribute app-release.apk     --app 1:1234567890:android:abc123def456     --groups "beta-testers"     --release-notes "Bug fixes and performance improvements"
```

---

## 4. CI/CD Pipeline

### 4.1 Complete Workflow

See `.github/workflows/build-release.yml` for full configuration.

**Pipeline Stages:**

```
Push/Tag
    │
    ▼
┌──────────────┐
│ Build Rust   │ (4 parallel jobs)
│ Libraries    │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Build Android│
│ APK          │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Run Tests    │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Sign Release │ (if tag)
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Create GitHub│
│ Release      │
└──────────────┘
```

### 4.2 Environment-Specific Builds

**Development:**
```bash
./gradlew assembleDebug
```

**Staging:**
```bash
./gradlew assembleRelease     -PversionNameSuffix="-beta"     -PapplicationIdSuffix=".beta"
```

**Production:**
```bash
./gradlew assembleRelease
```

---

## 5. Version Management

### 5.1 Semantic Versioning

Format: `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes

### 5.2 Version Bump Script

```bash
#!/bin/bash
# bump-version.sh

NEW_VERSION=$1

# Update Cargo.toml
sed -i '' "s/^version = ".*"/version = "$NEW_VERSION"/" rust-engine/Cargo.toml

# Update Android
sed -i '' "s/versionName = ".*"/versionName = "$NEW_VERSION"/" android-app/app/build.gradle.kts

# Update versionCode (convert to integer)
VERSION_CODE=$(echo $NEW_VERSION | tr -d '.' | sed 's/^0*//')
sed -i '' "s/versionCode = .*/versionCode = $VERSION_CODE/" android-app/app/build.gradle.kts

echo "Version bumped to $NEW_VERSION (code: $VERSION_CODE)"
```

---

## 6. Monitoring & Analytics

### 6.1 Crash Reporting

**Firebase Crashlytics:**
```kotlin
// Add to Application class
FirebaseCrashlytics.getInstance().setCustomKey("rust_version", "3.5.2")
```

**Sentry (Alternative):**
```kotlin
SentryAndroid.init(this) { options ->
    options.dsn = "https://key@sentry.io/project"
    options.release = "com.apptcheck.agent@3.5.2"
}
```

### 6.2 Performance Monitoring

```kotlin
// Trace Rust operations
val trace = FirebasePerformance.startTrace("booking_operation")
// ... run operation
trace.stop()
```

### 6.3 Analytics

```kotlin
// Log events
FirebaseAnalytics.getInstance(this).logEvent("booking_success") {
    param("museum", museumName)
    param("duration", durationMs)
}
```

---

## 7. Rollback Strategy

### 7.1 Quick Rollback

If critical issue detected:

1. **GitHub Releases:**
   - Edit release → Mark as pre-release
   - Upload previous version APK

2. **Play Store:**
   - Go to Release → Production
   - Promote previous version
   - Halt current rollout

### 7.2 Hotfix Process

```bash
# 1. Create hotfix branch
git checkout -b hotfix/v3.5.3 v3.5.2

# 2. Fix issue
# ... edit files ...

# 3. Bump version
./bump-version.sh 3.5.3

# 4. Commit and tag
git add .
git commit -m "Hotfix: Fix critical bug"
git tag v3.5.3
git push origin v3.5.3

# 5. CI automatically builds and releases
```

---

## 8. Security Considerations

### 8.1 Pre-Release Security Checklist

- [ ] No hardcoded credentials
- [ ] ProGuard/R8 obfuscation enabled
- [ ] Network security config validated
- [ ] Certificate pinning tested
- [ ] Root detection enabled (optional)
- [ ] Secure logging (no PII)

### 8.2 Dependency Updates

```bash
# Check for vulnerabilities
cargo audit
cd android-app && ./gradlew dependencyCheckAnalyze

# Update dependencies
cargo update
cd android-app && ./gradlew dependencyUpdates
```

---

## 9. Documentation Updates

### 9.1 Release Notes Template

```markdown
## Release v3.5.2

### New Features
- Feature 1 description
- Feature 2 description

### Bug Fixes
- Fixed issue with...
- Resolved crash when...

### Performance
- Improved startup time by 20%
- Reduced memory usage

### Security
- Updated dependencies
- Fixed vulnerability in...

### Known Issues
- Issue 1 (workaround available)
```

### 9.2 User Communication

- In-app changelog
- Email notifications (if applicable)
- Social media announcements

---

## 10. Post-Release

### 10.1 Monitoring

- Monitor crash rates for 24-48 hours
- Watch for unusual error patterns
- Check performance metrics

### 10.2 Feedback Collection

- Review user feedback
- Check support tickets
- Monitor app store reviews

### 10.3 Retrospective

- What went well?
- What could be improved?
- Action items for next release

---

**End of Documentation Suite**
