# Catalog Pure Engine Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract Catalog matching, reconciliation, and fusion into constitutionally pure JVM modules without changing observable behavior.

**Architecture:** `:catalog:model` owns stable immutable Catalog data and depends only on `:core:common`. `:catalog:engine` owns deterministic matching, reconciliation, and fusion and depends only on `:core:common` plus `:catalog:model`; the existing `:catalog` module remains the effectful adapter and orchestration layer.

**Tech Stack:** Kotlin 2.4.10, Gradle Kotlin DSL, JUnit/kotlin-test, Android library adapters, project module-boundary verifier, Detekt.

**Spec:** `docs/superpowers/specs/2026-09-06-catalog-pure-engine-extraction-design.md`

## Global Constraints

- Preserve matching scores, thresholds, tie-breaks, decisions, identifiers, and fingerprints exactly.
- Preserve reconciliation ranking, reason codes, winning lead, merge eligibility, and evidence fingerprints exactly.
- Preserve fusion primary selection, hysteresis, field selection, provenance, health, and canonical output exactly.
- Do not change Room schema version 12 or any migration.
- Do not change plugin protocol, bundled plugins, UI state, or navigation contracts.
- `:catalog:model` may depend only on `:core:common`.
- `:catalog:engine` may depend only on `:core:common` and `:catalog:model`.
- Neither pure module may import Android, coroutines, serialization, DI, plugins, storage, network, filesystem, clocks, dispatchers, Catalog repositories, or Catalog orchestration.
- Run focused tests, Detekt, and architecture verification per task. Defer the full repository gate until Task 5.

---

### Task 1: Establish the Pure Catalog Model Foundation

**Files:**
- Create: `catalog/model/build.gradle.kts`
- Create: `catalog/model/src/test/kotlin/app/openstory/catalog/model/CatalogModelBoundaryTest.kt`
- Move: `catalog/src/main/kotlin/app/openstory/catalog/model/*.kt` to `catalog/model/src/main/kotlin/app/openstory/catalog/model/`
- Move: `catalog/src/main/kotlin/app/openstory/catalog/identity/SourceKey.kt` to `catalog/model/src/main/kotlin/app/openstory/catalog/identity/SourceKey.kt`
- Move: `catalog/src/main/kotlin/app/openstory/catalog/identity/ExternalIdentifier.kt` to `catalog/model/src/main/kotlin/app/openstory/catalog/identity/ExternalIdentifier.kt`
- Modify: `settings.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`
- Modify: `catalog/build.gradle.kts`
- Modify: `library/build.gradle.kts`
- Modify: `storage/room/build.gradle.kts`
- Modify: `feature/catalog/build.gradle.kts`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: `PluginId` and `StoryId` from `:core:common`.
- Produces: unchanged FQCNs under `app.openstory.catalog.model.*`, `app.openstory.catalog.identity.SourceKey`, and `app.openstory.catalog.identity.ExternalIdentifier` from the new `:catalog:model` artifact.

- [ ] **Step 1: Add a failing constitutional module test**

Add this test to `ModuleGraphTest` before declaring the module:

```kotlin
@Test
fun catalogModelIsConstitutionallyPureJvm() {
    val policy = ModuleBoundaryPolicyLoader.load(
        File("../config/architecture/module-boundaries.json"),
    )
    val rule = policy.modules.getValue(":catalog:model")

    assertEquals("jvm", rule.platform.policyValue)
    assertEquals("exact", rule.dependencyMode.policyValue)
    assertEquals(setOf(":core:common"), rule.productionDependencies)
    assertTrue(rule.testDependencies.isEmpty())

    val build = File("../catalog/model/build.gradle.kts").readText()
    assertTrue("id(\"openstory.kotlin.jvm\")" in build)
    assertFalse("openstory.android" in build)
    assertFalse("kotlinx.coroutines" in build)
    assertFalse("javax.inject" in build)
}
```

- [ ] **Step 2: Run the new test and confirm the missing-module failure**

Run:

```powershell
.\gradlew.bat :build-logic:test --tests '*ModuleGraphTest.catalogModelIsConstitutionallyPureJvm' --no-daemon
```

Expected: FAIL because `:catalog:model` is absent from settings/policy and has no build file.

- [ ] **Step 3: Declare the module and its strict dependency boundary**

Add to `settings.gradle.kts` immediately after `include(":catalog")`:

```kotlin
include(":catalog:model")
```

Create `catalog/model/build.gradle.kts`:

```kotlin
plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    implementation(project(":core:common"))
    testImplementation(kotlin("test-junit"))
}
```

Add a `:catalog:model` policy entry with platform `jvm`, exact production dependency `:core:common`, and the forbidden imports from the spec. Update the frozen production-module count in `ModuleGraphTest` from `17` to `18`.

- [ ] **Step 4: Move foundational model sources without semantic edits**

Move the listed source files into `catalog/model/src/main`. Keep their existing package declarations and class bodies unchanged:

```kotlin
package app.openstory.catalog.model
```

```kotlin
package app.openstory.catalog.identity
```

The moved identity files are only `SourceKey.kt` and `ExternalIdentifier.kt`; effectful identity repositories, merge executors, and selectors stay in `:catalog`.

- [ ] **Step 5: Add explicit model dependencies**

Add this dependency to `:catalog` and every current module that imports the moved types directly (`:library`, `:storage:room`, `:feature:catalog`, and `:app`):

```kotlin
implementation(project(":catalog:model"))
```

Update each corresponding policy entry so its exact production dependency set matches its build script. Do not use `api(project(":catalog:model"))` as a compatibility shortcut.

- [ ] **Step 6: Add a source-level purity smoke test**

Create `CatalogModelBoundaryTest.kt`:

```kotlin
package app.openstory.catalog.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogModelBoundaryTest {
    @Test
    fun stableValuesRemainConstructibleWithoutRuntimeDependencies() {
        assertEquals(ContentType.MANGA, Story(app.openstory.common.id.StoryId("story:model"), ContentType.MANGA).contentType)
    }
}
```

- [ ] **Step 7: Run focused model and consumer verification**

Run:

```powershell
.\gradlew.bat :catalog:model:test :catalog:testDebugUnitTest :library:testDebugUnitTest :build-logic:test detekt verifyArchitecture --no-daemon
```

Expected: BUILD SUCCESSFUL; no duplicate classes remain under `catalog/src/main`.

- [ ] **Step 8: Commit the model foundation**

```powershell
git add settings.gradle.kts config/architecture/module-boundaries.json build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt catalog/model catalog/build.gradle.kts library/build.gradle.kts storage/room/build.gradle.kts feature/catalog/build.gradle.kts app/build.gradle.kts catalog/src/main/kotlin/app/openstory/catalog/model catalog/src/main/kotlin/app/openstory/catalog/identity
git commit -m "refactor(catalog): extract pure model foundation"
```

---

### Task 2: Extract Matching into `:catalog:engine`

**Files:**
- Create: `catalog/engine/build.gradle.kts`
- Create: `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/matching/TitleNormalizer.kt`
- Create: `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/matching/MatchPolicy.kt`
- Create: `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/matching/MatchResult.kt`
- Create: `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/matching/StoryMatcher.kt`
- Create: `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/matching/CatalogMatchIndex.kt`
- Move tests: `catalog/src/test/kotlin/app/openstory/catalog/matching/*.kt` to `catalog/engine/src/test/kotlin/app/openstory/catalog/engine/matching/`
- Modify: `settings.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`
- Modify: `catalog/build.gradle.kts`
- Modify: `storage/room/build.gradle.kts`
- Modify imports in: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogMatchSnapshot.kt`
- Modify imports in: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogCandidateIndex.kt`
- Modify imports in: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationEngine.kt`
- Modify imports in: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationEvidenceFactory.kt`
- Modify imports in: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify imports in: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntityMapper.kt`
- Modify imports in: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`

**Interfaces:**
- Consumes: `Story`, `ContentType`, `SourceKey`, `ExternalIdentifier`, `StoryId`, and `PluginId` from Task 1 and `:core:common`.
- Produces: `MatchPolicy`, `CatalogMatchCandidate`, `CatalogMatchEvidence`, `CatalogMatchResult`, `StoryResolution`, `TitleNormalizer`, `StoryMatcher`, and `CatalogMatchIndex` under `app.openstory.catalog.engine.matching`.

- [ ] **Step 1: Add a failing engine constitution test**

Add to `ModuleGraphTest`:

```kotlin
@Test
fun catalogEngineIsConstitutionallyPureJvm() {
    val policy = ModuleBoundaryPolicyLoader.load(
        File("../config/architecture/module-boundaries.json"),
    )
    val rule = policy.modules.getValue(":catalog:engine")

    assertEquals("jvm", rule.platform.policyValue)
    assertEquals("exact", rule.dependencyMode.policyValue)
    assertEquals(setOf(":core:common", ":catalog:model"), rule.productionDependencies)
    assertTrue(rule.testDependencies.isEmpty())

    val build = File("../catalog/engine/build.gradle.kts").readText()
    assertTrue("id(\"openstory.kotlin.jvm\")" in build)
    assertFalse("openstory.android" in build)
    assertFalse("kotlinx.coroutines" in build)
    assertFalse("kotlinx.serialization" in build)
    assertFalse("javax.inject" in build)
}
```

- [ ] **Step 2: Run the test and confirm the missing-engine failure**

Run:

```powershell
.\gradlew.bat :build-logic:test --tests '*ModuleGraphTest.catalogEngineIsConstitutionallyPureJvm' --no-daemon
```

Expected: FAIL because `:catalog:engine` is not declared.

- [ ] **Step 3: Declare the pure engine module**

Add to `settings.gradle.kts`:

```kotlin
include(":catalog:engine")
```

Create `catalog/engine/build.gradle.kts`:

```kotlin
plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":catalog:model"))
    testImplementation(kotlin("test-junit"))
}
```

Add the exact `:catalog:engine` policy from the spec and update the production-module count from `18` to `19`.

- [ ] **Step 4: Move matching code with only package/import changes**

Move the five matching files from `:catalog` to `:catalog:engine`. Change only the package declaration:

```kotlin
package app.openstory.catalog.engine.matching
```

Preserve every policy default, comparator, digest input, threshold, normalization rule, and collection-order rule. Replace internal imports from the old package with `app.openstory.catalog.engine.matching.*` only where required.

- [ ] **Step 5: Move matching tests and freeze current outputs**

Move `StoryMatcherTest.kt` and `CatalogMatchIndexTest.kt` into the engine test source set and change their package to:

```kotlin
package app.openstory.catalog.engine.matching
```

Keep every current assertion. Add this characterization assertion to the existing deterministic-ID test rather than inventing a new ID format:

```kotlin
assertEquals(firstResolution, secondResolution)
```

where both resolutions are produced from the same facts supplied in different list instances.

- [ ] **Step 6: Rewire production consumers explicitly**

Add `implementation(project(":catalog:engine"))` to `:catalog` and `:storage:room`, then update their architecture-policy dependency sets.

Replace old imports mechanically:

```kotlin
import app.openstory.catalog.matching.CatalogMatchCandidate
```

with:

```kotlin
import app.openstory.catalog.engine.matching.CatalogMatchCandidate
```

Apply the same package replacement for `CatalogMatchEvidence`, `CatalogMatchIndex`, `StoryMatcher`, `MatchPolicy`, `StoryResolution`, and `TitleNormalizer`. Do not add old-package type aliases or facades.

- [ ] **Step 7: Run focused matching verification**

Run:

```powershell
.\gradlew.bat :catalog:engine:test :catalog:testDebugUnitTest :storage:room:testDebugUnitTest :build-logic:test detekt verifyArchitecture --no-daemon
```

Expected: BUILD SUCCESSFUL; `rg --files catalog/src/main/kotlin/app/openstory/catalog/matching` returns no Kotlin implementation files.

- [ ] **Step 8: Commit matching extraction**

```powershell
git add settings.gradle.kts config/architecture/module-boundaries.json build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt catalog/engine catalog/build.gradle.kts storage/room/build.gradle.kts catalog/src/main catalog/src/test storage/room/src/main
git commit -m "refactor(catalog): extract pure matching engine"
```

---

### Task 3: Extract Reconciliation Decisions and Evidence

**Files:**
- Move to model: `catalog/src/main/kotlin/app/openstory/catalog/evidence/CatalogSourceRecord.kt`
- Split and move value types from: `catalog/src/main/kotlin/app/openstory/catalog/metadata/CatalogMetadata.kt`
- Create: `catalog/model/src/main/kotlin/app/openstory/catalog/metadata/CatalogMetadataValues.kt`
- Create: `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/evidence/CatalogEvidenceNormalizer.kt`
- Create: `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/evidence/CatalogEvidenceFingerprints.kt`
- Move: `CatalogReconciliationEngine.kt`, `ReconciliationModels.kt`, `ReconciliationPolicy.kt`, `CatalogCandidateIndex.kt`, `CatalogIngestReconciliationIndex.kt`, and `ReconciliationEvidenceFactory.kt` into `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/reconciliation/`
- Move: `catalog/src/main/kotlin/app/openstory/catalog/identity/CatalogStoryIdFactory.kt` into `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/reconciliation/CatalogStoryIdFactory.kt`
- Move tests: reconciliation engine/index/model/adversarial tests into `catalog/engine/src/test/kotlin/app/openstory/catalog/engine/reconciliation/`
- Modify: remaining reconciliation services, Home/Search/Details callers, DI, test fixtures, and explicit module dependencies.

**Interfaces:**
- Consumes: Task 1 model values and Task 2 `TitleNormalizer`.
- Produces: `ReconciliationEvidence`, `ReconciliationPolicy`, `ReconciliationAssessment`, `ReconciliationCandidateSelection`, `CatalogReconciliationEngine`, `CatalogCandidateIndex`, `InMemoryCatalogCandidateIndex`, and `CatalogIngestReconciliationIndex` under `app.openstory.catalog.engine.reconciliation`.

- [ ] **Step 1: Add a failing reconciliation characterization test**

Move the existing adversarial fixture first and add an equality assertion that evaluates the same evidence set in forward and reverse order:

```kotlin
assertEquals(
    engine.rankCandidates(incoming, candidates),
    engine.rankCandidates(incoming, candidates.reversed()),
)
```

- [ ] **Step 2: Run only the characterization test and confirm compilation fails against the not-yet-moved API**

```powershell
.\gradlew.bat :catalog:engine:test --tests '*ReconciliationAdversarialFixtureTest*' --no-daemon
```

Expected: FAIL because reconciliation engine packages are not present in `:catalog:engine`.

- [ ] **Step 3: Split metadata values from effect contracts**

Move these unchanged values to `CatalogMetadataValues.kt` in `:catalog:model`:

```kotlin
CatalogMetadataKey
CatalogMetadataLevel
CatalogMetadataStamp
CatalogMetadataSnapshot
```

Keep these in `:catalog`:

```kotlin
CatalogMetadataScope
CatalogMetadataAccess
CatalogMetadataResult
CatalogMetadataFailure
```

Move `CatalogSourceRecord` to `:catalog:model` without changing its FQCN.

- [ ] **Step 4: Move pure evidence and reconciliation code**

Use these package declarations:

```kotlin
package app.openstory.catalog.engine.evidence
```

```kotlin
package app.openstory.catalog.engine.reconciliation
```

Preserve all existing constants, hashing input order, hard-conflict gates, scoring, thresholds, reason ordering, stable-key ordering, and fork semantics.

- [ ] **Step 5: Keep effectful reconciliation workflows in `:catalog`**

Do not move:

```text
CatalogReconciliationService
CatalogReconciliationMaintenance
ReconciliationReviewService
ReconciliationCaseRepository
ReconciliationDiagnostics
StoryMergeLineage
```

Update those files to import the engine decision types. They remain responsible for clocks, repositories, mutexes, merge execution, review persistence, and diagnostics sinks.

- [ ] **Step 6: Run focused reconciliation verification**

```powershell
.\gradlew.bat :catalog:engine:test :catalog:testDebugUnitTest :feature:catalog:testDebugUnitTest :storage:room:testDebugUnitTest detekt verifyArchitecture --no-daemon
```

Expected: BUILD SUCCESSFUL with all existing reconciliation assertions unchanged.

- [ ] **Step 7: Commit reconciliation extraction**

```powershell
git add catalog/model catalog/engine catalog/src feature/catalog/src storage/room/src app/src config/architecture/module-boundaries.json *.gradle.kts
git commit -m "refactor(catalog): extract reconciliation engine"
```

---

### Task 4: Extract Canonical Fusion Decisions

**Files:**
- Move: `catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalModels.kt` to `catalog/model/src/main/kotlin/app/openstory/catalog/canonical/CanonicalModels.kt`
- Move: `CatalogFusionEngine.kt`, `FusionPolicy.kt`, `PrimarySelectionPolicy.kt`, and `CanonicalGenerationValidator.kt` into `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/fusion/`
- Move fusion tests into `catalog/engine/src/test/kotlin/app/openstory/catalog/engine/fusion/`
- Modify: `CatalogSourceAvailabilityResolver.kt`, `CanonicalFusionService.kt`, `CatalogFullMetadataFallbackService.kt`, DI, Feature Catalog fixtures, and explicit dependencies.

**Interfaces:**
- Consumes: canonical/model values from `:catalog:model` and evidence normalization/fingerprint helpers from Task 3.
- Produces: `FusionInput`, `FusionSource`, `CatalogFusionEngine`, `CanonicalGenerationCandidate`, `PrimarySelectionDecision`, fusion policy values, and `CanonicalGenerationValidator` under `app.openstory.catalog.engine.fusion`.

- [ ] **Step 1: Add a failing golden fusion test**

Move the current field/provenance fixture and assert the full generated value rather than isolated fields:

```kotlin
assertEquals(expectedGenerationCandidate, CatalogFusionEngine().fuse(input))
```

The expected object must include `fusionFingerprint`, `effectivePrimary`, `metadata`, `health`, `provenance`, `sourceContentTypes`, and `primarySelection`.

- [ ] **Step 2: Run the golden test and confirm the new package is missing**

```powershell
.\gradlew.bat :catalog:engine:test --tests '*CatalogFusionEngineFieldsTest*' --no-daemon
```

Expected: FAIL until fusion is present in the engine module.

- [ ] **Step 3: Move canonical immutable values into `:catalog:model`**

Move `CanonicalModels.kt` unchanged and keep:

```kotlin
package app.openstory.catalog.canonical
```

Repository and bootstrap use-case files remain in `:catalog`.

- [ ] **Step 4: Move pure fusion code into the engine package**

Use:

```kotlin
package app.openstory.catalog.engine.fusion
```

Do not move `CatalogSourceAvailabilityResolver`, `CanonicalFusionService`, or `CanonicalFusionContract`; they contain DI, repository work, suspend execution, or lifecycle result types.

- [ ] **Step 5: Rewire adapters without changing execution order**

Update `:catalog` services to assemble the same `FusionInput`, invoke the new engine, validate the same candidate, and promote through the existing repository path. Preserve the exact order of ranking, selection, fingerprinting, validation, and promotion.

- [ ] **Step 6: Run focused fusion verification**

```powershell
.\gradlew.bat :catalog:engine:test :catalog:testDebugUnitTest :feature:catalog:testDebugUnitTest :app:testDebugUnitTest detekt verifyArchitecture --no-daemon
```

Expected: BUILD SUCCESSFUL with existing fusion and canonical-presentation tests green.

- [ ] **Step 7: Commit fusion extraction**

```powershell
git add catalog/model catalog/engine catalog/src feature/catalog/src app/src config/architecture/module-boundaries.json *.gradle.kts
git commit -m "refactor(catalog): extract canonical fusion engine"
```

---

### Task 5: Close Boundary Gaps and Certify the Branch

**Files:**
- Modify: `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt`
- Modify: `config/architecture/module-boundaries.json`
- Inspect and, only if the scan fails, correct: `catalog/build.gradle.kts`, `catalog/model/build.gradle.kts`, `catalog/engine/build.gradle.kts`, `app/build.gradle.kts`, `library/build.gradle.kts`, `storage/room/build.gradle.kts`, and `feature/catalog/build.gradle.kts`
- Test: Catalog, Catalog Engine, Catalog Model, Feature Catalog, Storage Room, App, architecture, lint, and Detekt tasks.

**Interfaces:**
- Consumes: completed modules and adapters from Tasks 1-4.
- Produces: a certified branch with no old matching/reconciliation/fusion implementation in `:catalog` and no forbidden dependency leaks.

- [ ] **Step 1: Add final source-layout assertions**

Add a build-logic test that scans production sources and fails if the old algorithm packages contain implementation classes:

```kotlin
val root = File("..").canonicalFile
val matchingFiles = File(root, "catalog/src/main/kotlin/app/openstory/catalog/matching")
    .walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
    .map(File::getName)
    .toSet()
assertTrue(matchingFiles.isEmpty(), "Matching implementation remains in :catalog: $matchingFiles")

val reconciliationFiles = File(root, "catalog/src/main/kotlin/app/openstory/catalog/reconciliation")
    .walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
    .map(File::getName)
    .toSet()
assertEquals(
    setOf(
        "CatalogReconciliationService.kt",
        "CatalogReconciliationMaintenance.kt",
        "ReconciliationReviewService.kt",
        "ReconciliationCaseRepository.kt",
        "ReconciliationDiagnostics.kt",
        "StoryMergeLineage.kt",
    ),
    reconciliationFiles,
)

val fusionFiles = File(root, "catalog/src/main/kotlin/app/openstory/catalog/fusion")
    .walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
    .map(File::getName)
    .toSet()
assertEquals(
    setOf(
        "CatalogSourceAvailabilityResolver.kt",
        "CanonicalFusionService.kt",
        "CanonicalFusionContract.kt",
    ),
    fusionFiles,
)
```

- [ ] **Step 2: Run structural scans**

```powershell
rg -n '^import (android|androidx|kotlinx.coroutines|kotlinx.serialization|javax.inject|app.openstory.plugins|app.openstory.storage)\.' catalog/model/src/main catalog/engine/src/main
rg -n 'class (StoryMatcher|CatalogReconciliationEngine|CatalogFusionEngine)' catalog/src/main
```

Expected: both commands return no matches.

- [ ] **Step 3: Run the full branch verification once**

```powershell
.\gradlew.bat verifyArchitecture :build-logic:test test testDebugUnitTest lintDebug detekt :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Review the complete diff for behavior changes**

```powershell
git diff perf/discover-end-to-end...HEAD --stat
git diff perf/discover-end-to-end...HEAD -- catalog/engine catalog/model catalog/src config/architecture/module-boundaries.json
```

Confirm there are no modified numeric policy constants, threshold values, comparator directions, hash field orderings, reason enums, plugin contracts, Room schemas, or UI contracts.

- [ ] **Step 5: Commit final boundary certification**

```powershell
git add build-logic config catalog/engine catalog/model catalog app library storage feature settings.gradle.kts
git commit -m "test(catalog): certify pure engine boundary"
```
