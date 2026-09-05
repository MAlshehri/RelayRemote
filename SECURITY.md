# Security

Do not publish pairing codes, stored credentials, private keys, or keyboard text
in issues. Use this repository's **Security → Report a vulnerability** option
for a private report. Include the affected version, reproduction steps using
synthetic data, and the potential impact.

Only the latest source on `main` is maintained. This is an experimental,
independent client using an unofficial Apple TV protocol; it has not undergone
an independent security audit.

The app communicates on the local network and encrypts saved pairing
credentials using Android Keystore. Backups are disabled. See the README for
the implemented privacy and lifecycle behavior.
