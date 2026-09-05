# Signing a release

The release variant is optimized and non-debuggable. To install it, sign the
unsigned APK with a private Android signing key. Keep the key outside this
repository and back it up securely; future updates must use the same key.

Android Studio's **Build → Generate Signed App Bundle / APK** is the easiest
interactive option. For Linux/macOS, the repository also provides a Bash helper:

```bash
export ANDROID_HOME="/path/to/android-sdk"
export RELAYREMOTE_KEYSTORE="/private/path/release.jks"
export RELAYREMOTE_KEY_ALIAS="release"
read -r -s -p 'Keystore password: ' RELAYREMOTE_SIGNING_PASSWORD
export RELAYREMOTE_SIGNING_PASSWORD
bash scripts/build-release-apk.sh
unset RELAYREMOTE_SIGNING_PASSWORD
```

The helper defaults to Build-Tools `36.0.0`; override
`ANDROID_BUILD_TOOLS_VERSION` if needed. Set `RELAYREMOTE_KEY_PASSWORD` only
when the key password differs from the keystore password.

Outputs are ignored by Git:

- `artifacts/relay-remote-release.apk`
- `artifacts/relay-remote-release.sha256`

Before publishing any binary, verify its signature and non-debuggable manifest,
review embedded certificate identity and metadata, run the tests, and document
known compatibility limits. Do not upload private signing material or raw logs.
