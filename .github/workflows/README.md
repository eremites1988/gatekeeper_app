# GitHub Actions Workflows Documentation

## Workflows

### 1. **build-apk.yml** - Continuous Integration Build
Automatically builds debug and release APKs on every push and pull request.

#### Triggers
- **Push to branches:** `main`, `develop`, `claude/**`
- **Pull requests:** to `main` or `develop`
- **Manual trigger** (workflow_dispatch) with build type selection

#### Jobs
- **build**: Compiles debug and release APKs
  - Caches Gradle dependencies for faster builds
  - Generates build artifacts
  - Creates a build summary
  
- **test**: Runs unit tests
  - Executes `testDebugUnitTest` task
  - Publishes test results as PR comments

#### Artifacts
- **Debug APK**: Ready to install on devices/emulators
- **Release APK**: Unsigned, needs to be signed before distribution
- **Retention**: 30 days

#### Build Summary
Each workflow run generates a summary showing:
- Build number and branch
- Commit hash
- Generated artifacts

---

### 2. **release-apk.yml** - Release Build with Signing
Builds and signs release APKs when a version tag is pushed.

#### Triggers
- **Version tags**: Push a tag matching `v*.*.*` (e.g., `v1.0.0`)
- **Manual trigger** (workflow_dispatch) with optional version tag

#### Jobs
- **build-and-sign**: Builds release APK and signs it (if keystore is configured)
  - Decodes keystore from secrets
  - Signs with SHA256withRSA algorithm
  - Verifies signature
  - Creates GitHub Release with APK attached

#### Signing Configuration (Secrets)
Set these secrets in GitHub to enable signing:

1. **KEYSTORE_BASE64** (Required)
   - Base64-encoded keystore file
   - Generate: `base64 -i ~/.android/gatekeeper.keystore | pbcopy` (macOS) or `certutil -encode keystore.jks keystore.txt` (Windows)

2. **KEYSTORE_PASSWORD** (Required)
   - Password for the keystore

3. **KEY_ALIAS** (Optional)
   - Alias within the keystore (default: `gatekeeper`)

4. **KEY_PASSWORD** (Optional)
   - Password for the key (defaults to keystore password)

#### Artifacts
- **Signed Release APK**: Ready for distribution
- **Retention**: 90 days
- **GitHub Release**: Automatically created with APK attached

---

## How to Use

### Manual Build (Local)
```bash
# Debug build
./gradlew :app:assembleDebug

# Release build (unsigned)
./gradlew :app:assembleRelease

# Both using build script
chmod +x build.sh
./build.sh all
```

### Create a Release
1. Create and push a version tag:
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```

2. The workflow will:
   - Build the release APK
   - Sign it (if secrets are configured)
   - Create a GitHub Release
   - Attach the APK

### View Builds
- **GitHub Actions**: Go to your repository → Actions tab
- **Artifacts**: Download from the workflow run summary (valid for 30 days)
- **Releases**: Go to Releases tab for version releases

---

## Setting Up Signing

### 1. Create a Keystore (if you don't have one)
```bash
keytool -genkey -v -keystore ~/.android/gatekeeper.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias gatekeeper
```

### 2. Encode Keystore to Base64
**macOS/Linux:**
```bash
base64 -i ~/.android/gatekeeper.keystore | pbcopy
```

**Windows (PowerShell):**
```powershell
$keystore = [Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.android\gatekeeper.keystore"))
Set-Clipboard -Value $keystore
```

### 3. Add GitHub Secrets
1. Go to: Repository Settings → Secrets and variables → Actions
2. Click "New repository secret"
3. Add:
   - Name: `KEYSTORE_BASE64` → Value: (paste the base64 string)
   - Name: `KEYSTORE_PASSWORD` → Value: (your keystore password)
   - Name: `KEY_ALIAS` → Value: `gatekeeper`
   - Name: `KEY_PASSWORD` → Value: (key password, or same as keystore password)

---

## Best Practices

✅ **Do:**
- Use semantic versioning for tags (`v1.0.0`, `v1.0.1`, etc.)
- Configure signing secrets for production releases
- Review test results before merging PRs
- Monitor workflow runs for failures

❌ **Don't:**
- Commit keystore files to the repository
- Store plain-text passwords in code
- Push unsigned APKs to production
- Ignore test failures

---

## Troubleshooting

### Build fails with "Gradle wrapper not executable"
The workflow automatically handles this, but locally run:
```bash
chmod +x gradlew
```

### Release APK is unsigned
Ensure signing secrets are configured. Without them, the workflow generates an unsigned APK.

### Tests are failing
Check the "Run Tests" section in the workflow summary. Test results are published as comments on the PR.

### APK artifacts not found
- Verify build succeeded (check logs)
- Wait for the workflow to complete
- Artifacts are retained for 30 days

---

## Environment Details

- **Java Version**: 17
- **Android SDK**: 35 (compileSdk)
- **Min SDK**: 26
- **Target SDK**: 35
- **Build System**: Gradle with Kotlin DSL
- **Signing Algorithm**: SHA256withRSA
- **Digest Algorithm**: SHA-256
