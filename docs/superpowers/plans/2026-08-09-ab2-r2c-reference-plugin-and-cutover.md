# Architecture Baseline 2 R2C - Reference Plugin and Plugin Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Switch catalog consumers to the new catalog-source seam, port MyAnimeList to the vNext package/runtime, attach temporary Room persistence SPI adapters, and delete the old plugin API/host/network platform.

**Architecture:** `:catalog/source` is the only application-facing adapter over `PluginRuntime`. MyAnimeList is packaged and provisioned through the generic runtime. Legacy Wave 05 UI may temporarily consume the new source seam, but no `plugin-api`/`plugin-host` type remains when R2C closes.

**Tech Stack:** Kotlin/JVM catalog adapter tests, Android vNext runtime/integration tests, Room local tests, Gradle/Hilt transitional wiring.

## Global Constraints

- Architecture source of truth: `docs/superpowers/specs/2026-08-09-architecture-baseline-2-design.md`.
- This work is pre-Wave-06; do not implement Library, chapter sync, Reader, downloads, background sync, authentication, notifications, or release-hardening behavior.
- Android-native Kotlin remains fixed.
- Package namespace/application ID remains `app.openstory`.
- Minimum SDK remains 26; compile/target SDK remain 37 unless a dedicated architecture decision changes them.
- Build runtime remains JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0, Kotlin 2.4.10.
- Current retained libraries may be replaced only when a plan task explicitly does so; do not change versions opportunistically during this reset.
- Pre-MVP compatibility is intentionally breakable. Do not add permanent `Legacy*`, `Compat*`, `V1/V2` adapters, dual mappers, or Room migrations merely to preserve development-only contracts.
- Temporary migration-scoped bridges are allowed only when this plan names the bridge and its deletion task explicitly.
- Package-first, Gradle-module-second: do not create extra production modules beyond the approved target graph without a new architecture decision.
- TDD is mandatory for behavior changes: focused RED -> smallest GREEN -> affected module suite -> commit.
- Every task ends in a buildable, testable, independently reviewable repository state.
- Do not make a checkpoint green with `TODO()`, `error("not implemented")`, unconditional empty production results, or broad structural suppressions.
- Tests protect revalidated product/security invariants, not historical class shapes.
- Production Room entities/DAOs stay private to the storage adapter.
- Production plugin JavaScript receives only host-controlled capabilities and never Android `Context`, Room, filesystem paths, raw OkHttp, reflection, or plaintext managed credentials.

---
## R2 Closing Contract

Entry: R2B accepted.

R2 closes only when:

```text
active plugin protocol = :plugins:api
active plugin runtime  = :plugins:runtime
production plugin      = bundled-plugins/myanimelist-catalog
catalog plugin seam    = :catalog/source
```

and these legacy production paths are gone:

```text
core/plugin-api/
core/plugin-host/
core/network/
bundled-plugins/default-catalog/
bundled-plugins/javascript-catalog/
sample-plugins/selector-fixture/
```

`core/database` may temporarily implement only the new runtime persistence SPI. R3B Task 1 creates the target `:storage:room` implementations; R3B Task 3 deletes `core:database`.

### Task 1: Add the catalog-owned plugin source seam and cut legacy feature orchestration off `PluginHost`

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/source/CatalogSource.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/source/CatalogSourceModels.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/source/PluginCatalogSource.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/source/PluginCatalogSourceRegistry.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/source/PluginCatalogSourceTest.kt`
- Modify: `feature/home/build.gradle.kts`
- Modify: `feature/story/build.gradle.kts`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/RefreshHome.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/SearchCatalogs.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/CatalogSnapshotMapper.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/SearchCanonicalizer.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/SearchFilterMapper.kt`
- Modify: `feature/story/src/main/kotlin/app/openstory/story/domain/CatalogDetailsMapper.kt`
- Modify: `feature/story/src/main/kotlin/app/openstory/story/ui/StoryDetailViewModel.kt`
- Modify focused tests for all touched legacy feature-domain adapters.

**Interfaces:**
- Produces:

```kotlin
interface CatalogSource {
    val pluginId: PluginId
    val version: String
    suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>>
    suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage>
    suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails>
    suspend fun filters(): CatalogSourceResult<List<SourceFilter>>
}

interface CatalogSourceRegistry {
    suspend fun enabled(): List<CatalogSource>
    suspend fun source(pluginId: PluginId): CatalogSource?
}

sealed interface CatalogSourceResult<out T> {
    data class Success<T>(val value: T) : CatalogSourceResult<T>
    data class Failure(val failure: CatalogSourceFailure) : CatalogSourceResult<Nothing>
}

data class CatalogSourceFailure(
    val code: String,
    val retryable: Boolean,
)

enum class SourceContentType { LIGHT_NOVEL, WEB_NOVEL, MANGA, ANIME }

data class SourceHomeRequest(
    val languageTags: Set<String> = emptySet(),
    val contentTypes: Set<SourceContentType> = emptySet(),
)

data class SourceSearchRequest(
    val query: String,
    val filterValues: Map<String, List<String>> = emptyMap(),
    val nextToken: String? = null,
)

data class SourceSection(
    val sourceId: String,
    val title: String,
    val items: List<SourceItem>,
)

data class SourceItem(
    val sourceId: String,
    val title: String,
    val contentType: SourceContentType,
    val authors: Set<String>,
    val coverUrl: String?,
    val scoreValue: Double?,
    val scoreScale: Double?,
)

data class SourceSearchPage(
    val items: List<SourceItem>,
    val nextToken: String?,
)

data class SourceDetails(
    val sourceId: String,
    val sourceUrl: String?,
    val title: String,
    val aliases: Set<String>,
    val authors: Set<String>,
    val description: String?,
    val genres: Set<String>,
    val contentType: SourceContentType,
    val languageTags: Set<String>,
    val coverUrl: String?,
    val scoreValue: Double?,
    val scoreScale: Double?,
    val popularityRank: Long?,
)

sealed interface SourceFilter {
    val id: String
    val label: String
}

data class SourceOptionFilter(
    override val id: String,
    override val label: String,
    val multiple: Boolean,
    val options: List<SourceFilterOption>,
) : SourceFilter

data class SourceRangeFilter(
    override val id: String,
    override val label: String,
    val min: Double?,
    val max: Double?,
    val step: Double?,
) : SourceFilter

data class SourceTextFilter(
    override val id: String,
    override val label: String,
) : SourceFilter

data class SourceFilterOption(
    val value: String,
    val label: String,
)
```

`PluginCatalogSource` is the only catalog code that knows `PluginRuntime`, `PluginOperation`, or plugin wire DTO serializers.

- [ ] **Step 1: Write RED adapter tests**

Create `PluginCatalogSourceTest.kt` with a `FakePluginRuntime` that records the requested operation and returns encoded R2A DTOs:

```kotlin
@Test fun homeMapsWireDtoWithoutLosingSourceIdentity() = runTest {
    val runtime = FakePluginRuntime.success(
        PluginOperation.CATALOG_HOME,
        HomeOutputDto(
            sections = listOf(
                CatalogSectionDto(
                    sourceId = "top",
                    title = "Top manga",
                    items = listOf(
                        CatalogItemDto(
                            sourceId = "123",
                            title = "Example",
                            contentType = WireContentType.MANGA,
                            authors = listOf("Author"),
                            coverUrl = "https://cdn.myanimelist.net/cover.jpg",
                            score = ScoreDto(8.4, 10.0),
                        ),
                    ),
                ),
            ),
        ),
    )
    val source = PluginCatalogSource(hostedPlugin("org.example.catalog"), runtime, Json)

    val result = assertIs<CatalogSourceResult.Success<List<SourceSection>>>(source.home(SourceHomeRequest()))
    assertEquals("123", result.value.single().items.single().sourceId)
    assertEquals(SourceContentType.MANGA, result.value.single().items.single().contentType)
    assertEquals(PluginOperation.CATALOG_HOME, runtime.lastOperation)
}

@Test fun runtimeFailureBecomesCatalogSourceFailure() = runTest {
    val runtime = FakePluginRuntime.failure("plugin.rate_limited", retryable = true)
    val source = PluginCatalogSource(hostedPlugin("org.example.catalog"), runtime, Json)
    val result = assertIs<CatalogSourceResult.Failure>(source.search(SourceSearchRequest("x")))
    assertEquals("plugin.rate_limited", result.failure.code)
    assertTrue(result.failure.retryable)
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :catalog:test --tests app.openstory.catalog.source.PluginCatalogSourceTest --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Implement the source seam**

Keep source models transient and source-preserving. Map `WireContentType` exhaustively to `SourceContentType`; unknown protocol enum values fail decoding rather than silently defaulting. Do not introduce Room or canonical matching here.

- [ ] **Step 4: Replace direct `PluginHost` usage in legacy Wave 05 orchestration**

`RefreshHome`, `SearchCatalogs`, and Story detail refresh receive `CatalogSourceRegistry`/`CatalogSource` instead of `PluginHost`/`HostedPlugin<CatalogPlugin>`. Map source models into the still-legacy catalog persistence models only at these transition call sites. Mark those mapping functions `internal` and delete them in R3.

Run:

```bash
./gradlew :catalog:test :feature:home:test :feature:story:test --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add catalog/src feature/home/src feature/story/src
git commit -m "catalog: route sources through plugin runtime seam"
```

### Task 2: Port MyAnimeList as the canonical vNext reference plugin and managed credential policy

**Files:**
- Replace: `bundled-plugins/myanimelist-catalog/manifest.json`
- Modify: `bundled-plugins/myanimelist-catalog/main.js`
- Create: `plugins/api/src/test/resources/reference-plugins/myanimelist/manifest.json`
- Create: `plugins/api/src/test/kotlin/app/openstory/plugins/api/testing/MyAnimeListReferenceContractTest.kt`
- Create: `app/src/main/kotlin/app/openstory/di/MyAnimeListManagedCredentials.kt`
- Create: `app/src/main/kotlin/app/openstory/di/MyAnimeListBundledPlugin.kt`
- Modify: `app/build.gradle.kts` to add deterministic `packageMyAnimeListPlugin` Zip task.
- Replace: `app/src/main/assets/plugins/myanimelist-catalog.osp`
- Replace: `app/src/test/kotlin/app/openstory/di/MyAnimeListHttpGatewayTest.kt` with `MyAnimeListManagedCredentialsTest.kt`
- Rewrite: `app/src/test/kotlin/app/openstory/di/MyAnimeListCatalogPackageTest.kt` for the vNext manifest/asset hash contract.
- Rewrite: `app/src/androidTest/kotlin/app/openstory/MyAnimeListCatalogContractIntegrationTest.kt`
- Rewrite: `app/src/androidTest/kotlin/app/openstory/MyAnimeListLiveCatalogIntegrationTest.kt` to invoke the vNext runtime; keep it opt-in.

**Interfaces:**
- MAL plugin version becomes `2.0.0` for the intentionally incompatible pre-MVP package/protocol reset; manifest protocol major is `1`.
- MAL manifest provides only `CATALOG`, network hosts, protocol version, `main.js`.
- Client ID is injected by host request policy for `(pluginId=org.openstory.catalog.myanimelist, host=api.myanimelist.net)`.
- JavaScript never receives or constructs the Client ID.
- `MyAnimeListBundledPlugin` exposes one `BundledPluginDescriptor` for `plugins/myanimelist-catalog.osp`; its SHA-256 is detached from the `.osp` manifest and pinned in host code.
- `:app:packageMyAnimeListPlugin` is a reproducible Gradle `Zip` task with `isReproducibleFileOrder = true` and `isPreserveFileTimestamps = false`; it packages only `manifest.json` and `main.js` into the committed asset.

- [ ] **Step 1: Write RED manifest/credential tests**

Add these focused tests:

```kotlin
@Test fun vnextManifestContainsOnlyProtocolRuntimeContract() {
    val raw = File("bundled-plugins/myanimelist-catalog/manifest.json").readText()
    val manifest = Json.decodeFromString<PluginManifest>(raw)
    assertEquals("main.js", manifest.entry)
    assertFalse("packageChecksumSha256" in raw)
    assertFalse("\"runtime\"" in raw)
}

@Test fun credentialProviderScopesClientIdToMalApiHost() {
    val provider = MyAnimeListManagedCredentials(clientId = "marker-secret")
    assertEquals(
        mapOf("X-MAL-CLIENT-ID" to "marker-secret"),
        provider.headers(PluginId("org.openstory.catalog.myanimelist"), "api.myanimelist.net"),
    )
    assertTrue(provider.headers(PluginId("org.openstory.catalog.myanimelist"), "cdn.myanimelist.net").isEmpty())
    assertTrue(provider.headers(PluginId("org.example.other"), "api.myanimelist.net").isEmpty())
}
```

The runtime capability test from R2B supplies `marker-secret`, runs the MAL script, and asserts the value is absent from plugin output and diagnostics.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :plugins:api:test \
  :app:testDebugUnitTest \
  --tests app.openstory.di.MyAnimeListCatalogPackageTest \
  --tests app.openstory.di.MyAnimeListManagedCredentialsTest \
  --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Port manifest/script**

Keep existing MAL API behavior revalidated by R0: top manga Home, search, details, MAL manga ID as source ID, source-preserving score/author/cover/details. Update the JS entry object to operation method names expected by `PluginOperationRunner`.

- [ ] **Step 4: Run reference + Android contract tests**

```bash
./gradlew :plugins:api:test   :app:testDebugUnitTest   :app:connectedDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.MyAnimeListCatalogContractIntegrationTest   --stacktrace
```

Expected: **BUILD SUCCESSFUL**. Live MAL test remains opt-in and must not be a deterministic checkpoint requirement.

- [ ] **Step 5: Commit**

```bash
git add bundled-plugins/myanimelist-catalog plugins/api/src/test   app/build.gradle.kts app/src/main/assets/plugins/myanimelist-catalog.osp   app/src/main/kotlin/app/openstory/di/MyAnimeListManagedCredentials.kt   app/src/main/kotlin/app/openstory/di/MyAnimeListBundledPlugin.kt   app/src/test/kotlin/app/openstory/di   app/src/androidTest/kotlin/app/openstory/MyAnimeListCatalogContractIntegrationTest.kt
git commit -m "plugins: port myanimelist to vnext protocol"
```

### Task 3: Wire runtime persistence temporarily through `core:database`, delete legacy plugin platform, and accept R2

**Files:**
- Create: `core/database/src/main/kotlin/app/openstory/database/repository/RoomPluginStateStore.kt`
- Create: `core/database/src/main/kotlin/app/openstory/database/repository/RoomPluginDiagnosticsSink.kt`
- Modify: `core/database/build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Delete: `app/src/main/kotlin/app/openstory/di/PluginHostModule.kt`
- Create: `app/src/main/kotlin/app/openstory/di/PluginRuntimeModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/OpenStoryAppGraph.kt` to receive `CatalogSourceRegistry` rather than `PluginHost`
- Modify: `app/src/main/kotlin/app/openstory/OpenStoryApplication.kt` to remove the legacy `PluginHost` injection.
- Delete: `app/src/main/kotlin/app/openstory/di/BundledCatalogFixtureGateway.kt`
- Delete: `app/src/main/kotlin/app/openstory/di/BundledDefaultCatalogHost.kt`
- Delete: `app/src/main/kotlin/app/openstory/di/BundledJavaScriptCatalogLoader.kt`
- Delete: `app/src/main/kotlin/app/openstory/di/BundledMyAnimeListCatalogLoader.kt`
- Delete: `app/src/main/kotlin/app/openstory/di/JavaScriptExecutorEntryPoint.kt`
- Delete: `app/src/main/kotlin/app/openstory/di/MyAnimeListCatalogBundledPlugin.kt`
- Delete: `app/src/main/kotlin/app/openstory/di/MyAnimeListHttpGateway.kt`
- Delete: `app/src/main/assets/plugins/default-catalog.osp`
- Delete: `app/src/main/assets/plugins/javascript-catalog.osp`
- Delete: `app/src/main/assets/plugins/default-catalog-fixtures/`
- Delete: `app/src/main/assets/plugins/javascript-catalog-fixtures/`
- Delete: `app/src/androidTest/kotlin/app/openstory/BundledCatalogIntegrationTest.kt`
- Delete: `core/plugin-api/`
- Delete: `core/plugin-host/`
- Delete: `core/network/`
- Delete: `bundled-plugins/default-catalog/`
- Delete: `bundled-plugins/javascript-catalog/`
- Delete: `sample-plugins/selector-fixture/`
- Delete: `test/fixtures/src/main/kotlin/app/openstory/fixtures/plugin/FakeCatalogPlugin.kt`
- Delete: `test/fixtures/src/main/kotlin/app/openstory/fixtures/plugin/FakeContentPlugin.kt`
- Modify: `settings.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `scripts/verify-baseline-architecture.sh`
- Create: `docs/internal/checkpoints/architecture-baseline-2-r2.md`
- Modify: `docs/project/current-state.md`

**Interfaces:**
- Temporary `RoomPluginStateStore` implements only `app.openstory.plugins.runtime.persistence.PluginStateStore`.
- Temporary `RoomPluginDiagnosticsSink` implements only `app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink`; it maps the redacted runtime event to the existing Room diagnostic row without importing execution/capability/install packages.
- Fixed replacement point: R3B Task 1 creates both target SPI implementations in `:storage:room`; R3B Task 3 then deletes `core:database`.

- [ ] **Step 1: Write RED persistence adapter tests**

Adapt the existing Room registry/diagnostic tests to the new SPI. Representative assertions:

```kotlin
@Test fun activeAndPreviousVersionsRoundTripThroughRuntimeSpi() = runTest {
    val repository = roomPluginStateStore(database)
    val state = storedPluginState(active = "2.0.0", previous = "1.0.0", enabled = true)
    repository.replace(state)
    assertEquals(state, repository.find(state.pluginId))
}

@Test fun diagnosticsRoundTripOnlyRedactedFields() = runTest {
    val sink = roomPluginDiagnosticsSink(database)
    sink.record(PluginDiagnosticEvent(
        pluginId = PluginId("org.example.plugin"),
        code = "plugin.execution_failed",
        operation = "catalog.home",
        occurredAtEpochMillis = 100L,
        safeDetail = "safe marker",
    ))
    val restored = sink.recent(PluginId("org.example.plugin"), 10).single()
    assertEquals("safe marker", restored.safeDetail)
    assertFalse(restored.toString().contains("secret-cookie"))
}
```

Also add a source-boundary fixture proving these adapter files import only `app.openstory.plugins.runtime.persistence.*`, never execution/capability/install classes.

- [ ] **Step 2: Implement the temporary SPI adapter and app bindings**

Change `core/database` dependency from `:core:plugin-host` to `:plugins:runtime`, with the source-boundary gate allowing only `app.openstory.plugins.runtime.persistence.*`. Bind `PluginStateStore` to `RoomPluginStateStore` and `PluginDiagnosticsSink` to `RoomPluginDiagnosticsSink`. `PluginRuntimeModule` wires `AndroidBundledPluginSource` with only `MyAnimeListBundledPlugin.descriptor`, `BundledPluginProvisioner`, `DefaultPluginRuntime`, the JavaScript engine, and managed credentials. `OpenStoryAppGraph` receives `CatalogSourceRegistry`, not `PluginHost`; do not otherwise redesign composition yet because R4 owns that cleanup.

- [ ] **Step 3: Delete the old plugin platform and update architecture scripts**

Remove all old module includes/dependencies/policy entries. Replace Selector-oriented checks in `verify-baseline-architecture.sh` with R2 assertions:

```text
no core/plugin-api
no core/plugin-host
no core/network
no selector.json under production bundled plugins
no production import app.openstory.plugin.api or app.openstory.plugin.host
no production app asset for default/javascript fixture catalogs
only myanimelist-catalog.osp remains under app/src/main/assets/plugins
```

- [ ] **Step 4: Run the R2 gate**

```bash
./gradlew :plugins:api:test :plugins:runtime:testDebugUnitTest   :catalog:test :core:database:testDebugUnitTest   :feature:home:test :feature:story:test :app:testDebugUnitTest --stacktrace
./scripts/verify.sh
./scripts/check-module-dependencies.sh
```

On an Android device/emulator:

```bash
./gradlew :plugins:runtime:connectedDebugAndroidTest   :app:connectedDebugAndroidTest --stacktrace
```

Expected: all **PASS**.

- [ ] **Step 5: Record evidence, advance state, commit**

Create `architecture-baseline-2-r2.md` with actual commands/results. Update current state:

```text
Architecture Baseline 2 R2: ACCEPTED
Current active boundary: R3 - Catalog Core and Persistence
```

Commit:

```bash
git add -A
git commit -m "plugins: replace legacy plugin platform"
```

