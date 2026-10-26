#!/usr/bin/env bash
# Compile a debug APK:  ./scripts/build.sh          -> app/build/outputs/apk/debug/app-debug.apk
# Compile a release APK: ./scripts/build.sh release -> app/build/outputs/apk/release/app-release.apk
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
if [ "${1:-debug}" = "release" ]; then
  ./gradlew assembleRelease
  echo "APK: app/build/outputs/apk/release/app-release.apk"
else
  ./gradlew assembleDebug
  echo "APK: app/build/outputs/apk/debug/app-debug.apk"
fi
