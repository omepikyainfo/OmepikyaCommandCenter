# Tiny hybrid backend

This Node.js 20+ service provides the cloud-conversation half of Tiny. It intentionally does not execute Android/device actions.

## Hybrid request flow

1. The Android app continues to run its existing local command recognition and confirmation/safety pipeline first.
2. Only when a message is conversational or unsupported locally should a client send it to `POST /api/tiny/chat`.
3. This API calls the configured cloud conversation provider and returns a text reply plus a session ID.
4. The web UI can use the same endpoint for conversational chat. Browser speech recognition/synthesis remains a client capability and depends on browser/device support.

**Important:** this branch adds a standalone backend contract. The current Android client is not yet wired to this endpoint, and the service does not yet share durable memory between processes/devices.

## Run locally

Requires Node.js 20 or newer.

```sh
cd server
npm test
OPENAI_API_KEY=your_server_side_key npm start
```

The server binds to `127.0.0.1:8080` by default. Set `HOST=0.0.0.0` only when your deployment environment requires external binding. Production deployments require `TINY_API_TOKEN` and HTTPS. A Render blueprint is provided at the repository root (`render.yaml`); set `OPENAI_API_KEY` as a secret in the hosting dashboard, never in Git.

## Configuration

- `OPENAI_API_KEY`: required server-side provider credential. Never put this in Android resources, web JavaScript, Git, or a client-side environment variable.
- `TINY_MODEL`: optional model override; defaults to `gpt-4.1-mini`.
- `TINY_API_TOKEN`: required in production. Requests to `POST /api/tiny/chat` must include `Authorization: Bearer <token>`. Keep this token private and enter it only in the Android app's Tiny settings. The production server refuses to start without it.
- `OPENAI_RESPONSES_URL`: optional provider endpoint override, mainly for controlled testing.
- `PORT`: optional listener port; defaults to `8080`.
- `HOST`: optional listener host; defaults to `127.0.0.1`.
- `TINY_ALLOWED_ORIGINS`: comma-separated exact browser origins allowed for cross-origin requests. Leave unset if the UI and API are served same-origin. This is a CORS control, not authentication.
- `TINY_RATE_LIMIT`: requests per client IP per minute; defaults to `30`.

## API

`GET /healthz` returns service health, hybrid mode, and whether a provider key is configured (never the key itself).

`POST /api/tiny/chat`

Request:
```json
{ "message": "Explain my budget", "sessionId": "optional-session-id" }
```

Response:
```json
{ "reply": "A text-only assistant response.", "sessionId": "server-issued-or-existing-id" }
```

Input is limited to 4,000 characters and request bodies to 16 KiB. Conversation history is in-memory, limited to the latest six user/assistant exchanges, expires after 30 minutes, and is lost on restart. This is not a durable or authenticated multi-user memory store.

## Safety and production requirements

- No model tools or command-execution endpoint are registered in this service.
- Cloud output is text only; it must never be treated as an Android command or as authorization to perform an action.
- Keep device actions inside the existing local command pipeline and preserve user confirmation for sensitive actions.
- Production API requests require bearer-token authentication, and HTTPS must be enforced by the hosting platform. Configure exact browser origins if needed, and consider a persistent per-user session store with access controls and retention limits.
- Provider errors are returned as generic messages; provider response bodies and secrets are not sent to clients.
- This initial integration is intentionally stateless across server instances. Do not use it as production shared memory until identity, persistence, and retention are designed and tested.
