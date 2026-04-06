# GitHub Releases Guide

## Overview

This document describes the GitHub release workflow for the Booking Agent Android application (Rust + Kotlin).

## Version: 3.5.2
**Date:** April 6, 2026

---

## Automated Release Workflow

### Triggering a Release

Releases are automatically created when you push a version tag to GitHub:

```bash
# 1. Update version numbers in code
# - rust-engine/Cargo.toml: version = "3.5.2"
# - android-app/app/build.gradle.kts: versionName = "3.5.2"

# 2. Commit changes
git add .
git commit -m "Release v3.5.2"

# 3. Create and push tag
git tag v3.5.2
git push origin v3.5.2
```

### What Happens Automatically

When you push a tag matching `v*` (e.g., `v3.5.2`):

1. **Build Job Runs** (`build-release.yml`):
   - Sets up JDK 17 and Rust toolchain
   - Configures Android NDK r27
   - Builds both Debug and Release APKs
   - Creates a GitHub Release with attached APK files
   - Generates release notes from commit history

2. **Artifacts Published**:
   - `app-release.apk` - Optimized release build
   - `app-debug.apk` - Debug build for testing

---

## CI/CD Workflows

### 1. `release.yml` - Continuous Integration

**Triggers:**
- Push to `main` branch
- Pull requests to `main`

**Actions:**
- Builds debug APK
- Uploads as artifact (available for 90 days)
- Does NOT create a release

**Usage:** Download artifacts from GitHub Actions tab for testing.

### 2. `build-release.yml` - Release Automation

**Triggers:**
- Push of tags matching `v*` pattern

**Actions:**
- Builds both debug and release APKs
- Creates GitHub Release automatically
- Attaches APK files to release
- Generates release notes

---

## Manual Release Process

If you need to create a release manually:

### Step 1: Build Locally

```bash
cd android-app

# Build release APK
./gradlew assembleRelease

# Build debug APK
./gradlew assembleDebug
```

### Step 2: Create GitHub Release

1. Go to: `https://github.com/YOUR_REPO/releases`
2. Click "Draft a new release"
3. Tag version: `v3.5.2`
4. Release title: `Booking Agent v3.5.2`
5. Upload APK files:
   - `app/build/outputs/apk/release/app-release.apk`
   - `app/build/outputs/apk/debug/app-debug.apk`
6. Add release notes (see template below)
7. Publish release

---

## Release Notes Template

```markdown
## Release v3.5.2

### New Features
- Feature description here
- Another feature

### Bug Fixes
- Fixed issue with...
- Resolved crash when...

### Performance Improvements
- Improved startup time by X%
- Reduced memory usage

### Security Updates
- Updated dependencies
- Fixed vulnerability in...

### Known Issues
- Issue description (workaround if available)

### Installation
Download the APK and install on your Android device (API 26+).
```

---

## Version Management

### Semantic Versioning

Format: `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes, incompatible API
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible

### Updating Versions

Update in these files before tagging:

| File | Field | Example |
|------|-------|---------|
| `rust-engine/Cargo.toml` | `version` | `"3.5.2"` |
| `android-app/app/build.gradle.kts` | `versionName` | `"3.5.2"` |
| `android-app/app/build.gradle.kts` | `versionCode` | `1` (integer) |

---

## Signing Release Builds

For production releases, configure signing:

### 1. Generate Keystore

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias bookingagent \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

### 2. Configure GitHub Secrets

Add these secrets to your repository:

| Secret | Description |
|--------|-------------|
| `SIGNING_KEYSTORE` | Base64-encoded keystore file |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias name |
| `SIGNING_KEY_PASSWORD` | Key password |

### 3. Update build.gradle.kts

See `docs/deployment/deployment.md` for detailed signing configuration.

---

## Troubleshooting

### Build Fails on GitHub Actions

1. Check the Actions tab for error logs
2. Verify `Cargo.lock` is committed
3. Ensure NDK version matches configuration
4. Clear cache: Settings → Actions → Clear cache

### APK Not Attached to Release

1. Check workflow run completed successfully
2. Verify tag format matches `v*` pattern
3. Check workflow has `contents: write` permission
4. Review workflow logs for errors

### Version Mismatch

Ensure both Cargo.toml and build.gradle.kts have matching versions before tagging.

---

## Related Documentation

- [Architecture Specification](docs/architecture/architecture.md)
- [Implementation Guide](docs/implementation/implementation_guide.md)
- [Deployment Guide](docs/deployment/deployment.md)
- [Testing Plan](docs/testing/testing-plan.md)

---

## Support

For issues or questions:
- Open an issue on GitHub
- Check existing documentation
- Review workflow logs for debugging
