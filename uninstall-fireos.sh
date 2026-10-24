#!/usr/bin/env bash
# Remove only this fork. Existing launchers and system settings are untouched.
set -euo pipefail
SERIAL="${1:?usage: ./uninstall-fireos.sh <adb-serial>}"
adb -s "$SERIAL" uninstall dev.karat.materialhome
