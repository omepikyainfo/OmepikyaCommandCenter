# Tiny optional API and local mode

Tiny chat no longer opens the Connect Tiny dialog at startup. The API is optional and can be configured from the settings button when a trusted server is available.

## Without a configured API
- The status bar says that Tiny is in local mode.
- Sending a basic greeting, asking for the device time/date, or asking for help receives a deterministic on-device response.
- Other messages explain that a bundled on-device language model is not installed yet and point to the optional trusted API.
- Dismissing API settings does not block the chat screen.
- Device actions remain in Command Center and are not executed by the Tiny chat screen.

## Security and scope
Remote API URLs must still use HTTPS (HTTP is accepted only for localhost/127.0.0.1). API tokens remain optional and are only sent to the configured endpoint. The client does not invent a server URL or silently send conversation text elsewhere.

This change removes the cloud setup barrier and supplies a small offline fallback; it does **not** implement a full offline conversational model. General-purpose AI chat without a network requires bundling and integrating a suitable on-device language model separately.
