# Omepikya Command Center Web Preview

This is the first responsive web/PWA shell for the cross-platform roadmap.

## Run locally

Serve the repository root with any static HTTP server and visit `/web/`. For example:

```sh
python -m http.server 8000
```

Then open `http://localhost:8000/web/`.

## Included

- Responsive layout for phones, tablets and laptops.
- Browser/platform detection and a transparent capability list.
- Local-only command history and notes using browser storage.
- Safe demo actions for YouTube, web search and URL opening.
- Unsupported native app/system actions are reported as unavailable rather than falsely marked successful.

## Current limitations

- This is a frontend prototype; it is not connected to the Android command engine or an API.
- LocalStorage is not encrypted and is only local to this browser profile. Do not store secrets or sensitive personal data.
- No cross-device sync, native OS automation, authentication, or production offline caching is implemented.
- A production PWA should add tested icons, service-worker caching, accessibility/device testing, and CI coverage before being treated as release-ready.
