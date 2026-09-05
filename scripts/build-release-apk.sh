#!/usr/bin/env bash
set -euo pipefail

# Supply secrets through the environment; never place them in this repository.
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK directory}"
: "${RELAYREMOTE_KEYSTORE:?Set RELAYREMOTE_KEYSTORE to your private keystore path}"
: "${RELAYREMOTE_KEY_ALIAS:?Set RELAYREMOTE_KEY_ALIAS}"
: "${RELAYREMOTE_SIGNING_PASSWORD:?Set RELAYREMOTE_SIGNING_PASSWORD}"
export RELAYREMOTE_KEY_PASSWORD="${RELAYREMOTE_KEY_PASSWORD:-$RELAYREMOTE_SIGNING_PASSWORD}"

script_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
build_tools="$ANDROID_HOME/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-36.0.0}"
for tool in zipalign apksigner; do
  [[ -x "$build_tools/$tool" ]] || { echo "Missing Android tool: $build_tools/$tool" >&2; exit 1; }
done
[[ -f "$RELAYREMOTE_KEYSTORE" ]] || { echo "Keystore does not exist" >&2; exit 1; }

cd -- "$project_root"
./gradlew :app:assembleRelease
mkdir -p artifacts
release_temp="$(mktemp -d)"
trap 'rm -f -- "$release_temp/aligned.apk"; rmdir -- "$release_temp"; unset RELAYREMOTE_KEY_PASSWORD' EXIT
"$build_tools/zipalign" -f -P 16 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk "$release_temp/aligned.apk"
"$build_tools/apksigner" sign \
  --ks "$RELAYREMOTE_KEYSTORE" --ks-key-alias "$RELAYREMOTE_KEY_ALIAS" \
  --ks-pass env:RELAYREMOTE_SIGNING_PASSWORD --key-pass env:RELAYREMOTE_KEY_PASSWORD \
  --out artifacts/relay-remote-release.apk "$release_temp/aligned.apk"
"$build_tools/apksigner" verify artifacts/relay-remote-release.apk
"$build_tools/zipalign" -c -P 16 4 artifacts/relay-remote-release.apk
(
  cd artifacts
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum relay-remote-release.apk > relay-remote-release.sha256
  else
    shasum -a 256 relay-remote-release.apk > relay-remote-release.sha256
  fi
)
echo "Signed APK: artifacts/relay-remote-release.apk"
