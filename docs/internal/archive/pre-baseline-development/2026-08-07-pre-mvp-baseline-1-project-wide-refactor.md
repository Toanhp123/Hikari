# Pre-MVP Baseline 1 and Project-Wide Architecture Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebase the pre-public OpenStory/Hikari repository onto one clean Baseline 1 across the whole repository: reset development-only Room/Selector history, remove generation-based naming, narrow module/file ownership, eliminate stale IDE/dependency/checkpoint artifacts, decompose the oversized network boundary, restore zero-debt quality gates, and leave one compact transparent architecture before Wave 04 Task 03 runtime work resumes.

**Architecture:** Preserve product/domain/security semantics while cleaning repository mechanics that encode development history or unclear ownership. Rebase Room and Selector to initial schema 1, keep the typed endpoint/binding contract, narrow the Room plugin-registry adapter to a neutral host port, remove unused module edges and generated IDE state, extract validation-only URL policy and bounded response reading from the network gateway, move semantic-version policy out of installer orchestration, rename active verification by capability rather than wave, and enforce the resulting baseline permanently. Typed binding evaluation/DTO mapping remains the next Wave 04 Task 03 feature work.

**Tech Stack:** Kotlin 2.4.10, JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0, Room 2.8.4, kotlinx.serialization 1.11.0, coroutines 1.11.0, OkHttp 5.3.0, Jsoup 1.22.2, Kotlin test/JUnit, AndroidX migration/instrumentation testing.

## Global Constraints

- Android-only MVP; no account, cloud sync, remote chapter service, or push backend.
- Package namespace and application ID remain `app.openstory`.
- Minimum SDK remains 26. Compile and target SDK remain 37.
- Build runtime remains JDK 17; do not weaken the repository's JDK 17 gate.
- Do not change product/domain semantics: `CanonicalStory`, `CanonicalChapter`, `ChapterRelease`, catalog/content separation, local-first operation, and plugin security boundaries remain unchanged.
- Do not add Wave 05+ functionality during this refactor.
- Do not implement the missing typed selector evaluator, Catalog/Content DTO mappers, or JavaScript sandbox in this refactor. The refactor must leave a clean handoff for that work.
- No compatibility is required for development-only database files, selector JSON, `.osp` fixtures, or emulator installs created before this baseline. Developers clear app data/reinstall when moving to this baseline.
- Existing public-facing version spaces stay independent. “Baseline 1” does **not** mean one shared version variable.
- App `versionCode = 1` and `versionName = "1.0"` already match the baseline and should not be changed by this refactor.
- Repository index `schemaVersion = 1` already matches the baseline and should remain unchanged.
- Plugin API examples already target major 1; do not invent a new package-format version field that does not exist in the current source contract.
- The current typed selector endpoint/binding contract is rebased from development name “V2” to the only supported `schemaVersion = 1`.
- The old linear selector definition (`operations -> SelectorValue`) is removed, not retained behind `legacy`, `compat`, or version adapters.
- Active production selector code, active selector tests, sample plugin names, and active plugin-SDK docs must contain no architectural type/file names ending in `V1`, `V2`, `Legacy`, or `Compat` after the checkpoint. Historical archive documents are exempt.
- Root selector packages should expose canonical concepts, not implementation history. Validation implementation belongs under a validation package; Catalog and Content endpoint contracts remain under their domain subpackages.
- Do not replace small focused files with a monolith. “Fewer files” means removing duplicate/version-history layers and grouping by responsibility, not merging unrelated responsibilities.
- TDD is mandatory: focused failing test, minimal implementation, focused pass, affected module suite, then commit.
- One independently reviewable commit per task. Do not combine database, selector-contract, host-runtime cleanup, and documentation rebaseline in one commit.
- Every task must end with a clean `git status --short`.

---

## Source Audit and Baseline Decisions

The current source snapshot contains these relevant facts:

| Area | Current source | Baseline decision |
|---|---|---|
| App version | `versionCode = 1`, `versionName = "1.0"` | Keep |
| Room | `OpenStoryDatabase(version = 3)` + schemas `1.json`, `2.json`, `3.json` + migrations 1→2 and 2→3 | Current full schema becomes schema **1**; delete development migrations/history |
| Plugin API | `PluginApiVersion(major, minor)` with major-compat rules; fixtures use 1.0 | Keep compatibility model; baseline remains major **1** |
| Repository index | `CURRENT_SCHEMA_VERSION = 1` | Keep |
| Package layout | No independent package schema-version field exists | Do **not** invent one |
| Selector old contract | `SelectorPluginDefinition` schema 1, linear operations, raw `SelectorValue` runtime | Remove |
| Selector typed contract | `SelectorPluginDefinitionV2` schema 2, Catalog/Content endpoints and bindings | Rename/rebase as canonical `SelectorDefinition`, schema **1** |
| Selector decoder | `DecodedSelectorDefinition.V1/V2` | Replace with single-version decoder returning `SelectorDefinition` |
| Selector request ops | Includes fetch, cleanup, extraction, transform operations | Keep only document-acquisition operations used by typed contract: `HttpGet`, `RemoveElements` |
| Selector host | `SelectorInterpreter.kt` ~584 lines + `SelectorRuntime` + `TransformRegistry` serving old linear runtime | Replace with focused bounded `SelectorDocumentLoader`; typed evaluator remains next task after refactor |
| Active docs | Describe V1 compatibility + V2 continuation | Rebaseline to “Selector Schema 1” and mark old V1/V2 history as pre-baseline archive only |

### Target selector contract tree

```text
core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/
├── SelectorDefinition.kt
├── SelectorRequest.kt
├── SelectorBinding.kt
├── SelectorValidation.kt
├── catalog/
│   ├── CatalogSelectorEndpoints.kt
│   └── CatalogSelectorValidator.kt
├── content/
│   ├── ContentSelectorEndpoints.kt
│   └── ContentSelectorValidator.kt
└── validation/
    ├── SelectorDefinitionValidator.kt
    ├── SelectorRequestValidator.kt
    ├── SelectorBindingValidator.kt
    ├── SelectorOutputValidator.kt
    └── SelectorSyntaxValidator.kt
```

Rules for this tree:

- `SelectorDefinition.kt` owns only the serialized root envelope and `CURRENT_SCHEMA_VERSION = 1`.
- `SelectorRequest.kt` owns request-plan DTOs, requested limits, and the closed request-operation set (`HttpGet`, `RemoveElements`).
- `SelectorBinding.kt` owns the closed binding AST and binding enums.
- `SelectorValidation.kt` is the small public facade plus stable validation error/exception types.
- `catalog/` and `content/` own endpoint-specific serialized contracts and their endpoint validators.
- `validation/` owns shared internal structural/syntax/output validation.
- There is no `SelectorPluginDefinitionV2.kt`, `SelectorV2DefinitionValidation.kt`, `validateV1`, `DecodedSelectorDefinition.V1`, or `DecodedSelectorDefinition.V2`.

### Target selector host tree immediately after this refactor

```text
core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/
├── SelectorExecutionContext.kt
├── SelectorLimits.kt
├── SelectorDocumentLoader.kt
└── HtmlDocumentAdapter.kt
```

This is intentionally small. The next Wave 04 Task 03 runtime plan may then add focused packages such as `binding/`, `mapper/`, `validation/`, and `runtime/` when those behaviors actually exist. The refactor must not pre-create empty scaffolding.

### Target active selector fixture names

```text
sample-plugins/selector-fixture/
├── manifest.json
└── selector.json

core/plugin-api/src/test/resources/plugin-selector/
└── selector.json
```

No active fixture directory or test class should carry `v1`/`v2` naming.

---


## Project-Wide Audit Expansion

The second repository-wide audit extends the original Room/Selector cleanup with the following reviewed decisions.

| Area | Audit evidence | Baseline action |
|---|---|---|
| IDE metadata | `.idea/gradle.xml` hard-codes current modules; `.idea/misc.xml` hard-codes `temurin-17` while Gradle already enforces JDK 17 | Remove tracked `.idea/`; ignore it entirely |
| Project identity | repository/app = Hikari; namespace/plugin ecosystem/user-agent = OpenStory | Keep both; document exact ownership instead of mass-renaming |
| Registry persistence | `core/database` imports `plugin.host.install` concrete types | Narrow port to neutral registry records; rename adapter `RoomPluginRegistry` |
| Module dependencies | `core/network` declares `:core:plugin-api` but production imports none of it | Remove unused edge and policy entry |
| Network gateway | `AllowlistedHttpGateway.kt` is ~634 lines and owns URL validation + redirects + body reading | Extract `PluginUrlPolicy` and `BoundedResponseReader`; preserve behavior |
| Installer ownership | `PluginInstaller.kt` also owns full semantic-version algorithm | Move only `PluginVersionPolicy` into a focused file |
| Active verification naming | tests/scripts still contain Wave 02/03/checkpoint history | Rename active verification by capability; wave names remain only in docs/history |
| Detekt baseline | baseline contains only old Selector runtime/test findings | Delete baseline after selector cleanup and require clean Detekt |
| Test fixtures | plugin-api testFixtures and `:test:fixtures` serve different roles | Keep both; document roles; do not merge cosmetically |
| Wire DTO model files | Catalog/Content model files are ~270 lines but cohesive | Keep grouped; no DTO-per-file churn |
| Security host files | package verifier/storage are long but cohesive | Do not split without responsibility evidence |

### Permanent cleanliness definition

Baseline 1 is not considered complete unless all of the following are true:

```text
- no active V1/V2/Legacy/Compat selector generation names
- no active production/test class named for remediation or a completed Wave checkpoint
- no committed .idea state
- no database production import from plugin.host.install
- no unused core:network -> core:plugin-api dependency
- no Detekt baseline debt
- no production source file above 500 lines without a reviewed allowlist reason
- no test/support source file above 750 lines without a reviewed allowlist reason
- Hikari/OpenStory naming roles are explicit
- reusable verification is named by capability, not creation history
```

These are architecture/hygiene constraints, not requests to split cohesive files merely to lower line counts.

---

## Preflight: Freeze the Refactor Boundary

This section is not a product commit.

- [ ] **Step 1: verify JDK and repository state**

Run in PowerShell from the real Git repository:

```powershell
$ErrorActionPreference = "Stop"

java -version
if ($LASTEXITCODE -ne 0) { throw "JDK is unavailable" }

$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
if ($javaVersion -notmatch '17') {
    throw "This refactor must run under JDK 17. Found: $javaVersion"
}

if (git status --short) {
    throw "Worktree must be clean before baseline refactor."
}

git branch --show-current
git log -5 --oneline
```

Expected: JDK 17, clean worktree, current Wave 04 Task 03 work visible in history.

- [ ] **Step 2: create an isolated refactor branch**

```powershell
git switch -c refactor/pre-mvp-baseline-1
```

Expected: current branch is `refactor/pre-mvp-baseline-1`.

- [ ] **Step 3: record the pre-refactor verification result**

```powershell
bash ./scripts/verify.sh
./gradlew.bat --no-daemon --dependency-verification strict `
  :core:database:testDebugUnitTest `
  :core:plugin-api:test `
  :core:plugin-host:test `
  --stacktrace
```

Expected: record exact pass/fail evidence in the refactor checkpoint notes. If the baseline has a pre-existing failure, do not silently repair unrelated behavior inside this refactor; record it before proceeding.

- [ ] **Step 4: record the compatibility reset warning**

Before implementation, developers using local app data must accept:

```text
This branch intentionally does not migrate pre-baseline developer databases or selector fixtures.
Uninstall/clear app data when testing this branch against a device/emulator used by an older development build.
```

---

### Task 1: Commit the Pre-MVP Baseline Reset Decision

**Commit:** `docs: define pre-mvp baseline one reset`

**Files:**
- Create: `docs/superpowers/specs/2026-08-07-pre-mvp-baseline-1-design.md`
- Create: `docs/project/pre-mvp-baseline-1.md`
- Modify: `docs/project/document-governance.md`
- Modify: `docs/README.md`

**Interfaces:**
- Consumes: current approved product design, current unified docs, current source audit.
- Produces: one explicit architectural decision establishing that development compatibility before Baseline 1 is intentionally discarded while product/security semantics remain unchanged.

**Acceptance:**
- The decision explicitly lists every version space and whether it is reset or already at 1.
- It states that package format currently has no independent schema version and therefore no new field is introduced.
- It states that database schema 3 is rebased to initial schema 1.
- It states that the typed selector contract formerly called V2 becomes the only selector schema 1.
- It states that the old linear selector contract/runtime is removed rather than adapted.
- It states that old historical docs remain archived and are not rewritten as if they never existed.
- It states that the next product work remains Wave 04 Task 03.

- [ ] **Step 1: write the decision document**

The design must contain these normative decisions:

```text
DECISION-BASELINE-001 Database:
Current complete Room structure is initial schema version 1.
No migration is provided from development schema versions 1, 2, or 3.

DECISION-BASELINE-002 Selector:
The typed endpoint/binding contract is initial selector schema version 1.
The old linear selector contract is not part of the baseline.

DECISION-BASELINE-003 Versions:
Version spaces remain independent.
App 1/1.0 stays unchanged.
Repository index schema 1 stays unchanged.
Plugin API remains major 1 compatibility-based.
No package-format schema field is invented.

DECISION-BASELINE-004 Source architecture:
No active selector production/test/sample file or type is named by development generation (V1/V2/Legacy/Compat).

DECISION-BASELINE-005 Compatibility:
Pre-baseline development database files, selector JSON, sample packages, and emulator installs may be discarded.
```

- [ ] **Step 2: update document governance**

Add a precedence rule:

```text
Pre-MVP Baseline 1 decisions override active implementation wording that requires
Selector V1/V2 coexistence or Room 1→2→3 migration history. Archived documents remain
historical evidence only.
```

- [ ] **Step 3: validate documentation links**

```powershell
rg -n "pre-mvp-baseline-1" docs
```

Expected: `docs/README.md` and document governance point to the new decision.

- [ ] **Step 4: commit**

```powershell
git add -- `
  docs/superpowers/specs/2026-08-07-pre-mvp-baseline-1-design.md `
  docs/project/pre-mvp-baseline-1.md `
  docs/project/document-governance.md `
  docs/README.md

git diff --cached --check
git commit -m "docs: define pre-mvp baseline one reset"
```

---

### Task 2: Rebase the Current Room Database to Initial Schema 1

**Commit:** `database: rebase current schema as version one`

**Files:**
- Modify: `core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt`
- Delete: `core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabaseMigrations.kt`
- Replace: `core/database/schemas/app.openstory.database.OpenStoryDatabase/1.json`
- Delete: `core/database/schemas/app.openstory.database.OpenStoryDatabase/2.json`
- Delete: `core/database/schemas/app.openstory.database.OpenStoryDatabase/3.json`
- Delete: `core/database/src/androidTest/assets/database/v1/openstory.db`
- Delete: `core/database/src/androidTest/kotlin/app/openstory/database/CatalogMetadataMigrationTest.kt`
- Modify: `core/database/src/androidTest/kotlin/app/openstory/database/MigrationTest.kt`
- Modify: `core/database/src/test/kotlin/app/openstory/database/SchemaPolicyTest.kt`
- Test: `core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomStoryRepositoryTest.kt`
- Test: `core/database/src/androidTest/kotlin/app/openstory/database/Wave02CheckpointTest.kt`

**Interfaces:**
- Consumes: the logical schema currently represented by Room version 3.
- Produces: `OpenStoryDatabase(version = 1)` whose fresh schema is logically equivalent to the current version-3 schema, with no pre-baseline migration chain.

**Acceptance:**
- `@Database(version = 1)`.
- `Room.databaseBuilder` has no `.addMigrations(migration1To2, migration2To3)`.
- `OpenStoryDatabaseMigrations.kt` is absent.
- The schema directory contains exactly `1.json`.
- New `1.json` contains all current entities, including `plugin_versions` and all current catalog metadata columns.
- No `v1/openstory.db` development fixture remains.
- No Android test attempts a 1→2 or 2→3 migration.
- Fresh-database integrity, foreign keys, repository behavior, backup policy, and secret-table exclusions remain tested.

- [ ] **Step 1: rewrite the schema policy test first**

Change `SchemaPolicyTest.schemasAreContiguousThroughCurrentDatabaseVersion()` into a baseline-specific assertion:

```kotlin
@Test
fun databaseBaselineIsExactlySchemaOne() {
    val repositoryRoot = findRepositoryRoot()
    val moduleRoot = repositoryRoot.resolve("core/database")
    val databaseSource = moduleRoot.resolve(
        "src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt",
    ).readText()
    val committed = moduleRoot.resolve(
        "schemas/app.openstory.database.OpenStoryDatabase",
    ).listFiles { file -> file.extension == "json" }
        .orEmpty()
        .map { it.name }
        .sorted()

    assertTrue("version = 1" in databaseSource)
    assertEquals(listOf("1.json"), committed)
    assertFalse(moduleRoot.resolve(
        "src/main/kotlin/app/openstory/database/OpenStoryDatabaseMigrations.kt",
    ).exists())
}
```

Also change the backup/secret-table test to read `1.json`, not `3.json`.

- [ ] **Step 2: run the focused unit test and confirm failure**

```powershell
./gradlew.bat :core:database:testDebugUnitTest `
  --tests app.openstory.database.SchemaPolicyTest.databaseBaselineIsExactlySchemaOne `
  --stacktrace
```

Expected: FAIL because the database is still version 3 and schemas 2/3 still exist.

- [ ] **Step 3: remove migration registration and set database version 1**

Change only these two database-construction facts in `OpenStoryDatabase.kt`:

```kotlin
@Database(
    entities = [
        CanonicalStoryEntity::class,
        CatalogEntryEntity::class,
        StoryCatalogEntryEntity::class,
        LibraryEntryEntity::class,
        ContentMappingEntity::class,
        StoryContentMappingEntity::class,
        CanonicalChapterEntity::class,
        ChapterReleaseEntity::class,
        CanonicalChapterReleaseEntity::class,
        ReadingProgressEntity::class,
        PluginStateEntity::class,
        PluginVersionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
```

and the builder chain must be exactly migration-free:

```kotlin
Room.databaseBuilder(
    context.applicationContext,
    OpenStoryDatabase::class.java,
    databaseName,
)
    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
    .build()
```

Delete `OpenStoryDatabaseMigrations.kt`.

- [ ] **Step 4: regenerate Room schema 1 from source**

Delete old schema JSONs first, then run the module task that triggers Room schema export:

```powershell
Remove-Item core/database/schemas/app.openstory.database.OpenStoryDatabase/*.json
./gradlew.bat :core:database:assembleDebug --stacktrace
```

Expected: Room exports exactly `core/database/schemas/app.openstory.database.OpenStoryDatabase/1.json`.

- [ ] **Step 5: verify logical equivalence to the pre-refactor version-3 schema**

Before deleting the old branch reference, compare the new baseline schema with the old schema using Git:

```powershell
git show HEAD~1:core/database/schemas/app.openstory.database.OpenStoryDatabase/3.json > $env:TEMP/openstory-schema-old-v3.json
```

Compare table names, columns, foreign keys, and indices. Version metadata may differ; product structure must not.

At minimum verify:

```powershell
rg -n 'plugin_versions|external_story_id|source_url|authors_json|genres_json|cover_reference|publication_status' `
  core/database/schemas/app.openstory.database.OpenStoryDatabase/1.json
```

Expected: every current table/column is present.

- [ ] **Step 6: replace migration tests with fresh-baseline integrity tests**

Keep `MigrationTest.kt` only as the future migration harness location, but remove all current version-transition tests and `MigrationTestHelper.createDatabase(version = oldVersion)` usage. Rename the class to `DatabaseBaselineTest` and the file to:

```text
core/database/src/androidTest/kotlin/app/openstory/database/DatabaseBaselineTest.kt
```

Minimum tests:

```kotlin
@Test
fun freshDatabaseContainsCurrentBaselineTables()

@Test
fun freshDatabaseHasNoForeignKeyViolations()

@Test
fun freshDatabaseSupportsCurrentRepositoryRoundTrip()
```

Delete the binary `src/androidTest/assets/database/v1/openstory.db`; baseline tests create a fresh database through Room.

- [ ] **Step 7: run database verification**

```powershell
./gradlew.bat :core:database:testDebugUnitTest --stacktrace
./gradlew.bat :core:database:connectedDebugAndroidTest --stacktrace
bash ./scripts/verify-room-schema-stability.sh
```

Expected: PASS; exactly one committed Room schema exists.

- [ ] **Step 8: commit**

```powershell
git add -A core/database
git diff --cached --check
git commit -m "database: rebase current schema as version one"
```

---

### Task 3: Collapse Selector V1/V2 into One Canonical Selector Schema 1

**Commit:** `plugin-api: rebase typed selector contract as schema one`

**Files:**
- Delete: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorPluginDefinition.kt`
- Rename/replace: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorPluginDefinitionV2.kt` → `SelectorDefinition.kt`
- Modify: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorDefinitionDecoder.kt`
- Replace: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorOperation.kt` → `SelectorRequest.kt` together with current `SelectorRequestPlan.kt`
- Delete after merge: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorRequestPlan.kt`
- Modify: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorBinding.kt`
- Modify: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/SelectorValidation.kt`
- Modify tests under: `core/plugin-api/src/test/kotlin/app/openstory/plugin/api/selector/`

**Interfaces:**
- Consumes: current typed Catalog/Content endpoint and binding models.
- Produces: one serialized `SelectorDefinition` with `CURRENT_SCHEMA_VERSION = 1`, one decoder result type, and a request operation model containing only document-acquisition operations.

**Target signatures:**

```kotlin
@Serializable
data class SelectorDefinition(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val catalog: CatalogSelectorEndpoints? = null,
    val content: ContentSelectorEndpoints? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

class SelectorDefinitionDecoder(
    private val json: Json = SELECTOR_JSON,
) {
    fun decode(source: String): Result<SelectorDefinition>
}

@Serializable
sealed interface SelectorRequestOperation

@Serializable
@SerialName("http_get")
data class HttpGet(val urlTemplate: String) : SelectorRequestOperation

@Serializable
@SerialName("remove_elements")
data class RemoveElements(val css: String) : SelectorRequestOperation

@Serializable
data class SelectorRequestPlan(
    val operations: List<SelectorRequestOperation>,
    val limits: SelectorRequestedLimits? = null,
)
```

The following types must disappear from active source:

```text
SelectorPluginDefinitionV2
DecodedSelectorDefinition
DecodedSelectorDefinition.V1
DecodedSelectorDefinition.V2
SelectorValueType
SelectAll
SelectText
SelectAttribute
NormalizeWhitespace
```

These extraction/normalization behaviors belong to typed bindings, not request operations.

- [ ] **Step 1: rewrite decoder tests to express the new contract**

Replace V1/V2 dispatch tests with:

```kotlin
@Test
fun decodesCanonicalSchemaOne() {
    val decoded = SelectorDefinitionDecoder().decode(
        """{"schemaVersion":1,"catalog":null,"content":{"search":null,"story":null,"latest":null,"allChapters":null,"sync":null,"chapter":null}}""",
    ).getOrThrow()

    assertEquals(1, decoded.schemaVersion)
}

@Test
fun rejectsEveryUnknownSchemaVersion() {
    val result = SelectorDefinitionDecoder().decode("""{"schemaVersion":2}""")
    assertEquals(
        SelectorValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
        result.exceptionOrNull().selectorValidationCode(),
    )
}
```

Use the existing error helper pattern in the test module; do not expose raw parse exceptions.

- [ ] **Step 2: run focused test and verify it fails**

```powershell
./gradlew.bat :core:plugin-api:test `
  --tests app.openstory.plugin.api.selector.SelectorDefinitionDecoderTest `
  --stacktrace
```

Expected: FAIL while decoder still exposes V1/V2 results.

- [ ] **Step 3: replace the root definition and decoder**

Delete the linear root definition. Rename the typed root to `SelectorDefinition`, set schema version to 1, and make decoder return it directly.

Decoder behavior:

```text
parse root object
→ read only schemaVersion
→ require schemaVersion == 1
→ decode SelectorDefinition
→ normalize all non-contract parser failures to INVALID_DEFINITION
```

No compatibility branch exists.

- [ ] **Step 4: simplify request operations**

Move `SelectorRequestPlan`, `SelectorRequestedLimits`, `SelectorRequestOperation`, `HttpGet`, and `RemoveElements` into `SelectorRequest.kt`.

Delete `SelectorValueType`, `inputType`, `outputType`, `SelectAll`, `SelectText`, `SelectAttribute`, and `NormalizeWhitespace`.

- [ ] **Step 5: update selector serialization tests**

Update binding tests and fixture builders so they compile only against the canonical schema. The serialized binding `@SerialName` values remain unchanged; this refactor must not rename binding JSON discriminators without a separate reviewed reason.

- [ ] **Step 6: run plugin API suite**

```powershell
./gradlew.bat :core:plugin-api:test --stacktrace
```

Expected: PASS with no production/test imports of removed selector generation types.

- [ ] **Step 7: prove stale generation symbols are gone from active selector API**

```powershell
$stale = rg -n "SelectorPluginDefinitionV2|DecodedSelectorDefinition|SelectorValueType|SelectAll|SelectText|SelectAttribute|NormalizeWhitespace|\bV1\b|\bV2\b" `
  core/plugin-api/src/main `
  core/plugin-api/src/test
if ($LASTEXITCODE -eq 0) { throw "Stale selector generation symbols remain:`n$stale" }
```

Expected: no matches.

- [ ] **Step 8: commit**

```powershell
git add -A core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector `
  core/plugin-api/src/test/kotlin/app/openstory/plugin/api/selector
git diff --cached --check
git commit -m "plugin-api: rebase typed selector contract as schema one"
```

---

### Task 4: Normalize Selector File Ownership and Validation Packages

**Commit:** `plugin-api: simplify selector contract layout`

**Files:**
- Rename: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/catalog/CatalogSelectorDefinition.kt` → `CatalogSelectorEndpoints.kt`
- Rename: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/content/ContentSelectorDefinition.kt` → `ContentSelectorEndpoints.kt`
- Rename: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/catalog/CatalogSelectorValidation.kt` → `CatalogSelectorValidator.kt`
- Rename: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector/content/ContentSelectorValidation.kt` → `ContentSelectorValidator.kt`
- Move/rename: `SelectorBindingValidation.kt` → `validation/SelectorBindingValidator.kt`
- Move/rename: `SelectorRequestPlanValidation.kt` → `validation/SelectorRequestValidator.kt`
- Move/rename: `SelectorSyntaxValidation.kt` → `validation/SelectorSyntaxValidator.kt`
- Move/rename: `SelectorOutputShape.kt` → `validation/SelectorOutputValidator.kt`
- Replace: `SelectorV2DefinitionValidation.kt` → `validation/SelectorDefinitionValidator.kt`
- Merge/delete: `SelectorValidationFailure.kt` into `SelectorValidation.kt`
- Modify all selector validation tests/imports accordingly.

**Interfaces:**
- Consumes: canonical selector schema from Task 3.
- Produces: transparent source layout where root files are public concepts and implementation validation is isolated under `selector.validation`.

**Acceptance:**
- Root `selector/` production directory contains exactly the canonical public/shared concept files plus domain subdirectories; no version-suffixed validation file remains.
- Validator object names end in `Validator`, not `Validation`, to distinguish behavior objects from public validation facade/error types.
- Catalog/Content endpoint files are named after their actual contents.
- No `validateV1` method remains.
- Request validation requires a document-producing plan using only the canonical request operation set.
- Existing CSS/template/binding/output shape constraints remain semantically unchanged.

- [ ] **Step 1: update package-layout expectation test**

Create:

```text
core/plugin-api/src/test/kotlin/app/openstory/plugin/api/selector/SelectorSourceLayoutTest.kt
```

Test repository source paths:

```kotlin
@Test
fun selectorSourceUsesCanonicalGenerationFreeLayout() {
    val selectorRoot = findRepositoryRoot().resolve(
        "core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector",
    )
    val forbidden = selectorRoot.walkTopDown()
        .filter(File::isFile)
        .map(File::getName)
        .filter { name ->
            Regex("(?i)(v1|v2|legacy|compat)").containsMatchIn(name)
        }
        .toList()

    assertEquals(emptyList(), forbidden)
}
```

Add explicit expected root files:

```text
SelectorDefinition.kt
SelectorRequest.kt
SelectorBinding.kt
SelectorValidation.kt
```

and expected directories:

```text
catalog
content
validation
```

- [ ] **Step 2: run focused layout test and verify failure**

```powershell
./gradlew.bat :core:plugin-api:test `
  --tests app.openstory.plugin.api.selector.SelectorSourceLayoutTest `
  --stacktrace
```

Expected: FAIL because old filenames/layout remain.

- [ ] **Step 3: perform renames and package moves**

Use `git mv` for history preservation. Update imports and object names consistently:

```text
SelectorBindingValidation      -> SelectorBindingValidator
SelectorRequestPlanValidation  -> SelectorRequestValidator
SelectorSyntaxValidation       -> SelectorSyntaxValidator
SelectorV2DefinitionValidation -> SelectorDefinitionValidator
CatalogSelectorValidation      -> CatalogSelectorValidator
ContentSelectorValidation      -> ContentSelectorValidator
```

- [ ] **Step 4: simplify request validation**

`SelectorRequestValidator` no longer performs generic NONE/DOCUMENT/ELEMENTS/TEXT type-state transitions. It validates the canonical document request grammar:

```text
operations must be non-empty
first operation must be HttpGet
remaining operations may be RemoveElements only
exactly one HttpGet is permitted
request always finishes with a document by construction
```

Keep current operation-count, URL-template, CSS, origin, and requested-limit checks.

- [ ] **Step 5: keep `SelectorValidation` as the only public validation entry point**

Target facade:

```kotlin
object SelectorValidation {
    fun validate(
        definition: SelectorDefinition,
        manifest: PluginManifest,
    ): Result<Unit>

    fun validateRequestPlan(
        request: SelectorRequestPlan,
        manifest: PluginManifest,
    ): Result<Unit>

    fun validateBinding(
        binding: SelectorBinding,
    ): Result<Unit>

    fun validateCssForContract(
        css: String,
    ): Result<Unit>
}
```

Keep stable error codes. Do not leak internal validator types into consumers.

- [ ] **Step 6: run plugin API tests and detekt**

```powershell
./gradlew.bat :core:plugin-api:test detekt --stacktrace
```

Expected: PASS.

- [ ] **Step 7: inspect the final active API tree**

```powershell
Get-ChildItem core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector -Recurse -File |
  ForEach-Object { $_.FullName.Replace((Get-Location).Path + '\', '') } |
  Sort-Object
```

Expected: only target canonical files and packages; no historical generation naming.

- [ ] **Step 8: commit**

```powershell
git add -A core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector `
  core/plugin-api/src/test/kotlin/app/openstory/plugin/api/selector
git diff --cached --check
git commit -m "plugin-api: simplify selector contract layout"
```

---

### Task 5: Rebase Package Inspection and Deterministic Fixtures onto Selector Schema 1

**Commit:** `plugin-host: use canonical selector schema one packages`

**Files:**
- Modify: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/ZipPackageArchiveInspector.kt`
- Modify: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/install/ZipPackageArchiveInspectorSelectorValidationTest.kt`
- Rename: `sample-plugins/selector-v2-fixture/` → `sample-plugins/selector-fixture/`
- Modify: `sample-plugins/selector-fixture/manifest.json`
- Modify: `sample-plugins/selector-fixture/selector.json`
- Rename: `core/plugin-api/src/test/resources/plugin-selector-v2/selector-v2.json` → `core/plugin-api/src/test/resources/plugin-selector/selector.json`
- Rename: `core/plugin-api/src/test/kotlin/app/openstory/plugin/api/selector/SelectorV2CompleteFixtureTest.kt` → `SelectorCompleteFixtureTest.kt`
- Rename: `core/plugin-api/src/test/kotlin/app/openstory/plugin/api/selector/SelectorV2ValidationTest.kt` → `SelectorDefinitionValidationTest.kt`
- Update any sample-fixture references in package/contract tests.

**Interfaces:**
- Consumes: single canonical `SelectorDefinition` and `SelectorValidation` API.
- Produces: package inspection that decodes and validates exactly one supported declarative schema before activation.

**Acceptance:**
- `ZipPackageArchiveInspector` has no `when (DecodedSelectorDefinition.V1/V2)` branch.
- Declarative package inspection flow is:

```text
read selector.json
→ SelectorDefinitionDecoder.decode
→ SelectorValidation.validate(definition, manifest)
→ accept/reject
```

- `schemaVersion = 2` is rejected as unsupported.
- The canonical fixture uses `schemaVersion = 1` and still covers all current Catalog and Content endpoint contract shapes.
- Sample names and test names contain no V2 label.

- [ ] **Step 1: write the package inspector schema test**

Add/replace tests:

```kotlin
@Test
fun canonicalSelectorSchemaOneIsAccepted()

@Test
fun selectorSchemaTwoIsRejectedAsUnsupported()

@Test
fun malformedSchemaOneOutputBindingIsRejectedBeforeActivation()
```

- [ ] **Step 2: run focused test and verify failure**

```powershell
./gradlew.bat :core:plugin-host:test `
  --tests app.openstory.plugin.host.install.ZipPackageArchiveInspectorSelectorValidationTest `
  --stacktrace
```

Expected: at least schema-one typed fixture test fails until inspector is collapsed.

- [ ] **Step 3: simplify inspector**

Replace multi-generation branch with one definition value. Keep error redaction and package staging behavior unchanged.

- [ ] **Step 4: rename and update fixtures**

Canonical fixture `selector.json` must change only its root generation metadata: set `schemaVersion` to `1` and retain the current complete Catalog and Content endpoint/binding payload byte-for-byte except for formatting changes caused by the rename. Do not alter endpoint/binding semantics.

- [ ] **Step 5: run contract/package suites**

```powershell
./gradlew.bat :core:plugin-api:test :core:plugin-host:test :test:fixtures:test --stacktrace
```

Expected: PASS.

- [ ] **Step 6: prove no active fixture naming remains**

```powershell
$stale = rg -n "selector-v2|SelectorV2|schemaVersion\"\s*:\s*2" `
  sample-plugins `
  core/plugin-api/src/test `
  core/plugin-host/src/test
if ($LASTEXITCODE -eq 0) { throw "Stale selector fixture generation references remain:`n$stale" }
```

Expected: no matches except tests whose literal purpose is rejecting unsupported schema version 2; such tests should name the condition “unsupported schema”, not “V2 compatibility”.

- [ ] **Step 7: commit**

```powershell
git add -A `
  core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install `
  core/plugin-host/src/test/kotlin/app/openstory/plugin/host/install `
  core/plugin-api/src/test `
  sample-plugins
git diff --cached --check
git commit -m "plugin-host: use canonical selector schema one packages"
```

---

### Task 6: Remove the Old Linear Selector Runtime and Keep a Focused Bounded Document Loader

**Commit:** `plugin-host: replace legacy selector pipeline with document loader`

**Files:**
- Delete: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorInterpreter.kt`
- Delete: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorRuntime.kt`
- Delete: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/TransformRegistry.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorLimits.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorDocumentLoader.kt`
- Modify: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorExecutionContext.kt`
- Read-only dependency: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/HtmlDocumentAdapter.kt` (no planned semantic change in this refactor task)
- Delete/replace: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/SelectorRuntimeTest.kt`
- Create: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/SelectorDocumentLoaderTest.kt`
- Modify: `config/detekt/baseline.xml`

**Interfaces:**
- Consumes: `SelectorRequestPlan`, `SelectorExecutionContext`, `PluginHttpGateway`, `HtmlDocumentAdapter`, current budgets.
- Produces: a bounded host-owned document acquisition primitive for the future typed binding evaluator.

**Target interface:**

```kotlin
class SelectorDocumentLoader(
    private val http: PluginHttpGateway,
    private val parser: HtmlDocumentAdapter,
    private val limits: SelectorLimits,
) {
    suspend fun load(
        request: SelectorRequestPlan,
        input: Map<String, String>,
        context: SelectorExecutionContext,
    ): AppResult<HtmlDocument>
}
```

`SelectorLimits.kt` retains relevant host ceilings:

```text
maxOperations
maxDocumentCharacters
maxDocumentNodes
maxWallClockMillis
requestBudget
```

Remove old extraction-runtime-only ceilings from this class if they are no longer used by document loading:

```text
maxElements
maxTextValues
maxRegexInputCharacters
```

Those limits will belong to the future endpoint evaluation budget when typed binding evaluation is implemented.

**Acceptance:**
- No `SelectorValue`, `SelectorInterpreter`, `SelectorRuntime`, or `TransformRegistry` remains in active host source.
- The host still enforces wall-clock timeout, request budget, HTTP success status, document character ceiling, node ceiling, template input encoding, relative URL resolution through execution context/current gateway behavior, cancellation propagation, and operation-index diagnostics.
- `RemoveElements` remains host/parser owned.
- No extraction-to-text behavior exists in the request loader.
- No detekt suppression remains for deleted old runtime classes.

- [ ] **Step 1: write document loader tests before deleting the runtime**

Minimum focused tests:

```kotlin
@Test fun loadsDocumentWithinDeclaredHost()
@Test fun removesDeclaredElementsBeforeReturningDocument()
@Test fun missingTemplateInputReturnsTypedFailure()
@Test fun documentCharacterLimitIsEnforcedBeforeParse()
@Test fun nodeLimitIsEnforcedAfterParse()
@Test fun cancellationPropagatesWithoutSuccessValue()
@Test fun operationFailureContainsOperationIndex()
```

- [ ] **Step 2: run focused test and verify failure**

```powershell
./gradlew.bat :core:plugin-host:test `
  --tests app.openstory.plugin.host.selector.SelectorDocumentLoaderTest `
  --stacktrace
```

Expected: FAIL because `SelectorDocumentLoader` does not exist.

- [ ] **Step 3: extract limits into `SelectorLimits.kt`**

Move only relevant document-loader limits. Keep validation in the constructor.

- [ ] **Step 4: implement bounded document loading**

Refactor the useful subset of current `SelectorInterpreter.executeHttpGet` and `RemoveElements` behavior into `SelectorDocumentLoader`.

Execution grammar:

```text
withTimeout(maxWallClockMillis)
→ iterate request operations
→ HttpGet renders request URL and obtains bounded response
→ require 2xx
→ require decoded document <= maxDocumentCharacters
→ parse with base URI
→ require nodeCount <= maxDocumentNodes
→ RemoveElements operations mutate/return host document
→ return HtmlDocument
```

Do not keep the old `SelectorValue` state machine.

- [ ] **Step 5: delete old runtime classes and tests**

Delete `SelectorInterpreter.kt`, `SelectorRuntime.kt`, `TransformRegistry.kt`, and `SelectorRuntimeTest.kt` once equivalent document acquisition tests pass.

- [ ] **Step 6: clean detekt baseline**

Remove all baseline entries referencing deleted selector runtime/test symbols. Run:

```powershell
./gradlew.bat detekt --stacktrace
```

Expected: PASS without stale suppressions.

- [ ] **Step 7: run plugin-host suite**

```powershell
./gradlew.bat :core:plugin-host:test --stacktrace
```

Expected: PASS.

- [ ] **Step 8: prove old runtime symbols are absent**

```powershell
$stale = rg -n "SelectorInterpreter|SelectorRuntime|SelectorValue|TransformRegistry|SelectAll|SelectText|SelectAttribute|NormalizeWhitespace" `
  core/plugin-host/src/main `
  core/plugin-host/src/test
if ($LASTEXITCODE -eq 0) { throw "Old selector runtime symbols remain:`n$stale" }
```

Expected: no matches.

- [ ] **Step 9: commit**

```powershell
git add -A core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector `
  core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector `
  config/detekt/baseline.xml
git diff --cached --check
git commit -m "plugin-host: replace legacy selector pipeline with document loader"
```

---

### Task 7: Add Permanent Baseline and Source-Layout Architecture Gates

**Commit:** `build: enforce clean baseline architecture`

**Files:**
- Create: `scripts/verify-baseline-architecture.sh`
- Create: `scripts/tests/verify-baseline-architecture-test.sh`
- Modify: `scripts/verify.sh`
- Modify: `app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt`

**Interfaces:**
- Consumes: final source layout after Tasks 2–6.
- Produces: automated regression gate that prevents development-version duplication from returning accidentally.

**Acceptance:**
- Verification fails if Room database source is not version 1 during this pre-public baseline phase.
- Verification fails if Room schema directory contains anything except `1.json`.
- Verification fails if active selector source/test/sample paths contain generation names (`V1`, `V2`, `Legacy`, `Compat`) in filenames.
- Verification fails if removed selector production symbols reappear.
- Verification excludes `docs/internal/archive/` from historical-name checks.
- `scripts/verify.sh` invokes this gate before Gradle verification.
- Gate itself has a shell contract test using a temporary fixture directory.

- [ ] **Step 1: write the shell contract test**

The test creates a temporary fake repository with:

```text
PASS fixture:
- database source says version = 1
- schema directory contains `1.json` only
- selector/SelectorDefinition.kt

FAIL fixture A:
- SelectorPluginDefinitionV2.kt

FAIL fixture B:
- schema directory also contains `2.json`

FAIL fixture C:
- SelectorInterpreter.kt containing removed symbol
```

Assert the script exits 0 only for PASS.

- [ ] **Step 2: run shell test and verify failure**

```powershell
bash ./scripts/tests/verify-baseline-architecture-test.sh
```

Expected: FAIL because the verifier does not exist.

- [ ] **Step 3: implement `verify-baseline-architecture.sh`**

The script accepts optional root override for testing:

```bash
ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
```

Checks:

```text
1. OpenStoryDatabase.kt contains version = 1.
2. Room schema directory has exactly one JSON file named 1.json.
3. No active selector production/test/sample filename matches (?i)(v1|v2|legacy|compat).
4. No active selector source contains removed symbols:
   SelectorPluginDefinitionV2
   DecodedSelectorDefinition
   SelectorInterpreter
   SelectorRuntime
   SelectorValue
   TransformRegistry
   SelectAll
   SelectText
   SelectAttribute
   NormalizeWhitespace
5. Historical docs archive is not scanned.
```

- [ ] **Step 4: wire into shared verification**

Add before Room fingerprint calculation in `scripts/verify.sh`:

```bash
./scripts/verify-baseline-architecture.sh
```

- [ ] **Step 5: update architecture smoke test**

Assert `verify.sh` contains the new gate and the script has its own contract test.

- [ ] **Step 6: run shell and app unit tests**

```powershell
bash ./scripts/tests/verify-baseline-architecture-test.sh
bash ./scripts/verify-baseline-architecture.sh
./gradlew.bat :app:testDebugUnitTest `
  --tests app.openstory.ArchitectureSmokeTest `
  --stacktrace
```

Expected: PASS.

- [ ] **Step 7: commit**

```powershell
git add -- `
  scripts/verify-baseline-architecture.sh `
  scripts/tests/verify-baseline-architecture-test.sh `
  scripts/verify.sh `
  app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt
git diff --cached --check
git commit -m "build: enforce clean baseline architecture"
```

---

### Task 8: Rebaseline Active Development Documentation and Plugin SDK

**Commit:** `docs: rebaseline development docs on schema one`

**Files:**
- Modify: `docs/PROJECT-HANDBOOK.md`
- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Replace/rewrite: `docs/implementation/wave-04-selector-v2-runtime.md` → `docs/implementation/wave-04-selector-runtime.md`
- Modify: `docs/plugin-sdk/declarative-plugin-schema.md`
- Modify: `docs/plugin-sdk/package-format.md`
- Verify unchanged: `docs/plugin-sdk/api-versioning.md`
- Verify unchanged: `docs/plugin-sdk/repository-index.md`
- Modify: `docs/project/requirement-coverage.md`
- Modify: `docs/project/document-governance.md`
- Create: `docs/internal/checkpoints/pre-mvp-baseline-1.md`
- Keep unchanged: `docs/internal/archive/**`
- Keep historical source-local remediation specs/plans as history, but clearly mark superseded if they are still reachable from active indexes.

**Interfaces:**
- Consumes: refactored source tree and baseline decision.
- Produces: active docs whose terminology exactly matches the code after refactor and a new Wave 04 continuation plan that assumes only Selector Schema 1.

**Acceptance:**
- Active docs never instruct implementers to preserve Selector V1 compatibility.
- Active docs never call the canonical selector contract “V2”.
- Plugin SDK says `schemaVersion = 1` is the initial and only supported declarative selector schema.
- Package installation docs say “decode and validate selector schema” rather than “run V1/V2 validator”.
- Database current state says schema 1 is the initial baseline; old migrations are historical only.
- Current roadmap still says Wave 04 Task 03 is active.
- The new Wave 04 selector-runtime plan starts from `SelectorDocumentLoader` + canonical typed contract and proceeds to shared URL policy, binding evaluator, mappers, DTO validation, plugin adapters/factory, cancellation/redaction, and checkpoint.
- Archived planning/review packages remain unchanged.

- [ ] **Step 1: rename the active Wave 04 continuation plan**

```powershell
git mv `
  docs/implementation/wave-04-selector-v2-runtime.md `
  docs/implementation/wave-04-selector-runtime.md
```

Rewrite its baseline section to:

```text
Implemented baseline:
- Selector Schema 1 typed Catalog/Content contracts.
- Canonical SelectorRequestPlan for bounded document acquisition.
- SelectorDocumentLoader with network/document budgets.
- No legacy selector runtime exists.

Remaining Wave 04 Task 03:
1. shared PluginUrlPolicy;
2. endpoint-wide SelectorEvaluationBudget;
3. typed SelectorBindingEvaluator;
4. CatalogSelectorMapper;
5. ContentSelectorMapper;
6. shared PluginWireDtoValidator;
7. SelectorCatalogPlugin / SelectorContentPlugin / SelectorPluginFactory;
8. cancellation, redaction, and deterministic fixture checkpoint.
```

- [ ] **Step 2: rewrite plugin SDK selector terminology**

The schema documentation must show the repository's canonical complete selector fixture with `schemaVersion = 1` and the current Catalog/Content endpoint groups. Use `sample-plugins/selector-fixture/selector.json` as the literal example source instead of maintaining a second hand-written payload. Delete active migration/compatibility sections describing the old linear schema.

- [ ] **Step 3: correct package installation documentation**

Change installation step 6 from a V1/V2 branch to:

```text
For declarative packages, decode selector.json, require the supported selector schema version,
and run the complete selector contract validator before any runtime is initialized.
```

- [ ] **Step 4: document independent version spaces exactly as source implements them**

The active version table must state:

```text
Application: versionCode 1 / versionName 1.0 (unchanged)
Room database: schema 1 (rebased current complete schema)
Selector: schema 1 (typed endpoint/binding contract)
Plugin API: major/minor compatibility, baseline major 1
Repository index: schema 1
Package layout: no separate schema-version field in current contract
```

- [ ] **Step 5: update current state and requirement coverage**

Current state after the refactor:

```text
Wave 01–03 implementation present.
Wave 04 Task 01–02 implementation present.
Pre-MVP Baseline 1 refactor complete.
Wave 04 Task 03 remains active: typed selector runtime execution is not yet complete.
```

Do not mark Wave 04 complete.

- [ ] **Step 6: scan active docs for stale generation language**

Run:

```powershell
$stale = rg -n "Selector V1|Selector V2|V1/V2|SelectorPluginDefinitionV2|DecodedSelectorDefinition\.V[12]|wave-04-selector-v2-runtime" `
  docs `
  --glob '!docs/internal/archive/**' `
  --glob '!docs/superpowers/specs/2026-08-07-pre-mvp-baseline-1-design.md' `
  --glob '!docs/project/pre-mvp-baseline-1.md'
```

Any remaining match must be an explicitly labeled historical explanation, not an active instruction. Prefer moving such history into archive rather than keeping it in implementation guidance.

- [ ] **Step 7: validate Markdown links**

Run the repository's existing documentation link checker if present. If none exists, use the documentation audit script/process introduced by the unified docs package to verify local Markdown targets.

- [ ] **Step 8: commit**

```powershell
git add -A docs
git diff --cached --check
git commit -m "docs: rebaseline development docs on schema one"
```

---


### Task 9: Remove Generated Repository Metadata and Lock Project Identity

**Commit:** `build: clean repository metadata and project identity`

**Files:**
- Delete: `.idea/AndroidProjectSystem.xml`
- Delete: `.idea/compiler.xml`
- Delete: `.idea/gradle.xml`
- Delete: `.idea/misc.xml`
- Delete: `.idea/runConfigurations.xml`
- Delete: `.idea/vcs.xml`
- Delete: `.idea/.gitignore`
- Modify: `.gitignore`
- Create: `docs/project/project-identity.md`
- Test: `build-logic/src/test/kotlin/app/openstory/build/RepositoryHygieneTest.kt`

**Interfaces:**
- Consumes: Gradle JDK/module policy and existing Hikari/OpenStory naming.
- Produces: repository policy that is IDE-independent and one explicit naming boundary.

**Acceptance:**
- No `.idea` file is tracked or required to import/build the project.
- `/.idea/` is ignored entirely.
- `rootProject.name = "Hikari"`, app display name `Hikari`, and namespace/application ID `app.openstory` remain unchanged.
- Documentation defines `Hikari` as application/repository identity and `OpenStory` as technical namespace/plugin ecosystem identity.
- No build rule depends on a specific IDE distribution name such as `temurin-17`; JDK 17 remains enforced by Gradle.

- [ ] **Step 1: write the failing repository hygiene test**

Create `RepositoryHygieneTest.kt`:

```kotlin
package app.openstory.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryHygieneTest {
    private val root = File("..")

    @Test fun ideStateIsNotPartOfRepositoryBaseline() {
        assertFalse(File(root, ".idea").exists())
        assertTrue(File(root, ".gitignore").readText().lineSequence().any { it.trim() == "/.idea/" })
    }

    @Test fun hikariAndOpenStoryRolesRemainIntentional() {
        assertTrue(File(root, "settings.gradle.kts").readText().contains("rootProject.name = \"Hikari\""))
        assertTrue(File(root, "app/build.gradle.kts").readText().contains("applicationId = \"app.openstory\""))
    }
}
```

- [ ] **Step 2: run and verify the intended failure**

```powershell
./gradlew.bat :build-logic:test --tests app.openstory.build.RepositoryHygieneTest --stacktrace
```

Expected: FAIL because `.idea/` is still present and ignored only partially.

- [ ] **Step 3: remove IDE state and update ignore policy**

Delete `.idea/` and replace the individual IDE ignore rules with:

```gitignore
/.idea/
*.iml
```

Keep Gradle/local build ignores unchanged.

- [ ] **Step 4: document identity ownership**

`docs/project/project-identity.md` must state:

```text
Hikari
- Android application display name
- repository/root Gradle project name

OpenStory
- Kotlin/package namespace family: app.openstory
- plugin API/package/repository ecosystem terminology
- host protocol/user-agent family

These names are intentionally different roles, not aliases that should be mechanically replaced.
```

- [ ] **Step 5: run focused and build-logic suites**

```powershell
./gradlew.bat :build-logic:test --stacktrace
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: commit**

```powershell
git add -A .idea .gitignore docs/project/project-identity.md build-logic/src/test/kotlin/app/openstory/build/RepositoryHygieneTest.kt
git diff --cached --check
git commit -m "build: clean repository metadata and project identity"
```

---

### Task 10: Narrow Plugin Registry Ownership and Remove Unused Module Edges

**Commit:** `architecture: narrow plugin registry and dependencies`

**Files:**
- Modify: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/registry/PluginRegistry.kt`
- Modify: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/PluginInstaller.kt`
- Rename: `core/database/src/main/kotlin/app/openstory/database/repository/PluginStateRepository.kt` -> `RoomPluginRegistry.kt`
- Modify: `core/database/src/test/kotlin/app/openstory/database/repository/PluginStateRepositoryTest.kt`
- Modify: `core/database/build.gradle.kts`
- Modify: `core/network/build.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Test: `build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt`

**Interfaces:**
- Consumes: installed/staged package data and Room plugin tables.
- Produces: a neutral registry port so Room knows registry persistence data, not installer implementation classes.

**Target registry contract:**

```kotlin
data class PluginActivation(
    val pluginId: String,
    val version: String,
    val packageSha256: String,
    val location: String,
    val signatureState: String,
    val signerKeyId: String?,
    val signerFingerprintSha256: String?,
    val installSource: String,
    val sourceReference: String?,
    val unsignedWarningAcknowledged: Boolean,
    val acceptedCapabilities: Set<String>,
)

data class ActivatedPlugin(
    val pluginId: String,
    val version: String,
    val location: String,
    val enabled: Boolean,
)

interface MutablePluginRegistry : PluginRegistry {
    suspend fun activate(activation: PluginActivation): AppResult<ActivatedPlugin>
    suspend fun setEnabled(pluginId: String, enabled: Boolean): AppResult<Unit>
}
```

`StagedPluginPackage` maps to `PluginActivation` **inside plugin-host/install**, never inside database.

**Acceptance:**
- `core/database/src/main` has no import beginning `app.openstory.plugin.host.install.`.
- `RoomPluginRegistry` implements only the `registry` port.
- Installer behavior and atomic activation semantics remain unchanged.
- `core/network` no longer declares `implementation(project(":core:plugin-api"))` because production does not use it.
- module-boundary policy reflects actual dependencies and explicitly forbids database imports from host installer internals.

- [ ] **Step 1: write the failing boundary test**

Add an assertion to `ModuleBoundaryVerifierTest` that a database source importing:

```kotlin
import app.openstory.plugin.host.install.StagedPluginPackage
```

is rejected when the policy forbids `app.openstory.plugin.host.install.`.

- [ ] **Step 2: run focused failure**

```powershell
./gradlew.bat :build-logic:test --tests app.openstory.build.architecture.ModuleBoundaryVerifierTest --stacktrace
```

Expected: FAIL until policy and adapter are narrowed.

- [ ] **Step 3: introduce neutral registry records and adapt installer**

Map staged package data in plugin-host:

```kotlin
private fun StagedPluginPackage.toActivation(): PluginActivation =
    PluginActivation(
        pluginId = pluginId,
        version = version,
        packageSha256 = packageSha256,
        location = location,
        signatureState = signatureDecision.signatureState.name,
        signerKeyId = signatureDecision.signerKeyId,
        signerFingerprintSha256 = signatureDecision.signerFingerprintSha256,
        installSource = provenance.source.name,
        sourceReference = provenance.sourceReference,
        unsignedWarningAcknowledged = provenance.unsignedWarningAcknowledged,
        acceptedCapabilities = acceptedCapabilities.map { it.name }.toSet(),
    )
```

The database maps `PluginActivation` to `PluginVersionEntity`.

- [ ] **Step 4: rename the Room adapter**

Rename class/file/test references from `PluginStateRepository` to `RoomPluginRegistry`. DAO/entity names remain persistence-oriented and need not change.

- [ ] **Step 5: remove unused network dependency**

Delete from `core/network/build.gradle.kts`:

```kotlin
implementation(project(":core:plugin-api"))
```

Remove `:core:plugin-api` from `:core:network.productionDependencies` in `module-boundaries.json`.

Add database forbidden import:

```json
"forbiddenProductionImports": [
  "app.openstory.plugin.host.install."
]
```

- [ ] **Step 6: run affected suites**

```powershell
./gradlew.bat :build-logic:test :core:database:testDebugUnitTest :core:network:test :core:plugin-host:test --stacktrace
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: verify dependency/import shape**

```powershell
$bad = rg -n "^import app\.openstory\.plugin\.host\.install\." core/database/src/main
if ($LASTEXITCODE -eq 0) { throw "Database still imports installer internals:`n$bad" }
rg -n 'core:plugin-api' core/network/build.gradle.kts config/architecture/module-boundaries.json
```

Expected: no network dependency match and no database installer import.

- [ ] **Step 8: commit**

```powershell
git add core/plugin-host core/database core/network/build.gradle.kts config/architecture/module-boundaries.json build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt
git diff --cached --check
git commit -m "architecture: narrow plugin registry and dependencies"
```

---

### Task 11: Decompose the Network Gateway and Introduce the Shared URL Policy

**Commit:** `network: separate url policy and bounded body reading`

**Files:**
- Create: `core/network/src/main/kotlin/app/openstory/network/PluginUrlPolicy.kt`
- Create: `core/network/src/main/kotlin/app/openstory/network/BoundedResponseReader.kt`
- Modify: `core/network/src/main/kotlin/app/openstory/network/AllowlistedHttpGateway.kt`
- Modify: `core/network/src/main/kotlin/app/openstory/network/RequestBudget.kt`
- Create: `core/network/src/test/kotlin/app/openstory/network/PluginUrlPolicyTest.kt`
- Create: `core/network/src/test/kotlin/app/openstory/network/BoundedResponseReaderTest.kt`
- Modify: `core/network/src/test/kotlin/app/openstory/network/AllowlistedHttpGatewayTest.kt`

**Interfaces:**
- Consumes: allowed host set, optional base URI, HTTP responses and existing request budgets.
- Produces: validation-only URL policy reusable by gateway and the next selector output validators, plus isolated bounded-body decoding.

**Target API:**

```kotlin
data class ValidatedPluginUrl(
    val value: String,
    val host: String,
)

class PluginUrlPolicy(
    private val allowedHosts: Set<String>,
    private val baseUrl: String? = null,
) {
    fun resolve(candidate: String): AppResult<ValidatedPluginUrl>
}

internal class BoundedResponseReader {
    fun read(
        response: okhttp3.Response,
        budget: RequestBudget,
    ): AppResult<ByteArray>
}
```

`PluginUrlPolicy.resolve()` performs **no network request**.

**Acceptance:**
- initial URLs and redirect destinations use the same `PluginUrlPolicy`.
- output URL validation can reuse the policy without invoking HTTP.
- HTTPS/host/user-info/relative-resolution behavior is covered independently.
- compressed and decompressed limits remain exactly enforced.
- `AllowlistedHttpGateway.kt` becomes orchestration-focused and below the 500-line production threshold.
- existing gateway tests continue to prove redirects, cookies, headers, cancellation, rate budgets and redaction.

- [ ] **Step 1: write failing URL policy tests**

Cover:

```text
relative URL + valid base -> normalized allowed HTTPS URL
absolute HTTPS allowed host -> success
http:// -> plugin.https_required
undeclared host -> plugin.domain_denied
user-info URI -> invalid URL
validation causes zero HTTP calls
```

- [ ] **Step 2: write failing bounded reader tests**

Cover compressed and decompressed ceilings with deterministic byte streams.

- [ ] **Step 3: run focused failures**

```powershell
./gradlew.bat :core:network:test --tests app.openstory.network.PluginUrlPolicyTest --stacktrace
./gradlew.bat :core:network:test --tests app.openstory.network.BoundedResponseReaderTest --stacktrace
```

Expected: FAIL because the extracted components do not exist.

- [ ] **Step 4: extract URL policy and body reader without changing error codes**

Move scheme/host/relative-resolution logic and bounded body reading from the gateway. Do not add plugin-api dependencies.

- [ ] **Step 5: migrate gateway initial request and redirect checks to the policy**

There must be one scheme/host implementation, not duplicated checks in gateway and output validators later.

- [ ] **Step 6: run the complete network suite**

```powershell
./gradlew.bat :core:network:test --stacktrace
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: verify layout**

```powershell
(Get-Content core/network/src/main/kotlin/app/openstory/network/AllowlistedHttpGateway.kt).Count
```

Expected: fewer than 500 lines with no security behavior removed.

- [ ] **Step 8: commit**

```powershell
git add core/network
git diff --cached --check
git commit -m "network: separate url policy and bounded body reading"
```

---

### Task 12: Separate Installer Orchestration from Version Policy

**Commit:** `plugin-host: separate installer version policy`

**Files:**
- Modify: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/PluginInstaller.kt`
- Create: `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/PluginVersionPolicy.kt`
- Modify: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/install/PluginInstallerTest.kt`
- Create: `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/install/PluginVersionPolicyTest.kt`

**Interfaces:**
- Consumes: existing semantic version strings and current/previous registry state.
- Produces: identical version/downgrade/rollback decisions with installer orchestration readable independently.

**Acceptance:**
- `PluginInstaller.kt` owns installation orchestration and install/staged/installed request records.
- `PluginVersionPolicy.kt` owns semantic-version parsing/comparison and downgrade policy.
- no version behavior changes.
- version-specific tests move out of the 700+ line installer test.
- do not split `PackageVerifier` or transactional storage without new responsibility evidence.

- [ ] **Step 1: extract existing version-policy tests into a focused test class while keeping production unchanged**

Run the extracted tests to prove they still pass before moving code.

- [ ] **Step 2: move `PluginVersionPolicy` and private semantic-version helpers verbatim**

No algorithm rewrite is allowed in this task.

- [ ] **Step 3: run focused and module suites**

```powershell
./gradlew.bat :core:plugin-host:test --tests app.openstory.plugin.host.install.PluginVersionPolicyTest --stacktrace
./gradlew.bat :core:plugin-host:test --stacktrace
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: verify file ownership**

```powershell
rg -n "class PluginVersionPolicy|comparePreRelease" core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install
```

Expected: version-policy implementation exists only in `PluginVersionPolicy.kt`.

- [ ] **Step 5: commit**

```powershell
git add core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install core/plugin-host/src/test/kotlin/app/openstory/plugin/host/install
git diff --cached --check
git commit -m "plugin-host: separate installer version policy"
```

---

### Task 13: Rebase Active Tests and Verification Scripts onto Capability Names and Zero-Debt Quality Gates

**Commit:** `build: normalize verification and source hygiene`

**Files:**
- Rename: `core/database/src/androidTest/kotlin/app/openstory/database/Wave02CheckpointTest.kt` -> `DatabaseBaselineAcceptanceTest.kt`
- Replace/split: `core/database/src/androidTest/kotlin/app/openstory/database/Wave02CheckpointSupport.kt`
  - `DatabaseAcceptanceFixture.kt`
  - `DatabaseAcceptanceAssertions.kt`
- Move/rename: `scripts/verify-wave-checkpoint.sh` -> `scripts/checkpoints/app-shell.sh`
- Move/rename: `scripts/verify-wave-02-checkpoint.sh` -> `scripts/checkpoints/database.sh`
- Move/rename: `scripts/verify-wave-03-checkpoint.sh` -> `scripts/checkpoints/plugin-contracts.sh`
- Move/rename: `scripts/verify-instrumentation.sh` -> `scripts/instrumentation/android.sh`
- Move/rename: `scripts/verify-database-instrumentation.sh` -> `scripts/instrumentation/database.sh`
- Rename/update corresponding shell contract tests under `scripts/tests/`
- Modify: `scripts/verify.sh`
- Create: `scripts/verify-source-layout.sh`
- Create: `scripts/tests/verify-source-layout-test.sh`
- Delete: `config/detekt/baseline.xml`
- Modify: `build.gradle.kts`
- Modify: `app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt`

**Interfaces:**
- Consumes: existing checkpoint behavior and Detekt rules.
- Produces: durable verification named by capability plus a permanent source-layout guard.

**Acceptance:**
- no active Kotlin test/support filename contains `Wave02`, `Checkpoint`, or `Remediation` as development history.
- root `scripts/` contains the general entry points; checkpoint/instrumentation scripts live in focused subdirectories.
- no Detekt baseline exists after the old selector runtime is gone.
- `detekt` passes cleanly without `baseline.set(...)`.
- source-layout check rejects committed `.idea`, forbidden database installer imports, development-history test names, oversized unreviewed files and Detekt baseline debt; the earlier Baseline 1 gate remains responsible for exact schema/generation assertions.

**Source-layout thresholds:**

```text
production Kotlin source: max 500 lines
unit/android test or support Kotlin source: max 750 lines
```

The script ignores generated build output and `docs/internal/archive`. Any future exception requires an explicit allowlist entry with a reason in the script; no silent threshold increase.

- [ ] **Step 1: write the failing shell contract test for source layout**

The test creates temporary violations for:

```text
SelectorThingV2.kt
.idea/misc.xml
a database import from plugin.host.install
a 501-line production Kotlin file
a 751-line test Kotlin file
```

and asserts the verifier fails each case.

- [ ] **Step 2: run the shell test and verify failure before the verifier exists**

```powershell
bash ./scripts/tests/verify-source-layout-test.sh
```

- [ ] **Step 3: rename database acceptance tests by behavior and split support responsibilities**

Preserve every existing invariant; this is a structural rename/split only.

- [ ] **Step 4: move reusable verification scripts into capability directories**

Target:

```text
scripts/
├── verify.sh
├── verify-source-layout.sh
├── check-module-dependencies.sh
├── verify-room-schema-stability.sh
├── checkpoints/
│   ├── app-shell.sh
│   ├── database.sh
│   └── plugin-contracts.sh
├── instrumentation/
│   ├── android.sh
│   └── database.sh
└── tests/
```

Update internal calls; do not leave compatibility wrapper scripts with old wave names.

- [ ] **Step 5: remove Detekt baseline debt**

Delete `config/detekt/baseline.xml` and remove:

```kotlin
baseline.set(rootProject.file("config/detekt/baseline.xml"))
```

from root Detekt configuration.

- [ ] **Step 6: implement source-layout verifier**

It must check at least:

```text
no active V1/V2/Legacy/Compat architecture names
no .idea directory
no database -> plugin.host.install imports
production/test line thresholds
no Detekt baseline file
```

- [ ] **Step 7: integrate into `scripts/verify.sh` and ArchitectureSmokeTest**

`verify.sh` invokes source-layout verification before expensive Gradle work.

- [ ] **Step 8: run all shell contracts and Detekt**

```powershell
Get-ChildItem scripts/tests/*.sh | ForEach-Object { bash $_.FullName }
./gradlew.bat detekt --stacktrace
```

Expected: all PASS.

- [ ] **Step 9: run database/android test suites affected by renames**

```powershell
./gradlew.bat :core:database:testDebugUnitTest :app:testDebugUnitTest --stacktrace
```

Run instrumentation when devices are available; record rather than fabricate evidence.

- [ ] **Step 10: commit**

```powershell
git add -A core/database/src/androidTest scripts config/detekt build.gradle.kts app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt
git diff --cached --check
git commit -m "build: normalize verification and source hygiene"
```

---

### Task 14: Reconcile Active Architecture Documentation After the Project-Wide Cleanup

**Commit:** `docs: align baseline architecture and verification`

**Files:**
- Modify: `docs/PROJECT-HANDBOOK.md`
- Modify: `docs/project/current-state.md`
- Modify: `docs/project/document-governance.md`
- Modify: `docs/project/project-identity.md`
- Modify: `docs/implementation/current-roadmap.md`
- Modify: `docs/implementation/wave-04-selector-v2-runtime.md` (rename to canonical schema-1 filename if not already handled by Task 8)
- Modify: `docs/contributing/adding-a-module.md`
- Modify: `docs/plugin-sdk/declarative-plugin-schema.md`
- Move superseded remediation specs/plans/checkpoint narratives into `docs/internal/archive/pre-baseline-development/` where they are no longer active execution sources.

**Acceptance:**
- active docs describe one Selector Schema 1, one Room Schema 1, and the cleaned source layout.
- active docs use current verification paths under `scripts/checkpoints/` and `scripts/instrumentation/`.
- project identity policy explains Hikari/OpenStory roles.
- module documentation explains the intentional database implementation of the registry port while forbidding installer-internal imports.
- fixture ownership is explicit: plugin-api testFixtures = reusable contract kit; `:test:fixtures` = internal deterministic fake implementations/data.
- remediation documents that no longer drive execution are archived, not deleted or rewritten.
- next work remains Wave 04 Task 03 typed binding/runtime execution.

- [ ] **Step 1: update active source-layout diagrams and module dependency narrative**

Document the registry port and cleaned network components.

- [ ] **Step 2: update verification commands to new capability paths**

No active guide may instruct contributors to invoke removed `verify-wave-*` scripts.

- [ ] **Step 3: archive superseded remediation execution documents**

Preserve byte history where possible and mark them historical in the archive index.

- [ ] **Step 4: run documentation stale-reference scan**

```powershell
$stale = rg -n "verify-wave-|Wave02Checkpoint|SelectorPluginDefinitionV2|Selector V2 runtime" docs `
  --glob '!internal/archive/**'
if ($LASTEXITCODE -eq 0) { throw "Stale active documentation remains:`n$stale" }
```

Use the canonical phrasing `Selector Schema 1 runtime` in active docs.

- [ ] **Step 5: validate local Markdown links and docs manifest**

Run the repository's documentation link/hash check introduced by the unified docs package. If the check is not yet scripted, add it to the existing documentation packaging verification rather than creating another competing entry point.

- [ ] **Step 6: commit**

```powershell
git add docs
git diff --cached --check
git commit -m "docs: align baseline architecture and verification"
```

---

### Task 15: Run the Baseline 1 Checkpoint and Freeze the New Starting Point

**Commit:** `chore: record pre-mvp baseline one checkpoint`

**Files:**
- Modify: `docs/internal/checkpoints/pre-mvp-baseline-1.md`
- No production code should be changed in this task unless a checkpoint uncovers a refactor regression; any real regression must be fixed in a separate focused commit before the checkpoint commit.

**Interfaces:**
- Consumes: all Tasks 1–8.
- Produces: auditable proof that Baseline 1 is structurally clean and behaviorally ready for Wave 04 Task 03 continuation.

**Checkpoint requirements:**

```text
A. Source architecture
- one selector schema root
- no active V1/V2/Legacy/Compat generation naming
- no old linear runtime symbols
- target selector file tree matches plan

B. Versions
- Room schema exactly 1
- selector schema exactly 1
- repository index remains 1
- app version remains 1 / 1.0
- plugin API compatibility remains major 1 model
- no invented package schema field

C. Database
- fresh schema contains all current tables/columns
- repositories pass
- foreign keys pass
- backup/secret policy passes

D. Plugin contracts/packages
- full Catalog and Content typed selector fixture validates at schema 1
- unsupported schema versions fail deterministically
- package inspector rejects malformed selector before activation

E. Host boundary
- bounded document loader passes network/document/cancellation tests
- no old extraction pipeline remains

F. Docs
- handbook/current state/roadmap/plugin SDK match source
- archived history retained but excluded from active precedence

G. Build
- architecture gate passes
- shell contract tests pass
- Gradle verification passes under JDK 17
- relevant Android instrumentation passes on required API levels before checkpoint approval
```

- [ ] **Step 1: run textual/architecture scans**

```powershell
bash ./scripts/verify-baseline-architecture.sh

rg -n "version = 1" core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt
Get-ChildItem core/database/schemas/app.openstory.database.OpenStoryDatabase
```

Expected: only `1.json`.

- [ ] **Step 2: run focused module suites**

```powershell
./gradlew.bat --no-daemon --dependency-verification strict `
  :core:database:testDebugUnitTest `
  :core:plugin-api:test `
  :core:network:test `
  :core:plugin-host:test `
  :test:fixtures:test `
  --stacktrace
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: run shared verification**

```powershell
bash ./scripts/verify.sh
```

Expected: PASS.

- [ ] **Step 4: run database instrumentation on API 26 and API 37**

```powershell
if (-not $env:ANDROID_SERIAL_API_26) {
    throw "ANDROID_SERIAL_API_26 must name the configured API 26 emulator/device."
}
if (-not $env:ANDROID_SERIAL_API_37) {
    throw "ANDROID_SERIAL_API_37 must name the configured API 37 emulator/device."
}
bash ./scripts/checkpoints/database.sh
```

The serials are environment configuration only and are never committed.

Expected: database instrumentation passes on both API levels.

- [ ] **Step 5: run application checkpoint smoke on API 26 and API 37**

```powershell
bash ./scripts/checkpoints/app-shell.sh
```

Expected: instrumentation and launcher smoke pass on both API levels.

- [ ] **Step 6: record checkpoint evidence**

`docs/internal/checkpoints/pre-mvp-baseline-1.md` records:

```text
Git commit range
JDK version
Gradle/AGP/Kotlin versions
Room schema file list
selector schema value
module test commands and results
API 26/API 37 device IDs or CI job identities
architecture scan result
known remaining work: Wave 04 Task 03 typed runtime
```

Do not write `PASS` for a command that was not actually executed.

- [ ] **Step 7: final stale-name scan**

```powershell
$codeMatches = rg -n "SelectorPluginDefinitionV2|DecodedSelectorDefinition|SelectorInterpreter|SelectorRuntime|SelectorValue|TransformRegistry|\bSelector V1\b|\bSelector V2\b" `
  core app sample-plugins `
  --glob '!**/build/**'
if ($LASTEXITCODE -eq 0) { throw "Stale active architecture remains:`n$codeMatches" }
```

Expected: no matches.

- [ ] **Step 8: commit checkpoint evidence**

```powershell
git add docs/internal/checkpoints/pre-mvp-baseline-1.md docs/project/current-state.md
git diff --cached --check
git commit -m "chore: record pre-mvp baseline one checkpoint"
```

- [ ] **Step 9: verify clean worktree**

```powershell
git status --short
```

Expected: no output.

---

## Expected Commit Sequence

```text
1.  docs: define pre-mvp baseline one reset
2.  database: rebase current schema as version one
3.  plugin-api: rebase typed selector contract as schema one
4.  plugin-api: simplify selector contract layout
5.  plugin-host: use canonical selector schema one packages
6.  plugin-host: replace legacy selector pipeline with document loader
7.  build: enforce clean baseline architecture
8.  docs: rebaseline development docs on schema one
9.  build: clean repository metadata and project identity
10. architecture: narrow plugin registry and dependencies
11. network: separate url policy and bounded body reading
12. plugin-host: separate installer version policy
13. build: normalize verification and source hygiene
14. docs: align baseline architecture and verification
15. chore: record pre-mvp baseline one checkpoint
```

Do not squash these during implementation review. The sequence creates explicit recovery points for schema reset, contract rebase, host cleanup, dependency cleanup, network decomposition, repository hygiene, and final documentation reconciliation.

---

## Final Target State

After this plan completes, the repository should read like a project that was designed correctly from the start rather than one carrying internal development generations, IDE state, stale dependency edges, or wave-specific verification mechanics:

```text
OpenStory
├── Database schema 1
│   └── current complete durable model
├── Plugin API major 1
├── Repository index schema 1
├── Selector schema 1
│   ├── typed Catalog endpoints
│   ├── typed Content endpoints
│   ├── closed bindings
│   └── bounded request plan
└── Plugin host
    └── bounded document loader
        ↓
        next Wave 04 Task 03 work
        binding evaluator
        DTO mappers
        output validators
        plugin adapters/factory
```

There must be no active architectural story that says:

```text
old V1 → compatibility → V2
```

The active story becomes simply:

```text
Selector Schema 1
→ request document
→ evaluate typed bindings
→ map wire DTO
→ validate output
```

The second half is intentionally the next Wave 04 Task 03 implementation, not hidden inside this refactor.

---

## Explicit Non-Goals

Do not use this refactor to:

- add Catalog Home UI;
- implement story matching or chapter aggregation;
- add JavaScript execution;
- add WebView authentication;
- introduce a new plugin package schema field;
- alter Plugin API major/minor compatibility rules;
- change repository index schema semantics;
- change canonical domain IDs/models;
- change network allowlist/security **semantics**; Task 11 may extract URL/body responsibilities only when existing error codes, allowlist, redirect, budget, cancellation, cookie and redaction behavior remain unchanged;
- add backwards compatibility for pre-baseline development artifacts;
- delete historical documentation archives.

---

## Risk Controls

### Database reset risk

Because pre-baseline local databases are intentionally unsupported, the risk is not migration correctness; it is accidentally losing a table/column during rebasing. The control is logical comparison of new `1.json` to old `3.json` plus repository/instrumentation tests.

### Selector contract risk

The risk is accidentally changing serialized binding semantics while renaming V2 to schema 1. The control is to retain all existing binding `@SerialName` values and full Catalog/Content complete-fixture coverage, changing only root generation/version semantics and request-operation cleanup.

### Security regression risk

The risk is losing limits/cancellation/redaction while deleting the old interpreter. The control is a dedicated `SelectorDocumentLoaderTest` carrying forward every relevant network/document boundary before old runtime files are removed.

### Documentation drift risk

The risk is recreating the previous state where source and plans disagree. The control is a permanent baseline architecture script plus a final docs stale-generation scan and checkpoint evidence.

### Over-refactor risk

The risk is reorganizing unrelated modules under the banner of cleanliness. The control is strict scope: the set of Gradle modules and product/domain design remain unchanged. Refactoring is limited to database/selector baseline history, narrow registry-port coupling, an unused dependency edge, network responsibility extraction required by the reviewed selector architecture, installer version-policy ownership, repository metadata, verification/test naming, quality gates, fixtures, and active docs. Cohesive security/model files are explicitly left alone.

---

## Handoff After Checkpoint

Once this plan is complete and reviewed, resume Wave 04 Task 03 from the new baseline. The next implementation plan must **not** recreate any compatibility layer. Its starting components are:

```text
SelectorDefinition (schema 1)
SelectorRequestPlan
SelectorBinding
SelectorDocumentLoader
HtmlDocumentAdapter
PluginHttpGateway
PluginUrlPolicy
```

The shared validation-only `PluginUrlPolicy` is now already present from Task 11. The next missing architecture items are endpoint-wide selector evaluation budgets, typed binding evaluation, Catalog/Content DTO mapping, shared wire validation, plugin adapters/factory, and the Wave 04 security/cancellation checkpoint.
