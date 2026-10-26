#!/usr/bin/env bash
# Build, install on the connected phone (USB debugging on) and launch the app.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
ADB="$(find_adb)"
./gradlew installDebug
"$ADB" shell am start -n "$MAIN_ACTIVITY" >/dev/null
echo "Launched $APP_ID"
