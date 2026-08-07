# Wave 03 — Plugin Contracts and Packages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Freeze versioned catalog/content plugin contracts and safe package/repository formats before any plugin code is executed.

**Architecture:** The host owns serializable wire DTOs and validation. Declarative plugins use a bounded selector schema; JavaScript plugins target the same contracts. Portable archives and repository indexes carry immutable version/checksum/signature metadata.

**Tech Stack:** Kotlin, kotlinx.serialization, public plugin API module, Gradle test fixtures, ZIP/package schemas, Ed25519 metadata.

## Global Constraints

- Android-only MVP; no account, cloud sync, remote chapter service, or push backend.
- Package namespace: `app.openstory`.
- Minimum SDK: 26. Compile and target SDK: 37.
- Build runtime: JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0.
- Language/UI: Kotlin 2.4.10, Jetpack Compose BOM 2026.06.00, Navigation 3 version 1.1.4.
- Persistence/background: Room 2.8.4 and WorkManager 2.11.2.
- Concurrency/serialization: Kotlin coroutines 1.11.0 and kotlinx.serialization 1.11.0.
- Dependency injection: Hilt 2.60.1.
- JavaScript plugins execute only through AndroidX JavaScriptEngine 1.1.0 with host-controlled capabilities.
- Catalog metadata and readable content remain separate plugin responsibilities.
- Reading progress belongs to `CanonicalChapter`; exact `ChapterRelease` and reader position are also retained.
- No native-code plugins, unrestricted filesystem access, arbitrary Android APIs, or undeclared network domains.
- Every persistence change needs a migration test; every plugin contract needs deterministic fixtures.
- TDD is mandatory: demonstrate the focused failure, implement the smallest behavior, run focused tests, then run the module suite.
- Commit after each task. Do not combine tasks across checkpoints.
- Any deterministic `*Fixture`, fake, or test assertion helper shown in a test block is created in that task’s listed test file or `:test:fixtures`; it must not call live websites.


## Role of This Wave

This wave is the public ecosystem boundary. Changing it later has community compatibility cost, so it separates source-specific extraction from host-owned identity, storage, matching, and aggregation.

## Entry Dependencies

- Wave 02 checkpoint is approved.
- Canonical domain types and repository boundaries are stable.
- No runtime executes community plugin code yet.

## Exit Deliverables

- Versioned manifest and capability model.
- Catalog and content plugin interfaces.
- Declarative selector schema.
- Plugin package and repository formats.
- Reusable contract test kit and deterministic sample plugins.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Version the plugin manifest and capability model

**Files:**
- Create: core/plugin-api/build.gradle.kts
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/PluginManifest.kt
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/PluginCapability.kt
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/PluginApiVersion.kt
- Test: core/plugin-api/src/test/kotlin/app/openstory/plugin/api/PluginManifestTest.kt

**Interfaces:**
- Consumes: Domain IDs, content types, language tags, and serialization conventions.
- Produces: A serializable manifest for catalog/content/both plugins with API compatibility, domains, languages, entry points, update metadata, and declared capabilities.

**Acceptance:**
- Manifest IDs are stable lowercase reverse-domain-like tokens.
- Hosts reject unsupported major API versions before executing plugin data.
- Network capability is constrained to normalized HTTPS hosts; wildcard public suffixes are rejected.
- Permissions are explicit and default-deny.

**Implementation notes:**
- Define `PluginKind.CATALOG` and `PluginKind.CONTENT`; a plugin may declare both but each contract remains separate.
- Include package checksum, homepage/source URL, minimum host version, update URL, and optional repository provenance fields.
- Do not allow plugin manifests to request generic Android permissions.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-api/src/test/kotlin/app/openstory/plugin/api/PluginManifestTest.kt`:

```kotlin
package app.openstory.plugin.api

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PluginManifestTest {
    @Test fun manifestRejectsUndeclaredWildcardDomain() {
        assertFailsWith<IllegalArgumentException> {
            PluginManifest(
                id = "community.example", name = "Example", version = "1.0.0",
                api = PluginApiVersion(1, 0), kinds = setOf(PluginKind.CONTENT),
                languages = setOf("vi"), allowedHosts = setOf("*.com"),
                runtime = PluginRuntime.JAVASCRIPT, entry = "main.js",
            )
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.PluginManifestTest.manifestRejectsUndeclaredWildcardDomain
```

Expected: **FAIL** because the manifest and validation rules are undefined.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/PluginManifest.kt`:

```kotlin
package app.openstory.plugin.api

import kotlinx.serialization.Serializable

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val api: PluginApiVersion,
    val kinds: Set<PluginKind>,
    val languages: Set<String>,
    val allowedHosts: Set<String>,
    val capabilities: Set<PluginCapability> = emptySet(),
    val runtime: PluginRuntime,
    val entry: String,
) {
    init {
        require(id.matches(Regex("[a-z0-9]+(?:[._-][a-z0-9]+)+")))
        require(name.isNotBlank() && version.matches(Regex("\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?")))
        require(allowedHosts.all { it == it.lowercase() && !it.startsWith("*.") && '.' in it })
        require(entry.isNotBlank() && !entry.startsWith('/') && ".." !in entry)
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.PluginManifestTest.manifestRejectsUndeclaredWildcardDomain
./gradlew :core:plugin-api:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/plugin-api/build.gradle.kts core/plugin-api/src/main/kotlin/app/openstory/plugin/api/PluginManifest.kt core/plugin-api/src/main/kotlin/app/openstory/plugin/api/PluginCapability.kt core/plugin-api/src/main/kotlin/app/openstory/plugin/api/PluginApiVersion.kt core/plugin-api/src/test/kotlin/app/openstory/plugin/api/PluginManifestTest.kt
git commit -m "plugin-api: version manifest and capabilities"
```

### Task 2: Define catalog plugin contract and stable wire DTOs

**Files:**
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/catalog/CatalogPlugin.kt
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/catalog/CatalogModels.kt
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/Page.kt
- Test: core/plugin-api/src/test/kotlin/app/openstory/plugin/api/catalog/CatalogContractTest.kt

**Interfaces:**
- Consumes: Plugin manifest/version types and canonical content/language enums.
- Produces: Host-owned catalog contract for Home sections, search, filters, rankings, and entry details using bounded page DTOs.

**Acceptance:**
- Every returned item has a plugin-stable source ID.
- Page sizes are bounded and continuation tokens are opaque strings.
- Raw plugin values are DTOs; plugins cannot construct host `CanonicalStory` identities.
- Catalog score includes value and scale.

**Implementation notes:**
- Bound section/card counts in host validation even when plugin returns larger arrays.
- Represent images as remote references with declared host; do not return bitmaps or HTML.
- Keep filter definitions declarative: select, multi-select, range, text, and sort options.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-api/src/test/kotlin/app/openstory/plugin/api/catalog/CatalogContractTest.kt`:

```kotlin
package app.openstory.plugin.api.catalog

import app.openstory.plugin.api.Page
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CatalogContractTest {
    @Test fun catalogPageRejectsDuplicateSourceIds() {
        val item = CatalogCard("same", "Title", emptyList(), null, null)
        assertFailsWith<IllegalArgumentException> { Page(items = listOf(item, item), nextToken = null) }
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.catalog.CatalogContractTest.catalogPageRejectsDuplicateSourceIds
```

Expected: **FAIL** because catalog contract DTOs and page invariants are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/catalog/CatalogPlugin.kt`:

```kotlin
package app.openstory.plugin.api.catalog

import app.openstory.common.AppResult
import app.openstory.plugin.api.Page

interface CatalogPlugin {
    suspend fun home(request: CatalogHomeRequest): AppResult<List<CatalogSection>>
    suspend fun search(request: CatalogSearchRequest): AppResult<Page<CatalogCard>>
    suspend fun details(sourceId: String): AppResult<CatalogDetails>
    suspend fun filters(): AppResult<List<CatalogFilterDefinition>>
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.catalog.CatalogContractTest.catalogPageRejectsDuplicateSourceIds
./gradlew :core:plugin-api:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/plugin-api/src/main/kotlin/app/openstory/plugin/api/catalog/CatalogPlugin.kt core/plugin-api/src/main/kotlin/app/openstory/plugin/api/catalog/CatalogModels.kt core/plugin-api/src/main/kotlin/app/openstory/plugin/api/Page.kt core/plugin-api/src/test/kotlin/app/openstory/plugin/api/catalog/CatalogContractTest.kt
git commit -m "plugin-api: define catalog contract"
```

### Task 3: Define content plugin contract and incremental synchronization DTOs

**Files:**
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/content/ContentPlugin.kt
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/content/ContentModels.kt
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/content/SyncModels.kt
- Test: core/plugin-api/src/test/kotlin/app/openstory/plugin/api/content/ContentContractTest.kt

**Interfaces:**
- Consumes: Plugin manifest/version types, paging primitives, and domain content/language enums.
- Produces: Content contract for story search/details, recent chapters, full/incremental chapter lists, chapter body fetch, and optional direct catalog mappings.

**Acceptance:**
- Source story/release IDs are stable and opaque.
- Incremental result distinguishes upserted releases, tombstones, and next cursor.
- Chapter descriptors retain raw numbering/title plus normalized hints; host remains final authority.
- Chapter content is structured blocks, not executable HTML.

**Implementation notes:**
- Use a capability flag for real incremental sync; the host falls back to fingerprint comparison when absent.
- `ChapterDocument` permits paragraph, heading, divider, image reference, and note blocks; MVP reader renders text blocks and safe inline emphasis.
- Include source publish/update times as nullable because many sites omit reliable timestamps.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-api/src/test/kotlin/app/openstory/plugin/api/content/ContentContractTest.kt`:

```kotlin
package app.openstory.plugin.api.content

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ContentContractTest {
    @Test fun syncDeltaCannotDeleteUnknownBlankId() {
        assertFailsWith<IllegalArgumentException> {
            ChapterSyncDelta(upserts = emptyList(), tombstoneSourceReleaseIds = setOf(" "), nextCursor = "c2")
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.content.ContentContractTest.syncDeltaCannotDeleteUnknownBlankId
```

Expected: **FAIL** because content synchronization DTOs are undefined.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/content/ContentPlugin.kt`:

```kotlin
package app.openstory.plugin.api.content

import app.openstory.common.AppResult
import app.openstory.plugin.api.Page

interface ContentPlugin {
    suspend fun search(request: ContentSearchRequest): AppResult<Page<ContentStoryCandidate>>
    suspend fun story(sourceStoryId: String): AppResult<ContentStoryDetails>
    suspend fun latest(sourceStoryId: String, limit: Int): AppResult<List<SourceChapterRelease>>
    suspend fun allChapters(sourceStoryId: String): AppResult<List<SourceChapterRelease>>
    suspend fun sync(sourceStoryId: String, cursor: String?): AppResult<ChapterSyncDelta>
    suspend fun chapter(sourceReleaseId: String): AppResult<ChapterDocument>
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.content.ContentContractTest.syncDeltaCannotDeleteUnknownBlankId
./gradlew :core:plugin-api:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/plugin-api/src/main/kotlin/app/openstory/plugin/api/content/ContentPlugin.kt core/plugin-api/src/main/kotlin/app/openstory/plugin/api/content/ContentModels.kt core/plugin-api/src/main/kotlin/app/openstory/plugin/api/content/SyncModels.kt core/plugin-api/src/test/kotlin/app/openstory/plugin/api/content/ContentContractTest.kt
git commit -m "plugin-api: define content and sync contracts"
```

### Task 4: Specify declarative selector plugin schema

**Files:**
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorPluginDefinition.kt
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorOperation.kt
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorValidation.kt
- Test: core/plugin-api/src/test/kotlin/app/openstory/plugin/api/selector/SelectorValidationTest.kt
- Create: docs/plugin-sdk/declarative-plugin-schema.md

**Interfaces:**
- Consumes: Catalog/content wire DTOs and manifest capability model.
- Produces: A versioned, non-Turing-complete selector pipeline schema for request templates, CSS extraction, transforms, pagination, and document cleanup.

**Acceptance:**
- Selectors cannot execute script, reflection, filesystem, or arbitrary regex backtracking.
- Request URLs must be relative to declared HTTPS hosts or validated absolute URLs.
- Transform operations have explicit input/output types.
- Schema validation runs before package installation.

**Implementation notes:**
- Cap regex length and use only host-provided safe transformations; prohibit user-supplied replacement callbacks.
- Document every operation with a JSON example and expected host error code.
- Treat schema additions as API-versioned changes; never silently reinterpret an existing operation.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-api/src/test/kotlin/app/openstory/plugin/api/selector/SelectorValidationTest.kt`:

```kotlin
package app.openstory.plugin.api.selector

import kotlin.test.Test
import kotlin.test.assertTrue

class SelectorValidationTest {
    @Test fun definitionRejectsCrossHostRequestTemplate() {
        val definition = SelectorPluginDefinition(
            operations = listOf(HttpGet("https://evil.invalid/search?q={query}"))
        )
        assertTrue(SelectorValidation.validate(definition, setOf("allowed.example")).isFailure)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.selector.SelectorValidationTest.definitionRejectsCrossHostRequestTemplate
```

Expected: **FAIL** because selector schema and domain validation do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorOperation.kt`:

```kotlin
package app.openstory.plugin.api.selector

import kotlinx.serialization.Serializable

@Serializable
sealed interface SelectorOperation

@Serializable data class HttpGet(val urlTemplate: String) : SelectorOperation
@Serializable data class SelectAll(val css: String) : SelectorOperation
@Serializable data class SelectText(val css: String) : SelectorOperation
@Serializable data class SelectAttribute(val css: String, val attribute: String) : SelectorOperation
@Serializable data class NormalizeWhitespace(val enabled: Boolean = true) : SelectorOperation
@Serializable data class RemoveElements(val css: String) : SelectorOperation
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.selector.SelectorValidationTest.definitionRejectsCrossHostRequestTemplate
./gradlew :core:plugin-api:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorPluginDefinition.kt core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorOperation.kt core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorValidation.kt core/plugin-api/src/test/kotlin/app/openstory/plugin/api/selector/SelectorValidationTest.kt docs/plugin-sdk/declarative-plugin-schema.md
git commit -m "plugin-api: define declarative selector schema"
```

### Task 5: Define signed package archive and community repository index formats

**Files:**
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/packageformat/PluginPackageMetadata.kt
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/packageformat/RepositoryIndex.kt
- Create: core/plugin-api/src/main/kotlin/app/openstory/plugin/api/packageformat/PackageLayoutValidator.kt
- Test: core/plugin-api/src/test/kotlin/app/openstory/plugin/api/packageformat/PackageLayoutValidatorTest.kt
- Create: docs/plugin-sdk/package-format.md
- Create: docs/plugin-sdk/repository-index.md

**Interfaces:**
- Consumes: Plugin manifest, runtime entry formats, and host API version.
- Produces: Portable `.osp` ZIP package layout plus repository JSON format carrying checksums, signatures, changelogs, permissions, and rollback metadata.

**Acceptance:**
- Archive entries reject absolute paths, `..`, symlinks, duplicate names, zip bombs, and undeclared executables.
- SHA-256 checksum covers exact package bytes.
- Repository entries identify immutable version artifacts; updates never mutate an existing version URL silently.
- Unsigned packages remain installable only through an explicit warning path recorded as provenance.

**Implementation notes:**
- Set compressed/uncompressed byte ceilings and maximum entry count in the format specification.
- Use Ed25519 signatures over checksum + plugin ID + version; signer trust remains user/repository scoped rather than globally centralized.
- Repository index parsing must preserve unknown fields for forward compatibility while validation ignores no known security field.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-api/src/test/kotlin/app/openstory/plugin/api/packageformat/PackageLayoutValidatorTest.kt`:

```kotlin
package app.openstory.plugin.api.packageformat

import kotlin.test.Test
import kotlin.test.assertEquals

class PackageLayoutValidatorTest {
    @Test fun archiveRejectsTraversalEntry() {
        val result = PackageLayoutValidator.validateEntries(listOf("manifest.json", "../escape.js"))
        assertEquals(PackageLayoutError.PATH_TRAVERSAL, result.single())
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.packageformat.PackageLayoutValidatorTest.archiveRejectsTraversalEntry
```

Expected: **FAIL** because package layout validation is undefined.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/packageformat/PackageLayoutValidator.kt`:

```kotlin
package app.openstory.plugin.api.packageformat

object PackageLayoutValidator {
    private val allowedRoots = setOf("manifest.json", "selector.json", "main.js", "CHANGELOG.md", "LICENSE")
    fun validateEntries(entries: List<String>): List<PackageLayoutError> = buildList {
        if (entries.size != entries.toSet().size) add(PackageLayoutError.DUPLICATE_ENTRY)
        if (entries.any { it.startsWith('/') || it.split('/').any { part -> part == ".." } }) add(PackageLayoutError.PATH_TRAVERSAL)
        if (entries.any { it.substringBefore('/') !in allowedRoots && it !in allowedRoots }) add(PackageLayoutError.UNDECLARED_ENTRY)
        if ("manifest.json" !in entries) add(PackageLayoutError.MISSING_MANIFEST)
    }
}
enum class PackageLayoutError { PATH_TRAVERSAL, DUPLICATE_ENTRY, UNDECLARED_ENTRY, MISSING_MANIFEST }
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.packageformat.PackageLayoutValidatorTest.archiveRejectsTraversalEntry
./gradlew :core:plugin-api:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/plugin-api/src/main/kotlin/app/openstory/plugin/api/packageformat/PluginPackageMetadata.kt core/plugin-api/src/main/kotlin/app/openstory/plugin/api/packageformat/RepositoryIndex.kt core/plugin-api/src/main/kotlin/app/openstory/plugin/api/packageformat/PackageLayoutValidator.kt core/plugin-api/src/test/kotlin/app/openstory/plugin/api/packageformat/PackageLayoutValidatorTest.kt docs/plugin-sdk/package-format.md docs/plugin-sdk/repository-index.md
git commit -m "plugin-api: specify package and repository formats"
```

### Task 6: Build plugin contract test kit and deterministic fixtures

**Files:**
- Create: test/fixtures/src/main/kotlin/app/openstory/fixtures/plugin/FakeCatalogPlugin.kt
- Create: test/fixtures/src/main/kotlin/app/openstory/fixtures/plugin/FakeContentPlugin.kt
- Create: core/plugin-api/src/testFixtures/kotlin/app/openstory/plugin/api/testing/PluginContractSuite.kt
- Create: core/plugin-api/src/testFixtures/kotlin/app/openstory/plugin/api/testing/ContractAssertions.kt
- Create: sample-plugins/catalog-fixture/manifest.json
- Create: sample-plugins/content-fixture/manifest.json
- Test: core/plugin-api/src/test/kotlin/app/openstory/plugin/api/testing/PluginContractSuiteTest.kt

**Interfaces:**
- Consumes: All public catalog/content/package contracts.
- Produces: Reusable contract suite that plugin authors and bundled/community fixture plugins can run without live websites.

**Acceptance:**
- Suite catches unstable IDs, duplicates, blank content, invalid language tags, undeclared hosts, oversized pages, and malformed sync cursors.
- Fixtures include timeout, rate-limit, missing timestamp, deleted release, duplicate chapter, and special chapter cases.
- Contract output names the exact plugin method and offending source ID.

**Implementation notes:**
- Publish the test fixture artifact only after package/API formats stabilize; inside the repository consume it through Gradle test fixtures.
- All fixture clocks and IDs are deterministic.
- Generate a human-readable Markdown report and machine-readable JSON report for plugin CI.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-api/src/test/kotlin/app/openstory/plugin/api/testing/PluginContractSuiteTest.kt`:

```kotlin
package app.openstory.plugin.api.testing

import app.openstory.fixtures.plugin.UnstableIdContentPlugin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PluginContractSuiteTest {
    @Test fun suiteFlagsUnstableSearchIds() = runTest {
        val report = PluginContractSuite.content(UnstableIdContentPlugin()).run()
        assertTrue(report.violations.any { it.code == "content.search.unstable_id" })
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.testing.PluginContractSuiteTest.suiteFlagsUnstableSearchIds
```

Expected: **FAIL** because the contract suite and unstable fixture are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-api/src/testFixtures/kotlin/app/openstory/plugin/api/testing/PluginContractSuite.kt`:

```kotlin
package app.openstory.plugin.api.testing

import app.openstory.plugin.api.content.ContentPlugin

class PluginContractSuite private constructor(private val plugin: ContentPlugin) {
    suspend fun run(): ContractReport {
        val first = plugin.search(TestRequests.storySearch()).getOrNull()?.items.orEmpty()
        val second = plugin.search(TestRequests.storySearch()).getOrNull()?.items.orEmpty()
        val violations = buildList {
            if (first.map { it.sourceStoryId } != second.map { it.sourceStoryId })
                add(ContractViolation("content.search.unstable_id", "search"))
        }
        return ContractReport(violations)
    }
    companion object { fun content(plugin: ContentPlugin) = PluginContractSuite(plugin) }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-api:test --tests app.openstory.plugin.api.testing.PluginContractSuiteTest.suiteFlagsUnstableSearchIds
./gradlew :core:plugin-api:test :test:fixtures:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add test/fixtures/src/main/kotlin/app/openstory/fixtures/plugin/FakeCatalogPlugin.kt test/fixtures/src/main/kotlin/app/openstory/fixtures/plugin/FakeContentPlugin.kt core/plugin-api/src/testFixtures/kotlin/app/openstory/plugin/api/testing/PluginContractSuite.kt core/plugin-api/src/testFixtures/kotlin/app/openstory/plugin/api/testing/ContractAssertions.kt sample-plugins/catalog-fixture/manifest.json sample-plugins/content-fixture/manifest.json core/plugin-api/src/test/kotlin/app/openstory/plugin/api/testing/PluginContractSuiteTest.kt
git commit -m "plugin-sdk: add contract suite and deterministic fixtures"
```

## Wave Checkpoint

Do not begin `2026-08-03-04-plugin-host-and-security.md` until every item below is demonstrated on a clean checkout:

- [ ] Every sample plugin passes the contract suite.
- [ ] Malformed manifests/packages fail before runtime initialization.
- [ ] No plugin API exposes Android Context, Room DAO, filesystem path, WebView, or host repository.
- [ ] Package/repository JSON examples round-trip with unknown optional fields.
- [ ] API major/minor compatibility policy is documented and tested.

## Full Verification

```bash
./gradlew clean testDebugUnitTest lintDebug --stacktrace
```

Expected: **BUILD SUCCESSFUL**, no ignored failing tests, no unresolved lint errors, and no generated database schema drift.

## Review Packet

Attach to the checkpoint review:

- Commit range for this wave.
- Focused test output for every task.
- Full verification output.
- Any deliberate deviations from the approved design, with rationale and updated spec text.
- Screenshots or screen recordings only when the wave changes visible UI.
