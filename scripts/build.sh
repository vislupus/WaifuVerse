#!/usr/bin/env bash
# Compile a debug APK:  ./scripts/build.sh          -> app/build/outputs/apk/debug/WaifuVerse-<version>-debug.apk
# Compile a release APK: ./scripts/build.sh release -> app/build/outputs/apk/release/WaifuVerse-<version>.apk
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
if [ "${1:-debug}" = "release" ]; then
  ./gradlew assembleRelease
  echo "APK: $(ls app/build/outputs/apk/release/*.apk)"
else
  ./gradlew assembleDebug
  echo "APK: $(ls app/build/outputs/apk/debug/*.apk)"
fi
