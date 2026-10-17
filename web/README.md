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
- A web app manifest and scalable SVG app icon.
- A versioned service worker that caches the app shell for offline reopening after the first successful load.
- Unsupported native app/system actions are reported as unavailable rather than falsely marked successful.

## Current limitations

- This is a frontend prototype; it is not connected to the Android command engine or an API.
- LocalStorage is not encrypted and is only local to this browser profile. Do not store secrets or sensitive personal data.
- No cross-device sync, native OS automation, authentication, or production monitoring is implemented.
- Service workers require HTTPS or localhost. Browser support for installing SVG icons and iOS home-screen behavior varies; verify on target devices before release.
- GitHub Pages deployment is not enabled by this branch alone. The draft pull request must be reviewed and merged, and Pages hosting must be configured before a public app URL is available.
