# Verification status

This document distinguishes recorded hardware results from automated checks.
It is not a claim of universal Apple TV compatibility.

## Recorded hardware baseline

The July 2026 pairing investigation used a Samsung Galaxy S25 Ultra running
Android 16 and a first-generation Apple TV 4K running tvOS 18.6.

Discovery, the Companion/HAP PIN exchange, credential persistence, and
authenticated reconnection after a force-stop and cold launch were observed.
A Keystore AES-GCM IV-generation defect was reproduced and corrected, with a
physical-device regression check passing afterward.

## Automated coverage

JVM tests cover framing, OPACK/TLV8, fake-peer pairing and verification,
cryptography, credential envelopes, discovery identity merging, command order,
retry behavior, keyboard archives, and ViewModel lifecycle/race handling.
Android instrumentation covers Compose controls and Android Keystore behavior.
The public screenshot fixture renders synthetic state without connecting to a TV.

Run the commands in the README for current results. Historical test runs do not
guarantee that a different tvOS version or audio configuration behaves identically.

## Remaining hardware acceptance

- Navigation, live swipes, power, app launching, and playback effects.
- Touchpad hold opening item menus, and moving, deleting, and offloading an app.
- Holding Back, Guide, Channel up/down, Screensaver, and profile switching.
- Scanning by holding rewind or forward, and Mute restoring the saved volume.
- Configurable skip intervals across different tvOS applications.
- Volume and television power through the user's audio/HDMI-CEC setup.
- TV reboot, Wi-Fi interruption, and reconnection recovery.
- The complete [keyboard interaction matrix](KEYBOARD_INTERACTION_MATRIX.md),
  including existing text, live editing, focus changes, and the App Store regression.

Now Playing metadata remains disabled. Keyboard is available only when
tvOS exposes an active RTI text session; other controls use live capability gates.

When reporting results, include model and software versions but omit personal
device names, serial numbers, network addresses, pairing data, and entered text.
