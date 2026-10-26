#!/usr/bin/env bash
# "Hot reload" from the terminal: rebuilds and reinstalls automatically whenever
# a source file changes, then relaunches the app on the phone.
# (For true in-place hot reload use Android Studio's Live Edit, see README.)
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
ADB="$(find_adb)"
echo "Watching for changes… (Ctrl+C to stop)"
./gradlew --continuous installDebug 2>&1 | while IFS= read -r line; do
  echo "$line"
  if [[ "$line" == *"BUILD SUCCESSFUL"* ]]; then
    "$ADB" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
    "$ADB" shell am start -n "$MAIN_ACTIVITY" >/dev/null 2>&1 || true
    echo ">>> reinstalled and relaunched $APP_ID"
  fi
done
