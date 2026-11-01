#!/usr/bin/env bash
# Publish a release in one command (needs the GitHub CLI: https://cli.github.com, `gh auth login` once):
#   ./scripts/release.sh            -> builds the release APK and publishes it as v<versionName>
#   ./scripts/release.sh 1.0.1      -> same, but checks that versionName is 1.0.1 first
# It creates the git tag, the GitHub Release and uploads WaifuVerse-<version>.apk.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
command -v gh >/dev/null 2>&1 || { echo "GitHub CLI (gh) not found. Install it from https://cli.github.com and run 'gh auth login'." >&2; exit 1; }

VERSION="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
if [ -n "${1:-}" ] && [ "$1" != "$VERSION" ]; then
  echo "app/build.gradle.kts has versionName = $VERSION but you asked for $1. Bump versionCode/versionName first." >&2
  exit 1
fi
TAG="v$VERSION"

./gradlew assembleRelease
APK="$(ls app/build/outputs/apk/release/WaifuVerse-*.apk | head -1)"
[ -f "$APK" ] || { echo "APK not found after build." >&2; exit 1; }

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release $TAG already exists; uploading the APK to it (replacing the old file)."
  gh release upload "$TAG" "$APK" --clobber
else
  gh release create "$TAG" "$APK" --title "WaifuVerse $VERSION" --generate-notes
fi
echo "Published: $(gh release view "$TAG" --json url -q .url)"
