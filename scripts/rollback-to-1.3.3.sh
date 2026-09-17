#!/usr/bin/env bash
set -euo pipefail

# Restore the known-good 1.3.3 build on the connected Android device and make
# GitHub's v11 release the latest public APK again.
REPO="MainulSQ/PixelTouch"
DEVICE="${ANDROID_SERIAL:-}"
APK_PATH="${TMPDIR:-/tmp}/PixelTouch-1.3.3.apk"

curl --fail --location --output "$APK_PATH" \
  "https://github.com/$REPO/releases/download/v11/PixelTouch.apk"

if [[ -n "$DEVICE" ]]; then
  adb -s "$DEVICE" install -r -d "$APK_PATH"
else
  adb install -r -d "$APK_PATH"
fi

# Removing v12 makes v11 (1.3.3) GitHub's latest release again.
if gh release view v12 --repo "$REPO" >/dev/null 2>&1; then
  gh release delete v12 --repo "$REPO" --yes
fi

echo "Rollback complete: PixelTouch 1.3.3 (v11) is installed and published."
