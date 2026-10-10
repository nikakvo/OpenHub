#!/usr/bin/env bash
# Build a signed release APK of OpenHub and copy it to the Windows Download folder
# (OpenHub-<version>.apk for releases, plus a timestamp for test builds, because
# Telegram caches files by name).
#
# Usage: ./build.sh            normal flavor ("OpenHub")
#        FLAVOR=FullName ./build.sh
# Optional env: KEYSTORE, KEY_ALIAS, KEYSTORE_PASSWORD, KEY_PASSWORD, OUT_DIR
#
# Passwords: put them once in a file next to the keystore (same name, .env), e.g.
# ~/keys/tears-gps.env with KEYSTORE_PASSWORD=... and KEY_PASSWORD=..., chmod 600.
# Without that file the script asks for them.
set -euo pipefail
cd "$(dirname "$0")"

KEYSTORE="${KEYSTORE:-$HOME/keys/tears-gps.jks}"
KEY_ALIAS="${KEY_ALIAS:-tearsgps}"
OUT_DIR="${OUT_DIR:-/mnt/d/Download}"
FLAVOR="${FLAVOR:-Normal}"
flavor_dir="$(tr '[:upper:]' '[:lower:]' <<< "${FLAVOR:0:1}")${FLAVOR:1}"

[ -f "$KEYSTORE" ] || { echo "Keystore not found: $KEYSTORE" >&2; exit 1; }

SIGNING_ENV="${SIGNING_ENV:-${KEYSTORE%.*}.env}"
if [ -f "$SIGNING_ENV" ]; then
    if [ -n "$(find "$SIGNING_ENV" -perm /077)" ]; then
        echo "Warning: $SIGNING_ENV is readable by others, run: chmod 600 $SIGNING_ENV" >&2
    fi
    set -a
    # shellcheck disable=SC1090
    . "$SIGNING_ENV"
    set +a
fi

if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
    read -rsp "Keystore password: " KEYSTORE_PASSWORD; echo
fi
if [ -z "${KEY_PASSWORD:-}" ]; then
    read -rsp "Key password (Enter = same as keystore): " KEY_PASSWORD; echo
    KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
fi

# Passed as environment variables, so the passwords never show up in the process list
# and are never written to a file.
export ORG_GRADLE_PROJECT_sign_path="$KEYSTORE"
export ORG_GRADLE_PROJECT_key_alias="$KEY_ALIAS"
export ORG_GRADLE_PROJECT_store_password="$KEYSTORE_PASSWORD"
export ORG_GRADLE_PROJECT_key_password="$KEY_PASSWORD"

./gradlew "assemble${FLAVOR}Release" --console=plain

apk="$(ls -t "app/build/outputs/apk/${flavor_dir}/release/"*.apk | head -1)"
version="$(grep '^VERSION_NAME' gradle.properties | cut -d= -f2 | tr -d ' ')"
# Test builds (versions like 3.3.0-tears.30) get a timestamp so every APK has a unique
# name; release versions (3.3.0) get a clean name for the GitHub release.
if [[ "$version" == *-* ]]; then
    name="OpenHub-${version}-$(date +%Y%m%d-%H%M%S).apk"
else
    name="OpenHub-${version}.apk"
fi

mkdir -p "$OUT_DIR"
cp "$apk" "$OUT_DIR/$name"

apksigner="$(ls -d "${ANDROID_HOME:-$HOME/android-sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
if [ -n "$apksigner" ]; then
    "$apksigner" verify --print-certs "$OUT_DIR/$name" | grep -m1 "SHA-256" || true
fi
echo "APK: $OUT_DIR/$name"
