#!/bin/bash
# Build the launcher APK with the Android SDK tools only (no Gradle): aapt2 -> javac -> d8 -> zipalign -> apksigner.
#
# Needs: Android SDK (platform 30, build-tools 35.0.0) and JDK 17+.
# Usage: ./build.sh [--debug|--release]; no argument keeps the historical debug build.
#   ANDROID_HOME   SDK location (default: ~/Library/Android/sdk on macOS, ~/Android/Sdk on Linux)
#   KEYSTORE / KEYSTORE_PASS / KEY_ALIAS      historical debug signing overrides
#   RELEASE_KEYSTORE / RELEASE_KEYSTORE_PASS / RELEASE_KEY_ALIAS  required for --release
#   RELEASE_KEY_PASS                           optional separate release key password
#   VERSION_CODE / VERSION_NAME
set -euo pipefail
cd "$(dirname "$0")"
MODE="${1:---debug}"
if [ "$#" -gt 1 ] || { [ "$MODE" != "--debug" ] && [ "$MODE" != "--release" ]; }; then
  echo 'usage: ./build.sh [--debug|--release]' >&2
  exit 2
fi
if [ "$MODE" = "--release" ]; then
  : "${RELEASE_KEYSTORE:?Set RELEASE_KEYSTORE to a persistent release keystore path}"
  : "${RELEASE_KEYSTORE_PASS:?Set RELEASE_KEYSTORE_PASS without placing it on the command line}"
  : "${RELEASE_KEY_ALIAS:?Set RELEASE_KEY_ALIAS to the persistent signing alias}"
  [ -f "$RELEASE_KEYSTORE" ] || { echo 'Release keystore does not exist.' >&2; exit 1; }
fi
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -d "$HOME/Library/Android/sdk" ]; then ANDROID_HOME="$HOME/Library/Android/sdk"; else ANDROID_HOME="$HOME/Android/Sdk"; fi
fi
BT="$ANDROID_HOME/build-tools/${BUILD_TOOLS:-35.0.0}"
JAR="$ANDROID_HOME/platforms/android-30/android.jar"
for f in "$BT/aapt2" "$BT/d8" "$JAR"; do [ -e "$f" ] || { echo "missing $f (set ANDROID_HOME / BUILD_TOOLS)"; exit 1; }; done
OUT=build
rm -rf "$OUT"; mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/assets/licenses"

# Ship upstream attribution and licenses with the APK as well as the source.
cp -R assets/. "$OUT/assets/"
cp LICENSE NOTICE "$OUT/assets/licenses/"
cp third_party/material-color-utilities/LICENSE "$OUT/assets/licenses/material-color-utilities-LICENSE"
cp third_party/material-color-utilities/README.md "$OUT/assets/licenses/material-color-utilities-README.md"

"$BT/aapt2" compile --dir res -o "$OUT/res/compiled.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -o "$OUT/base.apk" \
  -A "$OUT/assets" -0 ttf --java "$OUT/gen" --min-sdk-version 30 --target-sdk-version 34 \
  --version-code "${VERSION_CODE:-184}" --version-name "${VERSION_NAME:-1.0.0}" "$OUT/res/compiled.zip"

# JDK core classes (for lambdas) plus android.jar for the Android API; d8 desugars to the TV's level.
JAVAC_OPTIONS=(-g)
if [ "$MODE" = "--release" ]; then JAVAC_OPTIONS=(-g:none); fi
if ! javac -nowarn --release 17 "${JAVAC_OPTIONS[@]}" -cp "$JAR" -d "$OUT/classes" $(find src third_party "$OUT/gen" -name '*.java') 2> "$OUT/javac.log"; then
  cat "$OUT/javac.log"; echo "javac failed"; exit 1
fi

D8_OPTIONS=(--debug)
if [ "$MODE" = "--release" ]; then D8_OPTIONS=(--release); fi
"$BT/d8" "${D8_OPTIONS[@]}" --min-api 30 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ "$MODE" = "--release" ]; then
  APK="$OUT/cleanfire-release.apk"
  export RELEASE_KEY_PASS="${RELEASE_KEY_PASS:-$RELEASE_KEYSTORE_PASS}"
  "$BT/apksigner" sign --ks "$RELEASE_KEYSTORE" --ks-key-alias "$RELEASE_KEY_ALIAS" \
    --ks-pass env:RELEASE_KEYSTORE_PASS --key-pass env:RELEASE_KEY_PASS --out "$APK" "$OUT/aligned.apk"
else
  KS="${KEYSTORE:-$HOME/.android/debug.keystore}"
  export KEYSTORE_PASS="${KEYSTORE_PASS:-android}"
  ALIAS="${KEY_ALIAS:-androiddebugkey}"
  if [ ! -f "$KS" ] && [ -z "${KEYSTORE:-}" ]; then
    mkdir -p "$(dirname "$KS")"
    keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias androiddebugkey \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
  fi
  APK="$OUT/material-home-fireos.apk"
  "$BT/apksigner" sign --ks "$KS" --ks-key-alias "$ALIAS" \
    --ks-pass env:KEYSTORE_PASS --key-pass env:KEYSTORE_PASS --out "$APK" "$OUT/aligned.apk"
fi
"$BT/apksigner" verify --verbose "$APK"
ls -lh "$APK"
