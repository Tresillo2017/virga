# Virga

Virga is an unofficial native Android client for Cloudflare. It lets you check and act on your Cloudflare account from a phone: Workers and Workers Builds, Pages, DNS and zones, R2, D1 and KV, with analytics for each.

Virga is not affiliated with, endorsed by, or sponsored by Cloudflare, Inc. Cloudflare, the Cloudflare logo and Cloudflare Workers are trademarks of Cloudflare, Inc.

## Status

Pre-release. Version 0.1.0 (`versionName`), not yet published. Tested on the JVM (see Testing) and on one Android 17 emulator with the demo account, including a minified release build signed with the debug key. Not run on any Android 9 or Android 16 device, not tested against a real production account with a least-privilege token, and not run on a lock screen or with two builds watched at once. The Wear OS companion (below) has not been run on a Wear emulator or a physical watch. It is verified by JVM and Robolectric tests only, its 24h summary tile query has not been run against the live Cloudflare API, and the credential handoff has not been tried over a real Data Layer between two devices. See `docs/release-checklist.md` for what is left before a Play Store release.

## Features by product

| Area | What you can do |
|---|---|
| Home | Account analytics for a 1h, 6h, 24h, 7d or 30d window (requests, cache hit rate, Worker invocations and errors, CPU time), search across products, a "Building now" card, shortcuts. Switch between accounts. |
| Compute (Workers) | List Workers; overview with active deployment and bindings; metrics; deployments and rollback; logs; custom domains; settings (variables, secrets, delete with typed-name confirmation). |
| Compute (Workers Builds) | Build history and detail, live logs, trigger and cancel builds, build configuration. **Watch build** pins a running build to an Android Live Update (or an ongoing notification before Android 16). |
| Compute (Pages) | Projects, deployments and their logs, retry, domains, production branch and environment variables, delete with typed-name confirmation. |
| Domains | Zones with filters; zone overview and analytics; pause, development mode and cache purge; DNS records (search, add, edit, bulk delete, proxy toggle); DNS settings (DNSSEC, BIND export and import). |
| Storage: R2 | Buckets, object browser (folders, upload, download, delete), bucket settings (public access, custom domains, CORS, bucket lock, lifecycle), metrics. |
| Storage: D1 | Databases, table browser, row editing, SQL console, time travel, export, metrics. |
| Storage: KV | Namespaces, keys and values (text, JSON, hex preview), expiration and metadata, bulk import and delete, metrics. |
| Account | Sign in, switch accounts, settings, About. |

## What the API cannot do

Some things are not available through the Cloudflare API, or are only partly available. Virga does not fake them. Where a feature is not in the API, the screen shows an **Open in dashboard** row that opens the Cloudflare dashboard in the browser. Examples:

- Changing zone mode (DNS only or CDN only) and nameservers: the app shows these read-only and links to the dashboard for changes.
- Connecting a Git repository to Workers Builds, and similar setup steps the API does not expose.
- Registrar actions such as domain renewal.
- Anything else the API does not serve is listed with the dashboard link rather than silently omitted.

Some endpoints are beta. Responses are parsed defensively: unknown fields are ignored and optional fields are nullable.

## Build and run

Requirements: Android Studio with JDK 17, Android SDK platform 37, and a device or emulator running Android 9 (API 28) or newer. Notifications for Live Update need Android 16 (API 36) or newer; below that the app uses an ordinary ongoing notification.

```sh
./gradlew :app:installDebug          # install the debug build
./gradlew testDebugUnitTest          # unit tests for every module
./gradlew koverXmlReport             # coverage (XML under each module's build/reports/kover)
./gradlew lintDebug                  # Android Lint
./gradlew assembleRelease            # minified release APK (unsigned; see the release checklist)
```

The release build uses R8 with rules in `app/proguard-rules.pro`. It is unsigned in the repository. The owner adds a signing configuration outside the repo.

## Wear OS companion

A small watch app (`:wear`, Wear OS 4 / API 33 or newer) for checking and acting on Workers Builds from the wrist. It needs the phone app for the first sign-in. It uses the same application id as the phone app, and is not standalone (`com.google.android.wearable.standalone=false`).

**What it does**

- A build tile and a complication showing the latest Workers Build.
- A 24h tile with Worker requests and Worker errors. These are Workers invocations and Workers errors from the account GraphQL analytics, not zone HTTP traffic. It needs a token with analytics access; without it the tile says so.
- A build list and build detail. **Cancel** and **Retry** each sit behind a confirm step. Retry is hidden unless a safe target and trigger can be chosen: the target is the build's commit, else its branch, and the trigger is the one whose branches include the build's branch, else the only trigger.
- **Watch this build** on the watch alerts when a build finishes (a foreground service polls only while a watched build is active; it stops at the end of the build or after 2 hours with a "Stopped watching" notification). The watch does not poll builds watched on the phone. Virga relies on Wear OS's default bridging of the phone's notifications for those and does not guarantee it: it works only while the phone is connected and depends on the user's notification settings, and Wear OS may not bridge the phone's ongoing in-progress notification, so only the plain build-finished notification may reach the watch. A build watched on the watch is not mirrored to the phone.
- The demo account works: choosing **Explore the demo** on the phone hands demo mode to the watch.

**Permissions (watch):** `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` and `POST_NOTIFICATIONS`.

**What it does not do:** DNS, Pages, R2, D1, KV, the SQL console, logs, settings, secrets, account switching, typing or pasting a token on the watch, and standalone sign-in.

**Pairing and handoff**

- The watch app advertises the Wear capability `virga_watch_app`. While the phone is signed in and at least one paired watch (connected or not) advertises it, the phone publishes the token, the account list and the selected account over the Wear Data Layer (item path `/virga/credential`). It republishes on every change and as a 6-hourly heartbeat (WorkManager). With no such watch it writes nothing, deletes any item it wrote earlier and does not schedule the heartbeat. A phone-side listener for capability changes (`WatchCapabilityListenerService`) re-checks when the watch app is installed or removed, even while the phone app is not running.
- The watch encrypts the token with its own Android Keystore key (the same AES-GCM vault design as the phone) and follows the phone's selected account.
- The Data Layer item is stored by Google Play services on the phone as well as on the watch, until it is deleted.
- Signing out on the phone deletes the Data Layer item, and the watch wipes its token. **Disconnect** on the watch's Reconnect screen wipes the watch's copy. Virga does not revoke the Cloudflare API token on sign-out; revoke it in the Cloudflare dashboard if the watch is lost.
- The watch locks itself after 24 hours without a check-in from the phone and shows **Reconnect from phone**. This is the bound when the phone cannot reach the watch to delete the item (for example, the watch is offline). A 401 from the API also wipes the watch token.

**Build and run**

```sh
./gradlew :wear:installDebug         # install on a connected Wear OS 4+ device or emulator
./gradlew :wear:testDebugUnitTest    # watch unit and Robolectric tests
./gradlew :wear:assembleRelease      # minified release APK (unsigned; see the release checklist)
```

To try the handoff on emulators, create a Wear OS emulator and pair it to a phone emulator through Android Studio's device pairing, install `:app` on the phone and `:wear` on the watch, then sign in (or explore the demo) on the phone. Not yet done; see Status.

## Demo account

On the sign-in screen, choose **Explore the demo**. No token is needed. The demo account serves canned responses from `core:network`'s demo engine, and each feature contributes its own routes. Writes in demo mode update in-memory state where that is cheap, and otherwise return a success response. The demo has two accounts (personal and staging), and the Pages, Workers, Domains, R2, D1 and KV screens all have seeded data. Demo requests are logged under the `VirgaDemo` logcat tag when no route matches.

Demo data is invented. Hostnames such as `example.com` and `lantern-docs` are placeholders.

## Sign-in and privacy

- Sign-in uses a Cloudflare API token that you paste. Virga checks it on entry (`/user/tokens/verify`), then reads your accounts.
- The token is encrypted with AES-GCM using a non-exportable Android Keystore key. The ciphertext is stored in DataStore. The token is never shown again after entry.
- The token is sent only in the `Authorization` header, and only to `https://api.cloudflare.com`. It is stored encrypted on the phone and is never logged or exported. Only while a paired watch has the Virga watch app installed, it is also written to the Wear Data Layer (Google Play services, which stores the item on the phone and on the watch) and the watch stores it encrypted with its own Keystore key.
- No analytics, crash reporting or advertising SDKs. Virga has no server of its own.
- Backups are off (`allowBackup="false"`), and backup and device-transfer rules exclude the credential store.
- Permissions: `INTERNET`; `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` and `POST_PROMOTED_NOTIFICATIONS` for the Watch build service and its Live Update.

## Module map

```
:app                    MainActivity, splash, navigation host, bottom bar, Koin start, demo wiring, wearsync (publishes the credential to the watch)
:wear                   Wear OS app: tiles, complication, builds, alerts, handoff receiver (see Wear OS companion)
:core:wearsync          Data Layer handoff contract: payload, codec, staleness policy (no Android APIs in its code)
:core:common            Result and error types, dispatchers, formatters
:core:network           CloudflareApi (REST envelope), GraphQL client, pagination, rate limiting, demo engine
:core:auth              credential vault, session, account selection
:core:navigation        Navigator, route-key base types, cross-feature route keys
:core:ui                theme, components, charts (Compose Canvas), confirmation dialogs, state scaffolds
:feature:account        sign-in, account switcher, settings, About
:feature:home           account home: analytics, search, building-now card
:feature:workers        Worker list and detail
:feature:buildsdata     Workers Builds API, demo routes and watch engine shared by phone and watch (no UI)
:feature:builds         Workers Builds screens, watch service, Live Update (depends on :feature:buildsdata)
:feature:pages          Pages projects, deployments, domains, settings
:feature:domains        zones, zone overview, DNS records and settings
:feature:r2             buckets, objects, bucket settings, metrics
:feature:d1             databases, SQL console, table browser, time travel, export, metrics
:feature:kv             namespaces, keys, values, bulk operations, metrics
```

Feature modules depend only on `core:*`, with one exception: `:feature:builds` may depend on `:feature:buildsdata`, a data-only module with no UI. `:wear` depends on `:core:common`, `:core:network`, `:core:auth`, `:core:wearsync` and `:feature:buildsdata` only. Cross-feature links go through route keys in `core:navigation`. See `docs/architecture.md` and `docs/feature-module-guide.md`.

## Testing

- Unit tests use Ktor `MockEngine` (through `mockCloudflareApi`), `kotlinx-coroutines-test` and Turbine.
- Demo routes are tested through the same repositories the screens use.
- Screens are tested on the JVM with Robolectric Compose flows (`DemoFlow`, see `docs/feature-module-guide.md`, "UI flow tests"). Pixel layout and device behaviour are not covered by these tests.
- Latest run (uncommitted working tree, 2026-11-14): `./gradlew clean assembleDebug testDebugUnitTest lintDebug` passed. `testDebugUnitTest` ran twice (the second with `--rerun`): 1551 tests, 0 failures, 21 skipped each time. Lint passed with no errors.
- Latest run with the Wear OS modules (2026-11-14): `./gradlew testDebugUnitTest lintDebug :app:assembleRelease :wear:assembleRelease` passed. 1828 tests, 0 failures, 21 skipped (245 in `:wear`, 51 in `:feature:buildsdata`, 9 in `:core:wearsync`). The coverage table below predates the Wear OS modules and has not been regenerated.
- Coverage (`./gradlew koverXmlReport`), non-UI LINE coverage per module. Rule: UI classes are excluded when a package segment is `ui`, `brand`, `chart` or `components`, or when the top-level class name ends in `Screen`, `Route`, `Tab`, `Sheet`, `Dialog` or `ComposableSingletons`, or when it is a top-level `*Kt` file in a `ui` package. Generated classes (`$$serializer`, `_Factory`, `_Impl`, `BuildConfig`, `R`) are excluded. Computed from the root Kover XML by a script that sums covered and missed LINE counters per module.

| Module | Non-UI covered / total | Non-UI line coverage |
|---|---|---|
| app | 31 / 194 | 16.0% |
| core:auth | 165 / 193 | 85.5% |
| core:common | 138 / 139 | 99.3% |
| core:navigation | 107 / 109 | 98.2% |
| core:network | 486 / 493 | 98.6% |
| core:testing | 111 / 119 | 93.3% |
| core:ui | 0 / 0 (all UI) | n/a |
| feature:account | 486 / 493 | 98.6% |
| feature:builds | 713 / 937 | 76.1% |
| feature:d1 | 2178 / 2421 | 90.0% |
| feature:domains | 2059 / 2201 | 93.5% |
| feature:home | 1178 / 1193 | 98.7% |
| feature:kv | 1188 / 1333 | 89.1% |
| feature:pages | 1451 / 1585 | 91.5% |
| feature:r2 | 2012 / 2468 | 81.5% |
| feature:workers | 1413 / 1466 | 96.4% |
| **All** | **13716 / 15344** | **89.4%** |

Lowest figures: app (16.0%, not yet examined by line), feature:builds (76.1%) and feature:r2 (81.5%). The 80% target is met by every module except app, builds and r2. The per-class breakdown is not recorded here.
