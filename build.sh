#!/usr/bin/env bash
# Convenience wrapper around the Gradle build tasks.
#
#   ./build.sh debug     # build the debug APK
#   ./build.sh release   # build the unsigned release APK
#   ./build.sh all       # build both (default)
#   ./build.sh test      # run unit tests
#   ./build.sh clean     # clean build outputs
#
# Requires a local Android SDK (set ANDROID_HOME / sdk.dir in local.properties)
# and internet access to Google's Maven repository.
set -euo pipefail

cd "$(dirname "$0")"
chmod +x gradlew 2>/dev/null || true

target="${1:-all}"

case "$target" in
  debug)
    ./gradlew :app:assembleDebug
    echo "Debug APK: app/build/outputs/apk/debug/"
    ;;
  release)
    ./gradlew :app:assembleRelease
    echo "Unsigned release APK: app/build/outputs/apk/release/"
    ;;
  all)
    ./gradlew :app:assembleDebug :app:assembleRelease
    echo "APKs under app/build/outputs/apk/"
    ;;
  test)
    ./gradlew :app:testDebugUnitTest
    ;;
  clean)
    ./gradlew clean
    ;;
  *)
    echo "Usage: $0 {debug|release|all|test|clean}" >&2
    exit 1
    ;;
esac
