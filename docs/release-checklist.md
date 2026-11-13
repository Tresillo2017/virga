# Release checklist: Virga for Google Play

Everything the owner still has to do before a Play Store release. The repository does not contain a release signing key, and the app is not yet tested against a real production account. Items marked **Blocker** must be done before upload.

## 1. Signing (Blocker)

- Create an upload key in a password manager or hardware-backed store. Never commit it, and never put it in `gradle.properties`.
- Add a release `signingConfig` in a local-only file (for example `keystore.properties` in `.gitignore`) or enroll in Play App Signing and upload with an upload key.
- Confirm the signed APK or AAB installs over a debug build only if the keys match. Expect a fresh install otherwise.
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

These come from the verification passes. The consolidated list is in the final verification report. The most important:

- **Watch service on the release build:** on the minified build, three logged Watch taps (19:57, 19:58 and 20:02 local time) were followed by Android's "Stop FGS timeout" about 5 seconds later, and the service stopped with no ongoing notification. Two later taps on the same build worked. Debug builds worked in both taps tried. Not diagnosed. Fix and retest before release.
- **Watch button state:** the Overview "Watch build" button does not show when the build is already watched.
- **Splash:** the wordmark was not visible in the captures taken. The splash does not match the specification.
- **Landscape rotation:** the screen goes black after rotation on several screens.
- **Font scale change while running:** a black screen until relaunch.
- **Not reached in verification:** several create, delete and rollback flows in R2, D1, KV and Pages, Import BIND (picker only), and the Workers Deployments split editor.
- **Demo gaps:** BIND import returns no records in the demo.
- **Lint warnings** remain (see the final report): `PluralsCandidate`, `UseKtx`, `InlinedApi` for `POST_NOTIFICATIONS` (guarded at runtime), and a few string-style warnings.

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
