# Virga for Wear OS: glance-and-act companion

Status: draft, awaiting approval. Owner decisions (offline sign-out, alerts) and builder calls are folded in below.

## Intent

Virga is an unofficial Cloudflare client for Android phones. The owner wants a Wear OS variant for glancing at the account and acting on it quickly, without taking the phone out. The watch is for the moments where the question is "did my deploy go through?" and the action is "cancel it" or "run it again". It is not a smaller copy of the phone app.

## Goals

- See the state of the latest Workers Build from the watch face (complication) and from a tile.
- See a 24h account summary (requests, errors) on a tile.
- Browse recent builds, open one, and cancel a running build or retry a finished one.
- Get a notification on the watch when a build finishes or fails: bridged from the phone for builds watched there, and from the watch's own engine for builds watched on the watch.
- Work with the phone out of range (Wi-Fi or LTE watch), after the one-time handoff from the phone.
- The demo account works on the watch, so everything can be tested without a real token.

## Non-goals

- DNS, Pages, R2, D1, KV, SQL console, log reading, settings screens, secrets. These stay on the phone.
- Typing or pasting a token on the watch.
- Standalone sign-in on the watch.
- Phone-as-proxy mode (the watch holding no token).

## Anti-goals

- A cramped port of phone screens. Every watch screen has one purpose and one primary action.
- The token ever leaving the phone-to-watch channel, being logged, or being shown.
- A watch that keeps acting indefinitely for an account the phone has signed out of. Bounded by the 24h check-in rule, not eliminated.

## Constraints

- Wear OS 4 or newer (API 33+) is the proposed floor, since Wear Compose Material 3 and current Tiles target it. This is the builder's to confirm.
- Reuse `core:common`, `core:network`, `core:auth` and the builds API layer. Do not fork them.
- Phone project rules apply: Kotlin, Compose, Koin, Navigation 3 where it fits, JVM-testable logic, no mutation of shared state, small files.
- Unofficial app and trademark wording as in the README applies to the watch listing.

## Approaches considered

1. **Companion (chosen).** The phone sends the token and the selected account to the watch over the Wear Data Layer. The watch encrypts and stores the token with its own Keystore key and calls Cloudflare itself. Pros: no typing, works when the phone is away, reuses repositories. Cons: the token exists on a second device, and sign-out has to reach the watch.
2. **Phone as proxy.** The watch holds no token and asks the phone for small summaries. Pros: token stays on the phone. Cons: nothing works when the phone is out of range. Rejected by the owner.
3. **Standalone sign-in on the watch.** Cloudflare has no device-code flow, so this is still typing or scanning a token. Worst experience, most work. Rejected.

## Design

### Modules

- New `:wear` Android application module, same applicationId as the phone app so the Play listing pairs them, built with a new `virga.android.wear` convention plugin next to the existing application plugin in `build-logic`.
- `:wear` depends on `core:common`, `core:network`, `core:auth` and `feature:builds` (API and gateway layers only, not its phone UI). If depending on `feature:builds` pulls phone-only UI or Android components onto the watch, the builder splits the API and gateway code into a `feature:builds-api` style module. That split is a refactor of existing code and should be its own step with the phone tests still green.
- `:app` gains a small `wear-sync` piece: observes `SessionRepository`, sends the credential handoff, and removes it on sign-out or account change.

### Credential handoff

- Transport: Wear Data Layer. The phone writes an encrypted-in-transit item (the Data Layer is already end-to-end between paired devices) containing the token, the account list, the selected account id, and a demo flag.
- Watch side: a listener receives the item, stores the token in a watch-local vault using the same `AesGcmCipher` and Android Keystore key scheme `core:auth` uses on the phone, and updates a watch `SessionRepository`.
- Sign-out on the phone, or switching account, updates or deletes the Data Layer item. The watch clears its vault on deletion.
- Offline sign-out (owner decision): Virga does not revoke the Cloudflare token on sign-out, so a watch that missed the sign-out still holds a working token and a 401 never arrives. The watch therefore records the time of its last successful check-in with the phone in its vault. If no check-in has happened for 24 hours, the watch stops all actions and reads, shows "Reconnect from phone", and wipes the token once the phone confirms a sign-out or the user dismisses the state. The phone's sign-out behavior is unchanged. The 24h window is a named constant. A 401 from Cloudflare still clears the watch token immediately.
- Demo mode on the phone sends the demo flag and no token, and the watch uses the existing demo engine.
- First-run on the watch with nothing received shows one screen: "Open Virga on your phone to connect", with no input fields.

### Surfaces

| Surface | What it shows | Action |
|---|---|---|
| Complication | Latest build status for the selected account, as short text and an icon (running, passed, failed) | Tap opens the build list |
| Build tile | Latest build: worker name, status, time or duration | Tap opens that build's detail |
| Summary tile | 24h requests and errors for the selected account. If the token lacks analytics access, a specific "Token lacks analytics access" state instead of a generic error (builder's call; build features do not need that scope) | Tap opens the app home |
| App: build list | Recent builds across Workers, newest first | Tap a build for its detail |
| App: build detail | Worker, branch or commit if present, status, timing | Cancel (running) or Retry (finished), each with a confirm step |
| Notification | Build finished or failed for a watched build | Tap opens its detail |

- Complications and tiles refresh from the watch's own network access on the system's schedule, plus a refresh when the app is opened. They show stale text with an age rather than blank.
- The watch has no account switcher. It follows the phone's selected account.
- Retry uses `startBuild` with the trigger of the previous build. If the trigger is unknown, Retry is hidden rather than guessed.

### Watched builds and alerts (owner decision)

- Builds watched on the phone: the phone's existing build notification is bridged to the watch by Wear OS. No new code, and it only works while the phone is connected. The phone's Live Update flow is unchanged.
- Builds the user starts watching from the watch: the watch's own engine and registry (reused from `feature:builds`) poll while the build runs and post a Wear notification when it ends. These notifications are not mirrored to the phone.
- The phone does not sync its watched builds to the watch, so the Data Layer payload stays small.
- Builder's call: avoid a double alert for one build by keeping the two paths disjoint. A build watched on the watch is not also watched on the phone by the handoff.
- Latency (builder's call): tiles and complications refresh on the system schedule, roughly every 15 minutes at best. Fast alerts come only from the bridged phone notification or from a build actively watched on the watch.

### Errors and states

Each screen has loading, empty, error and signed-out states. Errors use short fixed phrases (offline, token rejected, rate limited) and a retry gesture. A 401 shows "Reconnect from phone" and wipes the watch token.

### Testing

- Pure logic (credential handoff mapping, staleness, retry-trigger choice, complication text) is JVM-tested, like the rest of the repo.
- Demo engine drives the watch UI in Compose tests and a Wear emulator pass.
- Data Layer handoff is tested behind an interface with a fake transport. Real two-device pairing is a manual step listed in `docs/release-checklist.md`.

## Left to the builder

- Exact Wear Compose Material 3, Tiles and watchface complication library versions, and the final minimum API.
- Whether `feature:builds` needs the API-layer split described above.
- Icons, tile layout details, and round vs. square screen handling.
- Play Store listing and signing for the watch APK or bundle (outside the repo, as for the phone).

## Open questions for the owner

None.
