# First Local Vertical Slice — Local MP4 End-to-End Implementation Plan

> **For agentic workers:** implement task-by-task. Do not broaden scope while a task is red. Re-read the foundation Current Control Block before implementation and reopen an owning `Q-*` only when executable evidence contradicts a provisional decision.

**Status:** ACTIVE - Tasks 0-5 CLOSED; Task 6 is NEXT. Current evidence and next action live in the foundation Current Control Block.

**Goal:** Prove the V1 foundation with one deliberately small, restart-safe local vertical slice:

```text
register one SAF root
→ discover one standalone provider-declared MP4
→ create/reuse app-owned canonical Media
→ persist one local SourceBinding + Asset representation
→ auto-admit Media into Library
→ resolve the local representation at runtime
→ play it through Media3 session/service ownership
→ persist typed video progress
→ kill/force-stop process
→ relaunch, restore Library, re-resolve content, resume from durable progress
```

**Architecture:** Keep identity, persistence, storage access, scanning, source resolution, playback runtime and UI as separate authorities. `:app` remains the composition root. `:feature:library` remains presentation-only. `:storage:local` emits observations/access results but never decides canonical identity. `:ingestion:local` orchestrates scan semantics through domain ports but never imports Room or the local source implementation. `:data` owns the one canonical Room database and bounded semantic transactions. `:source:local` turns a canonical target/binding/asset into ephemeral `ResolvedContent`. `:playback:media3` owns the `Player`/`MediaSessionService`; app-level wiring supplies persistence callbacks without adding a DI framework or a forbidden implementation dependency.

**Primary decisions:** `Q-SRC-001`, `Q-STO-001`, `Q-ID-001`, `Q-REC-001`, `Q-SCN-001`, `Q-PER-001`, `Q-LIB-001`, `Q-PROG-001`, `Q-PROG-002`, `Q-RUN-001`, `Q-MOD-001`, `Q-API-001`.

**Current toolchain baseline:** JDK 17, Gradle 9.4.1, AGP 9.2.1, Kotlin 2.4.20, compile/target/min SDK 37/37/23, SDK package `platforms;android-37.0`.

**Verified first-slice library baseline from Task 0:** Room 2.8.5, KSP 2.3.12, WorkManager 2.11.2, Media3 1.11.1, kotlinx.coroutines 1.11.0, and Lifecycle 2.11.0 where lifecycle-aware Compose collection is actually needed. These pins were accepted only after the real repository compile/verification spike passed on Windows/JDK 17; no `kapt` was introduced. Avoid Lifecycle 2.12 alpha because its Compose artifacts require compileSdk 37.1, conflicting with the current locked compileSdk 37 bootstrap contract.

---

## 1. Research Conclusions That Constrain This Plan

### 1.1 Android platform conclusions

- SAF tree access remains the V1 universal local-root mechanism. Persist read access with the framework grant, but treat the URI/grant as current access evidence, never as `RootId` or canonical media identity.
- Recursive scanning uses `DocumentsContract`/`ContentResolver` directly rather than `DocumentFile`. `DocumentFile` is a convenience wrapper with substantial overhead; direct queries also allow explicit projection, cancellation and `EXTRA_LOADING` handling.
- A successful SAF query with `DocumentsContract.EXTRA_LOADING=true` is incomplete observation. It cannot produce blanket negative reconciliation authority.
- WorkManager owns persistent scheduling, not scan truth. `WorkRequest.id != ScanRunId`.
- Custom Worker constructor injection requires a custom `WorkerFactory`. To avoid on-demand initialization changing crash/force-stop rescheduling behavior, the implementation should remove the default WorkManager initializer and explicitly initialize WorkManager in `Application.onCreate()` with the custom configuration.
- Media3 playback that can outlive the Activity belongs in `MediaSessionService`. First slice uses an explicit same-app `SessionToken(ComponentName(...))` and keeps the service `android:exported="false"` to preserve the security baseline.
- Room schema history is exported and committed from the first production schema. No destructive migration fallback is allowed for canonical/user truth. Room 2.8.x and WorkManager 2.11.x both require minSdk 23, which exactly matches the current project baseline; this plan does not raise minSdk.

### 1.2 Reference-app lessons — evidence, not copied architecture

- **Nova Video Player:** scan/index behavior has been affected by Android background-execution changes. Lesson: do not make MediaStore indexing or a foreground-service lifetime the app's canonical scan truth.
- **Mihon:** repeated SAF wrapper lookup over large directories has produced severe traversal cost. Lesson: never write per-item `findFile`/tree re-search loops; traverse each directory once and operate on bounded observation batches.
- **Readium Kotlin Toolkit:** runtime navigator state is not durable application progress by itself; the application persists/restores its own locator. Lesson: keep player/reader runtime objects separate from durable progress ownership.
- **AntennaPod / Jellyfin Android TV:** playback implementation is isolated from broader UI/data concerns, and playback consumes an already-selected/resolved source. Lesson: resolve source before playback and keep Media3 implementation details behind the playback boundary.
- **VLC Android:** a mature media app can have broad permission/service requirements, but they are product-specific. Lesson: do not copy broad permissions or exported components into this local-first slice.

### 1.3 Chosen first media shape

The first supported shape is intentionally narrow:

```text
standalone document
MIME = video/mp4
canonical owner = Media
consumption target = MediaTarget(MediaId)
MediaUnit = not created
```

Filename/extension may be stored as representation evidence/fallback UI label, but the first acceptance contract does **not** use filename or extension as canonical identity or canonical metadata truth.

### 1.4 Explicitly out of scope for this slice

Do **not** implement any of the following merely because a table/module already exists or a reference app has it:

- CBZ/image-sequence reader;
- EPUB/publication reader or Readium integration;
- `MediaUnit`/chapter/episode modeling for the standalone MP4;
- MediaStore discovery adapter;
- rename/move continuity beyond exact current-locator reuse;
- fingerprint/hash-based rematching;
- authoritative negative reconciliation / `MISSING` transition application;
- automatic scheduled periodic scans;
- network source, INTERNET permission, cookies/tokens;
- metadata provider/enrichment pipeline;
- canonical title inferred from filename;
- History/Continue/Recently Added UI;
- playback queue, playlist, Android Auto, external MediaBrowser/controller support;
- playback-resumption receiver/system boot resumption;
- DI framework;
- new Gradle modules;
- numeric performance thresholds without measurement.

The schema may include already-approved table families needed for referential correctness, but unused future feature families are not materialized for symmetry.

---

## 2. Cross-Cutting Invariants

Every task must preserve these invariants.

### Identity

```text
RootId       != SAF tree URI
MediaId      != filename/path/documentId/hash
AssetId      != documentId/content URI
SourceId     != RootId
WorkRequestId != ScanRunId
Room target_row_id != ConsumptionTargetRef public identity
```

All canonical IDs are generated/owned by the app and persisted. Platform locators are typed evidence.

### Source / representation

```text
Source
  = configured origin/capability

SourceBinding
  = canonical target ↔ source association

Asset
  = durable representation lineage

ResolvedContent
  = ephemeral runtime-openable descriptor
```

Never persist `ResolvedContent` as canonical truth.

### Scan correctness

```text
positive observation may commit incrementally
partial/failed/cancelled scan may not publish absence
completed scan may be eligible for absence authority
first slice intentionally does not apply negative lifecycle transitions
```

Traversal and reconciliation are separate phases even when the first slice only performs positive materialization.

### Library

- Canonical Media existence does not imply active Library membership.
- New recognized Media under a registered root is auto-admitted when no suppression exists.
- Missing/unavailable storage never deletes Library membership.
- Explicit suppression is modeled even if the first UI does not expose Remove yet, so later rescan behavior has a correct durable home.

### Progress

- One current durable progress state per `ConsumptionTargetRef`.
- Video anchor is `positionMs`, not generic percentage.
- Completion is explicit.
- Progress may move backward.
- Writes are checkpointed/coalesced, not emitted every player tick/frame.
- Resume context records the binding/asset/revision used for the precise anchor.

### Module/API

- No feature imports Room, SAF/`DocumentsContract`, WorkManager, Media3, source implementation, or DAOs.
- `:ingestion:local` does not depend on `:data` or `:storage:local` implementation modules.
- `:source:local` does not import Room/DAO and does not pull scanner code into playback.
- `:playback:media3` does not directly depend on `:data`; persistence is injected through a narrow playback-api callback/event boundary owned by `:app` composition.
- Android framework objects do not cross pure-Kotlin public APIs when a small typed scalar/value object suffices.

### Verification ownership / agent token discipline

The commands in each task are **owning gates**, not blanket permission for the agent to execute every gate. Preserve Hikari-style ownership: cheap local reasoning stays agent-owned; expensive/runtime/acceptance evidence is user-owned by default.

- **Agent-owned:** focused JVM/unit tests, targeted non-device test filters, small compile checks, and narrow static/contract diagnostics. Batch related checks into one Gradle invocation where practical.
- **User-owned:** connected/instrumented/device/emulator tests; WorkManager/SAF/Media3 runtime acceptance; process-death proof; unfiltered module/full regression; cross-module `verifyArchitecture` / `:app:verifyFoundation` / security acceptance; `verify*.ps1`/`verify*.sh`; lint/Detekt sweeps; release-like builds; clean-checkout CI parity; benchmarks/profiling; and physical-device acceptance.
- A task may require a user-owned gate, but the agent stops at that boundary and hands off the exact command(s). The user may explicitly delegate a command back.
- Do not spend a user-owned acceptance run merely to manufacture a ceremonial RED. First make the relevant agent-owned unit/compile/static checks green unless a pre-change baseline is explicitly required.
- Prefer instrumentation class filters when handing off connected tests added by the current task. Do not request a broad device suite while a focused local failure remains unresolved.
- On a failed user-owned gate, narrow the next requested command to the smallest failing task/class before asking for another broad rerun.
- Keep evidence concise: successful commands are recorded as command + PASS + counts/timing; failures surface the first actionable region plus a small tail. Full logs are requested only when root-cause analysis needs them.
- A prior PASS may be reused only when the files/configuration that own that gate have not changed since that PASS. Reuse never converts an unexecuted required gate into `PASS`.
- `verifyArchitecture` remains required when project edges/build logic/public module boundaries change and at Tasks 0, 4, 7, 10, 12 and 13 checkpoints; outside those checkpoints it need not be repeated when the owning surface is untouched.
- Task 13 still performs the complete closure verification, but those broad closure gates remain user-owned by default.

For agent-owned commands, use one blocking invocation and do not tail/poll live output. If the runtime cannot await a command without repeated model turns or streamed progress, hand it to the user instead of supervising it.

---

# Implementation Tasks

## Task 0 — Dependency / Build / Architecture Preflight

**Status:** CLOSED — real Windows JDK-17 repository evidence passed on 2026-09-19.

**Purpose:** Prove the 2026 library/toolchain additions before any production model/schema work makes rollback expensive.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify only as required: `build.gradle.kts`
- Modify only as required: `data/build.gradle.kts`
- Modify only as required: `ingestion/local/build.gradle.kts`
- Modify only as required: `playback/media3/build.gradle.kts`
- Modify only as required: `app/build.gradle.kts`
- Add/modify repository-owned build contract tests only when a new pin needs mechanical protection.

**Verified Task 0 pins:**
- Room `2.8.5`
- Room Gradle plugin `2.8.5`
- KSP `2.3.12`
- WorkManager `2.11.2`
- Media3 `1.11.1`
- kotlinx.coroutines `1.11.0` when Flow/coroutine APIs are introduced explicitly
- Lifecycle `2.11.0` for lifecycle-aware Compose collection if required; do not use 2.12 alpha under compileSdk 37

**Rules:**
- KSP only; never `kapt`.
- Apply KSP/Room plugin only where compilation requires it (`:data`), not globally through every Android module.
- Use Room Gradle plugin with committed `schemaDirectory`.
- Do not weaken AGP 9 built-in Kotlin guards.
- Do not add dynamic versions.
- Do not change the existing module allow-list in this task.

**RED / prove first:**
- Add a build-contract assertion that production modules do not apply `kotlin-kapt`.
- Add/preserve a contract assertion that `compileSdk = 37` while the installed package contract remains `platforms;android-37.0`.

**GREEN:**
- Add only the dependencies/plugins needed to compile empty/minimal probes.
- Confirm KSP 2.3.12 + Kotlin 2.4.20 + AGP 9.2.1 compile together in this repository.
- Confirm Room schema generation path works with AGP 9 built-in Kotlin.

**Gate:**
```powershell
.\gradlew.bat help :data:compileDebugKotlin :app:compileDebugKotlin verifyArchitecture --no-daemon
.\scripts\verify-fast.ps1
```

**Exit criteria:** all current bootstrap gates remain green; no architecture edge changes; no product code yet.

**Execution evidence — 2026-09-19:**
- `PASS` — `./gradlew.bat help :data:compileDebugKotlin :app:compileDebugKotlin verifyArchitecture --no-daemon` (`BUILD SUCCESSFUL`, Gradle 9.4.1).
- `PASS` — `./scripts/verify-fast.ps1` (`BUILD SUCCESSFUL`).
- No reviewed project-dependency edge changed and no product model/schema was introduced by Task 0.

**Task result:** CLOSED. Resume at Task 1; do not repeat dependency research unless later executable evidence contradicts this preflight.

**Commit checkpoint:** `build: pin first-slice Android runtime dependencies`

---

## Task 1 — Minimal Canonical IDs, Target, Observation and Progress Contracts

**Status:** CLOSED — 9 focused JVM tests PASS and user-confirmed unfiltered module gate PASS on 2026-09-19; see foundation Current Control Block.

**Purpose:** Introduce only the stable types needed by the vertical slice before Room/Android implementation details appear.

**Files:**
- Create under `core/model/src/main/kotlin/app/universalmedia/core/model/`:
  - canonical ID value classes for `MediaId`, `UnitId`, `RootId`, `SourceId`, `SourceBindingId`, `AssetId`, `ScanRunId`;
  - `ConsumptionTargetRef` with both `MediaTarget(MediaId)` and `UnitTarget(UnitId)` because the public target contract is already locked, while this slice creates only `MediaTarget` rows for standalone MP4s;
  - minimal media/representation enums required by this slice.
- Create under `core/domain/src/main/kotlin/app/universalmedia/core/domain/`:
  - storage-root registration/access descriptor contracts;
  - `LocalDocumentObservation` / traversal outcome contracts;
  - scan-run/scope/outcome contracts;
  - semantic persistence/query ports;
  - typed video progress checkpoint/load contracts.
- Add pure JVM tests under `core/model/src/test/...` and `core/domain/src/test/...`.

**Required semantic ports — names may vary, responsibilities may not:**

```text
StorageRootStore
  registerOrReauthorize(...)
  get(...)

LocalTreeObservationSource
  observe(root, cancellation/batch sink)

ScanJournal
  beginRun(...)
  recordPositiveBatch(...)
  finalizeRun(...)

LocalMediaMaterializer / ReconciliationStore
  commitRecognizedLocalVideo(...)

LibraryQueries
  observeLibraryCards()

LocalSourceCatalog
  load target/binding/asset/current-locator context

ProgressStore
  load(target)
  checkpointVideo(...)
  markVideoCompleted(...)
```

Do **not** create one generic `Repository<T>` or public `withTransaction {}`.

**Observation contract constraints:**
- Carries `RootId` plus scoped locator evidence/provenance.
- Uses plain Kotlin types; no `Uri`, `Cursor`, `DocumentFile` or Room entity types.
- May carry `displayName`, MIME, size and last-modified as observation facts.
- Clearly distinguishes current locator evidence from canonical IDs.
- Supports an incomplete traversal result so SAF `EXTRA_LOADING` cannot be collapsed into success.

**Video progress contract:**

```text
VideoProgressCheckpoint
├── target: ConsumptionTargetRef
├── positionMs
├── completion state
├── binding/asset/revision context
└── observedAt / state revision as needed for stale-write rejection
```

**RED:** tests proving:
- different app-generated IDs are not constructed from locator strings;
- `MediaTarget` round-trip equality is independent of Room surrogate IDs;
- video progress can move backward;
- completion is not inferred from percentage;
- incomplete traversal is representable independently from failure.

**Agent-owned focused checks:**
```powershell
.\gradlew.bat :core:model:test --tests '*CanonicalIdentityTest' :core:domain:test --tests '*FirstSliceContractTest' --no-daemon
```

**User-owned module gate (per root `AGENTS.md`):**
```powershell
.\gradlew.bat :core:model:test :core:domain:test --no-daemon
```

`verifyArchitecture` may reuse the Task 0 PASS because Task 1 changes no Gradle project edge. If Task 1 unexpectedly changes a build/module boundary, hand the required architecture gate to the user rather than auto-running it.

**Exit criteria:** pure Kotlin contract layer exists; no Android/Room imports in core APIs.

**Commit checkpoint:** `domain: define first-slice identity scan and progress contracts`

---

## Task 2 — Canonical Room Database V1 + Semantic Transactions

**Status:** CLOSED - focused JVM/compile checks PASS and user-owned Room device acceptance PASS (10 tests, 0 failures/errors/skips; Redmi Note 9S / Android 15). Local result artifacts reviewed on 2026-09-19; see foundation Current Control Block.

**Purpose:** Materialize the minimum approved Q-PER schema needed by the first slice while establishing migration discipline from schema version 1.

**Files:**
- Modify: `data/build.gradle.kts`
- Create under `data/src/main/kotlin/app/universalmedia/data/`:
  - `UniversalMediaDatabase`;
  - internal Room entities/DAOs/mappers;
  - semantic transaction implementations;
  - query projections.
- Create: `data/schemas/.../1.json` through Room schema export.
- Add data instrumentation tests under `data/src/androidTest/...`.

**Materialize these logical table families:**

```text
media
media_unit            // schema support for the already-locked UnitTarget FK; no MP4 Unit rows
consumption_target
source
source_binding
storage_root
asset
asset_locator
library_entry
progress_state
video_resume_anchor
scan_run
scan_scope
scan_seen_asset   // or equivalent run-local positive journal
```

Do not pre-create `media_grouping`, History, image/publication anchors or metadata candidate tables merely for symmetry. `media_unit` is the one intentionally unused canonical table in schema v1 because Q-PER-001 already locks a referentially valid `ConsumptionTargetRef` bridge for both Media and Unit targets; the standalone MP4 path creates no Unit rows.

**Schema guardrails:**
- app-owned IDs are stable text/blob values independent from Android locator strings;
- `consumption_target.target_row_id` is internal surrogate only;
- full target bridge invariant: exactly one canonical referent per target row, with real FKs to `media` / `media_unit`; first-slice writes use only `MEDIA`;
- `LibraryEntry` is Media-owned and does not FK to Asset;
- `Progress` is target-owned and does not FK-own itself through Asset;
- Asset/locator/root disappearance cannot cascade-delete Library/Progress;
- owned children only may cascade, e.g. `progress_state → video_resume_anchor` on explicit progress purge;
- all common FK/join columns indexed;
- current-locator evidence that is treated as the same observed representation is protected against duplicate materialization by transaction/constraint design scoped to the registered root/provider locator; that uniqueness is an observation invariant, not a claim that the locator is canonical identity;
- no `fallbackToDestructiveMigration()` on canonical DB.

**Semantic transaction implementations:**
1. `registerOrReauthorizeRoot` — create/reuse `RootId` from app state; descriptor equality may be evidence for reauthorization but does not define the ID.
2. `beginScanRun` — store domain `ScanRunId`, declared scope/config generation and start timestamp.
3. `commitRecognizedLocalVideo` — in one bounded transaction:
   - exact-current-locator evidence lookup;
   - reuse or generate `MediaId`;
   - ensure `ConsumptionTargetRef` bridge;
   - ensure built-in Local `Source`;
   - ensure `SourceBinding`;
   - create/reuse/update `Asset` current representation facts;
   - create/update current `asset_locator` evidence;
   - write scan seen evidence;
   - auto-admit Library if no suppression intent exists.
4. `finalizeScanRun` — persist outcome/coverage only. First slice does not apply negative lifecycle transitions.
5. `checkpointVideoProgress` — atomically update `progress_state` + `video_resume_anchor` + resume context, rejecting stale writes if implementation uses state revisions.

**Built-in Local Source rule:**
- one app-owned durable source identity represents the local-source capability;
- it is not one Source per root and `SourceId != RootId`;
- do not derive it from Android URI/provider state.

**Library fallback label rule:**
- `displayName` may be persisted as Asset/locator observation evidence or exposed through the Library query projection as a representation fallback label;
- do not write filename-derived text into canonical metadata/title authority.

**RED tests:**
- second commit of same exact locator reuses `MediaId`, `SourceBindingId`, `AssetId`;
- concurrent/replayed commit of the same root-scoped current locator cannot create duplicate Media/Asset rows;
- two different locator observations do not collapse solely because filename/size match;
- Asset/root state mutation does not cascade-delete Library or Progress;
- active Library row is created on first recognition;
- suppression state prevents auto-admission if seeded by test;
- target surrogate never leaks from query/domain mapper;
- progress backward seek persists correctly;
- DB close/reopen restores IDs, Library and progress;
- schema export exists and Room schema validation passes.

**Agent-owned focused gate:**
```powershell
.\gradlew.bat :data:testDebugUnitTest --no-daemon
```

**User-owned acceptance gate after the focused gate is green:**
```powershell
.\gradlew.bat :data:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=app.universalmedia.data.RoomMediaStoreTest" --no-daemon
```

Do not request `verifyArchitecture` unless this task changes a project dependency/build boundary; if required, it is user-owned.

**Exit criteria:** canonical DB version 1 exists, schema JSON committed, semantic transactions proven on device/emulator.

**Commit checkpoint:** `data: establish canonical first-slice Room schema`

---

## Task 3 — SAF Root Registration and Direct DocumentsContract Access

**Status:** CLOSED - focused JVM/compile checks PASS; seven unaffected device cases passed in the original run, and the corrected current-document test passed the user-confirmed narrowed rerun (local XML: 1 test, 0 failures/errors/skips). Evidence reviewed on 2026-09-19; see foundation Current Control Block. Resume at Task 4.

**Purpose:** Establish real user-authorized local-root access without leaking Android locators into canonical identity.

**Files:**
- Create under `storage/local/src/main/kotlin/app/universalmedia/storage/local/`:
  - SAF descriptor codec/internal types;
  - tree-access validator;
  - direct `DocumentsContract` traversal adapter;
  - current-document access adapter used later by local source resolution.
- Add storage instrumentation tests under `storage/local/src/androidTest/...`.
- Modify `storage/local/build.gradle.kts` only for required AndroidX test/runtime dependencies.

**Registration behavior:**
- `:app` owns the `ACTION_OPEN_DOCUMENT_TREE` / `ActivityResultContracts.OpenDocumentTree` launcher because ActivityResult is an Android UI lifecycle concern.
- the raw tree result is handed immediately to a narrow `:storage:local` registration adapter; feature code never receives the URI.
- `:storage:local` attempts `takePersistableUriPermission(..., FLAG_GRANT_READ_URI_PERMISSION)`, validates current access, and produces a non-framework registration observation/descriptor for the domain/data transaction.
- full URI is persisted only as current device-local access/locator evidence where required; never logged.
- failed grant/validation returns a typed error; do not register a fake usable root.

**Traversal behavior:**
- Use direct `ContentResolver.query()` + `DocumentsContract` APIs.
- Query only explicit required columns:
  - document id;
  - display name;
  - MIME type;
  - size;
  - last modified;
  - flags when needed.
- Traverse each directory once; never per-child re-query the whole parent tree.
- use an iterative/deque traversal with a run-local visited-directory evidence set so a malformed/provider-specific graph cannot cause recursive stack overflow or traversal cycles; directory locators remain evidence, not identity.
- validate nullable/provider-controlled columns defensively and bound in-memory handling of pathological names/metadata values; never log the raw URI/path while diagnosing malformed rows.
- Use bounded batches; no unbounded one-coroutine-per-file fan-out. A defensive per-run observation/directory budget may terminate the traversal as typed `LIMIT_REACHED`/incomplete coverage rather than manufacturing completion.
- Wire cancellation through `CancellationSignal` where platform query allows it.
- Detect provider/query failures as scoped incomplete/error evidence.
- Detect `EXTRA_LOADING` and return incomplete coverage rather than authoritative completion.
- Avoid `DocumentFile` in production scanner path.

**First-slice recognition candidate:**
- traversal emits all required observation facts;
- recognition itself stays in `:ingestion:local`/domain policy;
- directories are traversal structure, not media candidates;
- no canonical decisions in `:storage:local`.

**RED tests:**
- a fake/test DocumentsProvider tree is enumerated exactly once per directory in the representative fixture;
- `EXTRA_LOADING=true` yields incomplete traversal result;
- cancellation stops traversal without producing completion;
- provider exception yields typed scoped failure;
- URI/document ID never appears as generated `RootId`/`MediaId`/`AssetId`;
- production code contains no `DocumentFile` scanner usage.

**Agent-owned focused gate:**
```powershell
.\gradlew.bat :storage:local:testDebugUnitTest --tests '*SafTraversalTest' :storage:local:compileDebugAndroidTestKotlin :storage:local:processDebugAndroidTestManifest --no-daemon
```

**User-owned acceptance gate after the focused gate is green:**
```powershell
.\gradlew.bat :storage:local:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=app.universalmedia.storage.local.SafLocalStorageTest" --no-daemon
```

Do not request `verifyArchitecture` unless this task changes a project dependency/build boundary; if required, it is user-owned.

**Exit criteria:** SAF root can be validated/traversed through direct APIs and represented as typed observations without canonical identity leakage.

**Commit checkpoint:** `storage: add direct SAF root access and traversal`

---

## Task 4 — Scan Orchestration + WorkManager Ownership

**Status:** CLOSED - 8 focused JVM tests PASS; user confirmed both runtime handoff commands and verifyArchitecture succeeded. Reviewed local XML: 4 WorkManager tests and 1 app composition test, 0 failures/errors/skips on Redmi Note 9S / Android 15. Evidence reviewed on 2026-09-19; see foundation Current Control Block. Task 5 is next.

**Purpose:** Run a restart-safe root scan through Q-SCN/Q-RUN semantics while keeping WorkManager telemetry separate from domain scan truth.

**Files:**
- Create under `ingestion/local/src/main/kotlin/app/universalmedia/ingestion/local/`:
  - `LocalRootScanRunner` / coordinator;
  - MP4 classifier;
  - `LocalScanWorker`;
  - worker factory helper/factory contribution.
- Modify: `ingestion/local/build.gradle.kts`
- Create/modify in `app/src/main/kotlin/app/universalmedia/`:
  - `UniversalMediaApplication` / app graph;
  - WorkManager eager custom initialization.
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`
- Add WorkManager/scan tests.

**Composition rule:**

```text
UniversalMediaApplication / AppGraph
├── DB + semantic data ports
├── SAF observation source
├── scan runner
├── WorkerFactory
└── WorkManager Configuration
```

No static service locator. Android-created Worker receives dependencies through the custom `WorkerFactory`.

**WorkManager initialization:**
- remove WorkManager's default App Startup initializer using manifest merge metadata;
- explicitly call `WorkManager.initialize(applicationContext, configurationWithWorkerFactory)` from `Application.onCreate()`;
- use `WorkManager.getInstance(context)` everywhere else;
- do not use `Configuration.Provider` on-demand initialization for this slice.

**Enqueue policy:**
- unique work name derived from app-owned `RootId`, e.g. semantic `scan-root:<RootId>` string only as scheduler key;
- WorkManager `Data` carries only app-owned `RootId` plus small scheduler-safe scalars; it never carries the SAF URI/access descriptor, full path, DAO entity or serialized scan truth. The Worker reloads the current registered-root descriptor/config generation from canonical storage when execution actually begins;
- choose `KEEP` or an equivalent policy that prevents duplicate same-root scans from running concurrently while one is active;
- a new worker execution creates a **fresh app-owned `ScanRunId`**;
- scheduler retry never reuses old negative authority.

**Scan runner pipeline:**

```text
1. load registered root + current config generation
2. begin ScanRun with immutable declared root scope
3. traverse SAF observations
4. classify provider-declared MIME `video/mp4`
5. commit positive candidates in bounded batches
6. record incomplete regions/errors
7. finalize ScanRun outcome + coverage
8. never apply negative Asset/Library deletion in this slice
```

**Retry semantics:**
- retry only for explicitly classified transient runtime failures;
- revoked SAF grant/access-denied is terminal diagnostic, not infinite retry;
- cancellation/process death leaves run non-authoritative for absence;
- WorkManager restart may leave an abandoned/nonterminal run; recovery code treats it as interrupted operational evidence and starts a fresh run when work executes again.

**RED tests:**
- `WorkRequest.id` is never persisted as `ScanRunId`;
- two same-root enqueue requests coalesce/serialize;
- each actual worker attempt creates a fresh ScanRun;
- cancelled/failed/incomplete scan commits positives but never negative state;
- revoked access returns terminal failure/diagnostic without retry loop;
- large fake tree processing uses bounded batches and bounded concurrency;
- cyclic/duplicated provider directory evidence cannot loop forever; defensive traversal limit yields incomplete/non-authoritative coverage;
- worker input contains `RootId` rather than the persisted tree URI/descriptor;
- stale/superseded run cannot execute a negative-finalization path (first slice path is absent/disabled).

**Agent-owned focused gate:**
```powershell
.\gradlew.bat :ingestion:local:testDebugUnitTest --tests '*LocalRootScanRunnerTest' :ingestion:local:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon
```

**User-owned checkpoint gate:**
```powershell
.\gradlew.bat :ingestion:local:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=app.universalmedia.ingestion.local.LocalScanWorkerTest" verifyArchitecture --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=app.universalmedia.ScanCompositionTest" --no-daemon
```

**Exit criteria:** one root can be scheduled and scanned restart-safely with durable ScanRun truth independent from WorkManager state.

**Commit checkpoint:** `ingestion: add restart-safe local MP4 scan pipeline`

---

## Task 5 — Library Projection + Root Registration UI

**Status:** CLOSED - 8 focused JVM tests PASS; user confirmed both device commands, verifyArchitecture and manual Add Folder/rescan acceptance succeeded. Reviewed local XML: 6 Compose tests and 1 Room projection test, 0 failures/errors/skips on Redmi Note 9S / Android 15. Evidence reviewed on 2026-09-19; see foundation Current Control Block. Task 6 is next.

**Purpose:** Make the first persisted result visible without coupling the feature to Room/SAF/WorkManager.

**Files:**
- Modify: `feature/library/src/main/kotlin/app/universalmedia/feature/library/LibraryRoot.kt`
- Add internal UI models/callback contracts under `feature/library/...`.
- Add Compose tests under `feature/library/src/androidTest/...` or existing supported test source set.
- Modify app composition/navigation files under `app/src/main/kotlin/app/universalmedia/`.

**Feature API shape:**

```text
LibraryUiState
├── roots/scan summary needed by this screen
├── library cards
├── add-root enabled/busy state
└── typed user-facing error state

LibraryRoot(
  state,
  onAddRoot,
  onMediaSelected,
  onRetryScan
)
```

The feature receives plain stable IDs/scalars/UI models. It does not receive DAO, `Uri`, `WorkInfo`, `MediaController` or `ContentResolver`.

**App orchestration:**
- app launcher owns `OpenDocumentTree` ActivityResult registration;
- app passes the returned platform value directly to the storage registration adapter; only the storage adapter touches `ContentResolver`/persistable-grant APIs, then app passes the resulting platform-free registration observation into the data/domain transaction;
- after successful registration, app enqueues scan for `RootId`;
- app observes durable Library/scan projections and maps to `LibraryUiState`; if Flow is used, collection is lifecycle-aware with the stable Lifecycle baseline selected in Task 0; no ViewModel is introduced solely to satisfy a pattern if the app-owned state holder/composition root already provides the required lifetime;
- scheduler telemetry may supplement UI status but must not replace durable `ScanRun` status.

**Library card:**
- stable key/navigation value = `MediaId`;
- display label may use representation `displayName` fallback;
- do not persist fallback as canonical title;
- availability may be minimal (`available`/diagnostic) but Library membership remains visible if representation later becomes unavailable.

**RED UI tests:**
- empty state exposes Add Folder action;
- scanning state does not hide previously persisted Library entries;
- a recognized MP4 card is keyed by `MediaId`;
- representation label fallback renders without creating canonical metadata requirement;
- typed registration/scan failure is visible and retryable;
- feature package has no forbidden Android storage/Room/WorkManager/Media3 imports.

**Agent-owned focused gate:**
```powershell
.\gradlew.bat :feature:library:testDebugUnitTest --tests '*LibraryPresentationTest' :app:testDebugUnitTest --tests '*LibraryCoordinatorTest' --tests '*LibraryStateHolderTest' :feature:library:compileDebugAndroidTestKotlin :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon
```

**User-owned acceptance gate:**
```powershell
.\gradlew.bat :feature:library:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=app.universalmedia.feature.library.LibraryRootTest" --no-daemon
.\gradlew.bat :data:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=app.universalmedia.data.LibraryRootsTest" verifyArchitecture --no-daemon
```

Task 5 concretizes the planned durable scan projection with `LibraryQueries.observeLibraryRoots`: only RootId, access and current-generation run presence/outcome cross the domain boundary. No table/schema/module edge is added. The new Room query needs its own device gate for initial/active/finalized state, clock rollback, reopen and reauthorization. App observation/registration uses an application-owned scope, while Compose state collection is lifecycle-aware. Missing run finalization is shown as waiting for completion, not proof of a currently executing Worker.

Manual acceptance: launch the app, choose **Add Folder**, grant a folder containing a provider-declared MP4, wait for its card, and rescan without losing the card. Playback remains deferred to its owning tasks.

Do not request the full `:app:connectedDebugAndroidTest` surface here; Task 9 owns the composed app route/device proof.

**Exit criteria:** user can register one folder and see the recognized MP4 in Library after the asynchronous scan completes.

**Commit checkpoint:** `library: expose local root registration and media projection`

---

## Task 6 — Local Source Resolution Boundary

**Purpose:** Prove that playback receives runtime content through Source → Binding → Asset → ResolvedContent rather than by reading a URI directly from Library/Room.

**Files:**
- Create under `source/api/src/main/kotlin/app/universalmedia/source/api/`:
  - minimal `ResolvedContent`/`ResolvedVideo` runtime contract;
  - `SourceResolver` contract;
  - typed resolution errors.
- Create under `source/local/src/main/kotlin/app/universalmedia/source/local/`:
  - local resolver;
  - local access validation/openability adapter orchestration.
- Add source API/local tests.
- Add any narrow core/domain local-access port required for dependency inversion; do not add a direct `source:local → storage:local` project dependency unless executable evidence proves the existing Q-MOD boundary impossible and the owning decision is reopened first.

**ResolvedVideo constraints:**
- contains only runtime-openable data needed by playback (e.g. validated content URI string, MIME type, optional runtime metadata);
- carries no canonical authority of its own;
- is not saved to Room or navigation state;
- no expiring/auth data in this local slice.

**Resolution sequence:**

```text
MediaTarget(MediaId)
→ query active Local SourceBinding
→ query current Asset + locator/revision context
→ validate current root/locator access
→ create ephemeral ResolvedVideo
```

**Dependency inversion shape:**
- `:data` implements target/binding/asset query ports declared in stable contracts;
- `:storage:local` may implement a narrow local-locator access validator/opener contract declared outside the implementation module;
- `:app` injects both into `:source:local`;
- `:source:local` never imports DAO classes.

**RED tests:**
- valid target returns `ResolvedVideo` for the current Asset revision;
- missing/revoked access returns typed unavailable/access error, not a null/string exception;
- no binding returns typed no-source result;
- runtime resolved URI is not written to canonical Media ID or Library/navigation state;
- second process/app graph construction reconstructs resolution entirely from durable binding/asset/locator state.

**Agent-owned focused gate:**
```powershell
.\gradlew.bat :source:api:test :source:local:testDebugUnitTest --no-daemon
```

Do not request `verifyArchitecture` unless this task changes a project dependency/build boundary; if required, it is user-owned.

**Exit criteria:** canonical target can be resolved after restart without Library/feature directly knowing the SAF locator.

**Commit checkpoint:** `source: resolve local media through durable binding and asset`

---

## Task 7 — Playback API Contract + Internal Media3 Session Service

**Purpose:** Consume `ResolvedVideo` with Media3 while preserving the existing module graph and exported-component security baseline.

**Files:**
- Create under `playback/api/src/main/kotlin/app/universalmedia/playback/api/`:
  - `PlaybackRequest` using stable `ConsumptionTargetRef` + ephemeral `ResolvedVideo` + initial resume anchor;
  - playback state/command contract needed by app UI;
  - narrow `PlaybackProgressSink` or typed progress-event sink used for dependency inversion.
- Create under `playback/media3/src/main/kotlin/app/universalmedia/playback/media3/`:
  - `PlaybackService : MediaSessionService`;
  - ExoPlayer/MediaSession ownership;
  - same-app MediaController connection helper;
  - minimal video surface adapter needed by app UI without leaking Media3 into feature modules.
- Modify: `playback/media3/src/main/AndroidManifest.xml`
- Modify: `playback/media3/build.gradle.kts`
- Modify app manifest/dependencies only as needed for foreground playback permissions/merged service declaration.
- Add playback tests/instrumentation.

**Manifest/security:**
- service explicitly `android:exported="false"`;
- declare the required `<intent-filter><action android:name="androidx.media3.session.MediaSessionService"/></intent-filter>` because Media3 defines this as the service interface; `exported=false` still prevents other apps from invoking/binding it;
- do not add the legacy/external `android.media.browse.MediaBrowserService` action or a MediaButtonReceiver in this slice;
- same app connects with explicit `SessionToken(context, ComponentName(context, PlaybackService::class.java))`;
- add only the foreground-service permissions required by Media3 continuous playback (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) if required by target API/runtime behavior, and declare `android:foregroundServiceType="mediaPlayback"` on the service when the foreground playback path is enabled;
- do not add INTERNET, broad storage or unrelated permissions;
- security verifier must still find MainActivity as the only exported component.

**Runtime ownership:**

```text
PlaybackService
├── ExoPlayer
├── MediaSession
├── current canonical target context
└── progress-event/checkpoint producer

Activity/app UI
└── explicit MediaController to service
```

`Player`, `MediaItem`, `MediaController`, `MediaSession` never become domain/persistence types.

**Progress dependency seam:**
- `:playback:media3` does **not** gain a direct `:data` dependency;
- because `PlaybackService` is Android-instantiated, `playback:api` exposes a narrow immutable runtime-dependency entrypoint (for example `PlaybackRuntimeDependenciesProvider`); `UniversalMediaApplication` implements it and returns only the playback dependencies, not the entire `AppGraph`;
- service resolves that typed application entrypoint once in `onCreate()` and obtains the app-supplied `PlaybackProgressSink`;
- sink implementation in `:app` delegates to the domain `ProgressStore`/data implementation;
- no global static registry and no arbitrary service lookup by class/key.

**RED tests:**
- service owns player/session independent of Activity lifecycle;
- explicit same-app controller connects while service is not exported;
- exported-component security test remains exactly one exported component;
- playback request receives `ResolvedVideo`, not raw DAO/Asset entity;
- service teardown releases session/player;
- feature modules have zero Media3 imports.

**Agent-owned focused gate:**
```powershell
.\gradlew.bat :playback:api:test :playback:media3:testDebugUnitTest --no-daemon
```

**User-owned checkpoint gates:**
```powershell
# Prefer class-filtered Media3 instrumentation when concrete classes exist.
.\gradlew.bat :playback:media3:connectedDebugAndroidTest :app:verifyFoundation verifyArchitecture --no-daemon
.\scripts\verify-security-baseline.ps1
```

**Exit criteria:** local MP4 can be started through the internal session service with no security-boundary regression.

**Commit checkpoint:** `playback: add internal Media3 session service`

---

## Task 8 — Typed Video Progress Checkpointing + Resume Compatibility

**Purpose:** Prove durable progress independent from the runtime player and representation availability.

**Files:**
- Extend playback/media3 checkpoint producer.
- Extend app composition adapter implementing `PlaybackProgressSink`.
- Extend data progress transaction/tests if needed.
- Add domain policy tests for resume selection.

**Initial checkpoint policy:**
- dirty progress is sampled/coalesced on a bounded time interval during active playback;
- immediate checkpoint on pause and explicit stop/session transition when possible;
- `STATE_ENDED` writes explicit `COMPLETED` plus final anchor;
- do not write every `onEvents`, frame or Compose recomposition;
- exact periodic interval is an implementation constant/policy with a test ensuring write rate is bounded. Start conservative; tune only with measurement.

**Stale write protection:**
- serialize writes per target, or use a monotonically increasing in-process checkpoint sequence/state revision so an older asynchronous write cannot overwrite a newer one;
- do **not** enforce monotonic `positionMs` because rewind/backward seek is valid.

**Resume compatibility for first slice:**
- exact anchor reuse allowed when durable context resolves to the same compatible local Asset/revision;
- if precise context is unavailable/incompatible, return typed fallback (`start from beginning` for first slice) rather than applying normalized percentage as exact truth;
- source/asset revision context is updated when successful playback establishes a new precise anchor.

**Kill semantics:**
- unexpected process kill during active playback may lose at most the dirty interval since the last checkpoint;
- process-death acceptance test pauses first, waits for immediate checkpoint, then force-stops, so exact persisted-resume behavior is deterministic within a small media-position tolerance.

**RED tests:**
- 10,000 synthetic position samples do not produce 10,000 DB writes;
- pause causes a prompt checkpoint;
- backward seek persists the lower position;
- stale delayed checkpoint cannot overwrite newer state;
- ended state becomes explicit completed;
- compatible same Asset/revision resumes exact persisted `positionMs`;
- incompatible context does not blindly reuse precise anchor.

**Agent-owned focused gate:**
```powershell
.\gradlew.bat :core:domain:test :data:testDebugUnitTest :playback:media3:testDebugUnitTest --no-daemon
```

**User-owned acceptance gate:**
```powershell
# Prefer class-filtered progress/persistence Media3 + Room instrumentation when concrete classes exist.
.\gradlew.bat :data:connectedDebugAndroidTest :playback:media3:connectedDebugAndroidTest --no-daemon
```

Task 7 already owns the nearby architecture checkpoint; request `verifyArchitecture` here only if Task 8 changes module/build boundaries, and treat it as user-owned.

**Exit criteria:** playback progress is durable typed application state, not Media3 runtime state.

**Commit checkpoint:** `progress: persist typed video checkpoints and resume context`

---

## Task 9 — App Playback Route and End-to-End Orchestration

**Purpose:** Connect Library selection to source resolution, progress load and playback without teaching the feature about implementation details.

**Files:**
- Modify/create app-level navigation/composition files under `app/src/main/kotlin/app/universalmedia/`.
- Add minimal app-owned player route/screen; reusable Media3-specific surface remains in `:playback:media3` if needed.
- Modify `MainActivity.kt` only as composition requires.
- Add app instrumentation tests.

**Navigation contract:**
- route/state argument = `MediaId` only;
- for this two-surface proof, prefer a small app-owned state machine/`rememberSaveable`-compatible stable-ID state over introducing Navigation Compose solely for one Library→Player transition; add a navigation framework only when real route/back-stack requirements justify it later;
- never put URI, `AssetId`, Room row id, `ResolvedVideo`, `MediaItem` or serialized DAO entity in navigation state.

**Open flow:**

```text
Library onMediaSelected(MediaId)
→ app creates MediaTarget(MediaId)
→ ProgressStore.load(target)
→ SourceResolver.resolve(target)
→ choose exact/fallback resume decision
→ PlaybackRequest(target, ResolvedVideo, resume)
→ Media3 service/controller
→ app player route renders controls/surface
```

**Failure flow:**
- source unavailable/access lost: show typed retry/back action while keeping Library row and Progress untouched;
- resolution failure does not delete binding/asset/media automatically;
- playback decoder/content failure is surfaced separately from storage/source absence.

**Minimal UI only:**
- play/pause;
- seek sufficient to prove progress;
- back to Library;
- no polish/queue/full settings expansion.

**RED tests:**
- navigation/app route state contains only MediaId/scalars and survives Activity recreation without persisting runtime content;
- selecting item resolves before playback;
- access failure preserves Library card and progress;
- returning from player does not create duplicate Media/Library entries;
- Activity recreation reconnects to existing service/session rather than creating a second player owner.

**Agent-owned focused gate:**
```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

**User-owned acceptance gate:**
```powershell
# Prefer the new/affected app orchestration instrumentation class filter once concrete classes exist.
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
```

Do not request `verifyArchitecture` unless this task changes a project dependency/build boundary; Task 10 owns the next scheduled architecture checkpoint.

**Exit criteria:** the user-visible path from Library card to playing/resuming MP4 works through all intended boundaries.

**Commit checkpoint:** `app: compose local library to playback vertical slice`

---

## Task 10 — Idempotency, Rescan and Failure-Safety Characterization

**Purpose:** Prove the architecture remains correct before process-death closure and before future reconciliation breadth is added.

**Tests to add across domain/data/storage/ingestion/app:**

### Same locator rescan

```text
scan 1 → Media M / Binding B / Asset A
scan 2 same exact current locator → reuse M / B / A
```

Assert:
- no duplicate Media;
- no duplicate active Library membership epoch;
- no duplicate built-in Local Source;
- current Asset observation/provenance may refresh safely.

### Incomplete traversal

Provider returns positives then `EXTRA_LOADING`/error:
- positives may be committed;
- ScanRun is not authoritative complete;
- no existing Asset or Library row becomes missing/deleted.

### Cancellation/process interruption

- cancel worker during traversal/positive batching;
- committed positive rows remain valid;
- run ends interrupted/cancelled or is recoverably nonterminal;
- next worker attempt starts a fresh ScanRun;
- no negative reconciliation is published.

### Access revoked

- resolver/scan reports access failure;
- Library membership remains;
- Progress remains;
- no infinite WorkManager retry.

### Locator changed / rename-move not yet solved

Characterize explicitly:
- first slice does **not** promise rename/move identity continuity when exact locator evidence changes;
- test prevents accidental filename/hash matching from silently becoming canonical policy;
- record this as deferred breadth owned by `Q-ID-001/Q-REC-001`, not as a defect in first-slice acceptance.

**Agent-owned focused pre-check:**
```powershell
.\gradlew.bat :core:domain:test --no-daemon
```

**User-owned checkpoint gate:**
```powershell
.\gradlew.bat :data:connectedDebugAndroidTest :storage:local:connectedDebugAndroidTest :ingestion:local:connectedDebugAndroidTest :app:connectedDebugAndroidTest verifyArchitecture --no-daemon
```

Prefer class filters for newly added characterization tests when they can preserve the same checkpoint evidence.

**Exit criteria:** reruns, interruption and access loss cannot corrupt canonical identity/user state.

**Commit checkpoint:** `test: characterize first-slice rescan and interruption safety`

---

## Task 11 — Real Process-Death / Persisted-SAF End-to-End Evidence

**Purpose:** Close the actual §13.1 proof on a device/emulator, not merely through same-process mocks.

**Preferred harness:** a repository-owned Bash/PowerShell script orchestrates **two separate app instrumentation invocations** with an external `adb shell am force-stop` between them. This proves real process reconstruction without turning `:benchmark` into a functional-test module and without killing the harness mid-assertion. Do not fake process death by only recreating the Activity.

Recommended evidence shape:

```text
instrumentation Phase A
  → drives real UI/SAF flow
  → records expected MediaId/progress into test-evidence storage only
  → exits
external script
  → am force-stop target package
instrumentation Phase B (fresh process)
  → reads expected test evidence
  → queries through app/domain composition + UI
  → asserts same canonical state/resume
```

The test-evidence file is assertion input only; production code must not read it and no production bypass/deep link is introduced.

**Fixture:**
- one tiny self-owned/licensed MP4 test fixture;
- place it in a DocumentsUI-visible test folder through test setup/adb/harness;
- record fixture provenance in test docs;
- no network download required by the app.

**Two-phase proof:**

### Phase A — establish durable state
1. clean install/test state;
2. launch app;
3. select test folder through actual DocumentsUI/SAF flow;
4. verify read grant is persisted;
5. wait for WorkManager scan completion;
6. assert one Library item with captured `MediaId`;
7. open/play MP4;
8. seek to a known non-zero position;
9. pause and wait for immediate progress checkpoint;
10. externally force-stop `app.universalmedia`.

### Phase B — reconstruct from durable state
1. relaunch target process;
2. assert same Library item / same app-owned `MediaId` exists;
3. assert no scan is required merely to reconstruct Library truth;
4. open item;
5. re-resolve current content from persisted SourceBinding/Asset/SAF locator/grant;
6. assert playback resumes within a documented tolerance of the paused checkpoint;
7. assert no duplicate Media/Binding/Asset/Library row was created.

**If fully automated DocumentsUI selection is unstable on API 23 or API 37:**
- keep deterministic lower-layer automated tests;
- add a repository-owned manual/adb device proof protocol as an explicit gate rather than weakening the requirement or inserting a test-only production bypass;
- do not add a production deep link/debug API that grants storage authority behind the picker.

**Device matrix:**
- primary: API 23/default instrumentation lane for minSdk compatibility where APIs exist;
- primary modern: Android 17 / SDK 37.0 `google_apis` lane used by current CI;
- real physical device when available for final evidence parity with bootstrap practice.

**User-owned gate:** run only the repository-owned two-phase process-death harness produced by this task. The agent prepares the exact command/protocol and stops for returned evidence. Do **not** rerun the full bootstrap verifier here; Tasks 12/13 own the broad quality/closure gates.

**Exit criteria:** real force-stop/restart demonstrates Library + source resolution + progress restoration with persisted SAF access.

**Commit checkpoint:** `test: prove first local slice across process restart`

---

## Task 12 — Security, Performance and Resource-Lifetime Audit

**Purpose:** Ensure the working slice did not quietly violate the foundation while chasing end-to-end success.

### Security checks
- only MainActivity exported;
- playback service explicitly not exported;
- no INTERNET permission;
- no broad storage permission;
- production logs contain no full user path, full content URI, token/cookie;
- malformed/unopenable content returns typed failure;
- persisted grants are read-only for first slice unless a later write use case is explicitly approved.

### Performance checks
- scanner has no `DocumentFile`/per-item tree re-search loop;
- traversal is iterative/cycle-safe and provider-controlled rows are defensively bounded; a safety budget yields incomplete coverage, never false completion;
- WorkManager input/logging contains RootId/safe scalars rather than raw SAF access descriptors;
- traversal batch size and concurrency are bounded;
- full recursive scan is not one Room transaction;
- Library screen reads a query projection rather than N+1 DAO calls;
- progress writes are bounded/coalesced;
- Media3 has exactly one player owner in service;
- DB and service/player are process-wide where semantically required and properly released on process/service teardown.

### Measurement
- run existing startup Macrobenchmark to characterize regression from bootstrap baseline if device lane is available;
- add a scan characterization test/benchmark for at least small and moderately sized synthetic trees before declaring scanner shape stable;
- record measurements, but do not invent pass/fail numeric thresholds without baseline evidence.

### StrictMode / debug diagnostics where practical
- no obvious main-thread Room/storage traversal;
- no leaked file descriptors/cursors;
- query cursors/resources closed deterministically;
- no unbounded coroutine scope owned by a Composable.

**User-owned quality checkpoint:**
```powershell
.\scripts\verify-fast.ps1
.\scripts\verify-security-baseline.ps1
.\gradlew.bat verifyArchitecture :app:verifyFoundation --no-daemon
```

Connected, benchmark/profile and device gates defined by the implemented harness are also user-owned. The agent should hand them off as the smallest coherent batch after focused local diagnostics are green.

**Exit criteria:** no architecture/security/performance regression requiring a workaround.

**Commit checkpoint:** `quality: audit first local slice boundaries and runtime cost`

---

## Task 13 — Full Verification, Documentation and Foundation Handoff

**Purpose:** Close the tranche only with executable evidence, then update the living foundation without creating a parallel source of truth.

**Files:**
- Update in place: `docs/foundation/android-universal-media-app-foundation.md`
- Update relevant engineering/security docs only when implementation changed an actual baseline.
- Keep this plan as implementation history; do not create `docs/state/` or a second handoff document.

**Full verification — user-owned closure gate by default:**
- all JVM/unit tests not already covered by an unchanged reusable PASS;
- all relevant Android instrumented tests;
- `verifyArchitecture`;
- `:app:verifyFoundation`;
- `verifyFast`;
- security baseline;
- release-like build;
- first-slice real device/process-death proof;
- startup Macrobenchmark characterization when runnable;
- clean-checkout CI equivalent lanes.

Before handoff, the agent must make focused local checks green, self-review the diff, then emit the exact closure command batch. It must not auto-run these broad gates unless the user explicitly delegates them back.

**Foundation update rules:**
- Current Control Block records only evidence actually produced;
- §13.1 can be marked complete only after the real restart proof succeeds;
- any implementation detail that stayed compatible with provisional decisions does not require a new Q record;
- if an invariant proved impossible, reopen the exact owning `Q-*`, document evidence, revise the decision, then amend implementation — never hide contradiction with an adapter/workaround.

**Possible next concrete action after closure:** select exactly one next breadth tranche based on evidence, likely one of:
- rename/move/missing reconciliation;
- next local media shape;
- Library intent UX;
- reader slice;
- metadata slice.

Do not preselect it in code before first-slice evidence exists.

**Commit checkpoint:** `docs: close first local vertical slice evidence`

---

# 3. Expected Dependency / Runtime Flow After This Plan

```text
                               :app
                    composition / navigation
                  /        |         |        \
                 /         |         |         \
                v          v         v          v
       :feature:library  scan wiring  source wiring  playback wiring
                            |           |              |
                            v           v              v
                    :ingestion:local :source:local :playback:media3
                            |           |              |
                            |           v              v
                            |       :source:api     :playback:api
                            |           ^              ^
                            |           |              |
                  domain ports only     |       app-supplied progress sink
                            |           |              |
             +--------------+-----------+--------------+
             |              |                          |
             v              v                          |
           :data       :storage:local                  |
      Room/transactions SAF observation/access         |
             ^              ^                          |
             +------ :core:domain / :core:model -------+
```

Important: the arrows above show **runtime composition/data flow**, not permission for new direct Gradle edges. In particular:

- `:ingestion:local` receives data/storage implementations only through domain contracts supplied by `:app`;
- `:source:local` receives the data-backed catalog and storage-backed local-access port through constructor injection; it does not depend directly on either implementation module;
- `:playback:media3` receives a playback-api progress sink supplied by `:app`; it does not import `:data`;
- `:feature:library` receives UI state/callbacks only.

Existing Q-MOD allow-list remains authoritative.

---

# 4. Self-Review Pass — Gaps Found and Plan Corrections

This section records the review of the plan itself so implementers do not rediscover the same traps.

## SR-1 — WorkManager custom injection initially risked accidental lazy initialization

**Gap:** A first draft assumed `Configuration.Provider` could customize the default eager initializer. Current Android docs require removal of the default initializer for the custom provider to take effect; that becomes on-demand initialization and can delay crash/force-stop rescheduling behavior.

**Correction:** Task 4 removes the default initializer **and explicitly initializes WorkManager in `Application.onCreate()`** with a custom `WorkerFactory`. This retains eager application-start initialization plus constructor injection.

**Status:** resolved in plan.

## SR-2 — KSP research initially pinned 2.3.10, but current official release is 2.3.12

**Gap:** Kotlin quickstart examples can lag current release notes.

**Correction:** Task 0 uses current KSP 2.3.12 as the candidate and requires real compile verification against Kotlin 2.4.20/AGP 9.2.1 before production work proceeds.

**Status:** resolved in plan.

## SR-3 — Playback service needs Progress persistence without violating module graph

**Gap:** `:playback:media3` is not allowed to depend directly on `:data`/Room and currently does not depend on `:core:domain`.

**Correction:** define a narrow playback-api progress event/sink; `:app` supplies an adapter that delegates to the domain/data progress port. The Android-created service obtains the typed dependency from the app-owned process graph, not a static global registry.

**Status:** resolved; implementation must prove this wiring remains simple. If it requires broad cross-module APIs, reopen Q-MOD/Q-API rather than adding a hidden service locator.

## SR-4 — Local source resolution also needs storage access without a forbidden implementation edge

**Gap:** naïvely adding `:source:local → :storage:local` would modify the reviewed graph.

**Correction:** use a narrow stable local-locator access contract and app constructor wiring. Data provides binding/asset locator facts; storage-local provides access validation/openability; source-local orchestrates them without importing either implementation type.

**Status:** resolved at design level; Task 6 has a hard stop if the seam cannot stay narrow.

## SR-5 — Filename could accidentally become canonical title/identity

**Gap:** the Library needs a label before metadata exists, encouraging filename-derived canonical metadata.

**Correction:** representation display name is an explicit fallback projection/evidence only. Stable UI key/navigation remains `MediaId`.

**Status:** resolved.

## SR-6 — “Reuse identity” could accidentally overclaim rename/move support

**Gap:** first scan + rescan must prove ID reuse, but Q-ID/Q-REC require conservative multi-evidence matching for relocation.

**Correction:** first-slice acceptance guarantees reuse only for the same exact current locator evidence. Rename/move continuity is deliberately characterized as deferred; no filename/hash shortcut is allowed.

**Status:** resolved.

## SR-7 — Completed scan semantics could tempt premature negative reconciliation

**Gap:** a completed full-root scan is potentially eligible for absence authority, but implementing `MISSING`/move/retirement would expand scope substantially.

**Correction:** persist complete scope/outcome faithfully but first slice does not invoke negative lifecycle transitions. This is conservative: stale availability may remain until the reconciliation tranche, but user state cannot be destructively corrupted.

**Status:** resolved; do not mislabel stale representation as confirmed available if access validation later fails.

## SR-8 — Process death cannot be proven by Activity recreation

**Gap:** same-process instrumentation can pass while DB/grant/service reconstruction is broken.

**Correction:** Task 11 requires an external/separate-process harness or explicit device proof using real force-stop/relaunch and actual SAF persisted permission. No test-only production bypass.

**Status:** resolved; automation details remain implementation work.

## SR-9 — Media3 official samples often export the media service

**Gap:** copying sample manifest would violate the repo security invariant of one exported component.

**Correction:** first slice uses explicit same-app ComponentName/SessionToken and `android:exported="false"`; external media ecosystem is deferred.

**Status:** resolved.

## SR-10 — Foreground playback permissions change the Phase-0 manifest baseline

**Gap:** Media3 background/continuous playback legitimately needs foreground-service capability, while bootstrap was deny-by-default.

**Correction:** add only media-playback FGS permissions required by the actual service and update security documentation/verification evidence accordingly. Keep Internet and broad storage forbidden.

**Status:** planned; verify on API 23 and target 37 before closure.

## SR-11 — Room schema could grow into the entire foundation too early

**Gap:** Q-PER lists broad table families and an implementer may create all future tables immediately.

**Correction:** Task 2 materializes only the first-slice families. No Grouping/Unit/History/reader/metadata tables without a concrete consuming use case.

**Status:** resolved.

## SR-12 — Progress write ordering can corrupt backward seeks

**Gap:** blindly applying “latest highest position wins” prevents legitimate rewind; asynchronous writes can still arrive out of order.

**Correction:** serialize/state-revision checkpoint writes without imposing monotonic position. Newer checkpoint order wins even when its position is lower.

**Status:** resolved.

## SR-13 — A scan implementation can pass small tests but become O(n²)

**Gap:** SAF wrapper lookup patterns are deceptively simple and have caused real large-library performance problems.

**Correction:** direct directory enumeration, bounded batches, no parent re-search per child, synthetic larger-tree characterization before closure.

**Status:** resolved.

## SR-14 — WorkManager class renames can strand persisted requests

**Gap:** WorkManager persists Worker class names.

**Correction:** treat `LocalScanWorker` class path as a persisted runtime compatibility surface once released; if renamed later, custom WorkerFactory must map legacy names or migration/cleanup must be explicit.

**Status:** added as maintenance guardrail.

## SR-15 — Library could become coupled to `WorkInfo`

**Gap:** showing scan status directly from WorkManager state would contradict Q-RUN after process/scheduler edge cases.

**Correction:** UI status is primarily reconstructed from durable `ScanRun`/root data; scheduler telemetry is supplemental only.

**Status:** resolved.

## SR-16 — “Exact resume” across process death needs a defined tolerance

**Gap:** active playback checkpoints are intentionally coalesced, so arbitrary hard-kill cannot promise zero lost milliseconds.

**Correction:** deterministic closure proof pauses and persists immediately before force-stop; active-kill semantics explicitly allow loss up to the checkpoint interval. Tests document a small playback-position tolerance.

**Status:** resolved.

---

## SR-17 — Stable Lifecycle choice must not silently raise compileSdk

**Gap:** current Lifecycle 2.12 alpha Compose artifacts require compileSdk 37.1, while this repository deliberately locks compileSdk 37 / SDK package `android-37.0`.

**Correction:** if lifecycle-aware Compose Flow collection is introduced, Task 0 uses stable Lifecycle 2.11.0. No alpha dependency may force a compileSdk/platform-package change inside this slice.

**Status:** resolved in plan.

## SR-18 — `MediaSessionService` needs its service-interface action even when non-exported

**Gap:** removing all intent filters to keep the playback service private would conflict with Media3's required service declaration contract.

**Correction:** declare only `androidx.media3.session.MediaSessionService` on the service and keep `android:exported="false"`. Same-app explicit `SessionToken(ComponentName)` remains the client path; external/legacy browser and media-button components stay out of scope.

**Status:** resolved in Task 7.

## SR-19 — Functional process-death proof should not misuse the benchmark module

**Gap:** using `:benchmark` as the primary functional harness couples correctness proof to performance tooling and still complicates target-private state assertions.

**Correction:** Task 11 prefers an external repository script that runs Phase-A instrumentation, force-stops the target, then runs Phase-B instrumentation in a fresh process. Test-only evidence can carry expected IDs/positions across invocations; production code never consumes it. `:benchmark` remains performance-focused.

**Status:** resolved in plan.

## SR-20 — The Q-PER target bridge and “no unused tables” rule initially conflicted

**Gap:** excluding `media_unit` entirely while claiming a referentially complete `ConsumptionTargetRef` bridge would either remove `UnitTarget` from the locked contract or leave an unenforced polymorphic reference.

**Correction:** schema v1 includes `media_unit` solely to provide the already-approved Unit FK side of the target bridge. The standalone MP4 slice creates no Unit rows and does not implement Unit UX/semantics beyond the existing stable identity/target shape.

**Status:** resolved in Task 1/2.

---

## SR-21 — Root registration ownership could leak SAF into app/feature code

**Gap:** saying “app takes the persistable permission” too literally encourages `ContentResolver`/URI handling to spread through composition code, even though Q-MOD keeps Android storage mechanics in `:storage:local`.

**Correction:** `:app` owns only the ActivityResult launcher/lifecycle. It hands the raw result immediately to a narrow storage adapter, which performs the grant/validation and returns platform-free registration evidence. `:feature:library` never sees a URI.

**Status:** resolved in Task 3/5.

## SR-22 — SAF providers are untrusted graphs, not guaranteed well-formed trees

**Gap:** a recursive “walk the tree” implementation can stack-overflow, loop on pathological/provider-specific directory graphs, retain huge provider-controlled values, or silently declare completion after a defensive abort.

**Correction:** direct traversal is iterative, has a run-local visited-directory evidence set, validates nullable/provider-controlled columns, keeps batches/concurrency bounded, and may stop at a defensive observation budget only as typed incomplete coverage. Raw locators are never logged.

**Status:** resolved in Task 3/12.

## SR-23 — WorkManager input can accidentally become a second storage/secret channel

**Gap:** placing tree URIs or serialized root descriptors in WorkManager `Data` duplicates mutable locator state, increases leak/logging risk, and lets a delayed Worker act on stale access facts.

**Correction:** enqueue only `RootId` and scheduler-safe scalars. At execution time the Worker reloads the current root descriptor/config generation from canonical storage, then starts a fresh ScanRun.

**Status:** resolved in Task 4.

## SR-24 — Android-instantiated MediaSessionService needs a concrete non-locator dependency seam

**Gap:** “obtain from app graph” was underspecified and could turn into a service locator or direct dependency on `:app`.

**Correction:** a narrow interface owned by `playback:api` is implemented by `UniversalMediaApplication`; the service reads one immutable playback dependency bundle during `onCreate()`. It cannot query arbitrary app services or expose the whole graph.

**Status:** resolved in Task 7.

## SR-25 — A navigation framework would be premature breadth for a two-screen proof

**Gap:** adding Navigation Compose during the first slice creates route/back-stack/API surface unrelated to the architecture proof and can encourage serializing runtime playback objects into routes.

**Correction:** first slice uses app-owned stable `MediaId` route/state only and the smallest recreation-safe state holder. Navigation infrastructure is deferred until multiple real destinations/back-stack semantics require it.

**Status:** resolved in Task 9.

## SR-26 — Exact-locator idempotency needs a concurrency invariant without turning the locator into identity

**Gap:** same-root WorkManager serialization reduces duplicate scans, but replay/race at the transaction boundary can still materialize duplicate rows if “lookup then insert” is not protected. Simply making URI/documentId the canonical key would violate Q-ID.

**Correction:** Task 2 requires a root/provider-scoped current-locator observation invariant enforced transactionally/with an appropriate uniqueness strategy. A collision means “this current observation is already materialized; reuse its app-owned IDs”, not “the locator is the Media/Asset ID”.

**Status:** resolved in Task 2.

# 5. Implementation Stop Conditions

Stop the current task and reopen the owning foundation decision instead of working around it when any of these happens:

1. Correct source resolution requires `ResolvedContent` to become durable canonical identity.
2. Correct local playback appears to require `feature:library` importing Media3/SAF/Room.
3. Worker construction appears to require a global mutable service locator.
4. Same-root scanning cannot remain restart-safe without treating `WorkInfo` as ScanRun truth.
5. A failed/partial scan seems to require deleting/missing unseen Assets to make UI look correct.
6. Root/Media/Asset identity can only be made stable by deriving IDs from URI/path/documentId/hash.
7. Process restart reconstruction requires persisting Media3 objects or runtime `ResolvedContent`.
8. Room transaction correctness appears to require one transaction around the full recursive scan.
9. A broad storage or INTERNET permission appears “easier” than the currently locked capability boundary.
10. Existing module allow-list must change to make a concrete seam work and no narrow port can express it cleanly.

At a stop condition, collect the smallest executable evidence, identify the owning `Q-*`, revise foundation reasoning first, then update this plan if the decision changes.

---

# 6. Definition of Done for §13.1

The first local vertical slice is complete only when **all** statements are true:

- [ ] A user can register a SAF tree with persisted read access.
- [ ] A direct `DocumentsContract` scan discovers one provider-declared `video/mp4`.
- [ ] The app creates app-owned Media/Binding/Asset IDs that are not platform locators.
- [ ] Rescanning the exact same locator reuses canonical identity without duplication.
- [ ] The recognized Media is auto-admitted and visible in Library.
- [ ] Library feature code has no Room/SAF/WorkManager/Media3 implementation imports.
- [ ] App selection resolves MediaTarget → SourceBinding → Asset → ephemeral ResolvedVideo.
- [ ] Media3 service owns the player/session and remains non-exported.
- [ ] Video progress persists as typed `positionMs` + explicit completion/context.
- [ ] Progress writes are bounded/coalesced and backward seek is valid.
- [ ] Failed/incomplete/cancelled scans never produce negative/deletion authority.
- [ ] Access loss does not remove Library membership or Progress.
- [ ] A real force-stop/relaunch reconstructs Library and resolves the same canonical item from durable state.
- [ ] Reopening playback resumes from the persisted checkpoint within documented tolerance.
- [ ] Security baseline stays free of broad storage and INTERNET permission, with only MainActivity exported.
- [ ] Room schema v1 is exported/committed and canonical DB has no destructive fallback.
- [ ] `verifyArchitecture`, fast/security/release-like gates and relevant device tests pass.
- [ ] Startup/scan runtime is characterized; no invented numeric threshold is claimed.
- [ ] Foundation Current Control Block is updated in place with real evidence and exactly one next action.

Only then may broader V1 feature implementation be unlocked.
