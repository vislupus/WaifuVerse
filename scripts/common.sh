#!/usr/bin/env bash
# Shared helpers for the dev scripts.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.vislupus.waifuverse"
MAIN_ACTIVITY="$APP_ID/.MainActivity"
cd "$ROOT"

# Locate adb (Android SDK platform-tools) even when it is not on PATH.
find_adb() {
  if command -v adb >/dev/null 2>&1; then echo adb; return; fi
  for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "$LOCALAPPDATA/Android/Sdk"; do
    [ -n "$d" ] && [ -x "$d/platform-tools/adb" ] && { echo "$d/platform-tools/adb"; return; }
  done
  echo "adb not found. Install Android SDK platform-tools or add it to PATH." >&2
  exit 1
}
