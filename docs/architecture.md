# Virga: architecture and build contract

Virga is an unofficial native Android client for Cloudflare. This file is the contract every builder works from. The companion research is `docs/case-study-cloudflare-dashboard.md`; read the section for your area before writing code, and treat anything it marks "unconfirmed" as something to check against the live API reference (https://developers.cloudflare.com/api/) before relying on it.

## Decided by the owner

- Native Android, published on the Play Store for other people.
- First version covers Workers (with Workers Builds), Pages, DNS and zones, R2, D1, KV: everything the public API allows, read and write, plus analytics.
- No server. The credential is stored encrypted on the phone and sent only to `api.cloudflare.com` for API calls; only while a paired watch advertises the Virga watch app's capability, it is also written to the Wear Data Layer (an item Google Play services stores on the phone and on the watch), and the watch stores it encrypted with its own Keystore key. The phone talks to `api.cloudflare.com` directly.
- "Watch build" exists for a build that is already running. It pins that build to an Android Live Update.
- Name: Virga. Logo: mark A, three slanted streaks of uneven length fading at the bottom. Animated splash: the streaks draw downward one after another, then the wordmark appears.
- Bottom tabs: Home, Compute, Domains, Storage, Account.

## Decided by the builder (owner has not objected, may change)

- Kotlin, Jetpack Compose, Material 3. Dark theme first, light theme supported. Accent is the logo's blue-violet (`#8E9BFF` on dark, `#3B55D9` on light), background near-black `#0B0E14`. No orange anywhere.
- applicationId and namespace root `com.tomasps.virga`. minSdk 28, compileSdk 37, targetSdk 36.
- Sign-in is a pasted Cloudflare API token, verified on entry. OAuth (PKCE) is a later addition because it needs a client registered and domain-verified by the owner; the credential layer is an interface so it can be added without touching features.
- Koin for dependency injection (no annotation processing). Ktor client on the OkHttp engine with kotlinx.serialization. Navigation 3. Charts drawn with Compose Canvas in `core:ui`, no chart library.
- A built-in demo account backed by canned responses, so the app can be explored and tested with no token and so Play reviewers can get in.
- Things the API cannot do (Workers Issues, Access, Pages rollback if confirmed absent, registrar renewals) appear as an "Open in dashboard" row, never as a silent omission and never as a faked feature.

## Hard rules

- Never log, display after entry, export, or send the token anywhere except the `Authorization` header to `https://api.cloudflare.com`. No analytics, crash reporting or ad SDKs. `android:allowBackup="false"` and data-extraction rules that exclude the credential store.
- Token at rest: AES-GCM with a non-exportable Android Keystore key, ciphertext in DataStore. Do not use `EncryptedSharedPreferences`.
- The word "Cloudflare" and Cloudflare logos do not appear in the app name, package, icon, or user agent. The phrase "Unofficial client for Cloudflare" appears on the sign-in and About screens.
- Every destructive or outage-capable action goes through `core:ui`'s confirmation dialogs. Typed-name confirmation for: delete Worker, delete Pages project, delete zone, delete bucket, delete database, delete KV namespace, D1 time-travel restore. Plain confirm for: delete DNS record, delete object/key, cancel build, rollback, pause zone, purge cache, disable DNSSEC, toggling public access.
- Immutable state: `data class` UI state with `copy`, `StateFlow` out of ViewModels, no mutable collections exposed.
- Files under 400 lines where practical, 800 maximum. Functions under 50 lines.
- Tests first for repositories, parsers, ViewModels and anything with logic. Use Ktor `MockEngine`, `kotlinx-coroutines-test` and Turbine. Target 80% line coverage on non-UI code.
- Parse defensively: `ignoreUnknownKeys = true`, nullable or defaulted fields for anything the reference does not mark required. Several of these APIs are beta.
- No hardcoded user-facing strings in composables; use `strings.xml` in the owning module.

## Modules

```
:app                    MainActivity, splash, nav host, bottom bar, Koin start, demo wiring, `wearsync` package (phone-side credential publisher)
:wear                   Wear OS app (minSdk 33): session, builds list/detail, tiles, complication, watch alerts, demo
:core:wearsync          Data Layer handoff contract shared by phone and watch: HandoffPayload, HandoffCodec, HandoffPaths, StalenessPolicy. No Android APIs in its code
:core:common            Result/error types, dispatchers, formatters (bytes, counts, durations, relative time)
:core:network           CloudflareApi (REST envelope), GraphQL client, pagination, rate limiting, demo engine
:core:auth              credential vault, session, account list and selection
:core:navigation        Navigator, route-key base types, cross-feature route keys
:core:ui                theme, components, charts, confirmation dialogs, state scaffolds
:core:testing           test-only UI-flow harness (DemoFlow): a feature's screens in demo mode, on the JVM. See docs/feature-module-guide.md
:feature:account        sign-in, token guidance, account switcher, settings, about
:feature:home           account home: search, analytics tiles, building now, shortcuts
:feature:workers        Worker list and detail (overview, metrics, deployments, logs, domains, settings)
:feature:buildsdata     Workers Builds api (repository, DTOs, triggers), demo routes and watch engine (registry, policy, store). No UI, no Android service
:feature:builds         Workers Builds list/detail/logs/trigger/cancel screens + watch service + Live Update. Depends on :feature:buildsdata
:feature:pages          Pages projects, deployments, domains, settings
:feature:domains        zones, zone overview and analytics, DNS records, DNS settings, zone toggles
:feature:r2             buckets, objects, bucket settings, metrics
:feature:d1             databases, SQL console, table browser, time travel, export, metrics
:feature:kv             namespaces, keys, values, bulk operations, metrics
```

Feature modules depend only on `core:*`, never on each other, with one exception: `:feature:builds` may depend on `:feature:buildsdata`, a data-only module with no UI. Cross-feature links (for example a Worker's R2 binding opening that bucket) go through route keys declared in `core:navigation`.

### Wear OS modules and dependency rules

- `:wear` is an application module built by the `virga.android.wear` convention plugin (`build-logic`). It applies the phone application convention with the same application id and namespace (the Data Layer only delivers the phone app's items to a watch app with the same id) and a minSdk of 33 (Wear OS 4).
- `:wear` may depend on `:core:common`, `:core:network`, `:core:auth`, `:core:wearsync` and `:feature:buildsdata`. It must not depend on `:core:ui`, `:feature:home`, `:feature:builds` or any other feature, because those carry the phone UI. Where it needs a phone query (the 24h summary), it duplicates the small query instead.
- `:feature:buildsdata` holds what both the phone and the watch need from Workers Builds. It was extracted from `:feature:builds`, which now depends on it. It has no Compose UI and no Android service.
- `:core:wearsync` is the only code both `:app` and `:wear` share for the handoff. Its code uses no Android APIs.
- The phone side of the handoff lives in the `wearsync` package of `:app` (publisher, heartbeat and clear-retry workers, Play Services transport). Features do not know about it.
- Handoff: the watch app advertises the Wear capability `virga_watch_app` (`WatchCapability.NAME` in `:core:wearsync`, kept through resource shrinking by `res/raw/keep.xml`). The phone publishes the credential and accounts as a Data Layer item at `/virga/credential` only while signed in and while `CapabilityClient` (FILTER_ALL, so a watch that is merely out of range still counts) finds a node with that capability; otherwise it deletes any item it wrote and cancels the heartbeat. A transient lookup failure neither publishes nor deletes. A manifest `WearableListenerService` with a `CAPABILITY_CHANGED` filter on `/virga_watch_app` re-evaluates when the app is installed or removed, including while the phone process is not running. Sign-out always deletes the item. The watch encrypts the token with its own Keystore key and treats a check-in older than 24 hours as stale (`StalenessPolicy`).

## Contracts the core modules must provide

`core:network`
- `CloudflareApi`: typed helpers over Ktor for `GET/POST/PUT/PATCH/DELETE` against `/client/v4`, decoding the `{success, errors, messages, result, result_info}` envelope into `ApiResult<T>` (success with optional page info, or a typed `ApiError`: Unauthorized, Forbidden with Cloudflare error codes, NotFound, RateLimited with retry-after, Network, Server, Decode). Raw-body variants for downloads, uploads (streamed, known length), multipart form uploads, and plain-text responses.
- Pagination helpers for both page/per_page and cursor styles.
- A shared rate limiter: token bucket sized well under 1,200 requests per 5 minutes, honours `Retry-After` on 429, and exposes remaining budget so the watch loop can slow down.
- `GraphQlClient`: `suspend fun <T> query(query: String, variables: JsonObject, deserializer): ApiResult<T>` against `/client/v4/graphql`, surfacing GraphQL `errors`.
- Demo mode: a `DemoRouteRegistry` that features contribute canned handlers to (method + path pattern to JSON), served through a Ktor `MockEngine` when the active session is the demo account. Writes in demo mode succeed against in-memory state where cheap, otherwise return a success envelope.

`core:auth`
- `CredentialVault` (Keystore + DataStore), `Credential` sealed type (ApiToken now, OAuth later, Demo).
- `SessionRepository`: `StateFlow<SessionState>` (SignedOut, SignedIn(accounts, selectedAccount)), sign-in by token (calls `/user/tokens/verify`, then `/accounts`), sign-out (wipes vault), select account, enter demo.
- Features read the selected account id from here; never store it themselves.

`core:navigation`
- `Navigator` (`navigate(key)`, `back()`, `openInBrowser(url)`), top-level tab keys, and the cross-feature route keys listed below. A way for a feature to declare its own internal route keys inside its own module, including whatever saved-state serialization registration Navigation 3 needs, without editing `core:navigation`.
- Each feature exposes one function that registers its entries with the app's entry provider, and one Koin module. `:app` calls both. Those two call sites per feature are pre-wired by the foundation against stub screens, so feature builders do not edit `:app`.

Cross-feature route keys: `WorkerDetail(scriptName)`, `BuildDetail(accountId, buildUuid, workerName)`, `PagesProject(projectName)`, `ZoneDetail(zoneId)`, `DnsRecords(zoneId)`, `R2Bucket(bucketName)`, `D1Database(databaseId)`, `KvNamespace(namespaceId)`, `SignIn`, `Settings`.

`core:ui`
- `VirgaTheme` (dark and light), typography, shapes.
- Components: `StatTile` (label, value, delta, optional sparkline), `LineChart`, `BarChart`, `StackedBarChart` (accessible: content descriptions summarising the data), `SectionHeader`, `ListRow`, `StatusDot`, `KeyValueRow`, `CodeBlock` (monospace, selectable, horizontal scroll), `EmptyState`, `ErrorState` (maps `ApiError` to friendly text with retry), `LoadingState`, `OpenInDashboardRow`, `TimeRangePicker` (1h, 6h, 24h, 7d, 30d), `SearchField`.
- `ConfirmDialog` and `TypedNameConfirmDialog`.
- `ScreenScaffold` with top bar, pull-to-refresh, snackbar host, and a `UiState<T>` (Loading, Content, Error) renderer.
- The Virga mark as a vector and the wordmark.

## Watch build

- `feature:builds` owns a foreground service of type `dataSync`, started only from the user's Watch tap while the app is visible.
- Polls `GET /accounts/{id}/builds/builds/{uuid}` about every 5 seconds, slowing down when the rate budget is low or on 429.
- API 36+: a promoted ongoing notification with `Notification.ProgressStyle`, four segments (queued, initializing, running, done), short critical text for the chip, actions Open / Stop watching / Cancel build (cancel asks for confirmation in-app). Checks `canPostPromotedNotifications()` and offers the settings shortcut when promotion is off.
- API 28 to 35: the same information as an ordinary ongoing notification with indeterminate or stepped progress.
- On a terminal state: stop the service, remove the ongoing notification, post one ordinary notification (succeeded / failed / cancelled / skipped). Never repost after the user dismisses.
- Handles `onTimeout`, caps a watch at 2 hours, survives process death by persisting the watched build ids, supports several watched builds in one service.
- Requests `POST_NOTIFICATIONS` at the moment of the first Watch tap, with a rationale.
