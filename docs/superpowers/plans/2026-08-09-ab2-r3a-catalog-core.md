# Architecture Baseline 2 R3A - Catalog Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `:catalog` own the minimal current catalog models, deterministic matching/ranking, durable repository contract, Home refresh, Search, and Details orchestration without introducing Room implementation details.

**Architecture:** R2C `CatalogSourceRegistry` is the remote/source seam. Pure catalog code decides canonical identity and semantic mutations; `CatalogRepository` owns durable-state semantics only. R3A uses fakes for persistence so the core behavior is independently testable before Room cutover.

**Tech Stack:** Kotlin/JVM, coroutines/Flow, deterministic pure catalog tests.

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

Entry: R2 accepted through R2C.

Exit:
- `Story`, `CatalogEntry`, matching/ranking, repository contracts and refresh/search/details services are owned only by `:catalog`;
- `CatalogRepository` exposes no DAO/Room/plugin execution API;
- product/source failures are isolated and typed at the catalog boundary;
- old `core:model`, `core:matching`, and `core:database` still exist only because R3B has not yet switched persistence and legacy presentation consumers.

R3A does **not** close R3.

### Task 1: Define minimal catalog-owned models and failure types

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/model/ContentType.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/model/Story.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogEntry.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogHome.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogDetails.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/model/Score.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/CatalogFailure.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/model/CatalogModelsTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class Story(
    val id: StoryId,
    val contentType: ContentType,
)

data class CatalogEntry(
    val storyId: StoryId,
    val pluginId: PluginId,
    val sourceId: String,
    val title: String,
    val aliases: Set<String>,
    val authors: Set<String>,
    val description: String?,
    val genres: Set<String>,
    val contentType: ContentType,
    val languageTags: Set<String>,
    val coverUrl: String?,
    val sourceUrl: String?,
    val score: Score?,
    val popularityRank: Long?,
)

data class Score(val value: Double, val scale: Double)

data class CatalogHomeSnapshot(
    val pluginId: PluginId,
    val pluginVersion: String,
    val refreshedAtEpochMillis: Long,
    val sections: List<CatalogHomeSection>,
)

data class CatalogHomeSection(
    val sourceId: String,
    val title: String,
    val items: List<CatalogEntry>,
)

data class StoryCatalogSnapshot(
    val story: Story,
    val entries: List<CatalogEntry>,
)

sealed interface CatalogFailure {
    val code: String
    val retryable: Boolean
}

data class CatalogStoreFailure(
    override val code: String,
    override val retryable: Boolean,
) : CatalogFailure
```

Technical fetch/plugin-version provenance is **not** a field on `Story` or `CatalogEntry`; it belongs to semantic mutation/persistence metadata.

- [ ] **Step 1: Write RED model tests**

```kotlin
@Test
fun storyStaysMinimal() {
    val story = Story(StoryId("story:1"), ContentType.MANGA)
    assertEquals(StoryId("story:1"), story.id)
}

@Test
fun scoreRequiresPositiveScaleAndBoundedValue() {
    assertFailsWith<IllegalArgumentException> { Score(8.0, 0.0) }
    assertFailsWith<IllegalArgumentException> { Score(11.0, 10.0) }
}

@Test
fun entryRejectsBlankSourceIdentity() {
    assertFailsWith<IllegalArgumentException> { entry(sourceId = " ") }
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :catalog:test --tests app.openstory.catalog.model.CatalogModelsTest --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Implement models and catalog-local validation**

Keep `ContentType` values needed by current product/plugin wire mapping:

```text
LIGHT_NOVEL
WEB_NOVEL
MANGA
ANIME
```

No Library status, chapter, release, progress, download, Room ID, plugin package state, or Compose model enters this package.

- [ ] **Step 4: Run catalog suite**

```bash
./gradlew :catalog:test --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/model   catalog/src/main/kotlin/app/openstory/catalog/CatalogFailure.kt   catalog/src/test/kotlin/app/openstory/catalog/model
git commit -m "catalog: define capability owned models"
```

### Task 2: Rebuild deterministic matching and ranking inside `:catalog`

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/matching/TitleNormalizer.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/matching/StoryMatcher.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/matching/MatchPolicy.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/matching/MatchResult.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/ranking/AggregateRanking.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/matching/StoryMatcherTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/ranking/AggregateRankingTest.kt`

**Interfaces:**
- Pure input snapshot:

```kotlin
data class CatalogMatchCandidate(
    val story: Story,
    val titles: Set<String>,
    val authors: Set<String>,
    val sourceKeys: Set<SourceKey>,
)

data class SourceKey(val pluginId: PluginId, val sourceId: String)

sealed interface StoryResolution {
    data class Existing(val storyId: StoryId) : StoryResolution
    data class Create(val story: Story) : StoryResolution
}
```

- [ ] **Step 1: Port the invariant tests, not old class shape**

Required RED cases:

```text
same source identity always keeps existing story
title match with conflicting non-empty authors does not auto-merge
input order does not change resolution
aggregate ranking preserves original score/scale
ranking tie-break is stable by StoryId/source key
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :catalog:test --tests '*StoryMatcherTest' --tests '*AggregateRankingTest' --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Implement pure matcher/ranking**

No Room, plugin runtime, Flow, Android, or Compose imports. Generate a new story ID from a stable normalized semantic signature plus collision suffix when no candidate is accepted.

- [ ] **Step 4: Run catalog suite**

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/matching   catalog/src/main/kotlin/app/openstory/catalog/ranking   catalog/src/test/kotlin/app/openstory/catalog/matching   catalog/src/test/kotlin/app/openstory/catalog/ranking
git commit -m "catalog: own deterministic matching and ranking"
```

### Task 3: Define durable repository contracts and semantic mutations

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogRepository.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogHomeMutation.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogDetailsMutation.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogMatchSnapshot.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/repository/CatalogRepositoryContractTest.kt`

**Interfaces:**
- Produces:

```kotlin
interface CatalogRepository {
    fun observeHomes(): Flow<List<CatalogHomeSnapshot>>
    fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?>
    suspend fun matchSnapshot(): CatalogMatchSnapshot
    suspend fun commitHomeRefresh(
        mutation: CatalogHomeMutation,
    ): Outcome<Unit, CatalogStoreFailure>
    suspend fun commitDetails(
        mutation: CatalogDetailsMutation,
    ): Outcome<StoryId, CatalogStoreFailure>
}
```

Repository responsibility is durable/local catalog state only. It must not:
- invoke plugins;
- perform HTTP;
- run matching/ranking;
- format UI;
- navigate;
- expose DAO/transaction handles.

- [ ] **Step 1: Write a fake repository contract test**

The test-local fake must prove an atomic semantic replacement model: a second Home mutation for plugin A replaces A membership while leaving plugin B untouched and keeping removed A entries available for details/history.

- [ ] **Step 2: Verify RED**

Expected: **FAIL** because repository/mutation types do not exist.

- [ ] **Step 3: Implement contracts + mutation validation**

`CatalogHomeMutation` includes one captured `refreshedAtEpochMillis` and hosted plugin version, resolved stories/entries, ordered sections and ordered source item IDs.

`CatalogDetailsMutation` carries rich entry metadata + fetch provenance and the resolved story ID.

- [ ] **Step 4: Run repository contract tests**

```bash
./gradlew :catalog:test --tests app.openstory.catalog.repository.CatalogRepositoryContractTest --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/repository   catalog/src/test/kotlin/app/openstory/catalog/repository
git commit -m "catalog: define durable repository boundary"
```

### Task 4: Implement Home refresh and cached Home query services

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogHomeQuery.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshResult.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/home/CatalogRefreshServiceTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/home/CatalogHomeQueryTest.kt`

**Interfaces:**
- `CatalogRefreshService` consumes `CatalogSourceRegistry`, `CatalogRepository`, `StoryMatcher`, and `Clock`.
- It fetches each source independently, normalizes/resolves with one durable match snapshot, then calls one semantic commit per successful source.
- `CatalogHomeQuery` combines cached source snapshots and ranking without invoking plugins.

- [ ] **Step 1: Write RED tests**

Required:

```text
source A failure leaves previous A snapshot untouched
source B success commits even when A fails
one refresh captures one timestamp for one source mutation
refresh matching result independent of incoming item order
cached Home query does not call source registry/runtime
```

- [ ] **Step 2: Verify RED**

Run focused classes; expected **FAIL**.

- [ ] **Step 3: Implement services**

Do not catch `CancellationException`. Convert source failures to `CatalogRefreshResult.SourceFailure` and continue other sources. No partial mutation is sent to repository.

- [ ] **Step 4: Run catalog Home tests**

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/home   catalog/src/test/kotlin/app/openstory/catalog/home
git commit -m "catalog: rebuild cached home orchestration"
```

### Task 5: Implement transient search and source-preserving detail enrichment

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchModels.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsService.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/details/CatalogDetailsServiceTest.kt`

**Interfaces:**
- Search returns transient grouped canonical results and per-source failures; it does not create Home memberships.
- Details loads an exact `(pluginId, sourceId)`, verifies returned source ID, resolves/preserves story identity, and commits rich metadata.

- [ ] **Step 1: Write RED tests**

Required:

```text
slow/failing source does not cancel successful search sources
search does not call commitHomeRefresh
same story from two sources appears once with both source cards
details source ID mismatch fails without persistence
details enrichment does not reorder/change Home membership
```

- [ ] **Step 2: Verify RED**

Expected: **FAIL**.

- [ ] **Step 3: Implement services**

Preserve source-specific score/scale in search result sources. Recent search history remains presentation/in-memory behavior and is not added here.

- [ ] **Step 4: Run catalog module suite**

```bash
./gradlew :catalog:test --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/search   catalog/src/main/kotlin/app/openstory/catalog/details   catalog/src/test/kotlin/app/openstory/catalog/search   catalog/src/test/kotlin/app/openstory/catalog/details
git commit -m "catalog: rebuild search and detail services"
```

