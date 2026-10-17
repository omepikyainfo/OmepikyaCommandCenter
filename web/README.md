# Tiny Web Interface

This folder contains a responsive, dependency-free browser client for Tiny. It supports typed messages, browser speech recognition where available, and optional spoken replies.

## Current status

The UI is implemented, but it is **not a working AI chat by itself**. It expects the shared Tiny conversation service at `POST /api/tiny/chat`. That service is not present in the repository yet. Until the backend exists, the page intentionally shows a clear disconnected message instead of inventing replies.

## API contract

Request:

```json
{
  "message": "Hello, Tiny",
  "sessionId": "stable-client-session-id"
}
```

Successful response:

```json
{
  "reply": "Hello! How can I help?",
  "sessionId": "stable-client-session-id"
}
```

- Accept JSON with a non-empty `message` string and optional `sessionId`.
- Return JSON with a non-empty `reply` string; optionally return the session ID.
- Return an appropriate non-2xx status and a JSON `error` string on failure.
- Keep model credentials and service secrets on the server; never put them in this browser client.
- The web client sends requests to the same origin. If the service is hosted separately, set `window.TINY_API_BASE` before this page's script runs and configure CORS on the service.
- Serve this page over HTTPS for reliable microphone access in production.

## Next integration step

Implement the shared service around Tiny's actual conversation engine and make both Android and web clients use the same session/message contract. The Android command pipeline currently handles device actions; it is not yet a general-purpose chat API, so the service must explicitly bridge conversational replies to supported command actions and preserve existing confirmation/safety gates.
