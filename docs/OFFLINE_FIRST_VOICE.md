# Offline-first Tiny voice

## What changed

The Android voice recognizer now prefers Android's dedicated on-device recognizer when the device exposes one (Android 12 / API 31 and newer). That path processes speech on-device. The recognizer also sets Android's `EXTRA_PREFER_OFFLINE` hint for compatible fallback speech services.

`VoiceEngine.isUsingOnDeviceSpeechRecognition()` exposes whether the dedicated on-device recognizer is active, and `VoiceEngine.isOnDeviceSpeechRecognitionAvailable(context)` lets the UI check device capability before starting a session.

## Important platform limits

- On-device recognition availability depends on the Android version, manufacturer, installed recognition service, and downloaded language data.
- On Android versions or devices without the dedicated on-device recognizer, Android's standard recognizer is used with an offline preference. Android documents that preference as best-effort; it is **not a guarantee** that recognition works without a network.
- Text-to-speech depends on the installed TTS engine and voice data. Install an offline voice for the preferred language to speak without internet.
- Local command parsing and safety gates remain in the Android app. Do not treat cloud responses as permission to execute device actions.

## Test offline behavior on a real device

1. Open Android Settings and find the installed speech recognition service / offline speech recognition language downloads. The exact menu varies by manufacturer.
2. Download the desired language pack and an offline text-to-speech voice.
3. Start the Command Center and test a simple local command such as `Open Wi-Fi settings`.
4. Enable airplane mode and test the same command using voice.
5. Confirm that the app shows a useful error if on-device speech recognition or offline language data is unavailable.
6. Repeat with internet enabled and compare results. Do not claim fully offline support on a device until the airplane-mode test passes.

## Next implementation steps

- Surface the on-device-recognition status in the voice UI before listening.
- Add an explicit user setting for local-only recognition versus system fallback.
- Select an installed non-network TTS voice when available and expose its offline status.
- Evaluate an embedded offline speech model and on-device language model after measuring APK size, RAM, latency, and device compatibility.
