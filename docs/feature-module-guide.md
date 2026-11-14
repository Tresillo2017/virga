# Feature module guide

Every feature builder follows this file. The worked example is `:feature:account`, which compiles and is tested. Read these files alongside it:

- `core/navigation/src/main/kotlin/com/tomasps/virga/core/navigation/RouteRegistry.kt` (the registry)
- `core/navigation/src/main/kotlin/com/tomasps/virga/core/navigation/Keys.kt` (cross-feature keys and their owners)
- `feature/account/src/main/kotlin/com/tomasps/virga/feature/account/AccountFeatureModule.kt` (Koin module and registration)
- `feature/account/src/main/kotlin/com/tomasps/virga/feature/account/details/AccountDetailsRepository.kt` (repository on CloudflareApi)
- `feature/account/src/main/kotlin/com/tomasps/virga/feature/account/account/AccountHomeViewModel.kt` (ViewModel with UiState)
- `feature/account/src/main/kotlin/com/tomasps/virga/feature/account/demo/AccountDemoRoutes.kt` (demo routes, including a path parameter)
- `feature/account/src/test/kotlin/com/tomasps/virga/feature/account/details/AccountDetailsRepositoryTest.kt` (unit test)

## Rules

1. You edit only your own `feature/<name>` module. `:app` is already wired. You never edit `:app`, `core:navigation`, or another feature.
2. Features depend on `core:*` only. Never depend on another feature. The one exception: `:feature:builds` depends on `:feature:buildsdata`, a data-only module with no UI that the watch app shares.
3. Every route key a feature owns is declared in that feature's module, unless another feature needs to navigate to it. Only those cross-feature keys go in `core:navigation/Keys.kt`, and a builder adds one only with the orchestrator's agreement.
4. Strings live in your module's `res/values/strings.xml`. No English in composables or ViewModels. Use `UiText.Resource` for text a ViewModel builds.
5. Get the session's account id from `SessionRepository.state` (`core:auth`), or from `selectedAccountIds()` in a ViewModel (see below). Never store it yourself. Exception: a cross-feature key that names a resource carries its `accountId` (for example `BuildDetail`), because a link can outlive an account switch.
6. Destructive actions go through `core:ui` dialogs (`ConfirmDialog`, `TypedNameConfirmDialog`).

## Build file

Start from `feature/account/build.gradle.kts`. Every feature uses the same dependency set:

```kotlin
plugins { id("virga.android.library") }
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended) // only if you need icons beyond core
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
}
```

Add `testImplementation(testFixtures(project(":core:network")))` if your tests use `mockCloudflareApi`. Add `libs.androidx.datastore.preferences` only if you persist something per device.

## Route keys

Route keys are `@Serializable` and implement `NavKey`. Put internal keys in your own package:

```kotlin
// feature/account/.../AccountKeys.kt
@Serializable data object AccountSwitcher : NavKey
```

Keys with arguments are data classes. Keep arguments to serializable primitives (ids, names).

```kotlin
@Serializable data class WorkerDetail(val scriptName: String) : NavKey  // cross-feature, in core:navigation
```

Declaring a key is what makes it saveable. `RouteRegistry.route(...)` does that for you. A key that is not registered throws `SerializationException` when the back stack is saved, and the unit test in `RouteRegistryTest` checks exactly that.

**Naming rule.** Keys are identified by their full class name, not their simple name. Two features may each declare a key called `Overview` or `Settings`. Routing the same key twice is still an error. `:app`'s `AppRoutesTest` builds the whole registry, so a duplicate fails in the unit tests, not on first launch.

## Registration: one function per feature

The function name is fixed: `register<Name>Entries(routes: RouteRegistry)`. Its body maps every key you own to a screen:

```kotlin
fun registerAccountEntries(routes: RouteRegistry) {
    routes.route(SignIn.serializer()) { SignInRoute() }
    routes.route(AccountSwitcher.serializer()) { AccountSwitcherRoute() }
}
```

- `route` makes the key saveable and registers the screen in one call. Calling it twice for the same key throws at startup, which is intended.
- The content lambda receives the key instance: `routes.route(WorkerDetail.serializer()) { key -> WorkerDetailRoute(key) }`. The one-argument form `{ WorkerDetailRoute(it) }` is the same.
- Screens reach the navigator through `LocalNavigator.current` (from `core:navigation`). Never pass the back stack around.
- The shell builds the registry in `:app` (`shell/AppRoutes.kt`). Your function is already called there.

## Koin module: one per feature

The val name is fixed: `<name>KoinModule`, for example `accountKoinModule`. `:app` loads it.

```kotlin
val accountKoinModule = module {
    single { AccountDetailsRepository(get()) }        // get() resolves CloudflareApi from core:network
    viewModel { AccountHomeViewModel(get(), get()) }  // koin-compose-viewmodel
}
```

Use `viewModel { }` for each ViewModel, never `single`. Each navigation entry then gets its own ViewModel, because the shell gives every entry its own ViewModel store. Two `WorkerDetail` entries with different names therefore do not share state.

For a ViewModel that takes the route key, use a parameter:

```kotlin
viewModel { params -> WorkerDetailViewModel(get(), params.get<String>()) }
// screen: koinViewModel<WorkerDetailViewModel>(parameters = { parametersOf(key.scriptName) })
```

## Repository on CloudflareApi

Repositories wrap `CloudflareApi` and return `ApiResult`. They do not map errors to UI text.

```kotlin
class AccountDetailsRepository(private val api: CloudflareApi) {
    suspend fun details(accountId: String): ApiResult<AccountDetails> =
        api.get(cloudflarePath("accounts", accountId))
}
```

- Build every path with `cloudflarePath(...)`. It escapes each segment, which matters for user-supplied values such as R2 object keys.
- Model responses as `@Serializable` data classes. Give every field that the reference does not mark required a default or make it nullable. The JSON config ignores unknown keys.
- Paginated lists: `api.allPages(path, deserializer = ListSerializer(Item.serializer()))`, or `pagedFlow` for progressive loading.
- Writes: `api.put`, `api.patch`, `api.delete`, or `api.postEmpty`. Confirm first with a `core:ui` dialog.

## ViewModel with UiState

State is an immutable `data class`, exposed as `StateFlow`. Screen-level loading and failure use `UiState<T>` from `core:ui` (`Loading`, `Content(data)`, `Failure(error: ErrorUi)`). Map `ApiError` with the shared mapper:

```kotlin
is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
```

Render a nullable state with a `let`, as `AccountHomeScreen` does. A null state means nothing is selected:

```kotlin
state.details?.let { details ->
    UiStateContent(state = details, onRetry = viewModel::retry) { data -> /* ... */ }
}
```

Use `viewModelScope` for work. In tests, `MainDispatcherRule` (copy it from the account feature's test folder) sets `Dispatchers.Main`.

**Reacting to the selected account.** Use `selectedAccountIds()` (from `core:auth`) rather than hand-rolling a collector. It emits each new id once, skips signed-out and restoring states, and emits again after sign-out and back in. Use `collectLatest`, so a switch cancels the load for the previous account instead of waiting for it:

```kotlin
init {
    viewModelScope.launch {
        session.selectedAccountIds().collectLatest { accountId -> load(accountId) }
    }
}
```

`AccountHomeViewModel` is the worked example.

## Demo routes

Each feature adds its canned responses as a `DemoRouteContributor`. Bind it in your Koin module so `coreNetworkKoinModule` collects it. Pass display text in from `strings.xml`:

```kotlin
single {
    val context: Context = get()
    AccountDemoRoutes(
        personalName = context.getString(R.string.account_demo_name_personal),
        stagingName = context.getString(R.string.account_demo_name_staging),
    )
} bind DemoRouteContributor::class
```

The contributor adds routes to the registry. Patterns use `{name}` for one segment and `{name...}` for the rest of the path:

```kotlin
registry.get("/accounts/{accountId}") { request ->
    val id = request.pathParams["accountId"].orEmpty()
    DemoReply.ok(detailsFor(id))  // or DemoReply.failure(HttpStatusCode.NotFound, 7003, "...")
}
```

- Demo requests carry no headers, so a demo handler never sees the token. `request.bodyBytes` holds the raw body (binary uploads included), and `request.bodyText` is its UTF-8 decoding.
- `DemoReply.empty()` answers a call that returns nothing (a 204 delete).
- Two routes with the same method and the same shape are rejected when registered. `{id}` and `{accountId}` count as the same shape, because the first one would win and the second would never answer.
- An unmatched demo request gets a 404 with code 7003, and its method and path are logged through the registry's `ApiLogger`.
- Writes in demo mode should update an `InMemoryStore<V>` when that is cheap. Otherwise return a success envelope.

## Transport helpers

Everything goes through `CloudflareApi`. Pick the helper that matches the call:

| Need | Call |
|---|---|
| JSON envelope, typed result | `api.get<T>`, `post`, `put`, `patch`, `delete`, `postEmpty`, `putEmpty`, or `api.execute(method, path, query, body, deserializer)` |
| Call that returns nothing | `api.delete<Unit>(path)` (a 204 or an empty 2xx is `Success(Unit)`) |
| Every page of a list | `api.allPages(path, deserializer = ListSerializer(Item.serializer()))` |
| Pages one at a time | `api.pagedFlow(...)`, which emits one result per page |
| Stream a large body down | `api.download(path) { channel, contentLength -> ... }` |
| Stream a large body up | `api.uploadStream(method, path, contentType, contentLength, write = { channel -> ... }, deserializer)` |
| multipart/form-data | `api.postMultipart(path, listOf(FormPart.Text(...), FormPart.File(...)), deserializer)` |
| Plain text (logs) | `api.getText(path)` |
| GraphQL | `api.graphQl.query(query, variables, deserializer)` |

Notes:

- Build every path with `cloudflarePath(...)`. Slashes, percent signs, `?` and `#` are escaped. A segment that is exactly `.` or `..` is refused with `ApiError.Local` before anything is sent, because OkHttp resolves those even when encoded. Such a key cannot be used.
- `download` and `uploadStream` have no total time limit. Only 60 seconds of silence fails them. Exceptions thrown by your `consume` or `write` lambda propagate to you unchanged. They are not turned into `ApiError.Network`.
- A `FormPart.Text` with `contentType = ContentType.Application.Json` sends that header. Workers script uploads need it on the `metadata` part, as Cloudflare's multipart upload documentation shows.
- Rate limiting is per lane. A REST 429 blocks REST and GraphQL. A GraphQL 429 blocks GraphQL only. A GET that is rate limited with a Retry-After of 10 seconds or less is retried once after the wait. Longer waits return `ApiError.RateLimited`.
- Paginated walks follow the server's own page size, so a server cap is handled. A list longer than 200 pages ends with `ApiError.Local`, never a silent partial success.
- Not yet available: signed-URL downloads from other hosts, and SigV4 signing for R2's S3 endpoint (needed for large R2 uploads and temporary credentials). Both are deferred. Do not build a feature on them until the orchestrator adds them.

## Errors

`ApiError` is the typed failure. Map it with `ApiErrorUiMapper`, and never show raw API text. `ApiError.Local` means the app itself could not finish: a refused request, a local keystore or storage failure, or a list it stopped early. Nothing from Cloudflare describes it, so the UI text is generic and retryable.

## Unit test

Test the repository against `mockCloudflareApi` from the testFixtures, and the ViewModel against a fake of its dependency. Arrange/Act/Assert, descriptive names:

```kotlin
@Test
fun `details are read from the account path and decoded, ignoring unknown fields`() = runBlocking {
    val api = mockCloudflareApi {
        get("/accounts/{accountId}") { request ->
            assertEquals("acc-1", request.pathParams["accountId"])
            DemoReply.ok(buildJsonObject { put("id", "acc-1"); put("name", "Alpha") })
        }
    }
    val result = AccountDetailsRepository(api).details("acc-1")
    assertEquals("Alpha", (result as ApiResult.Success).value.name)
}
```

For ViewModels, wait for a state with `withTimeout(WAIT_MS) { viewModel.state.first { ... } }`, where `WAIT_MS` is a named constant. A bare `withTimeout` does not compile, because it needs a duration. Waiting on a state avoids depending on dispatch timing.

## Stub screens you replace

Each feature ships `<Name>StubScreens.kt`, with a placeholder for every key it owns. Replace the bodies and keep the key-to-screen mapping. Do not rename the register function or the Koin val.

## Cross-feature links and notifications

- To open an external page, call `navigator.openInBrowser(url)`. It returns `false` and opens nothing unless the URL is HTTPS on a `cloudflare.com` domain (`ExternalLinks`). A URL from API data is therefore never opened.
- A notification opens a screen with a `virga://` link. `DeepLinks.buildDetailUri(BuildDetail(...))` builds one, and `DeepLinks.parse` reads one back. A new link kind is added to `DeepLinks` by the orchestrator. Anything not listed is ignored.
- Offer a parsed key to `PendingNavigation` (a Koin singleton in `:app`). The shell shows it once the user is signed in, after the stack has been reset to the home screen.

## Sensitive values

Never print a credential or token. `Credential.ApiToken` and `SignInUiState` redact their `toString`. If you add a state class that holds a secret, override `toString` the same way.

## Checklist before reporting

- `./gradlew :feature:<name>:testDebugUnitTest` passes.
- `./gradlew :app:testDebugUnitTest` passes. `AppRoutesTest` builds the whole registry, so a duplicate key or a missing screen fails here.
- `./gradlew :app:compileDebugKotlin` passes. This checks that the registration signature still matches the shell.
- Every key you own has a screen registered.
- Every string is in your `strings.xml`.

## Shared contracts added for the feature phase

Added before the feature builders start, so each one can build against them without touching core modules. Everything here is in place and compiles. Where a builder must replace a placeholder, this section says so.

### 1. Builds gateway (`core:common`, package `com.tomasps.virga.core.common.builds`)

`core:common` has no Android or network dependencies, so the plain data types and the interface live there. Features that show builds (Home, Worker detail) depend on `core:common` only, never on `:feature:builds`.

```kotlin
enum class BuildStatus { QUEUED, INITIALIZING, RUNNING, STOPPED, UNKNOWN; val isActive: Boolean; companion object { fun fromWire(raw: String?): BuildStatus } }
enum class BuildOutcome { SUCCESS, FAIL, CANCELED, SKIPPED, UNKNOWN; companion object { fun fromWire(raw: String?): BuildOutcome } }

data class BuildSummary(
    val buildUuid: String,
    val scriptName: String,
    val workerTag: String? = null,
    val status: BuildStatus,
    val outcome: BuildOutcome? = null,
    val branch: String? = null,
    val commitHash: String? = null,
    val commitMessage: String? = null,
    val author: String? = null,
    val createdOn: java.time.Instant,
    val startedOn: java.time.Instant? = null,
    val stoppedOn: java.time.Instant? = null,
)

interface BuildsGateway {
    suspend fun recentBuilds(accountId: String, scriptName: String, limit: Int): ApiResult<List<BuildSummary>>
    suspend fun activeBuilds(accountId: String): ApiResult<List<BuildSummary>>
    fun watchedBuildIds(): Flow<Set<String>>
    fun startWatching(accountId: String, build: BuildSummary)
    fun stopWatching(buildUuid: String)
}
```

- `fromWire` maps known values ignoring case, and anything else (including null) to `UNKNOWN`. Values are the API's lowercase strings (`queued`, `initializing`, `running`, `stopped`; `success`, `fail`, `canceled`/`cancelled`, `skipped`). Confirm them against the API reference before relying on the less common ones.
- `startWatching` cannot ask for permissions (it is a plain `Unit` function). The screen that offers Watch requests `POST_NOTIFICATIONS` first, with the rationale, and then calls `startWatching`. The builds implementation must still work if the user denied the permission: the build is watched, and the notification may be silent. Only the Watch tap triggers the request, so Home and Workers never ask on their own.
- `:feature:builds` implements `BuildsGateway` and binds it in `buildsKoinModule`. Features resolve it with `get<BuildsGateway>()`.
- **Placeholder to replace.** `feature/builds/.../TemporaryNoOpBuildsGateway.kt` is a no-op (empty lists, no watches). The builds builder replaces it with the real class, changes the `single { ... } bind BuildsGateway::class` line in `buildsKoinModule`, and deletes the file.

### 2. Route keys

- New key, owned by `:feature:builds`, declared in `core:navigation/Keys.kt` because Worker detail navigates to it:
  `@Serializable data class WorkerBuilds(val accountId: String, val scriptName: String) : NavKey`
  Registered in `registerBuildsEntries` with a stub, `WorkerBuildsStub`, in `BuildsStubScreens.kt`.
- No new keys for the list destinations. Reuse the existing keys, which are the same destinations:

| Requested name | Existing key | Owner |
|---|---|---|
| `WorkersList` | `WorkersRoot` | `:feature:workers` (Compute hub) |
| `PagesList` | `PagesRoot` | `:feature:pages` (Compute hub) |
| `ZonesList` | `DomainsRoot` | `:feature:domains` (Domains tab) |
| `R2BucketsList` | `R2Root` | `:feature:r2` (Storage hub) |
| `D1DatabasesList` | `D1Root` | `:feature:d1` (Storage hub) |
| `KvNamespacesList` | `KvRoot` | `:feature:kv` (Storage hub) |

### 3. UI kit additions (`core:ui`)

Components (`com.tomasps.virga.core.ui.components`):

```kotlin
@Composable fun VirgaTabRow(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)

enum class LoadMoreState { HIDDEN, AVAILABLE, LOADING, FAILED }
fun loadMoreStateOf(hasMore: Boolean, isLoading: Boolean, hasFailed: Boolean): LoadMoreState
@Composable fun LoadMoreFooter(state: LoadMoreState, onLoadMore: () -> Unit, modifier: Modifier = Modifier)

@Composable fun DestructiveMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
)
```

- `LoadMoreFooter` draws nothing when `HIDDEN`. Derive the state with `loadMoreStateOf`, so loading wins over an earlier failure and a failure wins over "more available".
- `DestructiveMenuItem` is drawn in the error colour. It does not confirm anything: the action still goes through `ConfirmDialog` or `TypedNameConfirmDialog`.

Charts (`com.tomasps.virga.core.ui.chart`):

```kotlin
@Immutable data class LineSeries(val name: String, val points: List<ChartPoint>)

@Composable fun MultiLineChart(
    series: List<LineSeries>,
    title: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    palette: List<Color> = LocalVirgaSemanticColors.current.chartSeries,
)
```

- All series share one y-axis range. Lines are matched by position, and the x-axis labels come from the longest series.
- The legend is the new `internal fun ChartLegend(names, palette)` in `ChartContainer.kt`. `StackedBarChart` uses it too.
- `LineChart` is unchanged. Use `MultiLineChart` for two or more lines.

Storage Access Framework (`com.tomasps.virga.core.ui.files`):

```kotlin
@Composable fun rememberSaveFileLauncher(mimeType: String, onResult: (Uri?) -> Unit): ManagedActivityResultLauncher<String, Uri?>
@Composable fun rememberOpenFileLauncher(onResult: (Uri?) -> Unit): ManagedActivityResultLauncher<Array<String>, Uri?>
```

- Save: `launcher.launch("suggested-name.txt")`. Open: `launcher.launch(arrayOf("application/json"))`. A null result means the user cancelled.
- `core:ui` exposes `libs.androidx.activity.compose` as `api`, because the launcher types appear in its public signatures.

Tests: `BuildEnumsTest` (core:common), `LoadMoreStateTest` and `MultiLineChartTest` (core:ui). Previews: `UiKitGapPreviews.kt`, plus two `MultiLineChart` previews in `ChartPreviews.kt`.

## UI flow tests

A feature's screens can be tested on the JVM, with no emulator. `core:testing` (package `com.tomasps.virga.core.testing`) is on every module's test classpath, and `DemoFlow` runs one flow of one feature in demo mode: the feature's real Koin module and real `register<Name>Entries` function, the core network demo engine, a signed-in demo session, the real `VirgaTheme`, and the real `NavDisplay`. The screens are the app's own composables. Pixel layout and device behaviour still need the emulator.

### Setup (already in place)

- `build-logic` adds to every module's unit tests: `robolectric`, `ui-test-junit4`, `ui-test-manifest`, `espresso-core` 3.7.0 (Compose's own Espresso 3.5 cannot idle Robolectric on SDK 37), and `:core:testing`. It also passes `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED` to every test task, because Robolectric reflects into JDK internals that JDK 27 closes.
- `config/robolectric/robolectric.properties` is read by every module's unit tests. It pins `sdk=37` (the highest SDK Robolectric 4.17 runs here) and `qualifiers=w411dp-h891dp-xxhdpi`, a phone-sized window. Lazy lists compose only the rows that fit, so a window that is too small hides rows the test expects to find.
- A feature needs nothing else. Write the test in `feature/<name>/src/test`, with `@RunWith(RobolectricTestRunner::class)` and `@get:Rule val compose = createComposeRule()`.

### API

```kotlin
class FeatureUnderTest(koinModules: List<Module>, registerEntries: (RouteRegistry) -> Unit)

class DemoFlow(
    compose: ComposeContentTestRule,
    feature: FeatureUnderTest,
    darkTheme: Boolean = true,                          // false for the light theme
    accounts: List<CloudflareAccount> = DEFAULT_DEMO_ACCOUNTS,
    selectedAccountId: String = accounts.first().id,
) : AutoCloseable {
    val koin: Koin                                      // the feature's graph, demo mode on
    val navigator: Navigator                            // the real Navigator, over a recording back stack
    val history: List<NavKey>                           // every key pushed by navigation, in order
    val openedUrls: List<String>                        // URLs screens opened through the navigator
    fun showStack(vararg keys: NavKey)                  // sets the composition once; the last key is shown
    fun show(key: NavKey)                               // showStack(key)
    inline fun <reified T : Any> get(): T               // a bean from the graph: a store, a repository
    fun waitForText(text: String, timeoutMs: Long = FLOW_WAIT_TIMEOUT_MS)
    fun waitForTag(tag: String, timeoutMs: Long = FLOW_WAIT_TIMEOUT_MS)
    fun waitUntilTextGone(text: String, timeoutMs: Long = FLOW_WAIT_TIMEOUT_MS)
    fun boundsOf(text: String): Rect                    // waits, then the node's bounds in the window (px)
    fun boundsOfDescription(prefix: String): Rect       // same, for a node whose content description starts with prefix (charts)
    fun assertTextShown(text: String)                   // waits, then asserts the node is displayed
    fun clickText(text: String)                         // waits, scrolls the enclosing list if needed, taps
    fun typeIntoField(label: String, text: String)      // the text field whose visible label is `label`
    fun assertNavigatedTo(key: NavKey)                  // fails with the recorded history if not pushed
    fun advanceClock(millis: Long)                      // runs delayed work on the paused main looper
    override fun close()                                // stops the feature's Koin graph
}

const val FLOW_WAIT_TIMEOUT_MS = 10_000L
val DEFAULT_DEMO_ACCOUNTS: List<CloudflareAccount>      // "demo-account-personal" and "demo-account-staging"

class DemoSessionRepository(accounts, selectedAccountId) : SessionRepository  // already signed in, demo
```

What the harness swaps, and why:

- **Session.** `DemoSessionRepository` replaces the real session layer, which needs the Android Keystore and DataStore. Features read only `SessionRepository.state`, so the swap is exact for them. Live sign-in is refused.
- **Dispatchers.** `DispatcherProvider` is unconfined, so repository work finishes inline and a wait never depends on a thread pool.
- **Navigator.** `Navigator` is a final class, so the recording happens one level down: `RecordingBackStack` records every key pushed onto the stack. `history` is that record. `navigator.back()` and `replaceAll` work as in the app.
- **Route registration.** `DemoFlow` builds its own `RouteRegistry`, calls `registerSharedKeys()`, then calls the feature's `register<Name>Entries`. A key the feature does not register fails when it is shown. A flow that opens another feature's screen must pass that feature's register function too.

### Waits, polling and virtual time

- Every wait polls for up to `FLOW_WAIT_TIMEOUT_MS`. A wait passes as soon as the node exists, and fails with Compose's timeout message if it never does.
- Robolectric's main looper is paused. Delayed work (a `delay`, a poll loop, a debounce) runs only when time advances. So a screen whose view model polls does not stall a wait: the wait simply does not see the next poll yet. To deliver it, call `flow.advanceClock(ms)`. `PollingScreenFlowTest` in `core:testing` proves both halves with a view model that loops on `viewModelScope`, as `BuildDetailViewModel` does: a wait returns, and `advanceClock(5_000)` delivers exactly one more tick. The builds screens themselves have no flow test yet.
- Compose's idle wait does not wait for pending delays, so a poll that has not yet fired does not hang a test.

### Actions and finders

- `clickText` matches the node that has the exact text and a click action. That is the merged row, button or chip, not the text inside it. Two nodes with the same text fail the test: use a more specific text or the unmerged tree in a custom matcher.
- `typeIntoField` finds the field by its visible label. Among all text fields, it picks the one whose lowest common ancestor with the label is deepest, so a form with several fields resolves to the right one. If two fields tie, it fails. It replaces the field's text, so a field can be typed into twice.
- Tap positions come from the node's bounds. `clickText` scrolls a node inside a scrolling column or lazy list into view first. A node outside any scroll container must be on screen.

### Example: a passing test

This is `feature/kv/src/test/kotlin/com/tomasps/virga/feature/kv/ui/KvDemoFlowTest.kt`, unchanged.

```kotlin
package com.tomasps.virga.feature.kv.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.KvRoot
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.kv.KvAddKey
import com.tomasps.virga.feature.kv.KvKeyDetail
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.kvKoinModule
import com.tomasps.virga.feature.kv.registerKvEntries
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val DEMO_PERSONAL_ACCOUNT = "demo-account-personal"
private const val INVALID_METADATA_MESSAGE = "Metadata must be a JSON value. Check the brackets and quotes."

/** Screen-level flows for KV in demo mode, through the feature's real Koin graph and real route registration. */
@RunWith(RobolectricTestRunner::class)
class KvDemoFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = DemoFlow(compose, FeatureUnderTest(listOf(kvKoinModule), ::registerKvEntries))
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `namespaces list shows the demo namespaces`() {
        flow.show(KvRoot)

        flow.assertTextShown("Session cache")
        flow.assertTextShown("Feature flags")
        flow.assertTextShown("Asset manifest")
    }

    @Test
    fun `opening a namespace lists its keys, and tapping one opens the key detail`() {
        flow.show(KvRoot)

        flow.clickText("Feature flags")
        flow.assertTextShown("checkout/new-flow")
        flow.clickText("checkout/new-flow")

        flow.assertNavigatedTo(KvKeyDetail(KV_DEMO_NS_FLAGS, "checkout/new-flow"))
        flow.waitForText("Show value")
    }

    @Test
    fun `add key with invalid metadata shows the validation message and writes nothing`() {
        flow.showStack(KvNamespace(KV_DEMO_NS_FLAGS), KvAddKey(KV_DEMO_NS_FLAGS))

        flow.typeIntoField("Key name", "flow/invalid-metadata")
        flow.typeIntoField("Value", "hello")
        flow.typeIntoField("Metadata (JSON)", "{not json")
        flow.clickText("Save key")

        flow.waitForText(INVALID_METADATA_MESSAGE)
        flow.assertTextShown("Add key")
        val store = flow.get<KvDemoStore>()
        assertNull(store.entries.get(store.entryKey(DEMO_PERSONAL_ACCOUNT, KV_DEMO_NS_FLAGS, "flow/invalid-metadata")))
    }

    @Test
    fun `add key with valid input returns to the namespace and lists the new key`() {
        flow.showStack(KvNamespace(KV_DEMO_NS_FLAGS), KvAddKey(KV_DEMO_NS_FLAGS))

        flow.typeIntoField("Key name", "flow/new-key")
        flow.typeIntoField("Value", "hello from the flow test")
        flow.clickText("Save key")

        flow.waitUntilTextGone("Save key")
        flow.waitForText("Feature flags")
        flow.assertTextShown("flow/new-key")
        val lookup = runBlocking {
            flow.get<KvRepository>().findKey(DEMO_PERSONAL_ACCOUNT, KV_DEMO_NS_FLAGS, "flow/new-key")
        }
        assertNotNull((lookup as ApiResult.Success).value)
    }
}
```

### Known limits

- `createComposeRule()` is the v1 rule, and the compiler warns that it is deprecated. The harness uses v1. The v2 rule (standard test dispatcher, explicit virtual time) was not tried, so this is an open item.
- Robolectric runs on SDK 37 with a phone-sized window. Pixel-level layout and theme colours are not verified here. Use the device for those.
- A flow whose screen resolves another feature's binding must include that module in `koinModules`. For example, the Workers screens get `BuildsGateway` from `buildsKoinModule`, so a Workers flow passes both modules.
- Light theme: `darkTheme = false` is covered by `KvLightThemeFlowTest`, which checks that the content renders. It does not assert colours.
- A flow runs in one Compose window. Dialogs and sheets are found like any other node, but a system permission prompt or a system back gesture is not simulated.
