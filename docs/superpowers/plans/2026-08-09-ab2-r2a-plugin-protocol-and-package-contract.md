# Architecture Baseline 2 R2A - Plugin Protocol and Package Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Define the replacement pure plugin manifest, operation wire DTOs, protocol validator, and detached artifact/repository integrity contract without changing the running legacy plugin host yet.

**Architecture:** `:plugins:api` becomes a pure Kotlin/JVM serialization boundary with no Android, app models, `AppResult`, or host Kotlin plugin interfaces. R2A is contract-only: current product flows continue using the legacy host until R2C.

**Tech Stack:** Kotlin serialization 1.11.0 and JVM contract tests.

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
## Entry / Exit Contract

Entry: R1 accepted; target module shells exist.

Exit:
- `:plugins:api` contains the complete vNext manifest/catalog/content/package wire contract used by later R2 plans;
- manifest/package protocol has no Selector runtime field or self-checksum;
- protocol output validation has an explicit declared-host input;
- no production consumer has been switched yet, so the legacy plugin platform remains the running implementation during this intermediate plan.

R2A does **not** close R2 and does not delete legacy plugin modules.

### Task 1: Define the new pure plugin manifest and service declarations

**Files:**
- Create: `plugins/api/src/main/kotlin/app/openstory/plugins/api/manifest/PluginManifest.kt`
- Create: `plugins/api/src/main/kotlin/app/openstory/plugins/api/manifest/PluginService.kt`
- Create: `plugins/api/src/main/kotlin/app/openstory/plugins/api/manifest/PluginProtocolVersion.kt`
- Create: `plugins/api/src/test/kotlin/app/openstory/plugins/api/manifest/PluginManifestTest.kt`

**Interfaces:**
- Produces:

```kotlin
@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val protocol: PluginProtocolVersion,
    val entry: String = "main.js",
    val provides: Set<PluginService>,
    val languages: Set<String> = emptySet(),
    val homepageUrl: String? = null,
    val sourceUrl: String? = null,
    val capabilities: PluginCapabilities = PluginCapabilities(),
)

@Serializable
@JvmInline
value class PluginProtocolVersion(val major: Int)

@Serializable
enum class PluginService { CATALOG, CONTENT }

@Serializable
data class PluginCapabilities(
    val network: NetworkCapability? = null,
)

@Serializable
data class NetworkCapability(
    val hosts: Set<String>,
)
```

The manifest does **not** contain `packageChecksumSha256`, runtime kind, selector entry, repository provenance, or a Kotlin host interface.

- [ ] **Step 1: Write failing invariant tests**

```kotlin
@Test
fun manifestRejectsWildcardNetworkHost() {
    assertFailsWith<IllegalArgumentException> {
        manifest(networkHosts = setOf("*.example.com"))
    }
}

@Test
fun manifestUsesSingleJavaScriptEntry() {
    assertFailsWith<IllegalArgumentException> {
        manifest(entry = "selector.json")
    }
}

@Test
fun serializedManifestHasNoSelfChecksumOrRuntimeField() {
    val json = Json.encodeToString(PluginManifest.serializer(), manifest())
    assertFalse("packageChecksumSha256" in json)
    assertFalse("\"runtime\"" in json)
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :plugins:api:test   --tests app.openstory.plugins.api.manifest.PluginManifestTest   --stacktrace
```

Expected: **FAIL** because the new protocol does not exist.

- [ ] **Step 3: Implement manifest validation**

Validate:
- reverse-domain-like lowercase ID;
- semantic version;
- protocol major > 0;
- `entry == "main.js"`;
- at least one provided service;
- normalized HTTPS hostnames only;
- no wildcard host;
- homepage/source URLs null or HTTPS;
- normalized language tags with no blanks/control characters.

- [ ] **Step 4: Run API module tests**

```bash
./gradlew :plugins:api:test --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add plugins/api/src/main/kotlin/app/openstory/plugins/api/manifest   plugins/api/src/test/kotlin/app/openstory/plugins/api/manifest
git commit -m "plugins: define vnext manifest protocol"
```

### Task 2: Define operation protocol and bounded catalog/content wire DTOs

**Files:**
- Create: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/PluginOperation.kt`
- Create: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/PageDto.kt`
- Create: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/PluginProtocolValidator.kt`
- Create: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocol.kt`
- Create: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/content/ContentProtocol.kt`
- Create: `plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocolTest.kt`
- Create: `plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol/content/ContentProtocolTest.kt`

**Interfaces:**
- Produces operation names:

```kotlin
enum class PluginOperation(val wireName: String) {
    CATALOG_HOME("catalog.home"),
    CATALOG_SEARCH("catalog.search"),
    CATALOG_DETAILS("catalog.details"),
    CATALOG_FILTERS("catalog.filters"),
    CONTENT_SEARCH("content.search"),
    CONTENT_STORY("content.story"),
    CONTENT_CHAPTERS("content.chapters"),
    CONTENT_CHAPTER("content.chapter"),
}
```

Catalog protocol owns its own serializable `WireContentType`; it does not import app/catalog models. The initial content protocol intentionally exposes only discovery/story/chapter-list/chapter-body operations. Incremental-sync operations are **not** predeclared during this pre-Wave-06 reset; Wave 07 may add a protocol-compatible operation when the sync capability actually starts.

Use these concrete catalog wire shapes so R2C Task 1 has one unambiguous decoder target:

```kotlin
@Serializable
enum class WireContentType { LIGHT_NOVEL, WEB_NOVEL, MANGA, ANIME }

@Serializable
data class ScoreDto(val value: Double, val scale: Double)

@Serializable
data class CatalogItemDto(
    val sourceId: String,
    val title: String,
    val contentType: WireContentType,
    val authors: List<String> = emptyList(),
    val coverUrl: String? = null,
    val score: ScoreDto? = null,
)

@Serializable
data class CatalogSectionDto(
    val sourceId: String,
    val title: String,
    val items: List<CatalogItemDto>,
)

@Serializable
data class CatalogHomeRequestDto(
    val languageTags: Set<String> = emptySet(),
    val contentTypes: Set<WireContentType> = emptySet(),
)

@Serializable
data class CatalogHomeOutputDto(val sections: List<CatalogSectionDto>)

@Serializable
data class CatalogSearchRequestDto(
    val query: String,
    val filterValues: Map<String, List<String>> = emptyMap(),
    val nextToken: String? = null,
)

@Serializable
data class CatalogSearchOutputDto(
    val items: List<CatalogItemDto>,
    val nextToken: String? = null,
)

@Serializable
data class CatalogDetailsRequestDto(val sourceId: String)

@Serializable
data class CatalogDetailsOutputDto(
    val sourceId: String,
    val sourceUrl: String?,
    val title: String,
    val aliases: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val description: String?,
    val genres: Set<String> = emptySet(),
    val contentType: WireContentType,
    val languageTags: Set<String> = emptySet(),
    val coverUrl: String?,
    val score: ScoreDto?,
    val popularityRank: Long?,
)
```

`CatalogProtocol.kt` also defines bounded serializable filter DTOs (`Option`, `Range`, `Text`) used by `catalog.filters`; they map one-to-one to the source filter types declared in R2C Task 1.

Use these content operation inputs as the initial public surface:

```kotlin
@Serializable data class ContentSearchRequestDto(val query: String, val nextToken: String? = null)
@Serializable data class ContentStoryRequestDto(val sourceStoryId: String)
@Serializable data class ContentChaptersRequestDto(val sourceStoryId: String, val nextToken: String? = null)
@Serializable data class ContentChapterRequestDto(val sourceReleaseId: String)
```

Use these remaining concrete protocol primitives:

```kotlin
@Serializable
data class PageDto<T>(
    val items: List<T>,
    val nextToken: String? = null,
)

@Serializable
sealed interface CatalogFilterDto {
    val id: String
    val label: String
}

@Serializable
@SerialName("option")
data class CatalogOptionFilterDto(
    override val id: String,
    override val label: String,
    val multiple: Boolean,
    val options: List<CatalogFilterOptionDto>,
) : CatalogFilterDto

@Serializable
@SerialName("range")
data class CatalogRangeFilterDto(
    override val id: String,
    override val label: String,
    val min: Double?,
    val max: Double?,
    val step: Double?,
) : CatalogFilterDto

@Serializable
@SerialName("text")
data class CatalogTextFilterDto(
    override val id: String,
    override val label: String,
) : CatalogFilterDto

@Serializable
data class CatalogFilterOptionDto(val value: String, val label: String)

@Serializable
data class ContentStoryCandidateDto(
    val sourceStoryId: String,
    val title: String,
    val authors: List<String> = emptyList(),
)

@Serializable
data class ContentStoryDetailsDto(
    val sourceStoryId: String,
    val title: String,
    val aliases: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val description: String? = null,
)

@Serializable
data class ContentReleaseDto(
    val sourceReleaseId: String,
    val title: String?,
    val rawNumber: String?,
    val languageTag: String?,
    val publishedAtEpochMillis: Long?,
)

@Serializable
data class ChapterDocumentDto(
    val title: String?,
    val blocks: List<ChapterBlockDto>,
)

@Serializable
sealed interface ChapterBlockDto

@Serializable @SerialName("paragraph")
data class ParagraphBlockDto(val text: String) : ChapterBlockDto

@Serializable @SerialName("heading")
data class HeadingBlockDto(val level: Int, val text: String) : ChapterBlockDto

@Serializable @SerialName("divider")
data object DividerBlockDto : ChapterBlockDto

@Serializable @SerialName("note")
data class NoteBlockDto(val text: String) : ChapterBlockDto
```

`ContentProtocol.kt` does not define host `StoryId`, canonical chapter identity, sync cursor policy, or persistence types.

`PluginProtocolValidator` is also pure protocol code:

```kotlin
data class ProtocolViolation(val code: String, val field: String)

object PluginProtocolValidator {
    fun validateOutput(
        operation: PluginOperation,
        payload: JsonElement,
        allowedNetworkHosts: Set<String>,
    ): List<ProtocolViolation>
}
```

It decodes the operation-specific output DTO, enforces item/text/token/block bounds, HTTPS URL syntax, and validates every returned remote URL host against `allowedNetworkHosts`. R2B Task 3 rejects the payload when this list is non-empty; catalog never sees schema-invalid protocol JSON.

- [ ] **Step 1: Write RED catalog bounds tests**

```kotlin
@Test
fun catalogSectionRejectsDuplicateSourceIds() {
    val item = CatalogItemDto(sourceId = "1", title = "One", contentType = WireContentType.MANGA)
    assertFailsWith<IllegalArgumentException> {
        CatalogSectionDto("top", "Top", listOf(item, item))
    }
}

@Test
fun scoreMustCarryPositiveScale() {
    assertFailsWith<IllegalArgumentException> { ScoreDto(value = 8.0, scale = 0.0) }
}
```

Add content tests that reject blank source story/release IDs and executable/raw HTML blocks. Keep structured content blocks only.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :plugins:api:test --tests '*ProtocolTest' --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Implement DTOs**

Port only wire-level invariants from the current contract:
- source IDs stable/non-blank;
- page/section item counts bounded;
- search query <= 1024 chars;
- continuation token null or non-blank;
- score finite and within scale;
- image URL HTTPS at DTO level; declared-host enforcement belongs to `PluginProtocolValidator` with the manifest host set;
- filter definitions bounded;
- chapter document structured and non-executable.

Do not add `AppResult`, `CatalogPlugin`, `ContentPlugin`, `StoryId`, `PluginId`, or app models.

- [ ] **Step 4: Run API suite**

```bash
./gradlew :plugins:api:test --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol   plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol
git commit -m "plugins: define vnext operation wire protocol"
```

### Task 3: Define detached package and repository provenance

**Files:**
- Create: `plugins/api/src/main/kotlin/app/openstory/plugins/api/packageformat/PluginArtifact.kt`
- Create: `plugins/api/src/main/kotlin/app/openstory/plugins/api/packageformat/RepositoryIndex.kt`
- Create: `plugins/api/src/test/kotlin/app/openstory/plugins/api/packageformat/RepositoryIndexTest.kt`
- Create: `docs/plugin-sdk/package-format.md`
- Create: `docs/plugin-sdk/repository-index.md`
- Delete at R2 close: legacy contents of the same docs that describe self-checksum/Selector layout.

**Interfaces:**
- Produces:

```kotlin
@Serializable
data class PluginArtifact(
    val pluginId: String,
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val signatureEd25519: String? = null,
)

@Serializable
data class RepositoryIndex(
    val schema: Int,
    val artifacts: List<PluginArtifact>,
)
```

- [ ] **Step 1: Write RED tests**

Create `RepositoryIndexTest.kt`:

```kotlin
class RepositoryIndexTest {
    private fun artifact(
        sha256: String = "a".repeat(64),
        url: String = "https://plugins.example/p.osp",
        signature: String? = null,
    ) = PluginArtifact("org.example.plugin", "1.0.0", url, sha256, signature)

    @Test fun invalidSha256IsRejected() = assertFailsWith<IllegalArgumentException> {
        artifact(sha256 = "not-a-sha")
    }

    @Test fun artifactUrlMustBeHttps() = assertFailsWith<IllegalArgumentException> {
        artifact(url = "http://plugins.example/p.osp")
    }

    @Test fun duplicatePluginVersionIsRejected() = assertFailsWith<IllegalArgumentException> {
        RepositoryIndex(schema = 1, artifacts = listOf(artifact(), artifact()))
    }

    @Test fun blankSignatureIsRejected() = assertFailsWith<IllegalArgumentException> {
        artifact(signature = " ")
    }
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :plugins:api:test   --tests app.openstory.plugins.api.packageformat.RepositoryIndexTest   --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Implement detached metadata**

Manifest bytes never contain their own full-archive checksum. Repository/install provenance carries SHA-256/signature externally.

Document canonical `.osp` layout:

```text
manifest.json
main.js
assets/<relative-file>   # regular bounded files only; optional
```

No `selector.json`.

- [ ] **Step 4: Run API suite and doc grep**

```bash
./gradlew :plugins:api:test --stacktrace
! grep -R -n 'packageChecksumSha256\|selector.json' docs/plugin-sdk/package-format.md
```

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add plugins/api/src/main/kotlin/app/openstory/plugins/api/packageformat   plugins/api/src/test/kotlin/app/openstory/plugins/api/packageformat   docs/plugin-sdk/package-format.md docs/plugin-sdk/repository-index.md
git commit -m "plugins: detach package integrity metadata"
```

