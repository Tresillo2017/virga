# Virga Wear OS Companion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Wear OS app (`:wear`) that receives the Cloudflare token from the phone and lets the owner glance at Workers Builds and the 24h summary, and cancel or retry a build.

**Architecture:** The phone publishes a credential handoff over the Wear Data Layer. The watch stores it with the existing `CredentialVault` and calls Cloudflare itself through `core:network`. Shared pure logic lives in a new `:core:wearsync` module. The builds data layer is extracted from `:feature:builds` into `:feature:buildsdata` so the watch does not depend on phone UI.

**Tech Stack:** Kotlin, Compose, Wear Compose Material 3, Wear Tiles and ProtoLayout, watch face complications data source, Play Services Wearable (Data Layer), Koin, DataStore, WorkManager (phone heartbeat), JUnit and Robolectric as in the rest of the repo.

**Spec:** `docs/superpowers/specs/2026-11-14-wear-os-companion-design.md`

## Global Constraints

- Minimum Wear OS API 33 (Wear OS 4). The phone app keeps minSdk 28.
- The `:wear` application id is `com.tomasps.virga`, the same as the phone app.
- Reuse `core:common`, `core:network`, `core:auth` and the builds API layer. Do not fork them.
- The token is never logged, never shown, never written outside the vault and the Data Layer item. `HandoffPayload.toString` is redacted.
- Offline window: `MAX_OFFLINE_MILLIS = 24 * 60 * 60 * 1000`, a named constant. After it the watch stops all actions and reads and shows "Reconnect from phone".
- A 401 from Cloudflare wipes the watch token immediately.
- The watch follows the phone's selected account. It has no account switcher.
- Retry is hidden when no safe target can be chosen.
- Cancel and Retry each sit behind a confirm step.
- Tiles and complications refresh on the system schedule (about 15 minutes at best) and show stale text with an age rather than blank.
- Pure logic is JVM-tested. Phone behavior and its 1552 existing tests must stay green after Task 1. Check with `.hill-climb/md3-audit/guard.sh` if present, otherwise `./gradlew testDebugUnitTest`.
- Unofficial-app and trademark wording from the README applies to the watch listing.
- Library versions: use the latest stable at execution time, add them to `gradle/libs.versions.toml`, and confirm with a Gradle sync. Do not guess versions from memory.

## Review Focus

- Phone signs out while the watch is offline: the watch must stop acting after 24h without a check-in (Task 4).
- A payload from an unknown newer `version` or with corrupt bytes: ignored, the watch keeps its current state (Task 2, Task 4).
- Account list changes so the selected account disappears: the watch must not call the API with a stale account id (Task 4).
- A worker with zero builds, or a build with no branch and no commit: lists show it, Retry is hidden (Task 5).
- Cloudflare rate limiting or a 5xx during a tile refresh: the tile shows the last good value with its age, not an error blank (Task 8).

---

### Task 1: Extract the builds data layer into `:feature:buildsdata`

**Files:**
- Modify: `settings.gradle.kts` (add `include(":feature:buildsdata")`)
- Create: `feature/buildsdata/build.gradle.kts`
- Move (package names unchanged): `feature/builds/src/main/kotlin/.../feature/builds/api/*`, `.../builds/demo/*`, and from `.../builds/watch/`: `WatchEngine.kt`, `WatchRegistry.kt`, `WatchStore.kt`, `WatchedBuild.kt`, `WatchPolicy.kt`, `DataStoreWatchStore.kt` to `feature/buildsdata/src/main/kotlin/...` at the same package paths
- Move tests: `api/*Test.kt`, `demo/BuildsDemoRoutesTest.kt`, `watch/WatchEngineTest.kt`, `watch/DataStoreWatchStoreTest.kt`, plus `testing/TestBuilds.kt` and `testing/MainDispatcherRule.kt` if those tests use them (copy if `:feature:builds` tests also need them)
- Modify: `feature/builds/build.gradle.kts` (add `api(project(":feature:buildsdata"))`)

**Interfaces:**
- Consumes: nothing new.
- Produces: module `:feature:buildsdata` exposing `BuildsRepository`, `BuildRecord`, `BuildTarget`, `TriggerRef`, `WorkerRef`, `WatchEngine`, `WatchRegistry`, `WatchStore`, `DataStoreWatchStore`, `WatchedBuild`, `WatchEvent`, `BuildFetcher`, `BuildsDemoRoutes`, all at their current packages. `:feature:builds` still compiles unchanged because of the `api(...)` dependency.

- [ ] **Step 1: Run the baseline.** Run `./gradlew :feature:builds:testDebugUnitTest`. Expected: PASS. Record the count of passing tests.
- [ ] **Step 2: Create `feature/buildsdata/build.gradle.kts`** with the `virga.android.library` plugin and dependencies: `api(project(":core:common"))`, `api(project(":core:network"))`, `implementation(libs.androidx.datastore.preferences)`, `implementation(libs.kotlinx.serialization.json)`, `implementation(libs.kotlinx.coroutines.core)`, `testImplementation(testFixtures(project(":core:network")))`. The namespace defaults to `com.tomasps.virga.feature.buildsdata`.
- [ ] **Step 3: Move the files listed above with `git mv`**, keep their package declarations, and add the module to `settings.gradle.kts` and the `api` dependency to `:feature:builds`.
- [ ] **Step 4: Run `./gradlew :feature:buildsdata:testDebugUnitTest :feature:builds:testDebugUnitTest :app:testDebugUnitTest`.** Expected: PASS, and the combined test count of the two modules equals the baseline from Step 1.
- [ ] **Step 5: Commit** `git commit -m "refactor: extract builds data layer into feature:buildsdata"`.

---

### Task 2: `:core:wearsync` handoff model and staleness rule

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/wearsync/build.gradle.kts`, `core/wearsync/src/main/kotlin/com/tomasps/virga/core/wearsync/HandoffPayload.kt`, `HandoffCodec.kt`, `HandoffPaths.kt`, `StalenessPolicy.kt`, `HandoffTransport.kt`
- Test: `core/wearsync/src/test/kotlin/com/tomasps/virga/core/wearsync/HandoffCodecTest.kt`, `StalenessPolicyTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces (package `com.tomasps.virga.core.wearsync`):
  - `@Serializable data class HandoffAccount(val id: String, val name: String)`
  - `@Serializable data class HandoffPayload(val version: Int, val token: String?, val isDemo: Boolean, val accounts: List<HandoffAccount>, val selectedAccountId: String?, val sentAtMillis: Long)` with `toString()` redacting `token`; `const val CURRENT_VERSION = 1` on its companion.
  - `sealed interface HandoffDecode { data class Ok(val payload: HandoffPayload); data object Invalid }` and `object HandoffCodec { fun encode(payload: HandoffPayload): ByteArray; fun decode(bytes: ByteArray): HandoffDecode }`. `decode` returns `Invalid` for corrupt bytes and for `version > CURRENT_VERSION`.
  - `object HandoffPaths { const val CREDENTIAL = "/virga/credential" }`
  - `object StalenessPolicy { const val MAX_OFFLINE_MILLIS: Long; fun isStale(lastCheckInMillis: Long?, nowMillis: Long): Boolean }`. `null` is stale. Exactly 24h is not stale, 24h plus 1 ms is.
  - `interface HandoffTransport { suspend fun publish(payload: HandoffPayload); suspend fun clear() }` (phone side).

- [ ] **Step 1: Write failing tests** in `HandoffCodecTest`: `roundTripsAllFields`, `toStringNeverContainsToken` (assert `"s3cret"` absent), `corruptBytesDecodeToInvalid`, `newerVersionDecodesToInvalid`, `demoPayloadWithNullTokenRoundTrips`. In `StalenessPolicyTest`: `nullCheckInIsStale`, `exactly24hIsNotStale`, `24hPlusOneMsIsStale`, `futureCheckInIsNotStale` (clock skew).
- [ ] **Step 2: Run** `./gradlew :core:wearsync:testDebugUnitTest`. Expected: FAIL (types missing).
- [ ] **Step 3: Implement** the types above with kotlinx.serialization JSON, `ignoreUnknownKeys = true`.
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add wearsync handoff model`.

---

### Task 3: Phone-side publisher in `:app`

**Files:**
- Modify: `gradle/libs.versions.toml` (play-services-wearable, androidx-work-runtime-ktx), `app/build.gradle.kts`, the app's Koin module file
- Create (in `app/src/main/kotlin/com/tomasps/virga/wearsync/`): `HandoffPublisher.kt`, `GmsHandoffTransport.kt`, `HandoffHeartbeatWorker.kt`
- Test: `app/src/test/kotlin/com/tomasps/virga/wearsync/HandoffPublisherTest.kt`

**Interfaces:**
- Consumes: `SessionRepository.state`, `CredentialVault.read()`, `Credential.ApiToken`, `Credential.Demo`, `HandoffTransport`, `HandoffPayload`.
- Produces: `class HandoffPublisher(session: SessionRepository, vault: CredentialVault, transport: HandoffTransport, clock: () -> Long)` with `fun start(scope: CoroutineScope)` (collects `session.state`, publishes on every `SignedIn` change, calls `transport.clear()` on `SignedOut`, ignores `Restoring`) and `suspend fun heartbeat()` (republishes the current state with a fresh `sentAtMillis`). `GmsHandoffTransport` writes the encoded payload to a `PutDataRequest` at `HandoffPaths.CREDENTIAL` and deletes the item on `clear()`. `HandoffHeartbeatWorker` is a periodic `CoroutineWorker` every 6 hours (`HEARTBEAT_INTERVAL_HOURS = 6`) that calls `heartbeat()`, scheduled when signed in and cancelled when signed out.

- [ ] **Step 1: Write failing tests** against a fake `HandoffTransport` and a fake `SessionRepository`: `signedInPublishesTokenAccountsAndSelection`, `demoPublishesNoTokenAndDemoFlag`, `signOutClearsTransport`, `restoringPublishesNothing`, `accountSwitchRepublishesWithNewSelection`, `heartbeatRepublishesWithFreshTimestamp`, `unreadableVaultPublishesNothing` (vault returns null while signed in: no publish, no crash).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*HandoffPublisherTest'`. Expected: FAIL.
- [ ] **Step 3: Implement `HandoffPublisher`** and the GMS transport and worker. Start the publisher from the existing app startup alongside the builds gateway (`createdAtStart` style). Add the Koin bindings.
- [ ] **Step 4: Run the same command, then `./gradlew :app:assembleDebug`.** Expected: PASS and a successful build.
- [ ] **Step 5: Commit** `feat: publish credential handoff to Wear OS`.

---

### Task 4: `:wear` module, convention plugin and watch session

**Files:**
- Modify: `build-logic/build.gradle.kts` (register `virga.android.wear`), `settings.gradle.kts` (`include(":wear")`)
- Create: `build-logic/src/main/kotlin/com/tomasps/virga/buildlogic/VirgaAndroidWearPlugin.kt`, `wear/build.gradle.kts`, `wear/src/main/AndroidManifest.xml`, `wear/src/main/kotlin/com/tomasps/virga/wear/session/WatchSessionRepository.kt`, `WatchSessionState.kt`, `WatchSessionPrefs.kt`, `HandoffListenerService.kt`, `WearKoinModule.kt`, `VirgaWearApp.kt`
- Test: `wear/src/test/kotlin/com/tomasps/virga/wear/session/WatchSessionRepositoryTest.kt`

**Interfaces:**
- Consumes: `HandoffPayload`, `StalenessPolicy`, `CredentialVault`, `Credential`, `ActiveCredential`, `DemoMode` (from `core:network`), `CloudflareAccount`.
- Produces:
  - Plugin `virga.android.wear`: same as the application plugin but `minSdk = 33`, same application id, and the manifest declares `<uses-feature android:name="android.hardware.type.watch" />`.
  - `sealed interface WatchSessionState { data object Restoring; data object NotConnected; data object Stale; data class Connected(val accountId: String, val isDemo: Boolean) }`.
  - `class WatchSessionRepository(vault: CredentialVault, active: ActiveCredential, demoMode: DemoMode, prefs: WatchSessionPrefs, clock: () -> Long)` with `val state: StateFlow<WatchSessionState>`, `suspend fun restore()`, `suspend fun apply(payload: HandoffPayload)`, `suspend fun clear()`, `suspend fun onUnauthorized()`, `suspend fun refreshStaleness()`.
  - `WatchSessionPrefs` stores `lastCheckInMillis`, `selectedAccountId`, `accountIds` in plain DataStore (not secret). The token lives only in `CredentialVault` backed by a DataStore named `virga_wear_session`.
  - `HandoffListenerService` is a `WearableListenerService` that decodes changed items at `HandoffPaths.CREDENTIAL` and calls `apply`, and calls `clear()` on `TYPE_DELETED`.

- [ ] **Step 1: Write failing tests** with a fake vault cipher: `applyStoresTokenAndConnects`, `applyDemoStoresDemoCredentialAndEnablesDemoMode`, `applyWithSelectedAccountMissingFromListIsNotConnected` (stale id never reaches the API), `applyOverwritesPreviousSelection`, `clearWipesVaultAndGoesNotConnected`, `staleAfter24hWithoutCheckIn`, `connectedAtExactly24h`, `applyRefreshesCheckIn`, `unauthorizedWipesVault`, `restoreWithEmptyVaultIsNotConnected`, `restoreWithStaleCheckInIsStaleAndStopsUsingToken` (`ActiveCredential.bearerToken()` is null), `invalidPayloadLeavesStateUnchanged`.
- [ ] **Step 2: Run** `./gradlew :wear:testDebugUnitTest --tests '*WatchSessionRepositoryTest'`. Expected: FAIL (module missing, then types missing).
- [ ] **Step 3: Create the plugin, module and manifest, then implement `WatchSessionRepository`.** The Wear Koin module reuses `AesGcmCipher`, `AndroidKeystoreKey`, `CredentialVault`, `ActiveCredential` and binds `CredentialProvider`. It does not bind `DefaultSessionRepository`.
- [ ] **Step 4: Run the tests again, then `./gradlew :wear:assembleDebug`.** Expected: PASS and a successful build. Confirm `:wear` pulls no `:core:ui` or `:feature:builds` dependency (`./gradlew :wear:dependencies --configuration debugRuntimeClasspath`).
- [ ] **Step 5: Commit** `feat: add wear module with watch session`.

---

### Task 5: Watch build data and Retry planning

**Files:**
- Create (in `wear/src/main/kotlin/com/tomasps/virga/wear/builds/`): `WearBuildsSource.kt`, `RetryPlanner.kt`
- Test: `wear/src/test/kotlin/com/tomasps/virga/wear/builds/WearBuildsSourceTest.kt`, `RetryPlannerTest.kt`

**Interfaces:**
- Consumes: `BuildsRepository` (`workers`, `builds`, `build`, `cancel`, `triggers`, `startBuild`), `BuildRecord.toSummary()`, `BuildSummary`, `TriggerRef`, `BuildTarget`, `ApiResult`.
- Produces:
  - `class WearBuildsSource(repository: BuildsRepository)` with `suspend fun recent(accountId: String): ApiResult<List<BuildSummary>>` (first page of up to `MAX_WORKERS_SCANNED = 20` workers, 5 builds each, newest first, at most `MAX_LISTED = 10`; a worker whose builds fail is skipped; fails only if the worker list fails), `suspend fun detail(accountId: String, build: BuildSummary): ApiResult<BuildSummary>`, `suspend fun cancel(accountId: String, buildUuid: String): ApiResult<Unit>`, `suspend fun retry(accountId: String, build: BuildSummary): ApiResult<String>`.
  - `object RetryPlanner { fun plan(build: BuildSummary, triggers: List<TriggerRef>): RetryPlan? }` where `data class RetryPlan(val triggerUuid: String, val target: BuildTarget)`. Rules: target is `Commit(commitHash)` if present, else `Branch(branch)`, else `null`. Trigger is the one whose branch matches `build.branch` if `TriggerRef` exposes a branch, else the only trigger if exactly one, else `null`.

- [ ] **Step 1: Write failing tests** with the demo engine or Ktor mock engine already used in `BuildsRepositoryTest`: `recentMergesWorkersNewestFirstAndCapsAtTen`, `workerWithNoBuildsIsSkipped`, `oneWorkerFailingDoesNotFailTheList`, `workerListFailureFails`, `cancelCallsCancelEndpoint`. In `RetryPlannerTest`: `prefersCommitOverBranch`, `branchOnlyUsesBranch`, `noBranchNoCommitIsNull`, `singleTriggerIsUsed`, `multipleTriggersPickBranchMatch`, `multipleTriggersNoMatchIsNull`, `noTriggersIsNull`.
- [ ] **Step 2: Run** `./gradlew :wear:testDebugUnitTest --tests '*wear.builds*'`. Expected: FAIL.
- [ ] **Step 3: Implement both types.** Open `Triggers.kt` first to see what `TriggerRef` exposes and adapt the branch-match rule to it.
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add watch builds source and retry planner`.

---

### Task 6: Watch presentation logic (status text, age, complication, tile model)

**Files:**
- Create (in `wear/src/main/kotlin/com/tomasps/virga/wear/present/`): `BuildStatusText.kt`, `AgeText.kt`, `ComplicationModel.kt`, `BuildTileModel.kt`
- Test: matching `*Test.kt` files under `wear/src/test/kotlin/com/tomasps/virga/wear/present/`

**Interfaces:**
- Consumes: `BuildSummary`, `BuildStatus`, `BuildOutcome`, `Instant`.
- Produces:
  - `enum class BuildVisualState { RUNNING, PASSED, FAILED, CANCELED, UNKNOWN }` and `fun BuildSummary.visualState(): BuildVisualState` (active statuses are RUNNING, `SUCCESS` is PASSED, `FAIL` is FAILED, `CANCELED` and `SKIPPED` are CANCELED, otherwise UNKNOWN).
  - `fun ageText(from: Instant, now: Instant): String` giving `now`, `5m`, `3h`, `2d`; future instants give `now`.
  - `data class ComplicationModel(val shortText: String, val title: String, val state: BuildVisualState)` and `fun complicationFor(latest: BuildSummary?, now: Instant): ComplicationModel`; `null` gives `"No builds"`.
  - `data class BuildTileModel(val workerName: String, val state: BuildVisualState, val detail: String, val isStale: Boolean)` and `fun tileFor(latest: BuildSummary?, fetchedAt: Instant?, now: Instant, staleAfter: Duration = STALE_AFTER)` where `STALE_AFTER = Duration.ofMinutes(30)`.

- [ ] **Step 1: Write failing tests:** `runningMapsFromAllActiveStatuses`, `unknownOutcomeIsUnknown`, `ageBoundaries` (59s gives `now`, 60s gives `1m`, 59m, 60m gives `1h`, 24h gives `1d`), `futureAgeIsNow`, `complicationForNullIsNoBuilds`, `longWorkerNameIsKeptInTitleAndShortTextStaysUnderSevenChars`, `tileMarksStaleAfterThirtyMinutes`.
- [ ] **Step 2: Run** `./gradlew :wear:testDebugUnitTest --tests '*wear.present*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add watch presentation models`.

---

### Task 7: Watch app screens

**Files:**
- Create (in `wear/src/main/kotlin/com/tomasps/virga/wear/ui/`): `WearNav.kt`, `BuildListScreen.kt`, `BuildDetailScreen.kt`, `ConfirmScreen.kt`, `ConnectScreen.kt`, `BuildListViewModel.kt`, `BuildDetailViewModel.kt`, `WearTheme.kt`; `wear/src/main/kotlin/com/tomasps/virga/wear/MainActivity.kt`
- Test: `wear/src/test/kotlin/com/tomasps/virga/wear/ui/BuildListViewModelTest.kt`, `BuildDetailViewModelTest.kt`, `WearFlowTest.kt`

**Interfaces:**
- Consumes: `WatchSessionRepository.state`, `WearBuildsSource`, `RetryPlanner`, `BuildsRepository.triggers`, `visualState`, `ageText`.
- Produces: screens and ViewModels using Wear Compose Material 3 (`ScalingLazyColumn`, `AppScaffold`, `SwipeDismissableNavHost` or the current Wear navigation equivalent). `BuildDetailViewModel` exposes `val state: StateFlow<DetailState>` and `fun cancel()`, `fun retry()`, where `DetailState` has `build`, `canCancel: Boolean`, `retryPlan: RetryPlan?`, and `action: ActionState` (`Idle`, `Confirming(kind)`, `Running`, `Failed(message)`, `Done`). `ConnectScreen` shows "Open Virga on your phone to connect" with no input. A `Stale` session shows "Reconnect from phone" and does not call the API.

- [ ] **Step 1: Write failing tests:** `listShowsLoadingThenBuilds`, `emptyAccountShowsEmptyState`, `offlineShowsRetryableError`, `unauthorizedCallsSessionOnUnauthorized`, `runningBuildOffersCancelNotRetry`, `finishedBuildOffersRetryWhenPlanExists`, `finishedBuildWithoutPlanHidesRetry`, `cancelRequiresConfirmBeforeCalling` (no API call before confirm), `notConnectedShowsConnectScreen`, `staleShowsReconnectAndNeverCallsApi`. Flow test with the demo engine: open app, see list, open a running build, cancel, confirm, see done.
- [ ] **Step 2: Run** `./gradlew :wear:testDebugUnitTest --tests '*wear.ui*'`. Expected: FAIL.
- [ ] **Step 3: Implement the screens and ViewModels.** Short fixed error phrases: "Offline", "Token rejected", "Rate limited". Round and square screens must both lay out (use scaling lists, no fixed pixel widths).
- [ ] **Step 4: Run the same command, then install on a Wear emulator** (`./gradlew :wear:installDebug`), enter demo through a sample handoff, and capture a screenshot of list and detail.
- [ ] **Step 5: Commit** `feat: add watch build list and detail screens`.

---

### Task 8: Build tile and complication services

**Files:**
- Create (in `wear/src/main/kotlin/com/tomasps/virga/wear/surfaces/`): `LatestBuildCache.kt`, `BuildTileService.kt`, `BuildComplicationService.kt`; manifest entries in `wear/src/main/AndroidManifest.xml`
- Test: `wear/src/test/kotlin/com/tomasps/virga/wear/surfaces/LatestBuildCacheTest.kt`, `BuildSurfaceRefreshTest.kt`

**Interfaces:**
- Consumes: `WearBuildsSource.recent`, `tileFor`, `complicationFor`, `WatchSessionRepository.state`.
- Produces: `class LatestBuildCache(store: DataStore<Preferences>)` with `suspend fun save(build: BuildSummary, fetchedAt: Instant)` and `suspend fun load(): CachedBuild?`; `class BuildSurfaceRefresher(source, session, cache, clock)` with `suspend fun refresh(): SurfaceData` (`SurfaceData(latest: BuildSummary?, fetchedAt: Instant?, status: SurfaceStatus)` where `SurfaceStatus` is `Fresh`, `Stale`, `NotConnected`, `Reconnect`). `BuildTileService` (`TileService`) and `BuildComplicationService` (`SuspendingComplicationDataSourceService`) render from `refresh()`. Tapping the tile opens the build detail, tapping the complication opens the list.

- [ ] **Step 1: Write failing tests:** `successfulRefreshSavesAndReturnsFresh`, `rateLimitedRefreshReturnsLastCachedAsStale`, `serverErrorRefreshReturnsLastCachedAsStale`, `failureWithEmptyCacheReturnsStaleWithNoBuild` (shows "No data", not a crash), `notConnectedSessionReturnsNotConnectedWithoutNetwork`, `staleSessionReturnsReconnectWithoutNetwork`, `unauthorizedWipesSessionAndReturnsReconnect`.
- [ ] **Step 2: Run** `./gradlew :wear:testDebugUnitTest --tests '*wear.surfaces*'`. Expected: FAIL.
- [ ] **Step 3: Implement the cache, refresher and the two services.** The tile freshness interval is 15 minutes. Register the complication types `SHORT_TEXT` and `LONG_TEXT`.
- [ ] **Step 4: Run the tests, then add the tile and complication on a Wear emulator** and capture screenshots for demo data and for the not-connected state.
- [ ] **Step 5: Commit** `feat: add build tile and complication`.

---

### Task 9: Watch-started build alerts

**Files:**
- Create (in `wear/src/main/kotlin/com/tomasps/virga/wear/alerts/`): `WearWatchService.kt`, `WearBuildNotifier.kt`, `WatchStarter.kt`; modify `BuildDetailScreen.kt` to add a Watch action
- Test: `wear/src/test/kotlin/com/tomasps/virga/wear/alerts/WearWatchFlowTest.kt`, `WearBuildNotifierTest.kt`

**Interfaces:**
- Consumes: `WatchEngine`, `WatchRegistry`, `DataStoreWatchStore`, `BuildFetcher`, `WatchEvent`, `WatchSessionRepository`.
- Produces: `WearWatchService` (foreground service while at least one watch is registered, stops when the registry is empty or the session is no longer `Connected`). `WearBuildNotifier` posts a notification on `WatchEvent.Finished` and `WatchEvent.Failed`, with a content intent that opens that build's detail, and marks it local-only so the phone does not mirror it. A watch started on the watch is not started on the phone.

- [ ] **Step 1: Write failing tests:** `finishedEventPostsNotificationWithOutcomeText`, `failedPollDoesNotPostFinished`, `notificationIsLocalOnly`, `serviceStopsWhenRegistryEmpties`, `serviceStopsWhenSessionBecomesStale`, `startWatchingAlreadyWatchedBuildDoesNotDuplicate`.
- [ ] **Step 2: Run** `./gradlew :wear:testDebugUnitTest --tests '*wear.alerts*'`. Expected: FAIL.
- [ ] **Step 3: Implement,** reusing `WatchEngine` and `WatchRegistry` as the phone does in `BuildWatchService`. Add the foreground service type and permissions to the `:wear` manifest.
- [ ] **Step 4: Run the tests, then on a Wear emulator start a demo watch, let it finish, and confirm one notification.**
- [ ] **Step 5: Commit** `feat: alert on builds watched from the watch`.

---

### Task 10: 24h summary tile

**Files:**
- Create (in `wear/src/main/kotlin/com/tomasps/virga/wear/summary/`): `SummaryQuery.kt`, `SummaryParser.kt`, `SummarySource.kt`, `SummaryTileService.kt`
- Test: `wear/src/test/kotlin/com/tomasps/virga/wear/summary/SummaryParserTest.kt`, `SummarySourceTest.kt`

**Interfaces:**
- Consumes: `GraphQlClient` from `core:network`, the query shape in `feature/home/.../analytics/AnalyticsQueries.kt` (read it, copy only the account-level requests and errors query; do not depend on `:feature:home`, which pulls phone UI via `core:ui`), `WatchSessionRepository.state`.
- Produces: `data class SummaryData(val requests: Long, val errors: Long)`; `sealed interface SummaryResult { data class Ok(val data: SummaryData); data object MissingAnalyticsAccess; data class Failed(val error: ApiError) }`; `class SummarySource(graphQl: GraphQlClient)` with `suspend fun last24h(accountId: String, now: Instant): SummaryResult`. A GraphQL authorization error maps to `MissingAnalyticsAccess`, which the tile renders as "Token lacks analytics access".

- [ ] **Step 1: Write failing tests:** `parsesRequestsAndErrors`, `emptyResultIsZeroNotFailure`, `authorizationErrorMapsToMissingAnalyticsAccess`, `networkFailureMapsToFailed`, `demoEngineReturnsSeededNumbers` (add a demo route in `:wear` demo wiring or reuse the Home demo GraphQL fixtures if they are in a reachable module; otherwise add a small fixture).
- [ ] **Step 2: Run** `./gradlew :wear:testDebugUnitTest --tests '*wear.summary*'`. Expected: FAIL.
- [ ] **Step 3: Implement the query, parser, source and tile service.** Serve cached last-good values with their age on failure, as in Task 8. Note in a code comment that the query duplicates the Home account query on purpose, and why.
- [ ] **Step 4: Run the tests and look at the tile on a Wear emulator for demo data and for the missing-access state.**
- [ ] **Step 5: Commit** `feat: add 24h summary tile`.

---

### Task 11: Docs and release checklist

**Files:**
- Modify: `README.md` (a "Wear OS" section: what it does, what it does not, how to build, how pairing works, the 24h rule), `docs/architecture.md` (new modules), `docs/release-checklist.md` (manual steps)

**Interfaces:** none.

- [ ] **Step 1: Update the three docs.** The release checklist must list these manual checks that tests cannot cover: real two-device pairing, sign-out on the phone while the watch is in airplane mode and the 24h lock after the window, tile and complication on a physical watch, a build watched on the phone bridged to the watch, a build watched from the watch not mirrored to the phone, and Play listing for the watch APK.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest lintDebug :app:assembleRelease :wear:assembleRelease`. Expected: all PASS and both release builds succeed (the release signing remains outside the repo).
- [ ] **Step 3: Commit** `docs: document the Wear OS companion`.
