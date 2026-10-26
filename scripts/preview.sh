#!/usr/bin/env bash
# Opens the project in Android Studio (Compose previews live in app/src/main/java/.../ui/Previews.kt).
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
if command -v studio >/dev/null 2>&1; then
  studio "$ROOT" &
elif [ -d "/Applications/Android Studio.app" ]; then
  open -a "Android Studio" "$ROOT"
elif command -v studio.sh >/dev/null 2>&1; then
  studio.sh "$ROOT" &
elif command -v studio64.exe >/dev/null 2>&1; then
  studio64.exe "$ROOT" &
else
  echo "Android Studio launcher not found on PATH. Open this folder in Android Studio manually: $ROOT"
  exit 1
fi
echo "Opening in Android Studio. Open ui/Previews.kt and switch to the Split/Design view."
