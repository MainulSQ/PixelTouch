#!/usr/bin/env bash
set -euo pipefail

# Rerun the GitHub Actions APK publisher for the current main branch.
gh workflow run publish-apk.yml --repo MainulSQ/PixelTouch --ref main
echo "Release requested. Check progress with: gh run list --repo MainulSQ/PixelTouch --workflow publish-apk.yml --limit 1"
