#!/usr/bin/env bash
# Install only this fork and open it. No grants, Home routing or settings changes.
# Usage: ./install-fireos.sh <adb-serial>
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SERIAL="${1:?usage: ./install-fireos.sh <adb-serial>}"
APK="$HERE/build/material-home-fireos.apk"
[[ -f "$APK" ]] || { echo 'Run ./build.sh first.' >&2; exit 1; }
adb -s "$SERIAL" install -r "$APK"
adb -s "$SERIAL" shell am start -W -n dev.karat.materialhome/com.rampey.materialtv.HomeActivity
