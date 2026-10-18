# Tiny: JARVIS-Inspired Product Roadmap

## Vision

Tiny should become a capable, voice-first personal assistant inspired by the experience of JARVIS: conversational, context-aware, proactive when invited, and able to carry out useful actions across the phone. The goal is not to copy fictional capabilities or promise impossible autonomy. Tiny should be reliable, privacy-respecting, transparent, and useful on an ordinary Android device.

## Product principles

1. **Phone-first:** core commands, personal notes, reminders, and supported device actions should work without Render or an internet connection.
2. **Local by default:** private notes and command history stay on the device unless the user explicitly chooses a cloud service or export.
3. **Voice and text parity:** everything possible by typing should also be accessible by voice, with clear feedback when speech recognition is unavailable.
4. **Actions, not just chat:** Tiny should complete supported tasks and report what actually happened.
5. **Permission and confirmation:** ask before sensitive, destructive, costly, or externally visible actions; respect Android permissions and OS restrictions.
6. **Honest capability status:** clearly distinguish built-in commands, local-model answers, cloud-assisted answers, and unsupported requests.
7. **Graceful offline behavior:** no feature should silently pretend to have worked when its required model, permission, or connection is missing.

## Roadmap

### Phase 0 — Reliable foundation (current)
- Keep Tiny launchable without a server or API key.
- Retain a basic offline fallback, limited calculator, and private note memory.
- Make offline/cloud status visible and explain the limits of basic replies.
- Add tests for calculator edge cases, note save/recall/forget, API failures, and offline startup.
- Keep debug and release APK builds green in CI.

**Exit criteria:** app starts without network; supported local features work; failures are clear; tests and both APK builds pass.

### Phase 1 — A dependable phone assistant
- Build a structured command router for time/date, arithmetic, timers, reminders, app launching, Android settings shortcuts, and supported device actions.
- Normalize common phrasing and support English/Hindi where practical.
- Connect voice input to the same command pipeline as typed input.
- Improve text-to-speech lifecycle, interruption, language fallback, and accessibility.
- Provide explicit confirmation for sensitive actions and a readable action result.

**Exit criteria:** a tested command catalog works offline wherever Android APIs allow it, with consistent text and voice behavior.

### Phase 2 — Real on-device conversational AI
- Integrate a maintained Android-compatible inference runtime behind a small LocalModelEngine interface.
- Support a user-selected, compatible quantized model stored in app-private storage; do not bundle huge weights in the APK.
- Provide model download/import, progress, free-space checks, integrity validation, cancellation, deletion, and version metadata.
- Check device RAM, storage, Android version, and ABI before enabling a model; offer a clear unsupported-device path.
- Run inference off the UI thread, support cancellation, and handle low-memory/model-load errors.
- Keep command execution separate from generated text: the model may propose an action, but the deterministic command router and safety checks must validate it.

**Exit criteria:** Tiny can answer open-ended questions in airplane mode after a compatible model is installed; the UI identifies the active model and local mode; no cloud endpoint is required. Model quality and speed must be tested on real target devices before claiming broad support.

### Phase 3 — Useful personal memory
- Replace the current single-note proof of concept with an organized local memory store: preferences, named notes, recurring routines, and conversation summaries.
- Add commands to inspect, correct, export, and delete saved memory.
- Make memory opt-in by category; avoid saving sensitive content by default.
- Add local search and retrieval so Tiny can use relevant saved information without sending it off-device.
- Provide retention controls and a clear “forget this” action.

**Exit criteria:** users can see what Tiny remembers, edit it, and delete it; tests prove data remains app-private.

### Phase 4 — Task planning and safe execution
- Turn requests into short, inspectable plans for multi-step tasks.
- Use existing CommandBrain, safety gates, execution coordinator, and action router rather than letting the model directly invoke Android APIs.
- Show steps, ask for approval when required, execute one step at a time, verify results, and stop safely on failure.
- Add action history, retry/recovery, undo where the platform supports it, and an explicit stop/cancel control.
- Never claim a message was sent, a setting changed, or a task completed until the system verifies the outcome.

**Exit criteria:** common multi-step phone workflows have tests for success, denial, partial failure, cancellation, and recovery.

### Phase 5 — Proactive assistant and routines
- Let users create scheduled routines such as morning briefings, reminders, and device-mode checklists.
- Keep schedules local and resilient across restarts within Android's background-work limits.
- Make proactive behavior configurable by schedule, category, and quiet hours.
- Require confirmation for external communication and other sensitive actions.
- Explain why a notification appeared and provide pause/disable controls.

**Exit criteria:** routines survive supported restart scenarios, obey quiet hours and permissions, and can be inspected, paused, or deleted.

### Phase 6 — Optional connected intelligence and services
- Keep cloud AI strictly optional and visibly separate from local mode.
- Add explicit controls for what text is sent to a configured provider; never transmit local memory by default.
- Add integrations only through narrow, permissioned adapters with timeouts, error handling, and tests.
- Support user-approved calendar, email, navigation, and smart-home integrations where APIs and account permissions permit.
- Clearly label any feature that requires network access or a third-party account.

**Exit criteria:** Tiny remains useful offline, and connected features fail without breaking local commands.

### Phase 7 — Camera, screen, and richer context (optional)
- Explore on-device OCR, image understanding, and user-initiated screen/context sharing only where Android permissions and APIs allow.
- Show a visible indicator when a camera, microphone, or shared context is being used.
- Do not run covert recording or bypass Android privacy indicators.
- Process locally where feasible and request consent before any external upload.

**Exit criteria:** context features are opt-in, permission-compliant, and transparent about where data is processed.

## Quality gates for every phase

- Unit tests for command parsing, safety decisions, local storage, and error paths.
- CI must pass unit tests plus debug and release APK builds.
- Manual testing on at least one lower-memory Android phone and one stronger device.
- Airplane-mode tests for every feature described as offline.
- Accessibility checks for large text, TalkBack labels, keyboard use, and speech alternatives.
- Privacy review for each new permission, integration, and data store.
- Release notes must distinguish implemented, experimental, and planned features.

## First implementation priorities

1. Finish and validate the existing offline calculator/note PR.
2. Add tests and strengthen the command router and voice-to-command flow.
3. Select an Android local inference runtime and a small quantized model based on actual target-device RAM/storage.
4. Implement the model manager and LocalModelEngine interface in a separate branch; keep rule-based offline commands working if the model is absent.
5. Test on a real phone before merging or distributing a model-enabled APK.

## Non-goals and boundaries

- No claim of human-level intelligence, perfect understanding, or fully autonomous control.
- No bypassing Android permissions, app sandboxing, or security confirmations.
- No background surveillance or silent recording.
- No cloud dependency for core local commands.
- No bundling of large model weights until download size, license, performance, and device compatibility are understood.

## How to measure progress

Track: offline command success rate; voice transcription success on supported devices; task completion verified by outcome rather than generated text; crash/ANR rate; model load time and tokens per second by device class; memory/storage use; battery impact; and user control over stored data.
