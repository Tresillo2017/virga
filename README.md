# Virga

Virga is an unofficial native Android client for Cloudflare. It lets you check and act on your Cloudflare account from a phone: Workers and Workers Builds, Pages, DNS and zones, R2, D1 and KV, with analytics for each.

Virga is not affiliated with, endorsed by, or sponsored by Cloudflare, Inc. Cloudflare, the Cloudflare logo and Cloudflare Workers are trademarks of Cloudflare, Inc.

## Status

Beta. Download the latest APK from [Releases](https://github.com/Tresillo2017/virga/releases); pre-releases are signed with the same key as stable releases, so later versions install as updates. Tested on the JVM (see Testing) and on an Android 17 emulator with the demo account. Not yet tested on Android 9 or Android 16 devices, or against a production account with a least-privilege token. Bug reports are welcome. See `docs/release-checklist.md` for what is left before a Play Store release.

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
./gradlew assembleRelease            # minified release APK (unsigned locally)
```

The release build uses R8 with rules in `app/proguard-rules.pro`. No signing key is in the repository: official releases are signed in CI from repository secrets.

## Releases

Releases are cut by GitHub Actions from a tag: stable (`v1.3.0`) or pre-release (`v1.3.0-alpha.1`, `-beta.1`, `-rc.1`). Run **Prepare release** in the Actions tab to pick the next version and publish it, or push a tag by hand. Each GitHub release carries the APK, the app bundle, the R8 mapping file and SHA-256 checksums. Signing keys come from repository secrets. See `docs/releasing.md`.

## Demo account

On the sign-in screen, choose **Explore the demo**. No token is needed. The demo account serves canned responses from `core:network`'s demo engine, and each feature contributes its own routes. Writes in demo mode update in-memory state where that is cheap, and otherwise return a success response. The demo has two accounts (personal and staging), and the Pages, Workers, Domains, R2, D1 and KV screens all have seeded data. Demo requests are logged under the `VirgaDemo` logcat tag when no route matches.

Demo data is invented. Hostnames such as `example.com` and `lantern-docs` are placeholders.

## Sign-in and privacy

- Sign-in uses a Cloudflare API token that you paste. Virga checks it on entry (`/user/tokens/verify`), then reads your accounts.
- The token is encrypted with AES-GCM using a non-exportable Android Keystore key. The ciphertext is stored in DataStore. The token is never shown again after entry.
- The token is sent only in the `Authorization` header, and only to `https://api.cloudflare.com`. It is never logged, exported or sent anywhere else.
- No analytics, crash reporting or advertising SDKs. Virga has no server of its own.
- Backups are off (`allowBackup="false"`), and backup and device-transfer rules exclude the credential store.
- Permissions: `INTERNET`; `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` and `POST_PROMOTED_NOTIFICATIONS` for the Watch build service and its Live Update.

## Module map

```
:app                    MainActivity, splash, navigation host, bottom bar, Koin start, demo wiring
:core:common            Result and error types, dispatchers, formatters
:core:network           CloudflareApi (REST envelope), GraphQL client, pagination, rate limiting, demo engine
:core:auth              credential vault, session, account selection
:core:navigation        Navigator, route-key base types, cross-feature route keys
:core:ui                theme, components, charts (Compose Canvas), confirmation dialogs, state scaffolds
:feature:account        sign-in, account switcher, settings, About
:feature:home           account home: analytics, search, building-now card
:feature:workers        Worker list and detail
:feature:builds         Workers Builds, watch service, Live Update
:feature:pages          Pages projects, deployments, domains, settings
:feature:domains        zones, zone overview, DNS records and settings
:feature:r2             buckets, objects, bucket settings, metrics
:feature:d1             databases, SQL console, table browser, time travel, export, metrics
:feature:kv             namespaces, keys, values, bulk operations, metrics
```

Feature modules depend only on `core:*`. Cross-feature links go through route keys in `core:navigation`. See `docs/architecture.md` and `docs/feature-module-guide.md`.

## Testing

- Unit tests use Ktor `MockEngine` (through `mockCloudflareApi`), `kotlinx-coroutines-test` and Turbine.
- Demo routes are tested through the same repositories the screens use.
- Screens are tested on the JVM with Robolectric Compose flows (`DemoFlow`, see `docs/feature-module-guide.md`, "UI flow tests"). Pixel layout and device behaviour are not covered by these tests.
- Latest run (uncommitted working tree, 2026-11-14): `./gradlew clean assembleDebug testDebugUnitTest lintDebug` passed. `testDebugUnitTest` ran twice (the second with `--rerun`): 1551 tests, 0 failures, 21 skipped each time. Lint passed with no errors.
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

## Contributing

Bug reports, feature ideas and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first, and report security problems privately as described in [SECURITY.md](SECURITY.md).

## License

Virga is free software, licensed under the [GNU General Public License v3.0](LICENSE).
