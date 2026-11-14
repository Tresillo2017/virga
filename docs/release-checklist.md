# Release checklist: Virga for Google Play

Everything the owner still has to do before a Play Store release. The repository does not contain a signing key (releases are signed in CI, see `docs/releasing.md`), and the app is not yet tested against a real production account. Items marked **Blocker** must be done before upload.

## 1. Signing (done)

- The upload key is in the `VIRGA_KEYSTORE_*` repository secrets, and the Release workflow signs every release with it. Keep a copy of the keystore and its password in a password manager.
- Enroll in Play App Signing when creating the Play listing, and register this key as the upload key.
- Keep the R8 keep rules in `app/proguard-rules.pro` and re-run the minified smoke test after any dependency upgrade.

## 2. Watch build service: Play Console declaration (Blocker)

The Watch build feature runs a foreground service of type `dataSync` (`BuildWatchService`), started only from a user tap while the app is visible. Google Play requires a declaration for foreground service types.

- In Play Console, declare the `dataSync` foreground service type and explain it: the service polls one Cloudflare build status while the user watches it, and stops at the end of the build, at 2 hours, or when the user stops the watch.
- Record a short demo video that shows the user tapping Watch build, the notification and Live Update, and the service stopping. Use the demo account so no secrets appear.
- Confirm the service has `android:foregroundServiceType="dataSync"` in the merged release manifest (it does in this build).
- Check the `POST_PROMOTED_NOTIFICATIONS` permission declaration and the Live Update wording against the current Play policy.

## 3. Data safety form (Blocker)

Answer based on what the app actually does:

- Data collected by the developer: none. Virga has no server and no analytics.
- Data shared with third parties: none by the developer. The user's Cloudflare API token and account data go directly from the phone to `api.cloudflare.com`.
- Encryption in transit: yes, HTTPS only.
- Users can request deletion: yes, by signing out or uninstalling. Signing out wipes the credential vault.
- Sensitive data: the Cloudflare API token, stored on device, encrypted with an Android Keystore key.
- Backups: excluded (`allowBackup="false"` and extraction rules).

Re-check after any change that adds a network host, an SDK, or local storage.

## 4. Listing and disclaimer (Blocker)

- App name: Virga. Do not use the word "Cloudflare" in the app name, the package name, the icon or the user agent. The current user agent is `Virga/<version> (Android; unofficial client)`.
- Short description: "Unofficial client for Cloudflare: check Workers, Pages, DNS, R2, D1 and KV from your phone." The phrase "Unofficial client for Cloudflare" must stay visible.
- Full description disclaimer: "Virga is not affiliated with, endorsed by, or sponsored by Cloudflare, Inc. Cloudflare, the Cloudflare logo and Cloudflare Workers are trademarks of Cloudflare, Inc." The same text is on the About screen.
- Check trademark use in screenshots and the feature graphic. Use the Virga mark, not the Cloudflare logo.
- Write the listing without implying that Cloudflare provides or recommends the app.
- Provide the demo account instructions in Play's "App access" section: "Choose Explore the demo on the sign-in screen. No account is needed."

## 5. Target SDK and platform (Blocker)

- `targetSdk` is 36 and `compileSdk` is 37. Google Play requires a recent target. Confirm the current requirement when you upload, and raise `targetSdk` if needed.
- `minSdk` is 28. Live Update needs Android 16 (API 36) or newer; older devices get an ordinary ongoing notification. Test both paths before release.
- Re-check the behaviour changes for foreground services and notifications for the target SDK.

## 6. Testing against a real account (Blocker)

- Create a dedicated Cloudflare account for testing, with no production zones.
- Create an API token with the least privilege each feature needs: Workers Scripts read and write, Workers Builds, Pages, Zone read and DNS edit, R2 storage, D1, Workers KV. Do not use the Global API key.
- Test sign-in with the token, account switching, a Worker overview, a Workers Builds trigger and watch, a Pages retry, a DNS record edit, an R2 object upload and download, a D1 console query, and a KV value read.
- Test error paths with a token that lacks one permission: confirm the "This token cannot do that" message appears.
- Test rate limiting on a long Watch session.
- Confirm no token appears in logcat during a full session.

## 7. OAuth client registration (optional, later)

Sign-in is by pasted API token today. OAuth (PKCE) would remove the need to paste a token, but it needs:

- A Cloudflare OAuth client registered for Virga, with its redirect URI registered and domain-verified by the owner.
- A decision on the redirect scheme (an app link on a domain the owner controls is preferred over a custom scheme).
- The credential layer already supports a second credential type, so features do not change.

Do not ship a placeholder client ID.

## 8. Known defects and unverified areas (before release)

Ordered by importance. Each item is either still open or was not re-checked in the latest pass; items confirmed fixed in the latest pass are listed under "Fixed since the last list" at the end of this section.

1. **Watch service stop line:** the platform logs "Stop FGS timeout" for `BuildWatchService` on every normal stop. Seen 2 of 2 times on the release build on 2026-11-14 (00:21:27 and 00:23:12, about 20 and 30 seconds after the watch started). The service was gone after each stop and the completion notification was posted, so nothing crashed. The cause is not established. It may be the platform's message for a `dataSync` foreground service that stops itself, which is an inference and not confirmed. Removing it would need a different service design. The owner decides whether that is worth it.
2. **Watch service on the release build, earlier symptom:** three taps (19:57, 19:58 and 20:02 local time, earlier build) were followed by "Stop FGS timeout" about 5 seconds after the tap, with no ongoing notification. Not reproduced in the two release cycles on 2026-11-14. Not diagnosed.
3. **Unexplained second FGS start:** each Watch tap logs two "Background started FGS" entries but one `onStartCommand`. Seen again on 2026-11-14. Not explained.
4. **Cold start is slow:** 9 to 12 seconds on the emulator under host load (load average 13 to 22), against 3 to 5 seconds in an earlier baseline. There is no equal-load baseline, so the cause is not separated from load. Koin is started on the main thread; moving it needs a readiness gate that also covers `BuildWatchService`.
5. **Splash on device:** the wordmark and the first app frame have not been captured on the device. The JVM tests (`AnimatedSplashTest`, `SplashGateTest`) show the wordmark is painted and the hand-off timing is right.
6. **Landscape rotation:** the screen went black after rotation on several screens in an earlier pass. Re-checked on 2026-11-14: Worker overview and build detail held their state and redrew. The other screens were not re-checked.
7. **Font scale change while running:** a black screen until relaunch (earlier pass, not re-checked).
8. **Live Update not fully captured:** the status-bar chip, lock-screen behaviour, two builds at once, and the Worker overview and Home "Stop watching" paths were not checked. The denied-notification install was not run. The progress segment colours are inside parcelled bundles and are unverified.
9. **Watch button on Overview:** the "Watch build" button does not show when the build is already watched (earlier pass, not re-checked).
10. **Not reached on a device:** create, delete and rollback flows in R2, KV and Pages; the Workers Deployments split editor is confirmed for a gradual split only. Workers variables cannot be added in the app (read-only, "Change them in the dashboard").
11. **Demo state is in memory:** writes reset on relaunch.
12. **Layout items open (Sweep A and B):** monospace values wrap mid-token (Pages URL, validation record names); Pages domain action buttons sit about 12 dp right of the hostname; chart titles are not visible (only the legend names the series); build supporting text orphans "s" on narrow rows; R2 bucket settings (Public URL, custom domains, lock, CORS, lifecycle) are bare rows; KV namespace subtitles show full hex ids; KV key detail "No metadata." sits on the gutter; the KV keys "Open in dashboard" row is inset differently; the D1 tables "entries" row is bare; D1 time travel shows a raw bookmark id; D1 settings show "Storage version: production" as a raw enum-style value; the Worker overview lacks the domain subtitle, Visit pill and Watch/View logs row in demo state.
13. **Screenshots:** seven Part A captures predate the header-inset revert (`worker-overview-dark`, `worker-deployments-dark`, `worker-domains-dark`, `worker-settings-dark`, `build-config-dark`, `settings-dark`, `settings-light`) and need re-capturing. `pages-deployment-detail-dark` predates the promote-card gap. `worker-logs-dark` was not captured because the app has no Worker logs screen. `docs/screenshots/features/r2-delete-bucket-typed-name-dark.png` overwrote a tracked file of the same name; restore the old one with `git checkout` if wanted. The Sweep B deletion of `domains-zone-overview-7d-dark.png` was a judgement call (it showed a different window).
14. **Not verified on any device other than the Android 17 emulator:** Android 9 (API 28), Android 16 (API 36, Live Update path) and any real phone.
15. **Lint:** `lintDebug` passes with no errors. Warnings remain: `PluralsCandidate`, `UseKtx`, `InlinedApi` for `POST_NOTIFICATIONS` (guarded at runtime) and string-style warnings. Not re-counted in this pass.
16. **Coverage gaps:** non-UI line coverage is 76.1% in `feature:builds` and 81.5% in `feature:r2`, and 16.0% in `app` (not examined by line). Method and table in the README.
17. **Unexplained system error during relaunch:** on 2026-11-14 at 00:25:41 the framework logged "Channel is unrecoverably broken" for `MainActivity`, during agent-driven relaunches. Not reproduced without relaunches. Not investigated.

Fixed since the last list (confirmed on the emulator on 2026-11-14 or by a test): the demo BIND import now returns records (A and CNAME verified in the demo); the BIND export writes SRV and CAA values (`DemoBindExportTest`); the Home analytics header alignment, the Compute and Storage row grouping, the chart axis labels, the light-theme status-bar icons and the system back behaviour (`ShellBackTest`). The flaky Home flow test (described under the flakiness note below) is fixed.

Flakiness (removed by changing timing, not by retrying): `HomeDemoFlowTest` "domains show a friendly status word, never the raw word, and See all opens the domains list" failed 1 of 6 runs before the fix. Its helper scrolled to a row and then asserted it was displayed without waiting for it to be displayed, so a later section load could move the row out of view. The list helper now waits until the row is displayed before asserting, and "one Worker is building" waits for its status dot. After the change: the Home class passed 5 of 5 runs and the full suite passed. Before the change the same class passed 5 of 5 in an earlier series, so the failure rate is low and a single failure was observed. `KvImportViewModelTest` passed 5 of 5 and uses virtual time throughout, so no change was made.

## 9. Deferred items

- Signed-URL downloads from non-API hosts and SigV4 signing for R2's S3 endpoint (needed for large R2 uploads and temporary credentials).
- Workers Issues, Access, and registrar renewals: shown as "Open in dashboard" rows, not built into the app.
- OAuth sign-in (section 7).
- Two Watch builds at once, lock-screen behaviour and notification action truncation ("Stop watching" truncates in the shade).

## 10. Before you tag the release

- `./gradlew clean testDebugUnitTest lintDebug assembleRelease` passes.
- A signed release build passes the smoke test on at least one Android 16 device and one Android 9 device.
- README and this checklist are current.
- Screenshots for the listing are taken from the demo account and contain no real account data.
- Device matrix still open (item 14 of section 8): a signed release on Android 9 and on Android 16, with the Watch flow run twice on each, and the stop-line question in item 1 settled.

Only the owner can do: create and hold the upload key (section 1); make the Play Console declarations and the demo video (sections 2 to 4); run the real-account test with a least-privilege token (section 6); register the OAuth client if it is wanted (section 7); decide whether the Watch stop line and its foreground-service design are acceptable for release (section 8, item 1).
