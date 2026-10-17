# Omepikya Command Center — Cross-Platform Roadmap

## Goal

Make Omepikya useful on the owner's Android phone, iPhone/iPad, Windows, macOS, and Linux while keeping one coherent command model and personal data model.

## Current state

- The current client is a native Android application written in Java.
- Android actions (launching apps, device settings, voice, background automation, contacts, and notifications) are coupled to Android APIs.
- The Python character-level LLM experiments are prototypes, not a production assistant backend.
- There is no web, iOS, or desktop client in the repository yet.

## Recommended approach: shared web client + platform adapters

Do not immediately rewrite the Android application. Preserve it as the Android-native client while extracting stable command and data contracts. Build a responsive Progressive Web App (PWA) as the first cross-platform surface; it can run in modern desktop browsers and can be added to the iOS/iPadOS home screen. For deeper OS integration, add platform-specific clients/adapters incrementally.

### Shared layers

1. **Shared command contract** — command, intent, plan, execution status, result, capability and error models, independent of Android classes.
2. **Command service** — optional authenticated service for syncing preferences, command history and shared workflows across devices. Keep it small and user-controlled; local-first should remain possible.
3. **Responsive PWA** — command input, plan preview, activity/history, settings, automations and status. Installable on desktop browsers and iOS/iPadOS Safari where supported.
4. **Platform adapters** — Android Java adapter, browser/PWA adapter, then native iOS and desktop adapters as needed. Every adapter must declare capabilities and unsupported actions explicitly.
5. **Storage and sync** — versioned data schema, export/import and optional encrypted sync. Never sync contacts, recordings, tokens or other sensitive data by default.

## Platform capability expectations

| Capability | Android | iOS/iPadOS | Windows/macOS/Linux PWA |
|---|---|---|---|
| Command UI, planning, history | Yes | Yes | Yes |
| Open websites and web services | Yes | Yes | Yes |
| Launch arbitrary installed apps | Android APIs, subject to visibility/permissions | Restricted; use URL schemes and Shortcuts where supported | Desktop bridge/native client needed |
| Device settings, Bluetooth, Wi-Fi toggles | Restricted Android intents/settings | Strongly restricted | Browser restricted; native helper needed |
| Voice input/output | Native APIs | Browser/native APIs with permission and availability limits | Browser/native APIs with permission and availability limits |
| Reliable background automation | Android-specific APIs and OS limits | Highly restricted background execution | Native service/scheduled task needed |
| Cross-device sync | Optional service | Optional service | Optional service |

The UI must never imply an action ran when the platform does not permit it. Show “unsupported on this device” or offer a safe alternative.

## Implementation phases

### Phase 1 — Establish stable contracts
- Document platform-independent command/action/result and capability schemas.
- Keep Android behavior intact and add regression tests.
- Add a capability report so the UI can distinguish supported, permission-required and unsupported actions.
- Keep debug signing stable; keep any production signing material outside the repository.

### Phase 2 — Build the PWA
- Create a responsive web client under `web/`.
- Start with command entry, plan preview, activity/history, help, and device capability display.
- Make the first iteration local-first and avoid requiring accounts or cloud storage.
- Add automated build and tests to CI.

### Phase 3 — Cross-device continuity
- Add explicit export/import first.
- Add optional authenticated, encrypted sync only after the data model and threat model are reviewed.
- Make sync opt-in and let the owner delete/export their data.

### Phase 4 — Native integrations
- Add iOS/iPadOS integration using supported app links, Shortcuts and native APIs; do not promise arbitrary app launches or unrestricted settings control.
- Add Windows/macOS/Linux companion clients only where browser APIs cannot deliver the needed capability.
- Use least privilege and require confirmation for sensitive or destructive actions.

## Definition of done

- Android build and tests remain green.
- PWA is responsive and installable on supported browsers.
- Each action advertises real platform capability and permission needs.
- Unsupported actions fail clearly rather than reporting false success.
- Personal data remains local by default, with explicit opt-in for sync.
- Build artifacts and signing keys are handled separately from source code.

## Important security note

A debug signing key is only for development builds. Do not commit a production signing key, password, or certificate private key to Git. For a personal production APK, store the keystore and passwords in a secure local backup or encrypted CI secrets, and use the same release key for all future updates.
