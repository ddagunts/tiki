#!/usr/bin/env bash
set -euo pipefail

JAVA_HOME="/home/dima/Downloads/android-studio-panda4-patch1-linux/android-studio/jbr"

export JAVA_HOME
./gradlew assembleRelease
shasum -a 256 "$APK" > "$APK.sha256"
echo "built: $APK"
echo "sha256: $(cat "$APK.sha256")"

MAPPING="app/build/outputs/mapping/release/mapping.txt"
if [[ -f "$MAPPING" ]]; then
    echo "r8 mapping: $MAPPING ($(wc -l < "$MAPPING") lines)"
else
    echo "WARN: $MAPPING not produced — is isMinifyEnabled=true for release?" >&2
fi
