#!/usr/bin/env bash
# Show the app's log output (crashes, errors) from the connected phone.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
ADB="$(find_adb)"
"$ADB" logcat -c || true
PID=""
for _ in 1 2 3 4 5 6 7 8 9 10; do
  PID="$("$ADB" shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
  [ -n "$PID" ] && break
  sleep 1
done
if [ -n "$PID" ]; then
  "$ADB" logcat --pid="$PID"
else
  "$ADB" logcat "*:E" AndroidRuntime:E
fi
