# Protocol reference and licensing

## Reference snapshot

Implementation began on 2026-07-30 using pyatv release 0.18.0 at commit:

```text
b277a4c8222ecdcbaab8a24e3e713ca44765adb4
```

The inspected source and tests covered:

- `_companion-link._tcp`, `_mediaremotetv._tcp`, and supplementary
  `_airplay._tcp` discovery records
- Companion's one-byte frame type plus 24-bit big-endian payload length
- OPACK messages and TLV8 authentication records
- HAP Pair Setup and Pair Verify
- HKDF-SHA512 session keys and ChaCha20-Poly1305 traffic encryption
- HID, touch, media-control, application, power, and RTI keyboard messages

Relay Remote is an independent Kotlin/JCA implementation. It does not package,
embed, invoke, or download Python or pyatv at runtime.

## Ported protocol surface

- PIN-based HAP SRP pairing
- Ed25519 controller/accessory identities and X25519 pair verification
- Per-direction Companion encryption keys and monotonic nonces
- HID navigation, Back/Menu, Home, Control Center, Play/Pause, volume, wake,
  sleep, Screensaver, Guide, and Channel up/down
- Held HID Select and Back, using pyatv's press, wait, release sequence
- Live Companion touch phases (press, hold, release) streamed during a swipe
- Companion media-control requests for previous, next, relative skip, scan
  begin/end, and get/set volume
- Installed application enumeration and launch
- User account enumeration and switching
- RTI keyboard session detection, current-text loading, clear, and replacement
  of committed text; phone Done does not send remote Select
- Power and media-capability event handling

Now Playing metadata is intentionally not exposed because this build does not
include the separate Media Remote Protocol protobuf session. The UI model can
render metadata when a future verified MRP implementation supplies it.

The pinned pyatv Companion implementation establishes no mute HID command, so
Relay Remote sends no guessed mute message. Mute instead reads the level with
get volume, sets it to zero, and restores the saved level on unmute. It is offered
only when the session advertises volume support.

Holding Select starts when the touchpad recognizes the hold, at 400 ms. The release
is delayed until Select has been down for at least one second, matching pyatv's
Hold delay, so an early lift still counts as a hold on the TV.

Siri audio, Subtitles, and InSight are not implemented. pyatv defines HID Siri and
caption-setting identifiers but no working audio path or caption payload, so Relay
Remote does not guess at them.

## Dependencies and licenses

- pyatv 0.18.0 — MIT, copyright Pierre Ståhl. Used as a protocol and test
  reference; its source is not included in the APK.
- dd-plist 1.30 — MIT, copyright Daniel Dreibrodt. Used for bounded binary
  property-list encoding and parsing of RTI keyboard messages.
- Bouncy Castle 1.85 — MIT-style Bouncy Castle license. Used for established
  SRP-6a primitives.
- Android Gradle Plugin, Kotlin, AndroidX, Compose, Material 3, Coroutines,
  DataStore, and protobuf — their upstream open-source licenses apply.

No pyatv code is executed on Android. Ported protocol behavior was limited to
the features required by Relay Remote.
