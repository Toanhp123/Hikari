# Wave 02 Domain and Local Storage Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close Wave 02 against the approved domain/storage plan by completing durable catalog metadata, hardening repository semantics, enforcing lifecycle integrity, and proving Room behavior on API 26 and API 37.

**Architecture:** Keep `:core:model` platform-neutral and extend only the metadata already assigned to Wave 02. Persist the additive fields through Room schema version 3 and an explicit 2→3 migration. Repository writes remain transactional, cancellation-safe, and validation failures stay typed. The Wave 02 checkpoint runs the database instrumentation suite independently on API 26 and API 37.

**Tech Stack:** Kotlin 2.4.10, JDK 17, Room 2.8.4, SQLite, coroutines/Flow, Kotlin test/JUnit, Android instrumentation.

## Global Constraints

- Package namespace: `app.openstory`.
- Minimum SDK: 26. Compile and target SDK: 37.
- Build runtime: JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0.
- `:core:model` must not depend on Android or Room.
- Existing Room schema versions 1 and 2 remain immutable.
- Every schema change requires a migration and migration test.
- Cancellation must propagate; it must not be converted into a storage failure.
- Progress writes are monotonic by timestamp; equal or older writes cannot overwrite existing state.
- Plugin disable/removal must not cascade-delete canonical story, chapter, release metadata, or progress.
- Explicit download persistence is outside Wave 02 and is recorded as not applicable until Wave 09.
- No test calls live websites.

---

### Task 1: Complete durable catalog metadata in the pure domain

**Files:**
- Modify: `core/model/src/main/kotlin/app/openstory/model/CatalogEntry.kt`
- Modify: `core/model/src/test/kotlin/app/openstory/model/CanonicalStoryTest.kt`
- Test: `core/model/src/test/kotlin/app/openstory/model/CatalogEntryTest.kt`

**Interfaces:**
- Consumes: `CatalogEntryId`, `PluginId`, existing story models.
- Produces: `CatalogEntry` fields `externalStoryId`, `sourceUrl`, `authors`, `genres`, `coverReference`, and `publicationStatus` with deterministic validation.

- [ ] Write failing tests proving blank external IDs are rejected, collection metadata is retained, and score invariants remain unchanged.
- [ ] Run `./gradlew :core:model:test --tests app.openstory.model.CatalogEntryTest --no-configuration-cache` and confirm failure because the fields do not exist.
- [ ] Add the fields and minimal validation without adding Wave 05 ingestion/ranking concerns.
- [ ] Update existing constructors and run `./gradlew :core:model:test --no-configuration-cache`.
- [ ] Commit `model: complete durable catalog metadata`.

### Task 2: Persist catalog metadata through Room schema version 3

**Files:**
- Modify: `core/database/src/main/kotlin/app/openstory/database/entity/StoryEntities.kt`
- Modify: `core/database/src/main/kotlin/app/openstory/database/mapping/StoryEntityMapper.kt`
- Modify: `core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt`
- Modify: `core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabaseMigrations.kt`
- Modify: `core/database/src/androidTest/kotlin/app/openstory/database/MigrationTest.kt`
- Create: `core/database/src/androidTest/kotlin/app/openstory/database/CatalogMetadataMigrationTest.kt`
- Create: `core/database/src/androidTest/kotlin/app/openstory/database/repository/CatalogMetadataRepositoryTest.kt`
- Create: `core/database/schemas/app.openstory.database.OpenStoryDatabase/3.json`

**Interfaces:**
- Consumes: completed `CatalogEntry` domain model.
- Produces: nullable/default-safe columns added by `migration2To3`, schema version 3, lossless mapper round-trip.

- [ ] Add a failing repository round-trip test for all new catalog metadata and a failing 2→3 migration test preserving existing rows.
- [ ] Run focused Android tests and confirm schema/model mismatch failure.
- [ ] Add columns `external_story_id`, `source_url`, `authors_json`, `genres_json`, `cover_reference`, and `publication_status`; migrate old rows with deterministic defaults.
- [ ] Increment Room to version 3, register `migration2To3`, and add schema 3 JSON.
- [ ] Run focused migration/repository tests and the full `:core:database` instrumentation suite.
- [ ] Commit `database: persist complete catalog metadata`.

### Task 3: Harden repository cancellation, validation, and progress monotonicity

**Files:**
- Modify: `core/database/src/main/kotlin/app/openstory/database/dao/ProgressDao.kt`
- Modify: `core/database/src/main/kotlin/app/openstory/database/repository/RoomStoryRepository.kt`
- Create: `core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomStoryRepositorySafetyTest.kt`
- Create: `core/database/src/test/kotlin/app/openstory/database/repository/RoomStoryRepositoryValidationTest.kt`

**Interfaces:**
- Consumes: `AppResult`, `AppError.Validation`, coroutine cancellation.
- Produces: cancellation propagation, `storage.release_mapping_mismatch`, and strict newer-timestamp-only progress updates.

- [ ] Write failing tests for equal timestamp overwrite, release/mapping mismatch, and cancellation propagation.
- [ ] Run focused tests and confirm failures under the existing repository implementation.
- [ ] Move mapping validation before the transaction, rethrow `CancellationException`, and change the progress predicate from `<=` to `<`.
- [ ] Run focused tests, database instrumentation, and `:core:database:testDebugUnitTest`.
- [ ] Commit `database: harden repository write semantics`.

### Task 4: Enforce canonical lifecycle integrity and schema policy

**Files:**
- Create: `core/database/src/main/kotlin/app/openstory/database/dao/StoryPurgeDao.kt`
- Modify: `core/database/src/main/kotlin/app/openstory/database/repository/LocalStoryRepository.kt`
- Modify: `core/database/src/main/kotlin/app/openstory/database/repository/RoomStoryRepository.kt`
- Create: `core/database/src/androidTest/kotlin/app/openstory/database/repository/StoryPurgeRepositoryTest.kt`
- Modify: `core/database/src/androidTest/kotlin/app/openstory/database/Wave02CheckpointSupport.kt`
- Modify: `core/database/src/androidTest/kotlin/app/openstory/database/Wave02CheckpointTest.kt`
- Modify: `core/database/src/test/kotlin/app/openstory/database/SchemaPolicyTest.kt`

**Interfaces:**
- Consumes: canonical story graph and Room foreign-key behavior.
- Produces: explicit `purgeStory(StoryId)` transaction, plugin-state isolation tests, orphan cleanup, contiguous schema checks, and secret-table backup guard.

- [ ] Add failing tests showing plugin registration deletion preserves user data and explicit purge removes linked orphan source records without foreign-key violations.
- [ ] Add failing policy tests requiring schemas 1..3 and rejecting session/cookie/token/auth-secret tables from the backed-up database.
- [ ] Implement `StoryPurgeDao.purgeStory`, expose it through `LocalStoryRepository`, and delete only source records orphaned after the canonical graph is removed.
- [ ] Run checkpoint, converter/integrity, repository, and schema policy tests.
- [ ] Commit `database: enforce canonical lifecycle integrity`.

### Task 5: Add the Wave 02 database checkpoint to local and CI verification

**Files:**
- Create: `scripts/verify-wave-02-checkpoint.sh`
- Create: `scripts/tests/verify-wave-02-checkpoint-test.sh`
- Modify: `.github/workflows/android.yml`
- Create: `docs/internal/checkpoints/wave-02-remediation.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: API 26/API 37 emulator serials and `:core:database:connectedDebugAndroidTest`.
- Produces: one command that verifies the Wave 01 fast gate plus database instrumentation on both required API levels, with CI jobs using the same entrypoint.

- [ ] Write a shell contract test proving serial/API validation and the database instrumentation task are mandatory.
- [ ] Run the contract test and confirm failure because the checkpoint script does not exist.
- [ ] Add the runner, CI jobs/artifacts, evidence document, and README commands.
- [ ] Run Bash syntax, contract tests, repository static verification, and all locally available pure tests.
- [ ] On the target Windows repository run API 26 and API 37 checkpoint verification before closing Wave 02.
- [ ] Commit `test: add wave 02 database checkpoint`.

## Self-Review

- Spec coverage: Wave 02 Tasks 1–6 are covered without adding Wave 05 catalog ingestion or Wave 09 downloads.
- Migration ownership: schema 1 and 2 are unchanged; schema 3 is additive with 2→3 migration.
- Error semantics: validation, cancellation, and storage failures remain distinguishable.
- Lifecycle semantics: remove-from-library remains separate from explicit canonical purge.
- Checkpoint evidence: database instrumentation is required on both API 26 and API 37.
