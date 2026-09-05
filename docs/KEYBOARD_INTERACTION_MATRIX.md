# Keyboard interaction matrix

This matrix is the acceptance authority for Relay Remote keyboard behavior.
Automated checks prove state, ordering, UI, and archive behavior. Items marked
physical require the connected Samsung and a focused tvOS text field.

| Area | Required behavior | Automated evidence | Physical evidence |
|---|---|---|---|
| Availability | Keyboard is enabled only for a live RTI text session. | Capability and unavailable-state tests. | Pending: leave and re-enter an App Store search field. |
| Initial value | Opening Keyboard loads the current TV value before editing is enabled. | Existing, empty, missing-session, and stale-load tests. | Pending: open a field containing a known value. |
| Secure fields | The UI warns that protected fields may not expose their value and never assumes hidden text is empty without an explicit start-fresh choice. | Initial-read failure and start-fresh tests. | Pending: tvOS password field, if available. |
| Insert and replace | Typing at the start, middle, or end sends the latest complete value without moving the local cursor after each response. | Full-value replacement and `TextFieldValue` cursor-preservation implementation. | Pending: start, middle, and end edits. |
| Delete and clear | Backspace, range deletion, and Clear update the TV to the exact remaining value, including empty. | Deletion and empty replacement tests. | Pending: delete one character, a selection, then all text. |
| Paste and rich input | Clipboard paste, autofill, voice input, hardware keyboard input, Unicode, emoji, and RTL text use the same committed-value path. | Unicode, emoji, RTL, and full-value tests; unfinished IME composition is withheld. | Pending: paste and Arabic/emoji input. |
| Rapid typing | Slow network writes cannot reorder text; queued intermediate values collapse to the latest value. | Slow-send, ordering, and conflation tests. | Pending: sustained fast typing. |
| External TV changes | Clean local state can reload TV changes; stale reloads never overwrite a newer local edit. | Reload, resume, dirty-wins, and stale-response tests. | Pending: change TV text with Siri Remote, then Reload. |
| Focus loss and return | Editing pauses when tvOS leaves the field and automatically reloads when focus returns. | Focus loss/return and missing-session tests. | Pending: move out of and back into the field. |
| Connection lifecycle | Disconnect retains unsent local text; resume reloads only clean state and never overwrites a dirty draft. | Failure, retry, resume, and lifecycle tests. | Pending: background/foreground and Wi-Fi interruption. |
| Dismissal | Normal close waits for the last send. Failed text remains visible for Retry; Close anyway is explicit. | Dirty-dismissal and close-anyway tests. | Pending: close during a slow/failing send. |
| Device switching | Switching Apple TVs closes Keyboard and cannot send queued text to the new device. | Multi-device pending-update test. | Pending only if a second paired Apple TV is available. |
| Rotation and process privacy | Rotation restores in-memory text; closing clears sensitive UI state; keyboard text is never persisted or logged. | ViewModel state and close cleanup; source/lint review. | Pending: rotate while editing and inspect logcat for absence of entered text. |
| Done/submit | Done dismisses the Android IME only. It never emits remote Select, so it cannot enter the highlighted tvOS key. | UI action and absence of `KeyboardOperation.Submit`. | Pending: App Store search regression. |

The physical pass must restore the field's original value after testing and must
not use real passwords or other sensitive text.
