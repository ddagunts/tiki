#!/usr/bin/env bash
# Build and verify a signed release APK.
#
# Reads signing creds from keystore.properties at the repo root (gitignored)
# or the TKEY_KEYSTORE_FILE / _PASSWORD / _KEY_ALIAS / _KEY_PASSWORD env vars.
# Without those, assembleRelease still runs but produces an unsigned APK.

set -euo pipefail

cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-/home/dima/Downloads/android-studio-panda4-patch1-linux/android-studio/jbr}"
export JAVA_HOME

APK="app/build/outputs/apk/release/app-release.apk"
UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"

./gradlew :app:assembleRelease --console=plain --no-daemon

if [[ -f "$UNSIGNED" && ! -f "$APK" ]]; then
    echo "WARN: produced $UNSIGNED — signing config not picked up." >&2
    APK="$UNSIGNED"
fi

if [[ ! -f "$APK" ]]; then
    echo "ERROR: expected APK at $APK was not produced." >&2
    exit 1
fi

shasum -a 256 "$APK" | tee "$APK.sha256"
echo "built: $APK"

# Verify signing when an Android SDK build-tools install is around.
APKSIGNER="$(find "${ANDROID_HOME:-/home/dima/Android/Sdk}/build-tools" -name apksigner 2>/dev/null | sort -V | tail -1 || true)"
if [[ -n "$APKSIGNER" && "$APK" != "$UNSIGNED" ]]; then
    echo "verifying with: $APKSIGNER"
    "$APKSIGNER" verify --print-certs "$APK" 2>&1 | grep -E 'Signer|certificate' | head -6
fi

MAPPING="app/build/outputs/mapping/release/mapping.txt"
if [[ -f "$MAPPING" ]]; then
    echo "r8 mapping: $MAPPING ($(wc -l < "$MAPPING") lines)"
fi
