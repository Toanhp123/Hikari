# Reader Image Continuity Cache / RICC-v1 Implementation Plan

**Status:** READY FOR IMPLEMENTATION — frozen after R2 + R2.2 coverage audit and plan self-review.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persistent, quota-bounded Reader image cache with bounded sliding-window prefetch so retained pages survive viewport eviction and chapter revisits, and their encoded bytes are reused after process recreation once existing Reader semantics reconstruct/reacquire the chapter manifest, without avoidable image-network refetch.

**Architecture:** Preserve HES-v1 and `:reader:engine` unchanged. `:reader` owns image identity, manifest/session semantics, single-flight, working-set policy, source lanes, and global content-fetch arbitration ports; `:downloads` owns durable automatic-cache policy, unified document+image quota admission, image metadata/store orchestration, and progress-aware eviction; `:storage:room` and `:storage:files` remain adapters; `:feature:reader` emits viewport/presentation facts and renders a stable asset request; `:app` composes Settings/security/network/Coil adapters and process-scoped coordinators.

**Tech Stack:** Kotlin 2.4.10, JDK 17, Gradle 9.5, AGP 9.3.0, coroutines/Flow, Hilt, Room schema 12, OkHttp 5.3.0, Coil 3.5.0, Compose, Robolectric, Room instrumentation tests.

**Spec:**
- `docs/superpowers/specs/2026-08-31-reader-image-continuity-cache-ricc-v1-design.md` (RICC-v1 R2)
- `docs/superpowers/specs/2026-08-31-reader-image-continuity-cache-ricc-v1-r2.2-hardening.md` (normative corrections; wins where it replaces/narrows R2)
- `docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md` (frozen HES-v1 constitution)

## Global Constraints

- `:reader:engine` receives no RICC code, storage fact, Android type, network effect, Compose, Coil, Room, Settings, or filesystem dependency.
- HES-v1 remains the only release/route reasoner; cache presence never selects another release.
- Preserve exactly 17 production modules plus `:benchmark`; no `:reader:assets` module is added.
- Room advances exactly `11 -> 12` through non-destructive `MIGRATION_11_12`; schemas 1..11 remain historical exports.
- `ReaderDocumentSanitizer.MAX_BLOCKS = 2_000` remains the defensive manifest cardinality ceiling.
- Generic Coil disk cache remains disabled for Reader assets; RICC is the sole semantic persistent image-byte cache.
- Current image-bearing `ReaderDocument` values remain non-persistable through `ReaderDocumentBlobCodec`; RICC persists image bytes, not a replacement semantic document format.
- Full sanitized source image `stableId` must survive separately from the current truncated UI block ID; current document fingerprint semantics must not change merely because the full value is retained.
- `TRUSTED_STABLE` is explicit source contract only; a non-blank `stableId` never implies content immutability.
- RICC-v1 `sourceNamespace` is not configurable and has exactly one derivation: the canonical `PluginId.value` of the exact `ReaderDocumentSource` that produced the selected `ReaderDocument`. Plugin/package/runtime version, display name, source label, host, adapter class name, and delivery locator never participate. A normal plugin update keeps this namespace stable. If a source intentionally breaks the identity semantics behind that canonical plugin identity, the change requires a reviewed RICC key/namespace-version migration (normally a key-schema revision); implementation must never silently suffix or replace the namespace with a version.
- Current authenticated session records do not expose a stable non-secret account identity. Production `ACCOUNT_SCOPED` image caching therefore fails closed to `NON_PERSISTENT_PRIVATE` until such an identity exists; public caching is allowed only when the plugin manifest explicitly declares it.
- Local presence uses exactly `UNKNOWN`, `LOCAL_AVAILABLE`, `LOCAL_MISSING`, `LOCAL_UNAVAILABLE`; unavailable authority is never converted to miss.
- One process-global `ContentFetchArbiter` owns admission for participating Reader document, Reader image, and Downloads document fetches. No participating path retains the old Reader global foreground/prefetch semaphores.
- Source/plugin document ordering is acquired before global admission. Image CDN fetches do not acquire the plugin source lane.
- Arbiter admission covers the complete network-body lifetime and cannot nest another arbiter admission.
- Explicit user downloads are `USER_WORK`; active `USER_WORK` is never cancelled merely to admit Reader visible work.
- Automatic-cache quota is one budget across automatic Reader document blobs plus RICC image blobs. Explicit downloads are outside this budget.
- First consumed promotion is immediate in memory and promptly durable; generic access touches are coalesced.
- Visible rendering never waits for quota reconciliation, eviction planning, full Room scans, next-chapter planning, or speculative queue drain.
- Storage/cache failure never makes otherwise-valid remote bytes unreadable.
- RICC persistent timestamps use the injected `app.openstory.common.Clock`; fairness, retry/throttle scheduling, and coroutine race ordering use `MonotonicClock`/coroutine scheduler time. New RICC code must not call `System.currentTimeMillis()` or use wall clock to order runtime races.
- RICC image-asset `ENOSPC`/physical-full persistence recovery is bounded: evict only eligible unprotected automatic-cache victims, retry the image blob write exactly once, then render transiently if persistence still fails. Existing automatic document writes remain best-effort and share the same authority/quota but are not expanded into a new multi-retry storage protocol.
- WorkManager child-constraint debt and non-participating background/plugin workloads remain outside RICC-v1.

## Frozen Implementation-Policy Values

These values are implementation-plan choices, not HES constants. The total fetch count is anchored to the current Reader maximum of two foreground plus one prefetch remote operations; the remaining values are conservative bounded V1 choices and are test-visible.

```text
MAX_TOTAL_CONTENT_FETCHES = 3
RESERVED_CRITICAL_INTERACTIVE_SLOTS = 1
MAX_NEXT_CHAPTER_SPECULATIVE_FETCHES = 1
USER_WORK_AGING_THRESHOLD = 2000 ms
MAX_READER_ASSET_BYTES = 16 MiB
MAX_TRANSIENT_ASSET_RETRIES = 1
TRANSIENT_ASSET_RETRY_DELAY = 250 ms
AUTOMATIC_CACHE_HIGH_WATERMARK = 10000 bp of configured quota
AUTOMATIC_CACHE_LOW_WATERMARK = 9000 bp of configured quota
ASSET_ACCESS_TOUCH_INTERVAL = 300000 ms
MAX_ENOSPC_EVICTION_VICTIMS = 32
COIL_PREWARM_BEHIND = 2 assets
INTERACTIVE_CURRENT_AHEAD = 4 assets
METERED_NEAR_AHEAD_MAX = 2 assets
NEXT_CHAPTER_OPENING_BURST = 4 assets
APPROACHING_END = 8000 bp
NEAR_END = 9000 bp
APPROACHING_END_TRANSITION_FRONTIER = 1 asset
NEAR_END_TRANSITION_FRONTIER = 4 assets
RECENT_COMMITTED_HISTORY_DEPTH = 2 chapters
```

`MAX_READER_ASSET_BYTES = 16 MiB` is deliberately a hard encoded-byte ceiling, not a statement that typical pages are that large. Every remote body is counted while read; the implementation never trusts `Content-Length` alone.

## Normalized Cross-kind Automatic-cache Retention Order

The R2 image-only eviction order is extended here so unified byte accounting also has deterministic document-vs-image victim selection. Least valuable is listed first:

```text
STALE_INVALIDATED
COLD_SPECULATIVE_IMAGE
WARM_SPECULATIVE_IMAGE
TRANSITION_SPECULATIVE_IMAGE
CURRENT_AHEAD_SPECULATIVE_IMAGE
WARM_DOCUMENT
CONSUMED_IMAGE_HISTORY
PROGRESS_PROTECTED_DOCUMENT
RECENT_IMAGE_HISTORY_2
RECENT_IMAGE_HISTORY_1
ACTIVE_CONSUMED_IMAGE
ACTIVE_INTERACTIVE_IMAGE
ACTIVE_READ_LEASE (never normal victim)
```

Rules:
- `WARM_DOCUMENT` includes ordinary automatic Reader document cache entries not protected by current progress.
- `PROGRESS_PROTECTED_DOCUMENT` is advisory elevated retention, not a permanent pin.
- Explicit downloads never enter this order.
- A progress-protected release does not upgrade unconsumed speculative image assets.
- Physical emergency may degrade protection from the bottom of semantic value upward, but an active read lease is never unlinked mid-read.

---

## Master Mapping Before Implementation

| Current master fact | Exact location | Plan consequence |
|---|---|---|
| Image block keeps only truncated UI ID + URL | `reader/.../document/ReaderDocument.kt`, `ReaderDocumentSanitizer.kt` | Task 1 preserves `stableAssetId` without changing existing fingerprint semantics. |
| Plugin protocol only promises stable ID across expiring URL changes | `docs/plugin-sdk/javascript-runtime.md`, `plugins/api/.../PluginManifest.kt` | Task 1 adds explicit content-identity and persistence declarations; defaults fail closed. |
| Every `ReaderDocumentSource` already exposes canonical `pluginId` | `reader/.../content/ReaderDocumentSource.kt` | Tasks 2/10 derive RICC `sourceNamespace` exactly from the **exact producing source's** `PluginId.value`; package/plugin version and display metadata never alter durable identity. |
| Image-bearing document is not document-cache persistable | `ReaderDocument.isLocalPersistable` | RICC does not change document codec. |
| N+1 prefetch drops successful document | `routing/PrefetchCoordinator.kt`, `ReaderRouteCoordinator.executePrefetch()` | Task 10 adds a non-commit artifact handoff. |
| Reader limiter owns source lane, probes, and two global semaphores | `routing/ReaderSourceExecutionLimiter.kt` | Task 3 splits source/probe semantics from one shared arbiter. |
| Downloads fetch bypasses Reader global limits | `downloads/.../ReaderDownloadContentSource.kt` | Task 3 routes explicit download fetch through source lane + `USER_WORK` arbiter. |
| Document cache quota is fixed constructor default and write calls `emptySet()` progress protection | `downloads/.../DownloadAwareReaderDocumentStore.kt` | Task 6 removes per-write private quota enforcement and uses the unified authority. |
| Settings has `automaticCacheQuotaBytes` | `settings/.../AppSettings.kt`, `SettingsDefaults.kt` | Task 7 observes Settings in app composition and updates the unified budget epoch/quota. |
| Reading progress truth already exists | `reader/.../progress/ReadingProgressRepository.kt` | Task 7 projects release IDs from this repository into unified quota/progress/write-authority policy; no shadow progress store. |
| Room is schema 11 | `storage/room/.../OpenStoryDatabase.kt` | Task 4 adds schema 12 and migration. |
| Current chapter blobs are whole-byte objects | `downloads/.../blob/BlobModels.kt` | RICC gets a separate image blob primitive and read lease; existing document blob API is not stretched into image streaming. |
| Durable wall-clock abstraction already exists | `core/common/.../Clock.kt` | Task 2 reuses `Clock` for persisted recency and adds a pure-JVM `MonotonicClock` beside it for runtime ordering/throttling. |
| Physical write admission already uses app-private usable-space reserve | `downloads/.../reconcile/StorageReconciliationService.kt`, `storage/files/.../FileBlobInventory.kt` | Tasks 5–8 reuse the reserve signal, add typed `ENOSPC`, bounded unprotected relief, and one persistent retry without moving filesystem policy into Reader. |
| Reader image Coil disk cache is disabled and retry reloads document | `feature/reader/.../ReaderImagePage.kt` | Tasks 12–13 keep disk cache disabled and make retry page-local. |
| Feature Reader declares unused `:downloads` edge | `feature/reader/build.gradle.kts` | Task 13 removes it only after package/import gates prove no production usage. |
| Architecture guards hard-code 17 modules / Room 11 | `scripts/verify-current-architecture.sh`, `build-logic/.../ModuleGraphTest.kt` | Task 16 intentionally advances only schema guard to 12 and verifies the tightened feature edge. |

---

### Task 1: Preserve Full Image Identity and Add Explicit Plugin Cache Trust Contracts

**Files:**
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/manifest/PluginManifest.kt`
- Modify: `plugins/api/src/test/kotlin/app/openstory/plugins/api/manifest/PluginManifestTest.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/document/ReaderDocument.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/document/ReaderDocumentSanitizer.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/document/ReaderDocumentSanitizerTest.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentSource.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/content/PluginReaderDocumentSource.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/content/PluginReaderDocumentSourceTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/PrefetchCoordinatorTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt`
- Modify: `downloads/src/test/kotlin/app/openstory/downloads/reader/ReaderDownloadContentSourceTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderContentTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderImagePageTest.kt`
- Modify: `feature/reader/src/androidTest/kotlin/app/openstory/reader/ui/ReaderScreenTest.kt`
- Modify: `bundled-plugins/mangadex-content/manifest.json`
- Modify: `bundled-plugins/mangadex-content/README.md`
- Modify: `app/src/test/kotlin/app/openstory/di/MangaDexContentPackageTest.kt`
- Modify: `app/src/androidTest/kotlin/app/openstory/MangaDexContentContractIntegrationTest.kt`
- Regenerate: `app/src/main/assets/plugins/mangadex-content.osp` via `:app:packageMangaDexPlugin`
- Modify: `docs/plugin-sdk/javascript-runtime.md`

**Interfaces:**
- Produces: `ReaderImageIdentityContract`, `ReaderImageLocatorContract`, `ReaderImagePersistenceContract`, `ReaderImageSourcePolicy`, and required `ReaderBlock.ImagePage.stableAssetId`.
- Preserves: existing `ReaderBlock.ImagePage.id`, `imageUrl`, and document fingerprint behavior.
- Fail-closed rule: no source receives `TRUSTED_STABLE` merely from a non-blank stable ID or the appearance of a hash in that ID. A bundled source may receive it only through an explicit maintained manifest/adapter contract plus integration tests that make the upstream identity assumption reviewable.
- Source-namespace provenance: `ReaderDocumentSource.pluginId` is the sole canonical source identity later consumed by RICC. Task 1 does not add a plugin-configurable cache namespace or version-derived namespace.

- [ ] **Step 1: Write RED protocol, package, sanitizer, and fingerprint tests**

Add tests proving defaults fail closed; bundled MangaDex explicitly opts into a maintained trusted-stable/public contract; its integration fixture proves the emitted full stable ID is `chapter.hash + filename` and changes when either revision component changes; full stable ID survives sanitization; URL rotation does not change the existing Reader document fingerprint when stable IDs are unchanged.

```kotlin
@Test
fun `remote image cache trust is explicit and defaults fail closed`() {
    val capability = ReaderCapability(remoteImages = true, offlineDownload = false)
    assertEquals(ReaderImageIdentityContract.DELIVERY_STABLE_ONLY, capability.imageIdentity)
    assertEquals(ReaderImagePersistenceContract.NON_PERSISTENT, capability.imagePersistence)
}

@Test
fun `sanitizer preserves full stable asset id without changing ui id contract`() {
    val stable = "chapter-hash/page-001.png"
    val document = assertIs<DocumentValidationResult.Valid>(
        ReaderDocumentSanitizer().sanitize(
            ChapterDocumentDto(null, listOf(ImagePageBlockDto(stable, "https://node-a.example/p.jpg"))),
            allowRemoteImages = true,
        ),
    ).document
    val image = assertIs<ReaderBlock.ImagePage>(document.blocks.single())
    assertEquals(stable, image.stableAssetId)
    assertTrue(image.id.startsWith("image-0-"))
}
```

Also build two sanitized documents with the same `stableId` and different HTTPS locators and assert their existing document fingerprints remain equal.

- [ ] **Step 2: Run focused RED tests**

```bash
./gradlew :plugins:api:test :reader:testDebugUnitTest :app:testDebugUnitTest \
  --tests '*PluginManifestTest*' \
  --tests '*ReaderDocumentSanitizerTest*' \
  --tests '*PluginReaderDocumentSourceTest*' \
  --tests '*MangaDexContentPackageTest*' \
  --no-daemon
```
Expected: FAIL because the new capability enums/fields and `stableAssetId` do not exist.

- [ ] **Step 3: Add explicit manifest contracts and preserve the required stable ID**

In `PluginManifest.kt`:

```kotlin
@Serializable
enum class ReaderImageIdentityContract {
    DELIVERY_STABLE_ONLY,
    STABLE_ID_CHANGES_WITH_CONTENT,
}

@Serializable
enum class ReaderImageLocatorContract {
    MUTABLE_OR_UNKNOWN,
    LOCATOR_CHANGES_WITH_CONTENT,
}

@Serializable
enum class ReaderImagePersistenceContract {
    NON_PERSISTENT,
    PUBLIC,
    ACCOUNT_SCOPED,
}

@Serializable
data class ReaderCapability(
    val offlineDownload: Boolean = true,
    val remoteImages: Boolean = false,
    val imageIdentity: ReaderImageIdentityContract = ReaderImageIdentityContract.DELIVERY_STABLE_ONLY,
    val imageLocator: ReaderImageLocatorContract = ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN,
    val imagePersistence: ReaderImagePersistenceContract = ReaderImagePersistenceContract.NON_PERSISTENT,
) {
    init {
        require(!remoteImages || !offlineDownload) {
            "Remote image reader capability cannot declare offline download"
        }
        require(remoteImages || imageIdentity == ReaderImageIdentityContract.DELIVERY_STABLE_ONLY)
        require(remoteImages || imageLocator == ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN)
        require(remoteImages || imagePersistence == ReaderImagePersistenceContract.NON_PERSISTENT)
    }
}
```

Extend image blocks without a default for the full identity, so every direct constructor must consciously supply it:

```kotlin
data class ImagePage(
    override val id: String,
    val stableAssetId: String,
    val imageUrl: String,
) : ReaderBlock
```

`ReaderDocumentSanitizer` validates and stores the full sanitized `ImagePageBlockDto.stableId`, still derives the existing truncated presentation ID, and keeps `canonicalFingerprint()` unchanged: it hashes the current block type + generated UI ID and continues to exclude locator/full stable ID.

- [ ] **Step 4: Add fail-closed source policy propagation**

In `ReaderDocumentSource.kt`:

```kotlin
data class ReaderImageSourcePolicy(
    val identityContract: ReaderImageIdentityContract,
    val locatorContract: ReaderImageLocatorContract,
    val persistenceContract: ReaderImagePersistenceContract,
) {
    companion object {
        val FAIL_CLOSED = ReaderImageSourcePolicy(
            ReaderImageIdentityContract.DELIVERY_STABLE_ONLY,
            ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN,
            ReaderImagePersistenceContract.NON_PERSISTENT,
        )
    }
}

interface ReaderDocumentSource {
    val pluginId: PluginId
    val imageSourcePolicy: ReaderImageSourcePolicy
        get() = ReaderImageSourcePolicy.FAIL_CLOSED
    suspend fun fetch(release: ChapterRelease): ReaderSourceResult
}
```

`PluginReaderDocumentSource` overrides `imageSourcePolicy` from the installed manifest. The default getter prevents unrelated fake sources from silently gaining persistence trust and avoids forcing semantic changes into all existing text-only test doubles.

- [ ] **Step 5: Declare bundled MangaDex trusted identity as an explicit maintained integration contract**

Set its manifest reader capability explicitly to:

```json
{
  "offlineDownload": false,
  "remoteImages": true,
  "imageIdentity": "STABLE_ID_CHANGES_WITH_CONTENT",
  "imageLocator": "MUTABLE_OR_UNKNOWN",
  "imagePersistence": "PUBLIC"
}
```

`MangaDexContentPackageTest` asserts this exact declaration. This is a **source-adapter contract authored by Hikari**, not a runtime inference from arbitrary IDs. Extend `MangaDexContentContractIntegrationTest` with two at-home fixtures so:
- changing only `baseUrl` leaves the emitted stable ID unchanged;
- changing `chapter.hash` or page filename changes the emitted stable ID;
- the stable ID remains `${chapter.hash}/${filename}`.

The manifest declaration is therefore reviewable together with the adapter logic that constructs the identity. The fixture proves Hikari's adapter construction rule; it does **not** by itself prove an upstream byte-immutability guarantee. Treat the manifest opt-in as an explicit maintained Hikari source contract: the review that lands this declaration must cite the authoritative MangaDex@Home contract used to justify `chapter.hash + filename` as content-revision identity in `bundled-plugins/mangadex-content/README.md`. If that evidence is unavailable or later becomes invalid, do not merge/retain the opt-in: downgrade to `DELIVERY_STABLE_ONLY + NON_PERSISTENT` before release. `imageLocator` stays `MUTABLE_OR_UNKNOWN`; locator persistence safety is not inferred from URL shape.

**External source-contract evidence gate (not derived from Hikari source):** review the current MangaDex API documentation repository (`https://gitlab.com/mangadex-pub/mangadex-api-docs`) and MangaDex@Home reference implementation (`https://github.com/mangadex-network/mangadex-at-cloud`) when landing this opt-in. The public docs establish that at-home delivery is constructed from a rotating `baseUrl` plus chapter hash/page filename; the reference implementation itself uses the hash/filename path for cacheable image delivery. These are supporting operational semantics, not permission to infer trust for arbitrary providers. Record the reviewed revision/date in the bundled-plugin README next to the Hikari contract.

- [ ] **Step 6: Update SDK wording**

Document that historical “stable across expiring URL changes” is only the delivery-stability baseline. `STABLE_ID_CHANGES_WITH_CONTENT` is a stronger explicit plugin-author contract and must only be declared when the source can guarantee the ID/revision changes whenever logical image content changes. `LOCATOR_CHANGES_WITH_CONTENT` is a separate strong contract: a normalized locator identity must change whenever logical encoded image bytes can change. Persistence permission (`PUBLIC`/`ACCOUNT_SCOPED`) never substitutes for either identity-safety contract. Plugin authors do **not** configure a separate RICC `sourceNamespace`: Hikari derives it from the installed source's canonical `PluginId`. Plugin/package version changes therefore do not invalidate RICC keys by themselves; an intentional source-identity contract break requires a reviewed key/namespace migration rather than a silent namespace change.

- [ ] **Step 7: Regenerate the bundled package before package assertions**

```bash
./gradlew :app:packageMangaDexPlugin --no-daemon
```
Expected: `app/src/main/assets/plugins/mangadex-content.osp` is deterministically regenerated from the edited manifest/script.

- [ ] **Step 8: Run GREEN compile/regression tests**

```bash
./gradlew :plugins:api:test :reader:testDebugUnitTest :downloads:testDebugUnitTest :feature:reader:testDebugUnitTest :app:testDebugUnitTest \
  --tests '*PluginManifestTest*' \
  --tests '*ReaderDocumentSanitizerTest*' \
  --tests '*PluginReaderDocumentSourceTest*' \
  --tests '*PrefetchCoordinatorTest*' \
  --tests '*ReaderRouteExecutorAdaptiveTest*' \
  --tests '*ReaderDownloadContentSourceTest*' \
  --tests '*ReaderContentTest*' \
  --tests '*ReaderImagePageTest*' \
  --tests '*MangaDexContentPackageTest*' \
  --no-daemon
```
Expected: PASS and every direct `ImagePage` test fixture supplies a full stable identity.

- [ ] **Step 9: Run the bundled MangaDex Android contract gate**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.MangaDexContentContractIntegrationTest \
  --no-daemon
```
Expected: PASS for base-URL rotation stability, content-revision identity changes, and existing content-contract fixtures.

- [ ] **Step 10: Commit**

```bash
git add plugins/api reader downloads/src/test feature/reader bundled-plugins/mangadex-content \
  docs/plugin-sdk/javascript-runtime.md \
  app/src/test/kotlin/app/openstory/di/MangaDexContentPackageTest.kt \
  app/src/androidTest/kotlin/app/openstory/MangaDexContentContractIntegrationTest.kt \
  app/src/main/assets/plugins/mangadex-content.osp
git commit -m "reader: preserve explicit image asset identity"
```

---

### Task 2: Add Pure Reader Asset Identity, Manifest, Key Encoding, and Store/Delivery Ports

**Files:**
- Modify: `core/common/src/main/kotlin/app/openstory/common/Clock.kt`
- Create: `core/common/src/test/kotlin/app/openstory/common/ClockTest.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetIdentity.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetManifest.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetStorePort.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetDeliveryPort.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetFailure.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetRuntimePolicy.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetIdentityTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetManifestTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetRuntimePolicyTest.kt`

**Interfaces:**
- Produces all cross-module RICC value/port types used by Tasks 4–15; later tasks must not invent parallel store/delivery models.
- Consumes `ReaderImageSourcePolicy` from Task 1.
- Freezes `ReaderAssetSourceNamespace` as a typed projection of the exact producing `ReaderDocumentSource.pluginId`; later tasks consume this type and never derive namespace from package/plugin version or presentation metadata.
- Keeps Room/filesystem/Settings/Coil types out of `:reader`.
- Produces/reuses controlled time contracts: existing `Clock` for durable epoch timestamps and new `MonotonicClock` for runtime ordering; neither leaks Android time APIs into `:reader`.

- [ ] **Step 1: Write RED golden-vector, downgrade, and secrecy tests**

Cover:
- a synthetic source explicitly declaring `STABLE_ID_CHANGES_WITH_CONTENT` gets `TRUSTED_STABLE`; same full identity across URL rotation -> same key;
- current bundled MangaDex explicitly declaring `STABLE_ID_CHANGES_WITH_CONTENT + PUBLIC` resolves to `TRUSTED_STABLE`; a control source with the same `${hash}/${filename}`-looking ID but no manifest opt-in remains fail-closed;
- exact producing `PluginId` -> canonical `ReaderAssetSourceNamespace`; changing only plugin/package version, display name, host, or adapter class leaves the namespace/key unchanged, while changing canonical `PluginId` changes the namespace/key;
- no test/helper may inject an arbitrary raw namespace string into a Reader page key; construction goes through `ReaderAssetSourceNamespace.fromPluginId(...)`;
- source/variant/security scope changes -> different key;
- `LOCATOR_BOUND` includes a one-way **canonically normalized** locator fingerprint and locator change -> different key;
- locator normalization is conservative/deterministic: host case/default `:443` normalize equal, query change differs, fragment-only change is equal, and distinct raw percent-encoding may safely miss rather than alias;
- ordered page identity/page count/reordering changes the dedicated `ReaderImageSetNamespace`; rotating only delivery host/base URL under `TRUSTED_STABLE` does not;
- a locator that lacks a safe stable/revalidation contract can downgrade to `NON_PERSISTENT`;
- every effective non-persistent manifest receives a non-null process-local `ReaderRuntimeAssetScopeId`; different manifests/sessions cannot join single-flight or Coil memory merely because source/release/locator facts collide;
- unsupported key schema version -> safe miss/repair candidate;
- duplicate/blank trusted IDs downgrade, never alias;
- account-scoped persistence without a stable non-secret namespace -> `TRANSIENT_ONLY` with runtime isolation;
- `STABLE_ID_CHANGES_WITH_CONTENT + PUBLIC security + NON_PERSISTENT plugin permission` keeps `TRUSTED_STABLE` identity but resolves `persistenceMode=TRANSIENT_ONLY`, obtains a runtime-isolation scope, and cannot capture durable authority;
- raw URL/token/private namespace text is absent from durable key hash vectors and metadata-facing facts;
- `FakeClock` controls durable epoch time independently from `FakeMonotonicClock`; advancing one never advances the other;
- `SystemMonotonicClock` contract is non-decreasing for successive reads and is used only for runtime durations/order, never persisted.

- [ ] **Step 2: Run RED tests**

```bash
./gradlew :core:common:test :reader:testDebugUnitTest \
  --tests '*ClockTest*' \
  --tests '*ReaderAsset*Test*' \
  --no-daemon
```
Expected: FAIL because `MonotonicClock`/`reader.assets` do not exist.

- [ ] **Step 3: Add controlled wall/monotonic time and implement canonical identity types**

Extend `core/common/Clock.kt` without changing existing `Clock` semantics:

```kotlin
fun interface MonotonicClock {
    fun nowNanos(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}

class FakeMonotonicClock(initialNanos: Long) : MonotonicClock {
    private var currentNanos = initialNanos
    override fun nowNanos(): Long = currentNanos
    fun advanceByNanos(durationNanos: Long) {
        require(durationNanos >= 0L)
        currentNanos += durationNanos
    }
}
```

`Clock.nowEpochMillis()` is the only RICC source for persisted `createdAt`/`lastAccessedAt`/`lastConsumedAt`. `MonotonicClock` is the only explicit clock used for queue fairness and in-memory touch throttling; coroutine `delay` remains scheduler-controlled. Neither time source is used as logical identity.

Then implement the exact V1 key schema and runtime-isolation scope:

```kotlin
data class ReaderAssetSourceNamespace private constructor(val value: String) {
    companion object {
        fun fromPluginId(pluginId: PluginId): ReaderAssetSourceNamespace =
            ReaderAssetSourceNamespace(pluginId.value)
    }
}

@JvmInline value class ReaderAssetKeySchemaVersion(val value: Int)
@JvmInline value class ReaderAssetKeyHash(val value: String)
@JvmInline value class ReaderAssetIdentityHash(val value: String)
@JvmInline value class ReaderDeliveryLocatorFingerprint(val value: String)
@JvmInline value class ReaderImageSetNamespace(val value: String)
@JvmInline value class ReaderRuntimeAssetScopeId(val value: String)

enum class ReaderAssetIdentityMode { TRUSTED_STABLE, LOCATOR_BOUND, NON_PERSISTENT }
enum class ReaderAssetPersistenceMode { DURABLE_AUTOMATIC, TRANSIENT_ONLY }
enum class ReaderContentVariant { ORIGINAL }

sealed interface ReaderCacheSecurityScope {
    data object Public : ReaderCacheSecurityScope
    data class AccountScoped(val stableNonSecretNamespace: String) : ReaderCacheSecurityScope
    data object NonPersistentPrivate : ReaderCacheSecurityScope
}

data class ReaderPageAssetKey(
    val schemaVersion: ReaderAssetKeySchemaVersion,
    val sourceNamespace: ReaderAssetSourceNamespace,
    val securityScope: ReaderCacheSecurityScope,
    val contentVariant: ReaderContentVariant,
    val persistenceMode: ReaderAssetPersistenceMode,
    val imageSetNamespace: ReaderImageSetNamespace,
    val runtimeIsolationScope: ReaderRuntimeAssetScopeId?,
    val pageIdentityHash: ReaderAssetIdentityHash,
    val hash: ReaderAssetKeyHash,
)
```

Key schema version is exactly `1`. `ReaderAssetSourceNamespace.fromPluginId()` is the **only** V1 construction path for Reader runtime namespace identity, and canonical encoding uses `sourceNamespace.value` exactly as supplied by the canonical `PluginId`; there is no lowercasing, host derivation, version suffix, package-version field, or display-name fallback. Canonical key encoding uses explicit **tagged length framing** in UTF-8 followed by SHA-256. Do not use `toString()`, JSON serialization, unordered map iteration, locale-sensitive formatting, or raw secrets.

Define one conservative locator canonicalizer in `ReaderAssetIdentity.kt`. Input is an already-sanitized HTTPS locator. Fingerprint these tagged fields:
1. literal `ricc-locator-v1`;
2. lowercase scheme `https`;
3. `IDN.toASCII(uri.host).lowercase(Locale.ROOT)`;
4. omit absent/default port `443`; retain a non-default decimal port;
5. preserve `uri.rawPath` exactly, normalizing only empty path to `/`;
6. preserve `uri.rawQuery` exactly including ordering/escaping, with explicit null/absent framing;
7. exclude fragment because it is not sent in the HTTP request;
8. user-info remains forbidden by the sanitizer.

Do **not** percent-decode/re-encode paths or queries. Distinct percent encodings may conservatively miss; they must not be merged by over-normalization. Add hard-coded golden vectors for host-case/default-port equivalence, query difference, fragment-only equivalence, and percent-encoding difference.

Freeze effective identity selection:

```text
STABLE_ID_CHANGES_WITH_CONTENT
    -> TRUSTED_STABLE

else LOCATOR_CHANGES_WITH_CONTENT
    -> LOCATOR_BOUND

else
    -> NON_PERSISTENT
```

Persistence permission is evaluated separately into `ReaderAssetPersistenceMode`. `DURABLE_AUTOMATIC` is allowed only when identity mode is `TRUSTED_STABLE` or `LOCATOR_BOUND`, the plugin contract explicitly permits `PUBLIC`/safe `ACCOUNT_SCOPED` persistence, and a safe durable security scope exists. Otherwise the effective mode is `TRANSIENT_ONLY` and `runtimeIsolationScope` is mandatory. In particular, `TRUSTED_STABLE + imagePersistence=NON_PERSISTENT` remains transient; identity quality never grants storage permission.

Add `ReaderRuntimeAssetScopeIdFactory` in the same file. Production creates an opaque scope from `sessionId + sourceNamespace.value + UUID.randomUUID()` and hashes the framed input; tests inject deterministic UUIDs. The UUID/raw input is never persisted. A new effective non-persistent manifest always receives a fresh scope and no runtime scope is restored across process death.

Derive `ReaderImageSetNamespace` **before page keys** using the same tagged framing:
- common: `ricc-image-set-v1`, source namespace, selected release ID, content variant, identity mode, persistence mode, page count;
- `TRUSTED_STABLE`: ordered `(ordinal, full stableAssetId)` pairs;
- `LOCATOR_BOUND`: ordered `(ordinal, ReaderDeliveryLocatorFingerprint)` pairs;
- `NON_PERSISTENT`: runtime-isolation scope plus ordered `(ordinal, SHA-256(full stableAssetId + locatorFingerprint))` pairs.

Selected release ID remains part of V1 deliberately; RICC does not add cross-release deduplication. Reorder/count/identity changes therefore re-key the set. Delivery base-URL/host rotation under `TRUSTED_STABLE` does not.

Derive `pageIdentityHash`:
- `TRUSTED_STABLE`: SHA-256 over `ricc-page-trusted-v1 + imageSetNamespace + ordinal + full stableAssetId`;
- `LOCATOR_BOUND`: SHA-256 over `ricc-page-locator-v1 + imageSetNamespace + ordinal + locatorFingerprint`;
- `NON_PERSISTENT`: SHA-256 over `ricc-page-runtime-v1 + runtimeIsolationScope + imageSetNamespace + ordinal + stableAssetId + locatorFingerprint`.

The final logical `hash` is SHA-256 over `ricc-key-v1`, schema version, source namespace, a one-way security-scope discriminator/hash, content variant, persistence mode, image-set namespace, optional runtime-isolation scope, and page identity hash. Raw stable IDs and raw locators stay runtime-only; durable metadata gets hashes/version only. Add hard-coded golden vectors for trusted-durable, locator-durable, and transient runtime-isolated cases.

- [ ] **Step 4: Implement immutable manifest/request models**

```kotlin
data class ReaderPageAssetDescriptor(
    val key: ReaderPageAssetKey,
    val uiBlockId: String,
    val stableAssetId: String,
    val imageOrdinal: Int,
    val deliveryLocator: String,
    val locatorFingerprint: ReaderDeliveryLocatorFingerprint,
)

data class ReaderAssetChapterManifest(
    val sessionId: ReaderSessionId,
    val storyId: StoryId,
    val canonicalChapterId: CanonicalChapterId,
    val selectedReleaseId: ChapterReleaseId,
    val sourceNamespace: ReaderAssetSourceNamespace,
    val securityScope: ReaderCacheSecurityScope,
    val contentVariant: ReaderContentVariant,
    val identityMode: ReaderAssetIdentityMode,
    val persistenceMode: ReaderAssetPersistenceMode,
    val graphRevision: ReaderChapterGraphRevision,
    val imageSetNamespace: ReaderImageSetNamespace,
    val runtimeIsolationScope: ReaderRuntimeAssetScopeId?,
    val descriptors: List<ReaderPageAssetDescriptor>,
)

data class ReaderPageAssetRequest(
    val sessionId: ReaderSessionId,
    val manifestRevision: Long,
    val descriptor: ReaderPageAssetDescriptor,
)
```

Enforce `descriptors.size <= ReaderDocumentSanitizer.MAX_BLOCKS` (currently 2,000), non-blank runtime locators/stable identities, and image ordinals exactly `0 until descriptors.size`. Require `runtimeIsolationScope != null` whenever `persistenceMode == TRANSIENT_ONLY` and `runtimeIsolationScope == null` whenever `persistenceMode == DURABLE_AUTOMATIC`. Also require durable mode to use only `TRUSTED_STABLE`/`LOCATOR_BOUND` plus a durable security scope. Raw locator/stable-identity strings remain in-memory manifest facts only; durable rows receive hashes only.

- [ ] **Step 5: Define the complete four-state store contract; keep unified-budget authority opaque**

```kotlin
enum class ReaderAssetLocalPresence { UNKNOWN, LOCAL_AVAILABLE, LOCAL_MISSING, LOCAL_UNAVAILABLE }
enum class ReaderAssetCachePressure { NORMAL, PRESSURED, EMERGENCY }

enum class ReaderAssetProtectionClass {
    ACTIVE_INTERACTIVE,
    ACTIVE_CONSUMED,
    RECENT_HISTORY_1,
    RECENT_HISTORY_2,
    CURRENT_AHEAD_SPECULATIVE,
    TRANSITION_SPECULATIVE,
}

data class ReaderAssetActiveProtections(
    val byKey: Map<ReaderAssetKeyHash, ReaderAssetProtectionClass>,
) {
    companion object { val EMPTY = ReaderAssetActiveProtections(emptyMap()) }
}

interface ReaderAssetReadLease : AutoCloseable {
    val sizeBytes: Long
    fun openStream(): java.io.InputStream
}

sealed interface ReaderAssetOpenResult {
    data class Available(val lease: ReaderAssetReadLease) : ReaderAssetOpenResult
    data object Missing : ReaderAssetOpenResult
    data object Corrupt : ReaderAssetOpenResult
    data object Unavailable : ReaderAssetOpenResult
}

/**
 * Opaque Reader-facing token. The :downloads implementation wraps the single
 * unified automatic-cache write authority captured before remote acquisition.
 */
interface ReaderAssetDurableWriteAuthority

data class ReaderAssetCommitFacts(
    val key: ReaderPageAssetKey,
    val storyId: StoryId,
    val canonicalChapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId,
    val sourceNamespace: ReaderAssetSourceNamespace,
    val securityScope: ReaderCacheSecurityScope,
    val contentVariant: ReaderContentVariant,
    val identityMode: ReaderAssetIdentityMode,
    val persistenceMode: ReaderAssetPersistenceMode,
    val imageSetNamespace: ReaderImageSetNamespace,
    val imageOrdinal: Int,
)

sealed interface ReaderAssetCommitResult {
    data object Persisted : ReaderAssetCommitResult
    data object Bypassed : ReaderAssetCommitResult
    data class Degraded(val failure: ReaderAssetFailure) : ReaderAssetCommitResult
}

enum class ReaderAssetInvalidationReason { CORRUPT, SECURITY_SCOPE, EXPLICIT_CLEAR }

sealed interface ReaderAssetClearScope {
    data object AllAutomatic : ReaderAssetClearScope
    data class Source(val sourceNamespace: ReaderAssetSourceNamespace) : ReaderAssetClearScope
    data class Account(
        val sourceNamespace: ReaderAssetSourceNamespace,
        val stableNonSecretNamespace: String,
    ) : ReaderAssetClearScope
    data class AllAccountScopesForSource(val sourceNamespace: ReaderAssetSourceNamespace) : ReaderAssetClearScope
}

interface ReaderAssetStorePort {
    suspend fun inspect(keys: Set<ReaderPageAssetKey>): Map<ReaderPageAssetKey, ReaderAssetLocalPresence>
    suspend fun openLocal(key: ReaderPageAssetKey): ReaderAssetOpenResult
    suspend fun captureDurableWriteAuthority(facts: ReaderAssetCommitFacts): ReaderAssetDurableWriteAuthority?
    suspend fun commit(
        facts: ReaderAssetCommitFacts,
        authority: ReaderAssetDurableWriteAuthority,
        payload: ReaderAssetPayload,
    ): ReaderAssetCommitResult
    suspend fun markConsumed(key: ReaderPageAssetKey)
    suspend fun invalidate(key: ReaderPageAssetKey, reason: ReaderAssetInvalidationReason)
    suspend fun cachePressure(): ReaderAssetCachePressure
    suspend fun reconcile(activeProtections: ReaderAssetActiveProtections)
    suspend fun releaseSession(sessionId: ReaderSessionId)
    suspend fun clearAutomatic(scope: ReaderAssetClearScope)
}
```

`ReaderAssetPayload.sourceIntegrityHash` stores only a normalized one-way digest of optional source integrity evidence; raw integrity/header text never crosses into durable metadata. Local blob integrity is separately recomputed as mandatory SHA-256 during physical commit/read.

`captureDurableWriteAuthority()` is called by the **single-flight producer before the remote image fetch begins**. `TRANSIENT_ONLY` or disabled policy returns null and still allows transient rendering, regardless of how strong the identity mode is. The concrete token is backed by the same `AutomaticCacheWriteAuthority` that protects document-cache writes in Task 6; RICC must not create a second epoch authority.

`releaseSession(sessionId)` is a storage-maintenance trigger only. The durable store does not own semantic per-session history; Task 11 computes the union of active protections and calls `reconcile()` after session release.

- [ ] **Step 6: Define payload, delivery, load outcome, and failure models exactly once**

```kotlin
class ReaderAssetPayload private constructor(
    private val encoded: ByteArray,
    val mimeType: String?,
    val sourceIntegrityHash: String?,
) {
    val sizeBytes: Int get() = encoded.size
    fun bytes(): ByteArray = encoded.copyOf()

    companion object {
        fun verifiedBounded(bytes: ByteArray, mimeType: String?, sourceIntegrityEvidence: String?): ReaderAssetPayload
    }
}

data class ReaderAssetDeliveryRequest(
    val assetKey: ReaderPageAssetKey,
    val deliveryLocator: String,
)

sealed interface ReaderAssetDeliveryResult {
    data class Success(val payload: ReaderAssetPayload) : ReaderAssetDeliveryResult
    data class Failure(val failure: ReaderAssetFailure) : ReaderAssetDeliveryResult
}

sealed interface ReaderAssetFailure {
    data class TransportUnavailable(val retryable: Boolean) : ReaderAssetFailure
    data class DeliveryRejected(val httpStatus: Int) : ReaderAssetFailure
    data object DeliveryLocatorStale : ReaderAssetFailure
    data object Unauthorized : ReaderAssetFailure
    data object AssetNotFound : ReaderAssetFailure
    data object AssetTooLarge : ReaderAssetFailure
    data object InvalidPayload : ReaderAssetFailure
    data object CacheCorrupt : ReaderAssetFailure
    data object CacheStorageUnavailable : ReaderAssetFailure
    data object Cancelled : ReaderAssetFailure
    data object Preempted : ReaderAssetFailure
    data object Superseded : ReaderAssetFailure
    data object RouteInvalidated : ReaderAssetFailure
}

@JvmInline
value class ReaderAssetConsumerToken(val value: Long)

sealed interface ReaderAssetRemoteOutcome {
    data class Success(val payload: ReaderAssetPayload) : ReaderAssetRemoteOutcome
    data class Failure(val failure: ReaderAssetFailure) : ReaderAssetRemoteOutcome
}

sealed interface ReaderAssetLoadOutcome {
    data class Local(val lease: ReaderAssetReadLease) : ReaderAssetLoadOutcome
    data class Remote(val payload: ReaderAssetPayload) : ReaderAssetLoadOutcome
    data class Failure(val failure: ReaderAssetFailure) : ReaderAssetLoadOutcome
}

interface ReaderAssetDeliveryPort {
    suspend fun fetch(request: ReaderAssetDeliveryRequest): ReaderAssetDeliveryResult
}
```

Structured coroutine cancellation remains `CancellationException` rather than being caught merely to manufacture a value result. `ReaderAssetFailure.Cancelled` exists for an explicit boundary/transport cancellation fact that is already materialized as data; it must never be produced by swallowing `CancellationException`. `ReaderAssetPayload.verifiedBounded` copies bytes and rejects anything above 16 MiB. Raw HTTP `403/404` are represented as `DeliveryRejected(status)` at the generic transport layer; Task 14 decides whether a bounded same-release refresh proves stale locator, route invalidation, or leaves a terminal page-local rejection. The transport adapter never guesses `DeliveryLocatorStale`/`AssetNotFound` solely from status code.

- [ ] **Step 7: Add explicit Reader-runtime policy constants and tests**

`ReaderAssetRuntimePolicy.kt` contains only Reader/runtime-owned frozen values: 16 MiB payload bound, transient retry/backoff, fetch concurrency defaults consumed by Task 3, viewport horizons, transition thresholds, and transition speculative concurrency. **Do not** put automatic-cache watermarks, access-touch interval, or ENOSPC eviction bounds in `:reader`; Task 6 owns those in `AutomaticCacheRuntimePolicy`. Tests assert exact values so later tuning is intentional.

- [ ] **Step 8: Run GREEN and architecture purity checks**

```bash
./gradlew :core:common:test :reader:testDebugUnitTest \
  --tests '*ClockTest*' \
  --tests '*ReaderAsset*Test*' \
  --no-daemon
bash scripts/verify-package-boundaries.sh
```
Expected: PASS and no `:reader:engine` changes.

- [ ] **Step 9: Commit**

```bash
git add core/common/src/main/kotlin/app/openstory/common/Clock.kt core/common/src/test/kotlin/app/openstory/common/ClockTest.kt reader/src/main reader/src/test
git commit -m "reader: define image asset continuity contracts"
```

---

### Task 3: Replace Reader-global Semaphores with Source Lane + One Promotable Content Fetch Arbiter

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ContentFetchArbiter.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ContentSourceExecutionLane.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderHalfOpenProbeRegistry.kt`
- Delete after migration: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiter.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/RouteSnapshotAssembler.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRuntimeLimits.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/reader/ReaderDownloadContentSource.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/DownloadModule.kt`
- Replace: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiterTest.kt` with focused lane/probe tests
- Create: `reader/src/test/kotlin/app/openstory/reader/assets/ContentFetchArbiterTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ContentSourceExecutionLaneTest.kt`
- Create/Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderHalfOpenProbeRegistryTest.kt`
- Modify: `downloads/src/test/kotlin/app/openstory/downloads/reader/ReaderDownloadContentSourceTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCompetitiveExecutionTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCoordinatorModelTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteCoordinatorAdaptiveTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteCoordinatorContractTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteReplanTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRuntimeStressTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/RouteSnapshotAssemblerAdaptiveTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/RouteSnapshotAssemblerTest.kt`

**Interfaces:**
- Produces one process-shared `ContentFetchArbiter`, one process-shared per-source `ContentSourceExecutionLane`, and HES-only `ReaderHalfOpenProbeRegistry`.
- `ContentFetchDemand` is intentionally promotable so Task 9 can raise an already queued speculative image fetch to visible priority without restart.

- [ ] **Step 1: Write RED ordering, promotion, fairness, and no-double-permit tests**

Required tests:
- source lane is acquired before arbiter for Reader plugin fetch;
- same-source foreground cancels Reader-owned prefetch work without cancelling caller/session parent;
- source-lane queue order is foreground > user work > prefetch, but only foreground preempts an active Reader prefetch;
- explicit `USER_WORK` queues ahead of prefetch and active `USER_WORK` is never force-cancelled by Reader critical work;
- no more than 3 total admitted remote operations;
- `USER_WORK`/PREFETCH/SPECULATIVE/BACKGROUND together occupy at most 2 slots, preserving one critical/interactive slot even during multiple explicit downloads;
- process-wide active `SPECULATIVE` image work never exceeds 1 even with two Reader sessions requesting different next chapters;
- queued `USER_WORK` waiting >=2,000 ms becomes eligible for one non-reserved slot ahead of newer queued work while one CRITICAL/INTERACTIVE slot remains protected; aging never cancels/preempts active high-priority work;
- a queued `SPECULATIVE` `ContentFetchDemand` promoted to `CRITICAL` is re-ranked in place and admitted without creating a second demand;
- recursive `withAdmission` from an already admitted coroutine is rejected before waiting;
- preempted/superseded work does not record HES health failure.

- [ ] **Step 2: Run RED tests**

```bash
./gradlew :reader:testDebugUnitTest :downloads:testDebugUnitTest \
  --tests '*ContentFetchArbiterTest*' \
  --tests '*ContentSourceExecutionLaneTest*' \
  --tests '*ReaderHalfOpenProbeRegistryTest*' \
  --tests '*ReaderDownloadContentSourceTest*' \
  --tests '*ReaderRuntimeStressTest*' \
  --no-daemon
```
Expected: FAIL; old limiter still owns global semaphores and Downloads bypasses them.

- [ ] **Step 3: Implement the promotable arbiter API**

```kotlin
enum class ContentFetchPriority {
    CRITICAL, INTERACTIVE, USER_WORK, PREFETCH, SPECULATIVE, BACKGROUND,
}

class ContentFetchDemand internal constructor(initial: ContentFetchPriority) {
    val priority: ContentFetchPriority
    fun promoteTo(priority: ContentFetchPriority)
}

class ContentFetchArbiter(
    private val maxTotal: Int = 3,
    private val reservedCriticalInteractive: Int = 1,
    private val maxSpeculative: Int = 1,
    private val userWorkAgingThresholdNanos: Long = 2_000_000_000L,
    private val monotonicClock: MonotonicClock,
) {
    fun newDemand(priority: ContentFetchPriority): ContentFetchDemand

    suspend fun <T> withAdmission(
        demand: ContentFetchDemand,
        block: suspend () -> T,
    ): T

    suspend fun <T> withAdmission(
        priority: ContentFetchPriority,
        block: suspend () -> T,
    ): T = withAdmission(newDemand(priority), block)
}
```

`promoteTo` is monotonic toward higher precedence only and signals the arbiter queue so a waiting ticket is re-ranked. Queue age/fairness uses `monotonicClock.nowNanos()` from Task 2; wall `Clock` is forbidden here. The arbiter uses explicit precedence, never enum ordinal. Queue ordering is explicit priority + FIFO until `USER_WORK` reaches the 2,000-ms aging threshold. After that threshold, at most one aged `USER_WORK` may claim a **non-reserved** slot before newer queued work, including newer high-priority demand for that non-reserved slot; the reserved CRITICAL/INTERACTIVE capacity remains unavailable to USER_WORK. Aging never cancels active work and active `USER_WORK` is never preempted by Reader demand. This gives a finite admission opportunity under sustained mixed load without sacrificing the reserved visible lane. Process-wide `SPECULATIVE` admission is separately capped at 1.

A coroutine-context admission marker rejects nested acquisition with `IllegalStateException`. Release occurs only when `block` returns/cancels, so callers must keep complete response-body consumption inside the block. `ReaderModule` provides the process binding `MonotonicClock = SystemMonotonicClock`; tests inject `FakeMonotonicClock`. Reuse the existing app-wide `Clock` binding from `CatalogModule` for durable wall time rather than adding a duplicate `Clock` provider.

- [ ] **Step 4: Split current limiter responsibilities without changing HES probe semantics**

Create:

```kotlin
enum class ContentSourceWorkPriority { FOREGROUND, USER_WORK, PREFETCH }

class ContentSourceExecutionLane {
    suspend fun <T> withSource(
        sourceId: PluginId,
        priority: ContentSourceWorkPriority,
        block: suspend () -> T,
    ): T
}
```

Move the current source-ID keyed queue/work-job cancellation logic here. Preserve the existing rule that a foreground request may cancel the **Reader-owned work Job** of active same-source prefetch, never the caller/session parent. Keep plugin `PluginReaderDocumentSource.invocationMutex` as a lower-level safety guard; it is not a process-global admission owner and does not replace the source lane.

Move `tryAcquireHalfOpenProbe`, lease identity, and release into `ReaderHalfOpenProbeRegistry`. Downloads never sees/acquires this registry.

- [ ] **Step 5: Migrate Reader document fetch acquisition order**

`ReaderRouteExecutor.fetch()` becomes:

```kotlin
sourceLane.withSource(source.pluginId, sourcePriority) {
    fetchArbiter.withAdmission(fetchPriority) {
        source.fetch(candidate)
    }
}
```

Map foreground document work to source `FOREGROUND` + global `CRITICAL`; N+1 document prefetch to source `PREFETCH` + global `PREFETCH`. `RouteSnapshotAssembler` uses only `ReaderHalfOpenProbeRegistry` for HALF_OPEN ownership. No old foreground/prefetch semaphore remains.

- [ ] **Step 6: Migrate Downloads document fetch without HES probe ownership**

`ReaderDownloadContentSource.fetch(release)` uses source `USER_WORK`, then `ContentFetchArbiter(USER_WORK)`, then `source.fetch(release)`. It never acquires HALF_OPEN probes and never calls a Reader wrapper that already owns arbiter admission.

- [ ] **Step 7: Run focused GREEN and HES regression tests**

```bash
./gradlew :reader:testDebugUnitTest :downloads:testDebugUnitTest \
  --tests '*ContentFetchArbiterTest*' \
  --tests '*ContentSourceExecutionLaneTest*' \
  --tests '*ReaderHalfOpenProbeRegistryTest*' \
  --tests '*ReaderRouteExecutorAdaptiveTest*' \
  --tests '*ReaderRouteCoordinatorAdaptiveTest*' \
  --tests '*ReaderRuntimeStressTest*' \
  --tests '*PrefetchCoordinatorTest*' \
  --tests '*ReaderDownloadContentSourceTest*' \
  --no-daemon
```
Expected: PASS with the same source preemption/probe behavior and a single global admission owner.

- [ ] **Step 8: Commit**

```bash
git add reader downloads app/src/main/kotlin/app/openstory/di
git commit -m "reader: unify promotable content fetch admission"
```

---

### Task 4: Add Room Schema 12 Reader Asset Metadata

**Files:**
- Create: `downloads/src/main/kotlin/app/openstory/downloads/assets/ReaderAssetMetadata.kt`
- Create: `downloads/src/main/kotlin/app/openstory/downloads/assets/ReaderAssetMetadataRepository.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/readerassets/ReaderAssetEntryEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/readerassets/ReaderAssetDao.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/readerassets/RoomReaderAssetMetadataRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/readerassets/Migration11To12Test.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/readerassets/RoomReaderAssetMetadataRepositoryTest.kt`
- Generate: `storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/12.json`

**Interfaces:**
- Produces `ReaderAssetMetadataRepository` used by Tasks 6 and 8.
- Does not expose Room types outside `:storage:room`.

- [ ] **Step 1: Write migration and repository RED tests**

Tests assert:
- schema 11 data survives migration;
- new table is empty after migration;
- fresh schema 12 equals migrated schema;
- key version/hash, source namespace, hashed security scope, variant, identity mode, persistence mode, image set hash, page identity hash, ordinal, blob ID, byte size, mandatory local checksum, timestamps persist;
- no raw URL/token/private scope/source-integrity header value exists in columns; optional source integrity is stored only as normalized one-way `source_integrity_hash`;
- `source_namespace` persists exactly `ReaderAssetSourceNamespace.value` (the canonical producing `PluginId.value`); repository mapping never derives it from plugin/package version, display metadata, URL, or source class name;
- bounded key lookup returns one row per existing key;
- usage sum and source/account invalidation queries are indexed/bounded.

- [ ] **Step 2: Run migration RED**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.readerassets.Migration11To12Test \
  --no-daemon
```
Expected: FAIL because schema 12 does not exist.

- [ ] **Step 3: Add exact schema 12 entity**

Use Room entity fields:

```kotlin
@Entity(
    tableName = "reader_asset_entries",
    indices = [
        Index("chapter_release_id"),
        Index(value = ["story_id", "canonical_chapter_id"]),
        Index(value = ["last_consumed_at_epoch_millis", "last_accessed_at_epoch_millis"]),
        Index(value = ["source_namespace", "security_scope_hash"]),
        Index("blob_id", unique = true),
    ],
)
data class ReaderAssetEntryEntity(
    @PrimaryKey @ColumnInfo(name = "logical_asset_key_hash") val logicalAssetKeyHash: String,
    @ColumnInfo(name = "key_schema_version") val keySchemaVersion: Int,
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "canonical_chapter_id") val canonicalChapterId: String,
    @ColumnInfo(name = "chapter_release_id") val chapterReleaseId: String,
    @ColumnInfo(name = "source_namespace") val sourceNamespace: String,
    @ColumnInfo(name = "security_scope_hash") val securityScopeHash: String?,
    @ColumnInfo(name = "content_variant") val contentVariant: String,
    @ColumnInfo(name = "identity_mode") val identityMode: String,
    @ColumnInfo(name = "persistence_mode") val persistenceMode: String,
    @ColumnInfo(name = "image_set_namespace_hash") val imageSetNamespaceHash: String,
    @ColumnInfo(name = "page_identity_hash") val pageIdentityHash: String,
    @ColumnInfo(name = "page_ordinal") val pageOrdinal: Int,
    @ColumnInfo(name = "blob_id") val blobId: String,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "local_blob_checksum") val localBlobChecksum: String,
    @ColumnInfo(name = "source_integrity_hash") val sourceIntegrityHash: String?,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "last_accessed_at_epoch_millis") val lastAccessedAtEpochMillis: Long,
    @ColumnInfo(name = "last_consumed_at_epoch_millis") val lastConsumedAtEpochMillis: Long?,
)
```

- [ ] **Step 4: Implement `MIGRATION_11_12`**

Migration only creates RICC structures/indexes; it does not alter or populate existing document/download rows and performs no image fetch. Add the entity/DAO to `OpenStoryDatabase`, set `version = 12`, add migration to `open()`.

- [ ] **Step 5: Generate schema and run GREEN instrumentation**

```bash
./gradlew :storage:room:assembleDebug --no-daemon
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.readerassets.Migration11To12Test,app.openstory.storage.room.readerassets.RoomReaderAssetMetadataRepositoryTest \
  --no-daemon
```
Expected: PASS; `12.json` exported.

- [ ] **Step 6: Commit**

```bash
git add downloads/src/main/kotlin/app/openstory/downloads/assets storage/room
git commit -m "storage: add reader asset metadata schema"
```

---

### Task 5: Add Atomic Image Blob Storage and Active Read Leases

**Files:**
- Create: `downloads/src/main/kotlin/app/openstory/downloads/assets/ReaderAssetBlobStore.kt`
- Create: `downloads/src/main/kotlin/app/openstory/downloads/assets/ReaderAssetBlobIdFactory.kt`
- Create: `storage/files/src/main/kotlin/app/openstory/storage/files/AtomicFileReaderAssetBlobStore.kt`
- Create: `storage/files/src/main/kotlin/app/openstory/storage/files/ReaderAssetBlobFileLocks.kt`
- Create: `storage/files/src/main/kotlin/app/openstory/storage/files/ReaderAssetStorageErrorClassifier.kt`
- Modify: `storage/files/src/main/kotlin/app/openstory/storage/files/FileBlobInventory.kt`
- Create: `storage/files/src/test/kotlin/app/openstory/storage/files/AtomicFileReaderAssetBlobStoreTest.kt`
- Modify: `storage/files/src/test/kotlin/app/openstory/storage/files/FileBlobInventoryTest.kt`

**Interfaces:**
- Produces physical blob store used only by `:downloads` orchestration.
- Does not depend on `:reader` from `:storage:files`.

- [ ] **Step 1: Write RED atomicity/lease tests**

Cover:
- write temp + fsync/close + atomic rename exposes either old complete blob or new complete blob, never partial;
- checksum is SHA-256 `BlobChecksum` compatible;
- read lease keeps the physical file readable while normal eviction requests deletion;
- `tryDeleteNowIfUnleased` returns false and does not unlink while any read lease is active;
- after the final lease closes, immediate delete may succeed and normal `deleteWhenUnleased` completes safely;
- process-death-style orphan temp is discoverable by bounded inventory cleanup;
- metadata-missing blob can be identified as an orphan;
- blob paths never contain raw URL/stable ID/security scope;
- each committed RICC blob gets a **generation-unique blob ID** derived from logical-key hash + injected random UUID/128-bit nonce, so clearing an old metadata generation cannot later delete a newly published blob for the same logical key; reconstructing the factory/process and writing the same logical key still yields a distinct generation ID;
- an injected/platform-classified `ENOSPC` write becomes typed `NoSpace`, while unrelated I/O becomes typed `Unavailable`; cancellation is still rethrown;
- a failed write never exposes a partial target file and leaves only a cleanup-safe temp artifact.

- [ ] **Step 2: Run RED storage tests**

```bash
./gradlew :storage:files:testDebugUnitTest --tests '*ReaderAssetBlob*' --no-daemon
```
Expected: FAIL.

- [ ] **Step 3: Define the Downloads-owned physical port**

```kotlin
@JvmInline value class ReaderAssetBlobId(val value: String)

class ReaderAssetBlobIdFactory(
    private val nextUuid: () -> java.util.UUID = java.util.UUID::randomUUID,
) {
    fun create(logicalKeyHash: ReaderAssetKeyHash): ReaderAssetBlobId =
        ReaderAssetBlobId(sha256Hex(logicalKeyHash.value + "\u0000" + nextUuid().toString()))
}

data class StoredReaderAssetBlob(
    val id: ReaderAssetBlobId,
    val sizeBytes: Long,
    val checksum: BlobChecksum,
)

sealed interface ReaderAssetBlobWriteResult {
    data class Stored(val blob: StoredReaderAssetBlob) : ReaderAssetBlobWriteResult
    data object NoSpace : ReaderAssetBlobWriteResult
    data class Unavailable(val cause: Throwable) : ReaderAssetBlobWriteResult
}

interface ReaderAssetBlobReadLease : AutoCloseable {
    val sizeBytes: Long
    fun openStream(): InputStream
}

interface ReaderAssetBlobStore {
    suspend fun writeAtomic(id: ReaderAssetBlobId, bytes: ByteArray): ReaderAssetBlobWriteResult
    suspend fun open(id: ReaderAssetBlobId): ReaderAssetBlobReadLease?
    suspend fun exists(id: ReaderAssetBlobId): Boolean
    suspend fun tryDeleteNowIfUnleased(id: ReaderAssetBlobId): Boolean
    suspend fun deleteWhenUnleased(id: ReaderAssetBlobId)
}
```

`writeAtomic` rejects payloads over 16 MiB even if caller already validated them.

- [ ] **Step 4: Implement separate RICC directory + lease-aware deletion**

Do not overload `ChapterBlobKey` or the existing chapter blob directory. Use a dedicated application-private `reader-assets/` root with hash-derived fanout if needed. `ReaderAssetBlobId` is generation-unique, not equal to `ReaderAssetKeyHash`; metadata is the only logical-key -> physical-generation mapping. Generation uniqueness never depends on wall/monotonic time: `ReaderAssetBlobIdFactory` hashes the logical key together with a fresh random UUID/128-bit nonce, with the UUID source injectable in tests. This prevents an old clear/eviction cleanup from unlinking a post-clear rewrite of the same logical key. Keep lock/refcount state process-local; process death does not persist lease truth. `tryDeleteNowIfUnleased()` acquires the same per-blob lock/refcount state and returns `false` instead of waiting when a read lease exists; this is the only deletion primitive Task 6 may count as physically reclaimed bytes during synchronous ENOSPC relief. Normal eviction/clear continues to use `deleteWhenUnleased()`. `ReaderAssetStorageErrorClassifier` walks the cause chain and recognizes Android `ErrnoException.errno == OsConstants.ENOSPC`; tests inject the classifier so local JVM tests do not depend on host-disk exhaustion. Unknown I/O is `Unavailable`, never guessed as no-space from message text. Cancellation is not converted into a write result.

- [ ] **Step 5: Run GREEN tests**

```bash
./gradlew :storage:files:testDebugUnitTest --tests '*ReaderAssetBlob*' --tests '*FileBlobInventoryTest*' --no-daemon
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add downloads/src/main/kotlin/app/openstory/downloads/assets/ReaderAssetBlobStore.kt storage/files
git commit -m "storage: add atomic reader asset blobs"
```

---

### Task 6: Build One Unified Automatic-cache Budget, Write Authority, and Final Publication Gate

**Files:**
- Create: `downloads/src/main/kotlin/app/openstory/downloads/cache/AutomaticCacheBudgetCoordinator.kt`
- Create: `downloads/src/main/kotlin/app/openstory/downloads/cache/AutomaticCacheRetention.kt`
- Create: `downloads/src/main/kotlin/app/openstory/downloads/cache/AutomaticCacheWriteAuthority.kt`
- Create: `downloads/src/main/kotlin/app/openstory/downloads/cache/AutomaticCacheRuntimePolicy.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/cache/CacheService.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/cache/CacheRepository.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStore.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentStore.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/assets/ReaderAssetMetadataRepository.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/assets/ReaderAssetBlobStore.kt`
- Create: `downloads/src/test/kotlin/app/openstory/downloads/cache/AutomaticCacheBudgetCoordinatorTest.kt`
- Modify: `downloads/src/test/kotlin/app/openstory/downloads/cache/CacheServiceTest.kt`
- Modify: `downloads/src/test/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStoreTest.kt`

**Interfaces:**
- Produces the **single** automatic-cache write authority, byte reservation authority, invalidation gate, and cross-kind eviction coordinator used by both document and image durable writes.
- Removes the fixed/private document cache quota from `DownloadAwareReaderDocumentStore`.
- No separate image-cache epoch may be introduced later.
- Automatic Reader document persistence receives an opaque write intent captured **before each remote source fetch attempt**, so clear/quota-zero during network cannot let an old completion capture a fresh post-clear authority.

- [ ] **Step 1: Write RED unified-quota, revocation, and TOCTOU race tests**

Tests must prove:
- document bytes + image bytes count against one quota, never `X + X`;
- two simultaneous 60-byte commits into 100 bytes cannot both reserve the same free 60 bytes;
- pending reservation bytes count before either commit completes;
- cancellation/failure releases reservation exactly once;
- speculative image victims precede `WARM_DOCUMENT`;
- `WARM_DOCUMENT` precedes consumed image history;
- progress-protected document outranks generic consumed history but remains evictable before recent/active image protection where pressure requires it;
- quota 0 denies new automatic durable authority/reservations;
- **document write authority captured before the remote source invocation becomes stale if clear or quota=0 occurs during network, before encode/write, or before metadata publication**;
- barrier test `capture intent -> begin source.fetch -> clear/quota-zero -> remote success -> persist` produces no automatic document metadata/blob; a later new foreground fetch may capture a fresh authority and cache normally;
- barrier-controlled race `revalidate -> clear -> metadata upsert` cannot publish stale metadata because revalidation + publication share one final gate;
- global clear revokes first, detaches the exact pre-clear document+image metadata set, and does not delete a same-key image generation published after the gate reopens;
- >100% triggers asynchronous reconciliation toward <=90% where enough evictable bytes exist;
- active protected overflow may temporarily remain >quota;
- explicit-download bytes never count toward automatic quota;
- physical-pressure relief considers only eligible automatic-cache victims, never explicit downloads or active read leases;
- one relief call processes at most `MAX_ENOSPC_EVICTION_VICTIMS = 32` metadata victims and stops earlier only when **physically reclaimed** bytes reach the requested relief bytes; a leased image generation contributes 0 reclaimed bytes;
- physical-pressure relief never degrades progress/recent/active protection in this synchronous ENOSPC recovery path; if unprotected bytes are insufficient it returns the smaller freed amount instead of widening eviction scope.

- [ ] **Step 2: Run RED**

```bash
./gradlew :downloads:testDebugUnitTest :reader:testDebugUnitTest \
  --tests '*AutomaticCacheBudgetCoordinatorTest*' \
  --tests '*DownloadAwareReaderDocumentStoreTest*' \
  --tests '*ReaderRouteExecutorAdaptiveTest*' \
  --no-daemon
```
Expected: FAIL; current stores enforce independently and have no shared revocation/publication authority.

- [ ] **Step 3: Implement normalized candidates, scopes, authority, and reservation**

```kotlin
sealed interface AutomaticCacheWriteScope {
    data object GlobalAutomatic : AutomaticCacheWriteScope
    data class ReaderAssetSource(val sourceNamespace: ReaderAssetSourceNamespace) : AutomaticCacheWriteScope
    data class ReaderAssetAccount(
        val sourceNamespace: ReaderAssetSourceNamespace,
        val securityScopeHash: String,
    ) : AutomaticCacheWriteScope
}

data class AutomaticCacheWriteAuthority internal constructor(
    internal val globalEpoch: Long,
    internal val scopedEpoch: Long,
    internal val scope: AutomaticCacheWriteScope,
)

data class AutomaticCacheReservation internal constructor(
    val id: Long,
    val bytes: Long,
)

sealed interface AutomaticCachePublicationResult<out T> {
    data class Published<T>(val value: T) : AutomaticCachePublicationResult<T>
    data object Revoked : AutomaticCachePublicationResult<Nothing>
}

class AutomaticCacheBudgetCoordinator {
    suspend fun updateQuota(quotaBytes: Long)
    suspend fun updateProgressProtectedReleaseIds(releaseIds: Set<ChapterReleaseId>)

    suspend fun captureWriteAuthority(
        scope: AutomaticCacheWriteScope = AutomaticCacheWriteScope.GlobalAutomatic,
    ): AutomaticCacheWriteAuthority?

    suspend fun reserve(
        bytes: Long,
        authority: AutomaticCacheWriteAuthority,
    ): AutomaticCacheReservation?

    suspend fun <T> publishIfCurrent(
        authority: AutomaticCacheWriteAuthority,
        reservation: AutomaticCacheReservation,
        publishMetadata: suspend () -> T,
    ): AutomaticCachePublicationResult<T>

    suspend fun release(reservation: AutomaticCacheReservation)
    suspend fun reconcile(activeAssetProtections: ReaderAssetActiveProtections = ReaderAssetActiveProtections.EMPTY)
    suspend fun relievePhysicalPressure(requiredBytes: Long): Long
    suspend fun clearAutomatic(scope: AutomaticCacheInvalidationScope)
    suspend fun snapshot(): AutomaticCacheBudgetSnapshot
}

data class AutomaticCacheRuntimePolicy(
    val highWatermarkBasisPoints: Int = 10_000,
    val lowWatermarkBasisPoints: Int = 9_000,
    val assetAccessTouchIntervalMillis: Long = 300_000L,
    val maxEnospcEvictionVictims: Int = 32,
)
```

The coordinator owns:
1. a short **publication/revocation gate** protecting policy epochs and metadata visibility;
2. serialized `committedBytes + pendingReservationBytes` accounting;
3. normalized candidate selection across document metadata and RICC metadata;
4. the latest active-protection union supplied by `reconcile()`, atomically replaced before any newly scheduled victim selection and used by quota/physical-pressure relief.

`AutomaticCacheRuntimePolicy` is Downloads-owned because watermark/touch/physical-eviction behavior is durable-cache policy, not Reader routing/runtime policy.

`publishIfCurrent()` reacquires the same publication gate used by clear/logout/quota-zero, revalidates authority, performs only the bounded metadata publication transaction while the gate is held, updates committed/pending accounting, then releases the gate. Network I/O and blob **writes/reads** are forbidden inside this gate. The sole physical-I/O exception is a bounded delete of an existing deterministic automatic-document blob when that delete must remain inside the gate to prevent a same-key ABA race; generation-unique RICC image deletion always occurs outside the gate.

This gate is mandatory: a plain `if (epoch == current) { upsert() }` has a TOCTOU window and is forbidden.

- [ ] **Step 4: Implement exact invalidation semantics**

Define `AutomaticCacheInvalidationScope` with:
- `AllAutomatic`;
- `ReaderAssetSource(sourceNamespace)`;
- `ReaderAssetAccount(sourceNamespace, securityScopeHash)`;
- `AllReaderAssetAccountsForSource(sourceNamespace)`.

For `clearAutomatic(scope)`:
1. acquire publication/revocation gate;
2. advance the affected global/scoped epoch **before** any deletion;
3. detach/remove the exact matching old metadata rows while new publication is excluded;
4. for automatic **document** keys, delete the deterministic `ChapterBlobKey` file before releasing the gate so a same-key post-clear document write cannot be unlinked by old cleanup;
5. for RICC image rows, return generation-unique `blobId`s, release the gate, then call `deleteWhenUnleased(blobId)` outside the gate;
6. schedule/recompute budget totals.

Quota transition to zero advances the global epoch before `updateQuota(0)` becomes externally visible and then reconciles toward zero. Re-enabling quota creates new authority; stale pre-zero authorities remain invalid forever.

- [ ] **Step 5: Capture an opaque automatic-document write intent before remote fetch**

Keep Downloads authority types out of `:reader`. Add a marker and compatibility seam in `ReaderDocumentStore.kt`:

```kotlin
interface ReaderDocumentDurableWriteIntent

interface ReaderDocumentStore {
    // existing read APIs

    suspend fun captureAutomaticWriteIntent(): ReaderDocumentDurableWriteIntent? = null

    suspend fun writeWithIntent(
        releaseId: ChapterReleaseId,
        fingerprint: String,
        document: ReaderDocument,
        intent: ReaderDocumentDurableWriteIntent?,
    ) {
        write(releaseId, fingerprint, document)
    }

    // existing write(...) remains for compatibility/test helpers.
}
```

In `:downloads`, `DownloadAwareReaderDocumentStore.captureAutomaticWriteIntent()` wraps the current `AutomaticCacheWriteAuthority` in a private implementation of `ReaderDocumentDurableWriteIntent`; null means durable automatic caching is disabled. `writeWithIntent()` accepts only that implementation and never captures a replacement authority when the intent is null/stale. Its legacy `write(...)` may be retained as a compatibility helper that captures immediately then delegates, but **production Reader remote flow must use `writeWithIntent` with the pre-fetch token**.

Modify `ReaderRouteExecutor` so every remote attempt captures the opaque intent immediately before entering the Task-3 source-lane/arbiter/source fetch. Return the intent beside that attempt's remote result and pass it only to persistence for the same successful attempt:

```text
captureAutomaticWriteIntent()
    -> source lane
    -> global arbiter
    -> source.fetch(candidate)
    -> validate
    -> if local-persistable: store.writeWithIntent(..., capturedIntent)
```

A failed/superseded attempt drops its intent. A hedge/next candidate captures its own intent. No cache authority is captured after remote success for that attempt. This is a cache-effect token only; it never influences route/source selection or HES health.

- [ ] **Step 6: Migrate automatic document writes onto the shared authority**

`DownloadAwareReaderDocumentStore.writeWithIntent()` becomes:

```text
unwrap the pre-fetch GlobalAutomatic authority from ReaderDocumentDurableWriteIntent
    -> null/stale/wrong token type: return success without durable cache
encode persistable ReaderDocument
reserve actual encoded bytes with captured authority
    -> denied: return success without durable cache
write complete ChapterBlob bytes
publishIfCurrent(authority, reservation) {
    cacheRepository.upsert(metadata)
}
    -> Published: keep blob
    -> Revoked/failure: delete blob best effort, release reservation
schedule async reconciliation when high watermark crossed
```

Authority is captured by `ReaderRouteExecutor` **before the corresponding remote source fetch starts**, so clear/quota-zero during network, validation, encoding, blob write, or metadata publication revokes that attempt. `writeWithIntent()` must never refresh a revoked/null token. Remove `cacheQuotaBytes` constructor/default and remove `cache.enforceQuota(cacheQuotaBytes, emptySet())`. `CacheService.store()` must not remain a bypass around `publishIfCurrent`; split it into physical/metadata helpers used under the coordinator or retire it from automatic document writes.

- [ ] **Step 7: Implement normalized eviction and hysteresis**

The coordinator snapshots document + image candidates into the exact retention order from the Global section. Victim metadata detachment is serialized with publication; image physical deletion happens via generation `blobId`/read lease, and document physical deletion uses its deterministic key safely under the gate when key replacement could race.

Reconciliation is never awaited by visible render. Crossing high watermark schedules bounded reconciliation toward low watermark. If active protections prevent reaching low watermark, report active overflow rather than looping.

- [ ] **Step 8: Implement bounded physical-pressure relief**

`relievePhysicalPressure(requiredBytes)` is synchronous but bounded and is called only after a typed `ENOSPC` from a durable automatic-cache write. It uses the last known active-protection union and the normalized cross-kind candidate order, but this emergency **retry helper only considers unprotected classes**:

```text
STALE_INVALIDATED
COLD_SPECULATIVE_IMAGE
WARM_SPECULATIVE_IMAGE
TRANSITION_SPECULATIVE_IMAGE
CURRENT_AHEAD_SPECULATIVE_IMAGE
WARM_DOCUMENT
CONSUMED_IMAGE_HISTORY
```

It does **not** evict `PROGRESS_PROTECTED_DOCUMENT`, recent image history, active consumed/interactive assets, active read leases, or explicit downloads. Process at most 32 victim rows and stop once **physically reclaimed** bytes >= `requiredBytes`. For a RICC image victim, detach metadata under the publication gate, then call `tryDeleteNowIfUnleased(blobId)` outside the gate; count its bytes only when that call returns true. A leased generation remains safely pending cleanup and contributes 0 reclaimed bytes to this retry attempt. Deterministic automatic-document victims are detached + deleted under the narrow gate exception above and may be counted when deletion succeeds. Return physically reclaimed bytes even when insufficient; the caller still performs exactly one write retry and then degrades to transient render. Broader protection degradation under a platform `EMERGENCY` signal remains Task 15 maintenance policy, not part of the visible-request retry loop.

- [ ] **Step 9: Run GREEN**

```bash
./gradlew :downloads:testDebugUnitTest :reader:testDebugUnitTest \
  --tests '*AutomaticCacheBudgetCoordinatorTest*' \
  --tests '*CacheServiceTest*' \
  --tests '*DownloadAwareReaderDocumentStoreTest*' \
  --tests '*ReaderRouteExecutorAdaptiveTest*' \
  --no-daemon
```
Expected: PASS, including barrier-controlled clear/publication races.

- [ ] **Step 10: Commit**

```bash
git add downloads reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentStore.kt \
  reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt \
  reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt
git commit -m "downloads: unify automatic cache authority"
```

---

### Task 7: Wire Settings, Progress Truth, Storage Summary, and Automatic-cache Policy Projection

**Files:**
- Create: `app/src/main/kotlin/app/openstory/cache/AutomaticCachePolicyCoordinator.kt`
- Create: `app/src/test/kotlin/app/openstory/cache/AutomaticCachePolicyCoordinatorTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/OpenStoryApplication.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/DownloadModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/settings/AppStorageSummaryAdapter.kt`
- Create: `app/src/test/kotlin/app/openstory/settings/AppStorageSummaryAdapterTest.kt`.

**Interfaces:**
- Consumes `AppSettingsRepository.settings`, `ReadingProgressRepository.observeAll()`.
- Produces live quota/progress policy into `AutomaticCacheBudgetCoordinator` without adding `:downloads -> :settings`.

- [ ] **Step 1: Write RED policy projection tests**

Test exact transitions:
- initial 256 MiB settings reaches budget coordinator;
- settings change to 64 MiB updates quota and schedules reconcile;
- quota change to 0 revokes the shared global automatic-cache write authority before new reservations/publications;
- progress rows project release IDs from existing `ReadingProgressRepository`; incomplete/currently-progressed releases are protected, completed rows are not permanent hard pins;
- storage summary automatic bytes equals unified document + image committed usage.

- [ ] **Step 2: Run RED**

```bash
./gradlew :app:testDebugUnitTest \
  --tests '*AutomaticCachePolicyCoordinatorTest*' \
  --tests '*AppStorageSummaryAdapterTest*' \
  --no-daemon
```
Expected: FAIL.

- [ ] **Step 3: Implement app-owned policy coordinator**

Use application scope to combine settings and progress streams, normalize settings, and invoke the Downloads-owned coordinator. `AutomaticCacheBudgetCoordinator.updateQuota(0)` performs the global authority revocation atomically; the app coordinator must not maintain a second epoch. The coordinator never persists a second progress database.

For V1 progress projection use:

```kotlin
progress.filter { it.completedAtEpochMillis == null }
    .mapTo(linkedSetOf()) { it.releaseId }
```

This protects currently in-progress releases without converting every completed historical chapter into a permanent progress pin; consumed RICC history has its own retention classes.

- [ ] **Step 4: Start exactly once from `OpenStoryApplication`**

Inject `AutomaticCachePolicyCoordinator` and call `start(applicationScope)` from `onCreate()`. `start` is idempotent in tests.

- [ ] **Step 5: Update Settings storage summary**

`AppStorageSummaryAdapter` reads `AutomaticCacheBudgetCoordinator.snapshot()` for automatic bytes, while explicit download totals remain sourced from Downloads. Do not sum only `CacheRepository.entries()` anymore because that would omit RICC images.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :app:testDebugUnitTest \
  --tests '*AutomaticCachePolicyCoordinatorTest*' \
  --tests '*AppStorageSummaryAdapterTest*' \
  --no-daemon
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/app/openstory/cache app/src/main/kotlin/app/openstory/OpenStoryApplication.kt app/src/main/kotlin/app/openstory/di app/src/main/kotlin/app/openstory/settings app/src/test
git commit -m "app: wire automatic cache policy truth"
```

---

### Task 8: Implement Downloads-owned RICC Store, Four-state Presence, Shared Anti-resurrection, and Corruption Repair

**Files:**
- Create: `downloads/src/main/kotlin/app/openstory/downloads/assets/DownloadReaderAssetStore.kt`
- Create: `downloads/src/main/kotlin/app/openstory/downloads/assets/ReaderAssetEvictionMapper.kt`
- Create: `downloads/src/test/kotlin/app/openstory/downloads/assets/DownloadReaderAssetStoreTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/DownloadModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`

**Interfaces:**
- Implements `ReaderAssetStorePort` from Task 2 using metadata Task 4, generation-safe blobs Task 5, and the **same** budget/write authority from Task 6.
- Must not create `ReaderAssetPolicyEpoch` or any parallel image-only quota/epoch state.
- Uses injected `Clock` for persisted timestamps, `MonotonicClock` for in-memory access-touch throttling, and existing `StorageWriteAdmission` for proactive app-private reserve checks.

- [ ] **Step 1: Write RED store correctness tests**

Cover:
- batch inspect returns explicit result for every key;
- metadata+blob present -> `LOCAL_AVAILABLE`;
- no metadata -> `LOCAL_MISSING`;
- Room/filesystem exception -> `LOCAL_UNAVAILABLE`;
- metadata without blob -> detach invalid metadata + `LOCAL_MISSING`;
- checksum mismatch on `openLocal` -> `Corrupt`, metadata detached, generation blob scheduled for deletion, one remote repair allowed later;
- an `Available` read lease delays physical unlink until its lease closes;
- `captureDurableWriteAuthority()` returns null for every `TRANSIENT_ONLY` manifest (including trusted-stable public identity whose plugin permission is NON_PERSISTENT) and wraps the Task-6 shared authority only for `DURABLE_AUTOMATIC`;
- clear/quota-zero/account invalidation after authority capture but before final publication yields `Bypassed` and cannot resurrect metadata;
- barrier race between final authority check and Room visibility is impossible because `publishIfCurrent` owns both;
- blob write succeeds but metadata fails -> generation blob is orphan-cleaned/reconciliation-safe;
- valid remote payload remains transiently renderable when persistent commit degrades;
- first blob write `NoSpace` -> bounded `relievePhysicalPressure(payloadBytes)` -> exactly one second blob write; second success publishes normally;
- first and second blob writes `NoSpace` -> no third write, reservation is released, result is `Degraded(CacheStorageUnavailable)`, remote payload remains renderable;
- ENOSPC relief encountering a leased image victim does not wait for/deallocate that lease and does not count its bytes as reclaimed; it may inspect other victims within the 32-row bound;
- non-ENOSPC `Unavailable` -> no eviction retry loop, one degraded result;
- `createdAt`/`lastAccessedAt`/`lastConsumedAt` come only from injected wall `Clock`;
- five-minute access-touch suppression uses `MonotonicClock`, so a wall-clock jump forward/backward does not alter throttle eligibility; after process reconstruction the in-memory monotonic throttle is empty and the first verified access may persist one fresh wall timestamp.

- [ ] **Step 2: Run RED**

```bash
./gradlew :downloads:testDebugUnitTest --tests '*DownloadReaderAssetStoreTest*' --no-daemon
```
Expected: FAIL.

- [ ] **Step 3: Map Reader security/persistence facts to the shared write authority**

`captureDurableWriteAuthority(facts)`:
- returns null immediately when `facts.persistenceMode == TRANSIENT_ONLY`;
- defensively rejects/returns null if durable mode is paired with `identityMode == NON_PERSISTENT` or `securityScope == NonPersistentPrivate`;
- for valid `DURABLE_AUTOMATIC + Public`, maps to `AutomaticCacheWriteScope.ReaderAssetSource(sourceNamespace)`;
- for valid durable account scope, maps to `AutomaticCacheWriteScope.ReaderAssetAccount(sourceNamespace, sha256(stableNonSecretNamespace))`;
- delegates authority capture to `AutomaticCacheBudgetCoordinator`.

Only the hash of a safe account namespace reaches metadata/budget scope facts. Credentials/cookies/tokens never enter this path.

- [ ] **Step 4: Implement atomic generation publication**

For persistent commit:

```text
validate bounded payload
reserve actual payload bytes with captured shared authority
allocate generation-unique ReaderAssetBlobId
write complete physical blob atomically
  -> Stored: continue
  -> NoSpace: budget.relievePhysicalPressure(payload.sizeBytes), then retry writeAtomic exactly once
      -> Stored: continue
      -> NoSpace/Unavailable: release reservation, return Degraded(CacheStorageUnavailable), keep remote payload transient
  -> Unavailable: release reservation, return Degraded(CacheStorageUnavailable), keep remote payload transient
budget.publishIfCurrent(authority, reservation) {
    metadataRepository.upsert(
        logical key hash/version,
        story/chapter/release/source,
        hashed security scope,
        variant/identity mode/persistence mode,
        image-set hash/page-identity hash,
        ordinal,
        generation blob id,
        byte size,
        mandatory SHA-256 local checksum,
        optional safe source integrity,
        timestamps
    )
}
```

On `Revoked` or metadata failure, release reservation and `deleteWhenUnleased(generationBlobId)` best effort. No independent “revalidate then upsert” code is allowed. `StorageWriteAdmission.canStore(payload.sizeBytes)` may proactively bypass persistence when the app-private reserve is already violated, but it does not replace typed ENOSPC handling because free space can race between admission and write.

- [ ] **Step 5: Implement local open/repair and touch semantics**

`inspect()` performs bounded metadata/existence checks and returns the four-state map. `openLocal()` acquires a physical read lease, verifies mandatory local SHA-256 before exposing bytes, and returns:
- `Available(lease)` for verified bytes;
- `Missing` after metadata-without-blob repair;
- `Corrupt` after checksum failure and detachment;
- `Unavailable` for authority/storage failure.

Inject `Clock` and `MonotonicClock` into `DownloadReaderAssetStore`. Physical commit uses `Clock.nowEpochMillis()` for `createdAt` and initial `lastAccessedAt`; `markConsumed(key)` obtains its own wall timestamp from the same `Clock`. Generic access touches keep a process-local `ReaderAssetKeyHash -> lastTouchMonotonicNanos` map and suppress writes until `MonotonicClock` advances by 300,000 ms. When a touch is due, persist `lastAccessedAt = Clock.nowEpochMillis()`. Never compare wall timestamps to decide whether the throttle duration elapsed.

- [ ] **Step 6: Delegate clear/reconcile correctly**

`ReaderAssetStorePort.clearAutomatic(scope)` maps to Task-6 `AutomaticCacheInvalidationScope`:
- `AllAutomatic` clears **automatic documents + RICC images**;
- `Source` clears matching RICC image rows;
- `Account` clears the one hashed account scope;
- `AllAccountScopesForSource` clears every account-scoped RICC row for that source without needing credential-derived identity.

`reconcile(activeProtections)` first replaces the store/budget coordinator's latest protection snapshot atomically, then schedules/coalesces any needed quota maintenance; callers do not wait for victim deletion. `releaseSession()` never stores semantic session state; it only triggers bounded maintenance after Task 11 has already recomputed/published the remaining union.

- [ ] **Step 7: Run GREEN**

```bash
./gradlew :downloads:testDebugUnitTest --tests '*DownloadReaderAssetStoreTest*' --no-daemon
```
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add downloads/src/main/kotlin/app/openstory/downloads/assets downloads/src/test/kotlin/app/openstory/downloads/assets app/src/main/kotlin/app/openstory/di
git commit -m "downloads: implement reader asset store"
```

---

### Task 9: Add Remote-only Single-flight, Priority Promotion, Bounded Acquisition, and Retry Taxonomy

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetSingleFlight.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetLoader.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetSingleFlightTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetLoaderTest.kt`

**Interfaces:**
- Consumes `ReaderAssetStorePort`, `ReaderAssetDeliveryPort`, `ContentFetchArbiter`, and a process-owned persistence `CoroutineScope`.
- `ReaderAssetSingleFlight` collapses **remote encoded-byte acquisition only**. It never shares `ReaderAssetReadLease` objects across consumers.
- Produces `ReaderAssetLoadOutcome` from Task 2 for Coil/working-set callers.

- [ ] **Step 1: Write RED single-flight/retry/lifetime tests**

Cover:
- local hit returns a fresh `ReaderAssetReadLease` directly and never enters the single-flight map;
- two local consumers of the same key receive two independently closable read leases;
- UI + viewport + transition missing the same key -> one remote body;
- speculative remote demand promoted to `CRITICAL` -> same `ContentFetchDemand`, same underlying fetch, no restart;
- security scope/variant/key differences never join;
- `invalidateSecurityScopedSource(source)` hard-cancels matching non-public in-flight producers/waiters (including CRITICAL), but never cancels public entries; this is a security-boundary cancellation, not QoS preemption;
- a revoked security-scoped producer cannot deliver its completed payload to a waiter after invalidation and its background durable publication is harmless/revoked by Task-6 authority;
- all consumers cancel before remote completion -> stale speculative producer may cancel;
- visible `UNKNOWN` performs one targeted local inspection before remote;
- `LOCAL_UNAVAILABLE` takes one cache-bypass visible remote path without local retry loop or durable write;
- cache `Corrupt` invalidates locally and allows exactly one remote repair generation;
- write authority is captured by the single-flight **leader before arbiter/network acquisition**;
- clear/quota-zero during network makes later persistence `Bypassed` but all still-valid consumers receive remote payload;
- network payload is returned to visible consumer without waiting for disk/quota reconciliation;
- after network success, the single-flight entry remains joinable until the one background durable commit attempt completes, preventing an immediate transition consumer from starting a second fetch during the network->disk handoff;
- 16 MiB exact payload accepted, 16 MiB +1 rejected;
- content-length underreports but streamed count exceeds max -> reject;
- one transient retry after 250 ms, then failure; cancellation aborts retry delay;
- transient retry timing advances only with the coroutine test scheduler/monotonic scheduling; changing wall `Clock` has no effect;
- `DeliveryRejected` is not silently reclassified here;
- preempted/superseded never reaches HES health.

- [ ] **Step 2: Run RED**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderAssetSingleFlightTest*' \
  --tests '*ReaderAssetLoaderTest*' \
  --no-daemon
```
Expected: FAIL.

- [ ] **Step 3: Implement remote-only process-scoped single-flight**

```kotlin
class ReaderAssetSingleFlight {
    suspend fun acquireRemote(
        key: ReaderPageAssetKey,
        priority: ContentFetchPriority,
        consumer: ReaderAssetConsumerToken,
        producer: suspend (ContentFetchDemand) -> ReaderAssetRemoteOutcome,
        afterSuccess: (ReaderAssetPayload) -> Job?,
    ): ReaderAssetRemoteOutcome

    fun invalidateSecurityScopedSource(sourceNamespace: ReaderAssetSourceNamespace)
}
```

Per-key state owns:
- one `ContentFetchDemand`;
- one producer Job;
- a result `Deferred<ReaderAssetRemoteOutcome>`;
- consumer registrations/highest demand;
- optional post-success persistence Job.

Joiners monotonically promote the same demand. Waiters receive the remote payload as soon as producer completes. The map entry is removed only after producer termination **and** the single leader-created `afterSuccess` persistence Job completes (or is absent). Waiters do not await that persistence Job.

If all consumers leave before remote completion and work remains purely speculative, producer cancellation is allowed. `invalidateSecurityScopedSource()` is stronger: it atomically marks matching non-public entries security-invalid, cancels their producer and post-success persistence Job regardless of demand priority, and completes unresolved waiters with exactly `ReaderAssetFailure.RouteInvalidated`. A producer that races to completion must check the invalidation bit before completing its shared result. Public entries are not matched. Once ordinary valid immutable bytes have completed, background persistence may otherwise continue across viewport staleness; Task-6 authority prevents policy resurrection.

- [ ] **Step 4: Implement loader local fast path before single-flight**

```text
openLocal
  Available -> ReaderAssetLoadOutcome.Local(fresh lease)
  Corrupt   -> invalidate/one repair budget -> remote
  Missing   -> remote
  Unavailable -> CRITICAL/INTERACTIVE cache-bypass remote; speculation suppressed
if manifest presence is UNKNOWN:
  targeted inspect exactly once -> reopen/remote according to resolved state
```

No local lease is stored in or returned from `ReaderAssetSingleFlight`.

- [ ] **Step 5: Implement bounded remote producer + background commit handoff**

The single-flight leader:
1. captures `ReaderAssetDurableWriteAuthority?` before remote admission;
2. uses the shared `ContentFetchDemand` with `ContentFetchArbiter`;
3. calls `delivery.fetch()` and keeps the entire bounded body read inside arbiter admission;
4. returns `ReaderAssetRemoteOutcome.Success(payload)` immediately to waiters;
5. in `afterSuccess`, launches exactly one process-scope `store.commit(facts, authority, payload)` if authority was non-null.

The commit job is not a network consumer and never holds arbiter admission. `LOCAL_UNAVAILABLE`/non-persistent cache-bypass uses `authority = null`.

- [ ] **Step 6: Implement bounded transient retry only**

Retry exactly once after 250 ms for `TransportUnavailable(retryable=true)` using cancellable coroutine `delay`; tests advance the coroutine scheduler, not wall `Clock`. Do not retry `DeliveryRejected`, `Unauthorized`, `AssetTooLarge`, `InvalidPayload`, or route semantics here. Locator/release recovery belongs to Task 14 and is generation-bounded separately.

- [ ] **Step 7: Run GREEN**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderAssetSingleFlightTest*' \
  --tests '*ReaderAssetLoaderTest*' \
  --no-daemon
```
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add reader/src/main/kotlin/app/openstory/reader/assets reader/src/test/kotlin/app/openstory/reader/assets
git commit -m "reader: add single flight image acquisition"
```

---

### Task 10: Produce Committed/Prefetched Manifests and Tie Asset Session Lifetime to Reader Session Ownership

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetManifestFactory.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetSessionState.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetSessionPort.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/content/ReaderLoadResult.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSessionContracts.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/PrefetchCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSessionFactory.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/PrefetchCoordinatorTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteSessionStateTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetManifestFactoryTest.kt`

**Interfaces:**
- Produces `COMMITTED_MANIFEST` and `ReaderPrefetchedDocumentArtifact` without changing HES route choice.
- `ReaderAssetManifestFactory` is the **single authority** that resolves Task-1 source policy into Task-2 effective identity/security mode, locator fingerprints, runtime-isolation scope, image-set namespace, and page keys.
- Produces `ReaderAssetSessionPort`, a non-blocking session-to-asset-runtime seam implemented by `ReaderAssetCoordinator` in Task 11.
- Session close calls the port's `releaseSession(sessionId)`; foreground acceptance registers the committed manifest before the result can become Reader-visible.

- [ ] **Step 1: Write RED commit/prefetch/supersession tests**

Prove:
- remote successful image load carries source trust policy **and exact producing `sourcePluginId`** from the exact selected source;
- manifest namespace is `ReaderAssetSourceNamespace.fromPluginId(sourcePluginId)`; package/plugin version or display metadata is not an input, and a producing-source `PluginId` mismatch with the selected release is rejected as an internal route/provenance invariant instead of manufacturing a different namespace;
- manifest derives full stable IDs + release/source/security/variant and not truncated UI ID alone;
- manifest factory derives one image-set namespace from the **complete ordered** identity/fingerprint list before deriving any page key;
- `TRUSTED_STABLE` base-URL rotation preserves image-set/page keys, while page reorder/count/stable-ID change does not;
- `LOCATOR_BOUND` uses Task-2 locator normalization and changed fingerprint re-keys set/page;
- effective non-persistent manifests receive fresh runtime-isolation scopes so otherwise-identical sessions/manifests cannot cross-join;
- foreground manifest becomes committed only after `ReaderRouteSession` accepts the foreground commit;
- accepted image foreground result calls `ReaderAssetSessionPort.registerCommitted(sessionId, proposedManifestRevision, manifest)` synchronously **after semantic commit acceptance but before the committed result returns to ViewModel/UI**, then uses the returned effective revision;
- if a same-release delivery refresh advanced the coordinator revision between semantic commits, the next foreground registration returns a strictly newer effective revision rather than reusing the route-session proposal;
- registration itself does not await Room/filesystem/network and a fake port can prove event order `semantic commit -> asset registration -> ViewModel result`;
- same-chapter document/locator reload advances `manifestRevision` so stale page presentation cannot target the refreshed manifest, while `chapterWindowRevision`/history slides only when canonical chapter actually changes;
- failed/requested target never slides asset window;
- N+1 success publishes `ReaderPrefetchedDocumentArtifact` with session ID, prefetch token/generation identity, graph revision, target chapter, selected release, document, source policy;
- artifact never mutates saved Reader identity or marks consumed;
- stale graph/token artifact is rejected;
- owner Job completion closes asset session idempotently.

- [ ] **Step 2: Run RED**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderAssetManifestFactoryTest*' \
  --tests '*PrefetchCoordinatorTest*' \
  --tests '*ReaderRouteSessionStateTest*' \
  --no-daemon
```
Expected: FAIL.

- [ ] **Step 3: Propagate source policy through remote success only**

Add `imageSourcePolicy: ReaderImageSourcePolicy?` and `sourcePluginId: PluginId?` to `ReaderLoadResult.Success`. Local persistable text documents set both to null. Remote source success copies both values from the exact `ReaderDocumentSource` used for that attempt. For image-bearing remote success, require `sourcePluginId == selectedRelease.pluginId`; a mismatch is an internal route/provenance violation and must not be repaired by inventing a namespace.

`ReaderAssetManifestFactory` applies **only** the Task-2 algorithms and may not infer trust from ID/URL shape. It derives `sourceNamespace` exactly once as `ReaderAssetSourceNamespace.fromPluginId(sourcePluginId)` from the exact producing source propagated in `ReaderLoadResult.Success`; selected-release `pluginId` is a consistency assertion, not a second derivation path. Plugin/package version, display name, host, and adapter class are deliberately absent from factory inputs. Current production has no stable non-secret account namespace; therefore any `ACCOUNT_SCOPED` declaration resolves to `NonPersistentPrivate + TRANSIENT_ONLY` plus a fresh runtime-isolation scope until a reviewed adapter exists. The factory first normalizes all locator fingerprints, resolves identity mode, security scope, and **persistence mode independently**, allocates exactly one runtime scope whenever persistence is transient, derives one image-set namespace from the full ordered descriptor input, and only then derives page keys. Tests reject partial-set/page-first key construction, arbitrary namespace injection, and any `DURABLE_AUTOMATIC` result whose plugin persistence permission/security scope does not authorize it.

V1 has no source-version field. A routine plugin update therefore keeps existing source namespace/key compatibility. If a source deliberately changes the semantic identity contract associated with its canonical `PluginId`, that change must first revise the normative RICC contract and introduce a reviewed key-schema/namespace-version migration; implementation must not append package/plugin version opportunistically.

Create the non-blocking seam:

```kotlin
interface ReaderAssetSessionPort {
    fun registerCommitted(
        sessionId: ReaderSessionId,
        proposedManifestRevision: Long,
        manifest: ReaderAssetChapterManifest,
    ): Long
    fun acceptPrefetchedArtifact(artifact: ReaderPrefetchedDocumentArtifact)
    fun releaseSession(sessionId: ReaderSessionId)

    companion object {
        val NO_OP: ReaderAssetSessionPort = object : ReaderAssetSessionPort {
            override fun registerCommitted(
                sessionId: ReaderSessionId,
                proposedManifestRevision: Long,
                manifest: ReaderAssetChapterManifest,
            ): Long = proposedManifestRevision
            override fun acceptPrefetchedArtifact(artifact: ReaderPrefetchedDocumentArtifact) = Unit
            override fun releaseSession(sessionId: ReaderSessionId) = Unit
        }
    }
}
```

Extend `ReaderForegroundResult.Committed` with immutable `assetManifest: ReaderAssetChapterManifest?` and `assetManifestRevision: Long?`. The manifest is derived before the result leaves coordinator, but `ReaderRouteSession` is the authority that accepts the foreground generation/plan. On an accepted image-bearing commit it proposes `lastKnownManifestRevision + 1`, invokes `assetSessionPort.registerCommitted(...)` synchronously, stores the **effective revision returned by the port**, and returns that value in `Committed`. The real Task-11 coordinator guarantees the returned revision is strictly newer than its current session revision; the Task-10 `NO_OP` compatibility port returns the proposal unchanged. This prevents a same-release delivery refresh from advancing a hidden second counter that the next semantic commit could accidentally reuse. The port method must be non-suspending/non-blocking; Task 11 launches presence inspection after in-memory registration. Chapter history/window revision is separate and changes only when canonical chapter changes.

- [ ] **Step 4: Add prefetch artifact handoff**

`ReaderRouteCoordinator.executePrefetch()` returns `ReaderPrefetchedDocumentArtifact?` rather than discarding `executeAdaptive()` success. `PrefetchCoordinator` sends it to `assetSessionPort.acceptPrefetchedArtifact()` only while token/target/graph facts remain current. This call never commits Reader semantic state and must remain non-blocking; Task 11 decides whether bounded transition acquisition is eligible.

- [ ] **Step 5: Add explicit close lifecycle**

`ReaderRouteSession` implements idempotent `close()` that cancels prefetch and invokes `assetSessionPort.releaseSession(sessionId)`. Constructor/factory wiring uses `ReaderAssetSessionPort.NO_OP` as a compatibility default until Task 11 installs the process-scoped coordinator; this keeps intermediate task commits buildable without changing semantics. `ReaderRouteSessionFactory.create(..., prefetchScope)` registers `prefetchScope.coroutineContext[Job]?.invokeOnCompletion { session.close() }`. `ReaderViewModel.onCleared()` also closes explicitly as a belt-and-suspenders owner teardown; duplicate close is harmless.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderAssetManifestFactoryTest*' \
  --tests '*PrefetchCoordinatorTest*' \
  --tests '*ReaderRouteSessionStateTest*' \
  --tests '*ReaderChapterGraphInvalidationTest*' \
  --no-daemon
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add reader
git commit -m "reader: register image asset manifests"
```

---

### Task 11: Implement Sliding Working Set, Multi-session Protections, and Bounded Prefetch Planner

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderViewportSnapshot.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetWorkingSetPolicy.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetPrefetchPlanner.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetCoordinator.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetWorkingSetPolicyTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetPrefetchPlannerTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetCoordinatorTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`

**Interfaces:**
- Consumes manifests Task 10, loader Task 9, store Task 8, network facts existing.
- Produces `requestPage`, `updateViewport`, `assetPresented`, `acceptPrefetchedArtifact`, `releaseSession`, `observeCommittedManifest`, and guarded same-selected-release `replaceDeliveryManifest` for feature/session/app.

- [ ] **Step 1: Write RED working-set tests**

Cover exact policy:
- mixed text/image viewport works on asset keys/ordinals, not Lazy item index;
- text-only document emits no RICC work;
- `registerCommitted` synchronously installs the in-memory manifest/revision before returning and only then launches bounded batch `store.inspect()` asynchronously; registration never waits for local inspection;
- same-chapter manifest replacement updates manifest revision without pushing the chapter into recent history; actual chapter change advances chapter-window history exactly once;
- current horizon stays rolling 4 and never queues whole chapter remainder;
- 2 behind are memory-prewarm hints only;
- OFFLINE schedules no remote asset work; retained local hits remain usable and a missing visible asset surfaces local/offline delivery failure without HES-health penalty;
- metered allows visible + max 2 near-ahead, no transition speculation;
- UNKNOWN allows visible/bounded interactive, no speculative work;
- unmetered enables bounded current ahead; next-opening remains disabled below 8000 bp;
- `8000..8999` on unmetered admits only the first next-opening asset into the transition frontier;
- `>=9000` on unmetered expands the transition frontier to max 4 opening assets; every next-opening fetch remains `SPECULATIVE`, with the process-wide active concurrency cap of 1 enforced by Task 3;
- next opening only comes from a valid prefetched manifest;
- direct Ch10 -> Ch50 history is Ch10/Ch50 while graph-next speculation targets Ch51;
- two sessions union protections; closing A preserves B;
- committed/viewport/presentation/history changes recompute the process union in memory immediately and launch a coalesced `store.reconcile(currentUnion)` update; a quota reconciliation started after that store update sees active/current/recent protections and cannot evict them as ordinary cold victims;
- rapid viewport frames do not synchronously call storage per frame; distinct accepted asset snapshots coalesce protection publication on the coordinator scope;
- stale viewport/chapter revision completion may retain valid bytes but cannot chain more work;
- `replaceDeliveryManifest(sessionId, expectedRevision, refreshedManifest)` succeeds only for the still-current session/revision and the same canonical chapter + selected release; it increments manifest revision without sliding chapter history, resets new keys to `UNKNOWN`, and publishes one new committed-manifest snapshot;
- stale or release/chapter-mismatched delivery replacement is rejected without mutating session state.

- [ ] **Step 2: Run RED**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderAssetWorkingSetPolicyTest*' \
  --tests '*ReaderAssetPrefetchPlannerTest*' \
  --tests '*ReaderAssetCoordinatorTest*' \
  --no-daemon
```
Expected: FAIL.

- [ ] **Step 3: Implement viewport/session state**

`ReaderAssetCoordinator` is process-scoped, implements `ReaderAssetSessionPort`, and keeps a map `ReaderSessionId -> ReaderAssetSessionState`; there is no process-global current chapter. Session state stores committed manifest + manifest revision, recent committed history depth 2, viewport revision, chapter-window revision, active protections, local-presence map, and prefetched artifact reference.

Expose an immutable stream for feature/runtime synchronization:

```kotlin
data class ReaderCommittedAssetManifestSnapshot(
    val sessionId: ReaderSessionId,
    val manifestRevision: Long,
    val manifest: ReaderAssetChapterManifest,
)

sealed interface ReaderDeliveryManifestReplacement {
    data class Applied(
        val snapshot: ReaderCommittedAssetManifestSnapshot,
    ) : ReaderDeliveryManifestReplacement
    data object Superseded : ReaderDeliveryManifestReplacement
    data object SemanticRouteMismatch : ReaderDeliveryManifestReplacement
}

fun observeCommittedManifest(
    sessionId: ReaderSessionId,
): Flow<ReaderCommittedAssetManifestSnapshot>

fun replaceDeliveryManifest(
    sessionId: ReaderSessionId,
    expectedManifestRevision: Long,
    refreshedManifest: ReaderAssetChapterManifest,
): ReaderDeliveryManifestReplacement
```

`replaceDeliveryManifest` is an **asset-delivery state replacement**, not a semantic Reader commit. It is accepted only if the session/revision is current and `canonicalChapterId + selectedReleaseId + sourceNamespace + contentVariant` still match the committed semantic route; `sourceNamespace` here is the already-derived typed canonical producing-`PluginId` namespace and is never recomputed from refreshed URLs/version metadata. On success it allocates the next manifest revision, replaces the committed asset manifest, preserves recent chapter history/chapter-window revision, initializes any changed keys to `UNKNOWN`, recomputes/publishes the protection union asynchronously, launches bounded inspection, and emits the new snapshot. This is required for `LOCATOR_BOUND`, where one locator change can change the image-set namespace and therefore many page keys. It never aliases old keys to new keys.

`registerCommitted()` performs only synchronized/in-memory state work. For an existing session it computes `effectiveRevision = maxOf(currentManifestRevision + 1, proposedManifestRevision)`; for first registration it accepts a positive proposal (normally 1). It replaces the committed manifest at that effective revision, slides recent history **only if canonical chapter changed**, initializes keys to `UNKNOWN`, recomputes the in-memory union of all session protections, launches a coalesced `store.reconcile(currentUnion)` plus bounded batch `store.inspect(manifest.keys)` on the coordinator scope, and returns `effectiveRevision` immediately. Neither storage call is awaited by foreground registration. Inspection completion updates only the still-current manifest revision. This makes coordinator delivery refresh and route-session semantic commits share one monotonic revision sequence without moving route authority into RICC. This is the concrete R2 §14 ordering: semantic foreground commit accepted -> in-memory manifest/protection facts registered and async inspection/protection publication launched -> result may become Reader-visible.

- [ ] **Step 4: Implement rolling planner**

Planner inputs only immutable snapshot facts and outputs bounded asset intents:

```kotlin
data class ReaderAssetPlan(
    val interactive: List<ReaderPageAssetDescriptor>,
    val currentAhead: List<ReaderPageAssetDescriptor>,
    val transition: List<ReaderPageAssetDescriptor>,
)
```

Every output list is deduplicated and capped by policy. No method accepts “remaining chapter” as an implicit unlimited range. Transition policy is exact: below 8000 bp `transition=[]`; 8000..8999 bp may contain only next ordinal 0; >=9000 bp may contain ordinals 0..3. These descriptors are always submitted as `ContentFetchPriority.SPECULATIVE`; “stronger near-end transition” means a wider eligible frontier, **not** reclassifying next-chapter work as `PREFETCH` and violating the frozen priority taxonomy. Task 3 enforces one active process-wide SPECULATIVE fetch.

- [ ] **Step 5: Implement consumption and retention promotion**

`assetPresented()` checks session + committed manifest revision + current viewport visibility before promoting. It updates memory class immediately, recomputes the union and schedules a coalesced `store.reconcile(currentUnion)`, then calls `store.markConsumed(key)` asynchronously/coalesced; the durable store owns wall-clock timestamping. A completed prefetch alone never sets consumed. `updateViewport()` follows the same rule after accepting a distinct snapshot: memory protection classes change first, then one coalesced async union publication; no Room/store call occurs per Compose frame.

- [ ] **Step 6: Implement prefetched-artifact supersession and session release**

`acceptPrefetchedArtifact()` revalidates session/token/graph identity before storing the artifact reference; stale artifacts are ignored and cannot start follow-on work. `updateViewport()` increments `viewportRevision`, unregisters/cancels obsolete speculative consumer interest, and permits already-completed valid immutable bytes to finish persistence without chaining old-revision work.

`releaseSession(sessionId)` is idempotent and performs this exact sequence:

```text
remove only that session's runtime state
cancel/unregister that session's outstanding interactive/prefetch/speculative consumers
recompute active-protection union from all remaining sessions
launch store.reconcile(remainingUnion)
call store.releaseSession(sessionId) as bounded maintenance trigger
```

It never clears another session's protection and never persists session/window revision state. Storage calls run on coordinator scope after the in-memory removal; teardown does not block ViewModel destruction.

- [ ] **Step 7: Run GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderAsset*Test*' --no-daemon
```
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add reader/src/main/kotlin/app/openstory/reader/assets reader/src/test/kotlin/app/openstory/reader/assets app/src/main/kotlin/app/openstory/di/ReaderModule.kt
git commit -m "reader: add sliding image working set"
```

---

### Task 12: Add App OkHttp Delivery and Coil Fetcher Integration

**Files:**
- Create: `app/src/main/kotlin/app/openstory/reader/assets/OkHttpReaderAssetDelivery.kt`
- Create: `app/src/main/kotlin/app/openstory/reader/assets/ReaderAssetCoilFetcher.kt`
- Create: `app/src/main/kotlin/app/openstory/reader/assets/ReaderAssetImageLoaderInstaller.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetLoadException.kt`
- Create: `app/src/test/kotlin/app/openstory/reader/assets/OkHttpReaderAssetDeliveryTest.kt`
- Create: `app/src/test/kotlin/app/openstory/reader/assets/ReaderAssetCoilFetcherTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/OpenStoryApplication.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Implements `ReaderAssetDeliveryPort` and Coil `Fetcher` for `ReaderPageAssetRequest`.
- `ReaderAssetLoadException` is Reader-owned so `:feature:reader` can inspect typed asset failures from Coil without depending on `:app`.
- `app` adds direct `implementation(libs.coil.compose)` for Coil core/fetcher APIs and `testImplementation(libs.okhttp.mockwebserver)` for transport tests; OkHttp client is already direct.
- No generic Coil disk cache ownership is introduced.

- [ ] **Step 1: Write RED transport/fetcher tests with MockWebServer**

Cover:
- body is fully read/closed before delivery call returns, so arbiter scope can cover body lifetime;
- `Content-Length >16 MiB` fails before body accumulation;
- streamed body crossing 16 MiB fails even if header is absent/incorrect;
- only HTTPS locators are accepted; redirects are not followed because locator identity belongs to the committed manifest;
- OkHttp must not hide an HTTP 408 or process `503 Retry-After` into a hidden follow-up/parser side effect inside one delivery call; the Reader loader owns the bounded retry budget;
- obvious HTML/JSON error payload is rejected as invalid image payload;
- 408/429/5xx and retryable I/O -> retryable `TransportUnavailable`; generic terminal delivery 401/403/404 (and other terminal 4xx without an explicit authenticated-delivery contract) -> `DeliveryRejected(status)`; the V1 app image adapter injects no plugin auth headers and therefore must **not** infer `Unauthorized`, `DeliveryLocatorStale`, or `AssetNotFound` from a status code alone;
- Coil fetcher uses `ReaderPageAssetRequest`, stable asset-key memory cache key, and no disk cache;
- local outcome holds the read lease while Coil consumes the source and releases it exactly once on close or EOF materialization, including Coil's `ImageSource.file()` temp-file path;
- transient remote outcome can decode from bounded encoded bytes even when durable commit was denied.

- [ ] **Step 2: Run RED**

```bash
./gradlew :app:testDebugUnitTest \
  --tests '*OkHttpReaderAssetDeliveryTest*' \
  --tests '*ReaderAssetCoilFetcherTest*' \
  --no-daemon
```
Expected: FAIL.

- [ ] **Step 3: Add exact app dependencies and implement delivery adapter**

In `app/build.gradle.kts`, add:

```kotlin
implementation(libs.coil.compose)
testImplementation(libs.okhttp.mockwebserver)
```

Use a dedicated injected `OkHttpClient` or an app-owned shared client with no plugin authentication header injection. `fetch()` executes one logical delivery request and consumes/closes the body synchronously inside the suspend call; it returns a bounded `ReaderAssetPayload`, never an escaping `ResponseBody`. Disable origin/proxy authenticators, strip credential headers at the final network boundary, disable redirects and OkHttp connection/status retries that would hide another delivery attempt, and remove `Retry-After` from 503 responses before OkHttp can turn it into a follow-up/parser side effect. `ReaderAssetLoader` remains the sole owner of the 250 ms one-retry policy. Generic HTTP status mapping stays transport-level: retryable transport statuses become retryable transport failure, while terminal delivery 401/403/404 remain `DeliveryRejected(status)` for Task 14's bounded same-release semantic refresh. `Unauthorized` is reserved for a layer with explicit authenticated-delivery/source semantics; the V1 image CDN adapter does not invent it from HTTP status alone.

- [ ] **Step 4: Implement custom Coil fetcher**

Fetcher accepts only `ReaderPageAssetRequest` and asks `ReaderAssetCoordinator.requestPage()`. Map local lease to a Coil source whose close callback closes the RICC read lease; map transient bounded bytes to an in-memory source. Map `ReaderAssetLoadOutcome.Failure` to Reader-owned `ReaderAssetLoadException` so Task 13 can distinguish `Superseded`/`RouteInvalidated` without a forbidden `:feature:reader -> :app` dependency. Reader asset requests always set:

```text
memoryCacheKey = "reader-asset:<ReaderAssetKeyHash>"
diskCachePolicy = DISABLED
```

- [ ] **Step 5: Install only for Reader asset model**

Register `ReaderAssetCoilFetcher.Factory` in the application singleton image loader/component registry and install that factory from `OpenStoryApplication.onCreate()`. Keep `ReaderAssetCoordinator` lazy behind a provider/lambda so creating the global image loader for unrelated artwork/catalog requests does not eagerly construct the RICC runtime graph. Do not replace artwork/catalog fetching behavior globally beyond adding this model-specific fetcher.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :app:testDebugUnitTest \
  --tests '*OkHttpReaderAssetDeliveryTest*' \
  --tests '*ReaderAssetCoilFetcherTest*' \
  --no-daemon
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetLoadException.kt app/src/main/kotlin/app/openstory/reader/assets app/src/test/kotlin/app/openstory/reader/assets app/src/main/kotlin/app/openstory/di/ReaderModule.kt app/src/main/kotlin/app/openstory/OpenStoryApplication.kt app/build.gradle.kts docs/superpowers/plans/2026-09-01-reader-image-continuity-cache-ricc-v1-implementation-plan.md
git commit -m "app: integrate reader asset transport"
```

---

### Task 13: Convert Feature Reader to Asset Requests, Viewport Facts, Presentation Events, and Page-local Retry

**Files:**
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderUiState.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderContent.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderImagePage.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt`
- Create: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewportTracking.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderImagePageTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderContentTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelContinuityTest.kt`
- Modify: `feature/reader/build.gradle.kts`
- Modify: `scripts/verify-package-boundaries.sh`
- Modify: `config/architecture/module-boundaries.json`

**Interfaces:**
- Consumes `ReaderPageAssetRequest`, `ReaderViewportSnapshot`, `ReaderAssetCoordinator` Reader-facing methods, including `observeCommittedManifest(sessionId)` so same-release delivery refresh can replace locator-bound keys without a semantic document reload.
- Removes production `:feature:reader -> :downloads` dependency.

- [ ] **Step 1: Write RED UI/runtime boundary tests**

Required assertions:
- mixed document Lazy indices map only image blocks to correct asset keys and image ordinals;
- success presentation fires only if asset is still visible/current when painter succeeds;
- page-local Retry calls `painter.restart()`/request-level retry and never calls ViewModel document retry for `CacheCorrupt`, transport, locator-local repair, or storage failure;
- `ReaderAssetFailure.Superseded` from a replaced delivery manifest is silent/non-user-visible and waits for the newer manifest snapshot/request; typed `RouteInvalidated` invokes a separate ViewModel route reload path;
- old P20 success after fling to P80 is not marked consumed;
- UI passes stable asset model to Coil, not raw `imageUrl`;
- a newer `ReaderCommittedAssetManifestSnapshot` for the same currently committed chapter/release updates only asset manifest/revision in UI state, rebuilds page requests with the new keys, and does **not** reload/replan the semantic document;
- a snapshot for a stale session/chapter/release is ignored;
- Reader screenshots/error copy remain coherent;
- no `app.openstory.downloads` import exists under `feature/reader/src/main`.

- [ ] **Step 2: Run RED**

```bash
./gradlew :feature:reader:testDebugUnitTest \
  --tests '*ReaderImagePageTest*' \
  --tests '*ReaderContentTest*' \
  --tests '*ReaderViewModelTest*' \
  --tests '*ReaderViewModelContinuityTest*' \
  --no-daemon
```
Expected: FAIL.

- [ ] **Step 3: Publish asset manifest/request in UI state**

Store the committed manifest/revision alongside document in `ReaderUiState` or a narrow `ReaderAssetUiState`; do not reconstruct keys from `documentFingerprint + block.id` inside Composables. `ReaderViewModel.commit()` copies `result.assetManifest` **and the `assetManifestRevision` already assigned/registered by `ReaderRouteSession`** after the same foreground target checks as document commit. The feature never calls `registerCommitted()` itself and never invents a revision.

The ViewModel also collects `ReaderAssetCoordinator.observeCommittedManifest(routeSession.sessionId)` for the lifetime of the route session. A snapshot may update only the asset manifest/revision when it still matches the UI's current canonical chapter + selected release + session. This is the delivery-refresh path from Task 14: no HES planning, document reload, progress reset, or chapter-history slide occurs. A changed locator-bound key therefore reaches Compose as a **new `ReaderPageAssetRequest`/Coil memory key before new bytes are decoded**; the old request is superseded rather than caching new bytes under an obsolete key.

- [ ] **Step 4: Add asset-level viewport tracker**

`ReaderViewportTracking.kt` observes `LazyListState.layoutInfo.visibleItemsInfo`, translates visible block keys back to manifest descriptors, derives leading/trailing image ordinal and direction, and emits debounced/distinct `ReaderViewportSnapshot` to coordinator. Raw `LazyListState`/Compose layout types never cross into `:reader`.

- [ ] **Step 5: Make image rendering page-local**

`ReaderImagePage` receives `ReaderPageAssetRequest`. Build a Coil `ImageRequest` with model=request, stable memory key, disk cache disabled. On success, after current visibility check, call `assetPresented`. On error:
- local/transport/cache/locator-repairable -> inline `Retry` restarts this painter/request;
- typed `RouteInvalidated` -> invoke `onRouteInvalidated` once for current manifest revision;
- never reuse the old generic `onReloadDocument` for page-local failures.

- [ ] **Step 6: Close Reader session from ViewModel**

Override `onCleared()` exactly as:

```kotlin
override fun onCleared() {
    routeSession.close()
    super.onCleared()
}
```

`ReaderRouteSession.close()` is idempotent and cancels its own prefetch/asset-session work plus releases only that session's protections. `viewModelScope` lifecycle cancellation remains owned by AndroidX; do not manually cancel or duplicate-close its child jobs. This explicit call must be safe with the owner-Job completion hook installed in Task 10.

- [ ] **Step 7: Remove unused Downloads edge and tighten gates**

Delete `implementation(project(":downloads"))` from `feature/reader/build.gradle.kts`. Update package/architecture policy to allow only actual feature Reader dependencies. Do not add Room/filesystem/Settings imports.

- [ ] **Step 8: Run GREEN UI tests**

```bash
./gradlew :feature:reader:testDebugUnitTest \
  --tests '*ReaderImagePageTest*' \
  --tests '*ReaderContentTest*' \
  --tests '*ReaderViewModelTest*' \
  --tests '*ReaderViewModelContinuityTest*' \
  --tests '*ReaderScreenshotTest*' \
  --no-daemon
bash scripts/verify-package-boundaries.sh
```
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add feature/reader scripts/verify-package-boundaries.sh config/architecture/module-boundaries.json
git commit -m "reader-ui: use semantic image asset requests"
```

---

### Task 14: Add Same-selected-release Delivery Refresh and Typed Route Invalidation Without HTTP Guessing

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetLocatorRefresh.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetSessionPort.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSessionContracts.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSessionFactory.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetLocatorRefreshTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetCoordinatorTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteCoordinatorContractTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteSessionStateTest.kt`

**Interfaces:**
- Produces one bounded same-selected-release refresh seam. It never asks HES to choose an alternate release.
- `ReaderRouteSession` remains the authority that proves which release is still committed and resolves that exact `ChapterRelease` from its current graph; RICC never invents a release from cache state.
- `ReaderAssetCoordinator` owns refresh orchestration and manifest replacement; `ReaderAssetLoader` remains a one-way local/remote loader beneath it.
- Raw HTTP status remains delivery evidence until the selected-release refresh resolves what changed.

- [ ] **Step 1: Write RED refresh/classification tests**

Cover:
- generic transport 401/403/404 arrives as `DeliveryRejected` and does not directly become `Unauthorized`, `DeliveryLocatorStale`, `AssetNotFound`, or HES failure;
- many pages rejected from one committed manifest join one release/manifest-scope refresh;
- `ReaderRouteSession` refuses refresh when `expectedManifestRevision` or selected release no longer match its committed identity;
- session refresh resolves the exact committed `ChapterRelease` from the still-current chapter graph and never substitutes a cache-nearby/alternate release;
- refresh starts only after the original image body/admission closes;
- refresh re-fetches `content.chapter` for that exact committed release through source lane + `CRITICAL` arbiter without `ReaderRouteEngine.plan()`;
- `TRUSTED_STABLE` compatible refreshed manifest with changed locator keeps same logical key and retries once with new runtime locator;
- `LOCATOR_BOUND` changed locator creates a **new image-set namespace/key set**; the committed asset manifest is atomically replaced at a new manifest revision before any refreshed bytes decode, and old keys are not rewritten/aliased;
- the stale Coil request receives `ReaderAssetFailure.Superseded` and never decodes refreshed bytes under its old memory key; Task-13 manifest observation rebuilds the request from the new revision;
- refreshed manifest that is semantically incompatible with the selected release/page set -> typed `RouteInvalidated`;
- refreshed locator unchanged + repeated 403/404 -> terminal page-local `DeliveryRejected`, not an invented route/source failure;
- one selected-release refresh cycle maximum per visible request generation;
- no cache -> network -> refresh -> route -> cache loop can continue indefinitely;
- refresh/local delivery failures do not reduce HES health.

- [ ] **Step 2: Run RED**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderAssetLocatorRefreshTest*' \
  --tests '*ReaderAssetCoordinatorTest*' \
  --tests '*ReaderRouteCoordinatorContractTest*' \
  --tests '*ReaderRouteSessionStateTest*' \
  --no-daemon
```
Expected: FAIL.

- [ ] **Step 3: Bind one session-authorized exact-release refresh port**

Define the session-bound contract in `ReaderAssetLocatorRefresh.kt`:

```kotlin
sealed interface ReaderSelectedReleaseRefreshResult {
    data class Refreshed(
        val document: ReaderDocument,
        val imageSourcePolicy: ReaderImageSourcePolicy,
    ) : ReaderSelectedReleaseRefreshResult
    data object Superseded : ReaderSelectedReleaseRefreshResult
    data object RouteInvalidated : ReaderSelectedReleaseRefreshResult
    data class Failure(val failure: ReaderAssetFailure) : ReaderSelectedReleaseRefreshResult
}

interface ReaderSelectedReleaseRefreshPort {
    suspend fun refreshSelectedRelease(
        expectedManifestRevision: Long,
        expectedReleaseId: ChapterReleaseId,
    ): ReaderSelectedReleaseRefreshResult
}
```

In Task 14, extend the Task-10 `ReaderAssetSessionPort` with these exact non-blocking lifecycle methods and add no other refresh dependency to the loader:

```kotlin
fun registerSelectedReleaseRefreshPort(
    sessionId: ReaderSessionId,
    port: ReaderSelectedReleaseRefreshPort,
)

fun unregisterSelectedReleaseRefreshPort(
    sessionId: ReaderSessionId,
)
```

The Task-10 `NO_OP` implementation makes both methods no-ops after this interface extension. `ReaderRouteSessionFactory` registers the newly created `ReaderRouteSession` as its own `ReaderSelectedReleaseRefreshPort` immediately after construction. Task 14 updates `ReaderRouteSession.close()` so it unregisters the refresh port first and then performs the existing idempotent `assetSessionPort.releaseSession(sessionId)` teardown; duplicate close remains harmless. `ReaderAssetCoordinator` stores only this narrow port beside the session asset state. There is no `ReaderAssetLoader -> ReaderAssetCoordinator` dependency and no DI cycle.

Add a **separate** routing fun-interface in `ReaderRouteSessionContracts.kt`; do not add a second abstract method to existing `ReaderRouteExecutionDelegate` because it is intentionally a `fun interface`:

```kotlin
internal fun interface ReaderSelectedReleaseRefreshDelegate {
    suspend fun refresh(
        release: ChapterRelease,
    ): ReaderSelectedReleaseRefreshResult
}
```

`ReaderRouteSession.refreshSelectedRelease()` first checks under its state lock that the requested release is still the committed release and that the supplied asset-manifest revision equals its last known effective revision. It resolves the exact `ChapterRelease` from current `ReaderSessionChapterGraph`; missing/replaced release returns `RouteInvalidated`, stale revision returns `Superseded`. It then calls `ReaderSelectedReleaseRefreshDelegate.refresh(exactRelease)` **outside** the state lock and rechecks committed identity/revision after completion. `ReaderRouteSessionFactory` wires this delegate to the coordinator's exact-release refresh function.

`ReaderRouteCoordinator` implements the exact-release operation by selecting the currently enabled `ReaderDocumentSource` whose `pluginId == exactRelease.pluginId`; absence is typed selected-release invalidation/source-unavailable evidence, never an alternate-source search. It then calls that exact source for the provided release through:

```text
ContentSourceExecutionLane(FOREGROUND)
    -> ContentFetchArbiter(CRITICAL)
    -> source.fetch(exactRelease)
    -> existing sanitizer/validator
```

It never invokes `ReaderRouteEngine.plan()`, alternate-release ranking, or HES recovery. Source policy comes from the exact source used for the refresh. The document refresh starts only after the original image request's arbiter/body scope has ended.

`ReaderAssetCoordinator` owns a refresh single-flight keyed by `(sessionId, manifestRevision, selectedReleaseId)`, so many rejected pages under one committed manifest join one session-authorized refresh call.

- [ ] **Step 4: Compare refreshed manifest before retry**

On `ReaderAssetFailure.DeliveryRejected`, `ReaderAssetCoordinator.requestPage()` invokes the manifest-scope refresher above; ordinary transient/cache failures remain Task-9 loader behavior. Use `ReaderAssetManifestFactory` with the refreshed source policy plus the current security/variant facts and compare against the still-current committed manifest:
- compatible `TRUSTED_STABLE`: locator may change while logical keys remain stable;
- compatible `LOCATOR_BOUND` + same normalized locator set: no new manifest is published; one repeated rejection is terminal page-local;
- compatible `LOCATOR_BOUND` + changed normalized locator set: image-set namespace may change and therefore **all page keys are recomputed**; never rewrite/alias old keys;
- changed stable identities/page structure/source semantics that cannot be proven compatible: `RouteInvalidated`.

For every compatible refresh that changes delivery facts, call Task-11 `replaceDeliveryManifest(sessionId, expectedManifestRevision, refreshedManifest)`. The coordinator performs the current-revision/same-chapter/same-release guard and publishes a new manifest revision without sliding chapter history. The loader then terminates the stale page attempt with `ReaderAssetFailure.Superseded`; it does **not** decode refreshed bytes under the stale Coil memory key. Task 13 observes the new manifest snapshot and issues a new `ReaderPageAssetRequest` with the current key. For `TRUSTED_STABLE` this is still a new manifest revision even though the page key may remain equal, keeping presentation/consumption revision checks correct.

`DeliveryLocatorStale` is a semantic **refresh conclusion** when a compatible manifest proves delivery facts changed; it is not an OkHttp status mapping.

- [ ] **Step 5: Enforce no nested admission and one refresh budget**

`ReaderAssetCoordinator.requestPage()` receives `DeliveryRejected` only after Task-9 loader has exited the image `delivery.fetch()`/arbiter/body scope; only then may the coordinator await the session-authorized document refresh, which acquires a later independent admission. Track `locatorRefreshAttempts` in the coordinator request recovery generation; maximum is 1. A successful changed-delivery refresh **ends that stale request generation** with `ReaderAssetFailure.Superseded`; the replacement request begins from the newly published manifest and does not inherit an old memory key. An unchanged refresh followed by the same rejection is terminal page-local for that generation. Transient transport retry from Task 9 and locator refresh budget are independent finite counters, and no automatic path loops `old request -> refresh -> old request`.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderAssetLocatorRefreshTest*' \
  --tests '*ReaderAssetCoordinatorTest*' \
  --tests '*ReaderRouteCoordinatorContractTest*' \
  --tests '*ReaderRouteSessionStateTest*' \
  --tests '*ReaderSourceHealthRegistryTest*' \
  --no-daemon
```
Expected: PASS and no RICC-local failure changes HES health.

- [ ] **Step 7: Commit**

```bash
git add reader
git commit -m "reader: refresh stale image delivery facts"
```

---

### Task 15: Add Security-scope Invalidation, Cache Hygiene, Pressure Admission, and Observability

**Files:**
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/auth/PluginSessionModels.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/auth/PluginSessionService.kt`
- Create: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/auth/PluginSessionServiceSecurityGenerationTest.kt`
- Create: `app/src/main/kotlin/app/openstory/reader/assets/ReaderAssetSecurityInvalidationObserver.kt`
- Create: `app/src/test/kotlin/app/openstory/reader/assets/ReaderAssetSecurityInvalidationObserverTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/OpenStoryApplication.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/assets/DownloadReaderAssetStore.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/reconcile/StorageReconciliationService.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/reconcile/StorageReconciliationPlan.kt`
- Modify: `downloads/src/test/kotlin/app/openstory/downloads/reconcile/StorageReconciliationServiceTest.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetDiagnostics.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetLoader.kt`

**Interfaces:**
- Adds a process-local, non-secret plugin credential generation so replacement login/logout/policy invalidation cannot be hidden by equal `PluginSessionSummary` values.
- Security invalidation targets only security-scoped/non-persistent Reader asset state for the affected source; public RICC manifests are not invalidated merely because a plugin auth session changed.
- Adds bounded maintenance and aggregate counters; no per-page verbose production log.

- [ ] **Step 1: Write RED invalidation/pressure/hygiene tests**

Cover:
- every credential-authority mutation (`completeVerifiedLogin`, `logout`, policy-mismatch clear, credential-store failure clear) increments a **process-local `credentialGeneration`**, even when status/expires fields would otherwise compare equal;
- simple `summary()`/credential reads that do not mutate authority do not increment generation;
- observer sees same-status replacement login because generation changes; it invalidates security-scoped/non-persistent Reader runtime state for that source and clears all durable account-scoped RICC rows for the plugin;
- authenticated -> logged-out/expired also invalidates/clears even if no stable account identity exists;
- public RICC manifests for the same source remain valid across an auth-generation change;
- because current auth has no stable non-secret account namespace, production policy never creates account-scoped persistent entries; observer is still future-safe and tests use synthetic safe namespaces;
- clear global automatic cache revokes the shared authority first and clears both automatic document metadata/blobs and RICC image metadata/blobs without touching explicit downloads;
- pressure `PRESSURED`/`EMERGENCY` disables speculative acquisition while CRITICAL remains usable/pass-through;
- app-private reserve failure maps to `EMERGENCY`; a typed ENOSPC emits cache-pressure/commit-failure diagnostics, but the visible request performs only the Task-8 bounded one-retry persistence path and counts only physically reclaimed bytes;
- later maintenance under `EMERGENCY` first exhausts ordinary unprotected victims, then may degrade protection in the exact remaining order `PROGRESS_PROTECTED_DOCUMENT -> RECENT_HISTORY_2 -> RECENT_HISTORY_1 -> ACTIVE_CONSUMED -> ACTIVE_INTERACTIVE`; it never evicts explicit downloads or an `ACTIVE_READ_LEASE`, processes at most 32 victims per maintenance pass, and reschedules another pass instead of looping on the visible path;
- orphan temp/blob and metadata-without-blob cleanup is bounded and never runs a full scan on visible path;
- diagnostics count memory/disk/network/single-flight/promotion/prefetch/locator/corruption/commit failure/pressure/eviction bytes.

- [ ] **Step 2: Run RED**

```bash
./gradlew :plugins:runtime:test :app:testDebugUnitTest :downloads:testDebugUnitTest :reader:testDebugUnitTest \
  --tests '*PluginSessionServiceSecurityGenerationTest*' \
  --tests '*ReaderAssetSecurityInvalidationObserverTest*' \
  --tests '*StorageReconciliationServiceTest*' \
  --tests '*ReaderAssetCoordinatorTest*' \
  --no-daemon
```
Expected: FAIL.

- [ ] **Step 3: Add mutation-visible credential generation and invalidate security-scoped runtime state**

Extend the runtime-only summary:

```kotlin
data class PluginSessionSummary(
    val pluginId: PluginId,
    val status: PluginSessionStatus,
    val expiresAtEpochMillis: Long?,
    val credentialGeneration: Long,
)
```

`DefaultPluginSessionService` owns a process-local `PluginId -> Long` generation map. Increment with checked monotonic `+1` on every operation that **changes credential authority**:
- after successful `store.replaceAll()` in `completeVerifiedLogin()`;
- after `store.clear()` in `logout()`;
- when `invalidateChangedPolicies()` clears records because policy fingerprint changed;
- when `validSessionRecords()` catches a store failure and clears records.

Do not increment on plain `summary()`, successful `sessionFor()` read, or a refresh that only republishes current facts. Every `publish()` includes current generation. This makes replacement login observable even if `status/expiresAt` are otherwise equal. The generation is process-local only, never persisted, never derived from cookies/account identity, and never used as a durable cache key.

Add the process-scoped coordinator hook:

```kotlin
fun ReaderAssetCoordinator.invalidateSecurityScopedSource(sourceNamespace: ReaderAssetSourceNamespace)
```

It synchronously marks matching **non-public** committed/prefetched manifests superseded, calls Task-9 `singleFlight.invalidateSecurityScopedSource(sourceNamespace)` to hard-cancel matching non-public producers/waiters regardless of priority, unregisters remaining consumers, and prevents any old runtime-isolation scope from serving a later request. Public manifests are untouched. A later request carrying the invalidated manifest revision returns typed `RouteInvalidated` so the existing Task-13/14 boundary may reacquire Reader facts; RICC never silently switches account/release.

`ReaderAssetSecurityInvalidationObserver` observes `PluginSessionService.observeInstalledSessions()` and retains the last `(status, credentialGeneration)` per plugin. For every event it derives the Reader namespace only as `ReaderAssetSourceNamespace.fromPluginId(summary.pluginId)`; auth/session/package version facts never redefine source identity. On:
- any `credentialGeneration` change; or
- a status transition into `LOGGED_OUT`/`EXPIRED`,

it performs both:
1. `ReaderAssetStorePort.clearAutomatic(AllAccountScopesForSource(pluginId.value))` so future account-scoped persistence cannot survive/logout-resurrect;
2. `ReaderAssetCoordinator.invalidateSecurityScopedSource(pluginId.value)` so current non-persistent/private memory/single-flight/manifest state cannot cross the credential boundary.

Current production cannot name a safe non-secret account namespace, so it must not synthesize one by hashing cookies or credentials. Task-10 non-persistent runtime-scope randomization is the first cross-session isolation layer; credential-generation invalidation closes the **same-live-session replacement-login** edge.

- [ ] **Step 4: Extend reconciliation inventory**

Include RICC reader-asset directory/metadata in bounded reconciliation. Global automatic clear/reconciliation is routed through `AutomaticCacheBudgetCoordinator`, which owns both automatic document and image inventories; it must never delete `EXPLICIT_DOWNLOAD` chapter blobs. RICC orphan cleanup works from generation blob IDs and detached metadata, not a full visible-path filesystem scan. `DownloadReaderAssetStore.cachePressure()` combines unified-budget pressure with existing `StorageWriteAdmission`: violated app-private reserve is `EMERGENCY`; high-watermark/low-space policy is at least `PRESSURED`. A successful later reserve/maintenance reevaluation may clear pressure. The synchronous ENOSPC retry path from Task 8 never widens into full reconciliation.

`StorageReconciliationPlan` makes physical emergency degradation deterministic and bounded. A normal/PRESSURED pass uses the normalized order only through unprotected `CONSUMED_IMAGE_HISTORY`. If app-private reserve is still violated under `EMERGENCY`, a maintenance pass may continue through:

```text
PROGRESS_PROTECTED_DOCUMENT
RECENT_IMAGE_HISTORY_2
RECENT_IMAGE_HISTORY_1
ACTIVE_CONSUMED_IMAGE
ACTIVE_INTERACTIVE_IMAGE
```

Stop immediately once the physical reserve is restored or after 32 victim rows, whichever comes first. Never select `ACTIVE_READ_LEASE` or explicit-download ownership. If still `EMERGENCY` after 32 victims, schedule another maintenance pass; do not spin in the caller/visible request. Image deletion remains lease-aware and only physically unlinks when no active read lease exists.

- [ ] **Step 5: Add aggregate diagnostics sink**

Define `ReaderAssetDiagnosticsSink` with no-op default and typed aggregate events/counters from R2 section 52. Tests assert event semantics; production logging remains sampled/aggregate.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :plugins:runtime:test :app:testDebugUnitTest :downloads:testDebugUnitTest :reader:testDebugUnitTest \
  --tests '*PluginSessionServiceSecurityGenerationTest*' \
  --tests '*ReaderAssetSecurityInvalidationObserverTest*' \
  --tests '*StorageReconciliationServiceTest*' \
  --tests '*ReaderAsset*Test*' \
  --no-daemon
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add plugins/runtime app downloads reader
git commit -m "reader: harden asset cache lifecycle"
```

---

### Task 16: Prove End-to-end Continuity, Process Recreation, Schema/Architecture Gates, and Freeze Evidence

**Files:**
- Create: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderImageContinuityTest.kt`
- Create: `app/src/test/kotlin/app/openstory/reader/assets/ReaderAssetIntegrationTest.kt`
- Create: `app/src/test/kotlin/app/openstory/reader/assets/ReaderAssetProcessRecreationIntegrationTest.kt`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`
- Modify: `scripts/verify-current-architecture.sh`
- Modify: `scripts/tests/architecture-baseline-2-state-test.sh` only where it asserts current schema wording.
- Modify: `docs/project/current-state.md`
- Create: `docs/internal/checkpoints/reader-image-continuity-cache-ricc-v1.md`

**Interfaces:**
- No new production API; this task closes acceptance and evidence.

- [ ] **Step 1: Add RED acceptance tests that cannot pass from Coil memory**

Tests use an explicit `TRUSTED_STABLE + PUBLIC` fixture source (and a separate fail-closed control source) and explicitly clear/replace Coil memory cache between requests:

```text
same chapter:
load+present P1 -> scroll away -> clear Coil memory -> request P1
=> image network calls 0, local disk read 1

offline revisit:
read P1..P10 online -> clear Coil memory -> network OFFLINE -> revisit retained P1
=> renders from RICC disk

A -> B -> A:
clear Coil memory before returning A
=> retained A image network calls 0

warm history:
A -> B -> C -> D -> A
=> if A metadata not evicted, consumed retained pages use disk

process recreation:
close/recreate ReaderAssetCoordinator + single-flight + store-facing runtime objects
reuse the same persistent Room/file adapters
Coil memory empty
semantic Reader document/manifest is reacquired/reconstructed through the existing Reader path
then image request => retained image network calls 0
```

`ReaderAssetProcessRecreationIntegrationTest` belongs in `:app` because the guarantee crosses Reader runtime + Downloads store + Room/files adapters. It must separately count chapter/document-source calls and image-delivery calls so it proves RICC byte reuse without falsely claiming offline semantic document reconstruction. Room instrumentation in this task remains responsible for schema/migration/repository correctness, not the whole process-recreation pipeline. If bundled MangaDex is shipped with `STABLE_ID_CHANGES_WITH_CONTENT`, the Task-1 source-contract evidence/package/instrumentation gate must also be green in the final checkpoint; otherwise the package must ship fail-closed and the checkpoint must state that source-specific limitation explicitly.

- [ ] **Step 2: Add transition/priority/concurrency acceptance tests**

On OFFLINE, remote asset calls are 0. On UNMETERED below 8000 bp transition calls are 0; at 8000..8999 bp the eligible transition frontier is exactly 1 opening asset; at >=9000 bp it is capped at 4, while process-wide active SPECULATIVE concurrency remains 1. Opening the next committed chapter reuses matching retained bytes. On METERED and UNKNOWN, transition speculation count is 0. A visible request starts ahead of queued speculative work. Duplicate demands collapse to one network body.

Also retain focused evidence from Tasks 5–9 that: first ENOSPC performs bounded unprotected relief and exactly one persistence retry; repeated/no-space or storage-unavailable failure still returns valid remote bytes for render; persistent recency uses controlled wall `Clock`; fairness/retry/touch throttling uses monotonic/scheduler time and is invariant to wall-clock jumps.

Add cross-layer regression cases for the self-review hardening:
- automatic document fetch captures its write intent before network; clear/quota-zero during the blocked remote fetch leaves **zero** automatic document publication after completion;
- `LOCATOR_BOUND` rejected delivery whose refreshed locator set changes publishes a newer committed asset-manifest revision, old request ends `Superseded`, and no refreshed bytes are decoded/cached under the obsolete key;
- a delivery refresh advancing manifest revision followed by a normal foreground semantic reload yields a strictly newer revision again (no revision reuse);
- replacement login/logout while a non-public CRITICAL image fetch is blocked hard-cancels the old producer/waiter and old-account bytes are never delivered; a public fetch for the same plugin is unaffected.

- [ ] **Step 3: Update schema/current-architecture guards intentionally**

In `scripts/verify-current-architecture.sh` and `ModuleGraphTest.kt`:
- production module count remains 17;
- latest Room schema becomes exactly 12;
- require `MIGRATION_11_12` instead of forbidding it;
- require schemas 1..12 contiguous;
- keep HES engine/package purity checks;
- assert `:feature:reader` no longer has `:downloads` dependency;
- assert no RICC file exists under `reader/engine`.

Do not describe this as HES-v2; it is an intentional persistence guard advancement for RICC.

- [ ] **Step 4: Run focused acceptance suite**

```bash
./gradlew :reader:testDebugUnitTest :downloads:testDebugUnitTest :feature:reader:testDebugUnitTest :app:testDebugUnitTest \
  --tests '*ReaderAsset*' \
  --tests '*ReaderImageContinuityTest*' \
  --tests '*PrefetchCoordinatorTest*' \
  --tests '*ReaderRouteSessionStateTest*' \
  --no-daemon
```
Expected: PASS.

- [ ] **Step 5: Run Room migration/process tests**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.readerassets.Migration11To12Test,app.openstory.storage.room.readerassets.RoomReaderAssetMetadataRepositoryTest \
  --no-daemon
```
Expected: PASS. Process-recreation continuity is already exercised by the app integration test in Step 4.

- [ ] **Step 6: Run broad Reader/Downloads/Feature regressions**

```bash
./gradlew :reader:engine:test :reader:testDebugUnitTest :downloads:testDebugUnitTest :feature:reader:testDebugUnitTest :app:testDebugUnitTest --no-daemon
```
Expected: PASS with no HES differential/contract regression.

- [ ] **Step 7: Run architecture gates**

```bash
./gradlew :build-logic:test verifyArchitecture --no-daemon
bash scripts/verify-package-boundaries.sh
bash scripts/verify-current-architecture.sh
```
Expected: PASS; 17 production modules, Room schemas 1..12, no forbidden edges/imports.

- [ ] **Step 8: Run Reader connected/UI smoke where device is available**

```bash
./gradlew :feature:reader:connectedDebugAndroidTest --no-daemon
```
Expected: PASS.

- [ ] **Step 9: Write checkpoint evidence**

`docs/internal/checkpoints/reader-image-continuity-cache-ricc-v1.md` records exact commands, environment, actual PASS/FAIL, schema 12 evidence, module count, and acceptance counters. Never mark an unrun gate PASS.

- [ ] **Step 10: Final implementation self-review before closure**

Review the complete diff for:
- wrong-image/cross-scope key risk;
- stale image **and automatic-document** commit resurrection after clear/logout/quota-zero, including clear during remote fetch;
- same-release locator refresh re-key/revision races and any decode under a stale Coil memory key;
- credential replacement/logout allowing non-public in-flight bytes to reach old waiters;
- quota reservation leak/overspend;
- nested arbiter/deadlock/body-lifetime mistakes;
- source-lane/probe HES behavior changes;
- stale session protections;
- read-lease unlink races;
- whole-chapter accidental prefetch;
- page retry escalating too broadly;
- feature/storage dependency leaks;
- raw URL/token/credential persistence;
- unbounded body/manifest/metadata behavior;
- any ENOSPC path that retries persistence more than once, evicts protected/explicit data in the synchronous relief path, or blocks render on broad reconciliation;
- any RICC runtime race/fairness/retry/touch throttle using wall clock, or any persisted recency timestamp using monotonic time/direct scattered system-clock calls.

Any Critical/High issue found here is fixed and all affected focused + broad tests rerun before the feature is called complete.

- [ ] **Step 11: Commit final gates/evidence**

```bash
git add build-logic scripts docs feature/reader/src/test app/src/test storage/room/src/androidTest
git commit -m "reader: verify image continuity cache"
```

---

## Plan Self-review — Gaps/Conflicts Found and Resolved

The plan was re-audited against R2 + R2.2 and current master before being frozen. The following issues were found during plan writing and are resolved above rather than deferred to implementation guesswork.

### PR-01 — Unified quota had no cross-kind victim ordering

**Gap:** R2 normalized document + image bytes into one budget, but its deterministic eviction classes were image-centric. Two implementations could both satisfy byte accounting yet choose contradictory document-vs-image victims.

**Resolution:** this plan freezes one cross-kind retention order. Ordinary automatic documents are `WARM_DOCUMENT`; progress-protected documents are `PROGRESS_PROTECTED_DOCUMENT`; explicit downloads remain outside automatic eviction.

### PR-02 — Current auth cannot safely implement `ACCOUNT_SCOPED`

**Gap:** `PluginSessionRecord` contains cookies/hosts/policy fingerprints but no stable non-secret account identity. Hashing credentials/cookies would violate the security contract and produce unstable identity.

**Resolution:** production account-scoped persistence fails closed to `NON_PERSISTENT_PRIVATE`. Store/invalidation contracts still support account scopes for future adapters/tests; logout conservatively clears any account-scoped entries for the plugin.

### PR-03 — Plugin `stableId` baseline is weaker than trusted immutable identity

**Gap:** SDK promises stable ID across expiring URL changes, not that it changes whenever content bytes change.

**Resolution:** Task 1 adds an explicit stronger manifest contract with fail-closed default. Bundled MangaDex receives `STABLE_ID_CHANGES_WITH_CONTENT` only as a **maintained Hikari integration contract**, with package + Android fixture tests that lock stable ID to `chapter.hash + filename` and prove base-URL rotation does not affect it. Other sources with similar-looking IDs receive no trust without their own explicit declaration.

### PR-04 — Process recreation could falsely claim offline cold-open

**Gap:** image-bearing Reader documents remain non-persistable, so RICC bytes alone cannot reconstruct a cold chapter offline.

**Resolution:** Task 16 process test reacquires/reconstructs semantic manifest separately and asserts **image delivery network = 0** after reconstruction. It does not claim RICC semantic offline reconstruction.

### PR-05 — Arbiter permit could be released at headers instead of EOF

**Gap:** an adapter returning `ResponseBody` would let `withAdmission` end while transfer continues.

**Resolution:** app `ReaderAssetDeliveryPort.fetch()` consumes/closes the bounded OkHttp body before returning `ReaderAssetPayload`; the arbiter block therefore covers full network-body lifetime by construction.

### PR-06 — Locator refresh could deadlock by reacquiring arbiter inside image fetch

**Gap:** stale-locator recovery is itself remote work.

**Resolution:** loader exits/closes the original image fetch admission first, then invokes manifest refresh in a new independent source-lane + arbiter operation. Nested admission has a runtime/test guard.

### PR-07 — Byte reservation cannot happen safely before payload size is known

**Gap:** streamed image size may differ from `Content-Length`; reserving based on an untrusted header can under-account.

**Resolution:** V1 remote transport reads into a **hard-bounded encoded ByteArray** (max 16 MiB) while holding network admission, then obtains final durable reservation using actual bytes. This is bounded memory, never unbounded accumulation, and decouples network slots from storage/quota work.

### PR-08 — File/Room publication order could expose metadata without complete bytes

**Gap:** publishing Room first risks false cache hit; writing file first risks orphan on crash.

**Resolution:** complete atomic blob is written first, then final metadata visibility occurs through Task 6 `publishIfCurrent()` under the same publication/revocation gate used by clear/logout/quota-zero. There is no `check epoch -> unlock -> upsert` window. A crash before publication can leave only a safe orphan generation, handled by bounded reconciliation; metadata never points to partial bytes.

### PR-09 — Active read lease could close before Coil decode finishes

**Gap:** a store method returning only an `InputStream` can release the physical protection before downstream decode consumes it.

**Resolution:** the custom Coil source owns the `ReaderAssetReadLease`; closing the Coil source/result closes the lease. Eviction uses `deleteWhenUnleased`.

### PR-10 — Source image policy could be lost between route execution and manifest derivation

**Gap:** `ReaderForegroundResult` currently includes release/document but not the exact source's image trust declaration.

**Resolution:** remote `ReaderLoadResult.Success` carries `ReaderImageSourcePolicy` from the exact `ReaderDocumentSource`; coordinator derives immutable manifest from that result. Local text documents carry no image policy because image-bearing documents are not current document-cache hits.

### PR-11 — Session release was not guaranteed by one lifecycle path

**Gap:** ViewModel scope cancellation and `ReaderRouteSession` prefetch lifecycle are related but current session has no close contract.

**Resolution:** both owner Job completion and `ReaderViewModel.onCleared()` invoke idempotent `ReaderRouteSession.close()`; only that session's protections are released.

### PR-12 — UI success does not necessarily mean consumed

**Gap:** an offscreen image request can finish after a fling.

**Resolution:** presentation event requires Coil success **and** current committed manifest revision **and** asset still visible in current viewport snapshot. Late P20 completion may remain cached but not consumed.

### PR-13 — Existing feature dependency edge contradicted new boundary

**Gap:** `:feature:reader` currently declares `:downloads` although current production Reader source does not use it.

**Resolution:** remove the edge only after Task 13 compiles/tests with no Downloads import; architecture/package guards are tightened in the same task.

### PR-14 — Settings storage summary would undercount RICC bytes

**Gap:** current `AppStorageSummaryAdapter` sums only document `CacheRepository` entries.

**Resolution:** Task 7 reads unified budget snapshot for automatic bytes, so Settings reflects document + RICC usage under the same configured quota.

### PR-15 — Clear-cache contract exists but current Settings UI has no clear command

**Gap:** spec requires explicit clear semantics, but current master exposes quota/storage summary rather than a user clear-cache action.

**Resolution:** V1 implements/test-drives `clearAutomatic(scope)` through the unified automatic-cache coordinator. `AllAutomatic` revokes first and clears both automatic document and RICC image ownership while preserving explicit downloads. No unrelated Settings UX is invented in this plan. A future clear UI can call the already-correct port.

### PR-16 — Download USER_WORK could accidentally acquire HES probe semantics

**Gap:** sharing Reader limiter wholesale would incorrectly make Downloads participate in HES half-open probe ownership.

**Resolution:** probes are split into `ReaderHalfOpenProbeRegistry`; Downloads acquires only source lane + `USER_WORK` arbiter.

### PR-17 — Current N+1 success has no reusable artifact identity

**Gap:** prefetch currently discards `ReaderLoadResult`; a later RICC callback without token/graph identity could seed stale transition work.

**Resolution:** artifact includes session/token or generation identity, graph revision, target chapter, selected release, validated document and source policy; session rejects stale artifacts before planner admission.

### PR-18 — Manifest bound and payload bound protect different resources

**Gap:** a 16 MiB byte cap does not prevent thousands of tiny rows/files; a 2,000-block cap does not prevent one giant asset.

**Resolution:** both are mandatory: existing `MAX_BLOCKS=2_000` bounds descriptor cardinality and 16 MiB bounds each encoded asset.

### PR-19 — Hysteresis could still thrash if every commit synchronously reconciles

**Gap:** 100/90 watermarks alone do not help if visible writes block on eviction after every page.

**Resolution:** reservation can deny/schedule bounded reconciliation; visible rendering is not blocked by reconciliation. Coordinator evicts toward low watermark asynchronously after high watermark is crossed.

### PR-20 — Exact implementation constants needed a repository-grounded rationale

**Resolution:** total fetch slots remain 3 to preserve the current Reader maximum (`2 foreground + 1 prefetch`) while bringing participating work under one authority. Other values are explicit conservative V1 policy choices, isolated in one test-visible policy object for later measurement-driven tuning without changing HES/spec architecture.

### PR-21 — Separate document/image epochs would still allow cross-kind resurrection

**Gap:** an image-only `ReaderAssetPolicyEpoch` plus a document budget epoch would let global clear/quota-zero revoke one cache kind before the other and violate the single automatic-cache authority contract.

**Resolution:** Tasks 6–8 use one `AutomaticCacheWriteAuthority`/publication gate for automatic document and RICC image writes. `ReaderAssetDurableWriteAuthority` is only an opaque Reader-facing wrapper around that shared authority; no second image epoch exists.

### PR-22 — Epoch recheck before Room upsert had a TOCTOU hole

**Gap:** `revalidate epoch -> release lock -> Room.upsert()` allows clear/logout to revoke after the check but before metadata becomes visible.

**Resolution:** Task 6 adds `publishIfCurrent()`. Revalidation and bounded metadata visibility run inside the same publication/revocation gate used by clear/logout/quota-zero. Network/blob I/O stays outside the gate.

### PR-23 — Old physical cleanup could delete a post-clear same-key image generation

**Gap:** if physical image path were deterministic from logical key, clear could detach old metadata, a new write could reuse the same path, and delayed old cleanup could unlink the new file.

**Resolution:** Task 5 makes RICC `ReaderAssetBlobId` generation-unique and metadata owns logical-key -> generation mapping. Old cleanup deletes only the detached generation ID. Existing deterministic document blobs are deleted under the publication gate when same-key replacement could race.

### PR-24 — Local read leases cannot be single-flight shared values

**Gap:** sharing one `ReaderAssetReadLease` among multiple Coil consumers makes one consumer's close invalidate another's decode lifetime.

**Resolution:** Task 9 performs `openLocal()` per consumer before single-flight. `ReaderAssetSingleFlight` collapses remote encoded-byte acquisition only. Every local consumer owns an independent lease.

### PR-25 — Network completion vs background durable commit could reopen duplicate fetch window

**Gap:** returning remote payload immediately is necessary for render latency, but removing the single-flight entry immediately can let a transition request refetch before the one durable commit becomes visible.

**Resolution:** Task 9 completes the waiter payload immediately but keeps the single-flight entry joinable until the single leader-created persistence Job ends. Joiners receive the already-fetched payload; they do not wait for persistence.

### PR-26 — Loader/single-flight types were referenced before being defined

**Gap:** the draft named `ReaderAssetLoadOutcome`, `ReaderAssetConsumerToken`, and in-flight priority types without one canonical definition.

**Resolution:** Task 2 now defines `ReaderAssetConsumerToken`, `ReaderAssetRemoteOutcome`, and `ReaderAssetLoadOutcome` once. Task 9 consumes those exact types and `ContentFetchDemand` from Task 3.

### PR-27 — HTTP 403/404 were over-classified as semantic asset failures

**Gap:** generic HTTP status cannot prove stale locator vs missing asset vs authorization/source behavior.

**Resolution:** Task 12 maps raw terminal delivery 4xx statuses without an explicit authenticated-delivery contract to `DeliveryRejected(status)`; PR-50 below makes this explicit for 401/403/404 in the V1 app image adapter. Task 14 performs at most one same-selected-release semantic refresh and only then concludes changed locator, route invalidation, or terminal page-local rejection.

### PR-28 — Logout invalidation required an account namespace current auth does not have

**Gap:** the draft attempted to clear one account-scoped namespace even though current `PluginSessionSummary` exposes no stable non-secret account identifier.

**Resolution:** Task 2 adds `AllAccountScopesForSource`. Task 15 clears every account-scoped RICC row for the plugin on authenticated -> logged-out/expired transition without deriving identity from cookies/credentials.

### PR-29 — Global clear must cover document + image automatic ownership

**Gap:** implementing `ReaderAssetStorePort.clearAutomatic(AllAutomatic)` as image-only would contradict the unified budget and user intent.

**Resolution:** Task 8 maps `AllAutomatic` to Task-6 unified clear, which revokes first and clears both automatic document and RICC image ownership. Explicit downloads remain untouched.

### PR-30 — Process-recreation proof was placed too low in the stack

**Gap:** a `:storage:room` test alone cannot prove Reader runtime reconstructs a manifest and then reuses RICC bytes with zero image delivery calls.

**Resolution:** Task 16 places process/runtime reconstruction in an `:app` integration test using persistent store adapters. Room instrumentation verifies migration/repository behavior separately.

### PR-31 — Final self-review task references and status were mechanically rechecked

**Gap:** after adding hardening tasks, stale task numbers/old terminology could make an otherwise correct plan non-executable.

**Resolution:** the final audit requires no unresolved placeholder markers, no normative claim that narrows non-persistability to a subset of image-bearing `ReaderDocument` values, no separate image-cache policy epoch authority, no direct 404->`AssetNotFound` mapping, and every freeze mapping below points to the final task numbers/types.

### PR-32 — Public persistence permission did not prove durable identity safety

**Gap:** `PUBLIC` only authorizes persistence. It does not prove a stable ID is content-revision identity, nor that a locator cannot serve different bytes later. The earlier draft risked making bundled MangaDex useful by silently upgrading `${hash}/${filename}` from a shape observation into a correctness guarantee.

**Resolution:** identity safety and persistence permission remain orthogonal. Task 1 requires an explicit `STABLE_ID_CHANGES_WITH_CONTENT` source contract, package regeneration, unit/package assertions, Android adapter fixtures, and a checked-in reference to the authoritative MangaDex@Home contract used to justify the bundled opt-in. If authoritative evidence is absent or invalidated, MangaDex must remain/downgrade fail-closed (`DELIVERY_STABLE_ONLY + NON_PERSISTENT`) rather than trade correctness for cache hits. Arbitrary third-party sources never inherit trust from URL/ID shape.

### PR-33 — ENOSPC degraded-pass-through contract was under-specified

**Gap:** the plan treated generic storage failure as best-effort degradation but did not implement R2 §41's stricter `ENOSPC -> bounded unprotected eviction -> one persistent retry -> transient render` contract. A naive implementation could retry forever, run full reconciliation on the visible path, or evict protected data just to save the current page.

**Resolution:** Task 5 exposes typed `NoSpace` without message-string guessing; Task 6 adds `relievePhysicalPressure(requiredBytes)` capped at 32 unprotected automatic-cache victims; Task 8 retries the physical blob write exactly once and then returns `Degraded(CacheStorageUnavailable)` while remote bytes remain renderable. Task 15 handles broader `EMERGENCY` maintenance separately.

### PR-34 — Wall-clock and monotonic-time ownership was not executable

**Gap:** R2 §47 requires durable recency to use wall clock and runtime scheduling to use monotonic/scheduler time, but the draft exposed `wallClockEpochMillis` through `markConsumed()` and described access coalescing from persisted timestamps. Wall-clock jumps could therefore change runtime throttle behavior.

**Resolution:** Task 2 reuses `core:common` `Clock` and adds pure-JVM `MonotonicClock`; `ReaderAssetStorePort.markConsumed(key)` no longer accepts caller time. Task 8 owns persisted wall timestamps and uses an in-memory monotonic touch throttle. Task 3 fairness uses `MonotonicClock`; Task 9 retry uses coroutine scheduler time. Tests advance wall and monotonic time independently.

### PR-35 — Durable-cache policy constants leaked into Reader runtime ownership

**Gap:** the draft said `ReaderAssetRuntimePolicy` owned all frozen constants, including automatic-cache watermarks and metadata touch interval. That would make `:reader` own Downloads/storage policy despite the module constitution.

**Resolution:** Task 2 retains only Reader/runtime values in `ReaderAssetRuntimePolicy`; Task 6 adds Downloads-owned `AutomaticCacheRuntimePolicy` for 10000/9000-bp watermarks, 300000-ms touch interval, and 32-victim ENOSPC relief bound.

### PR-36 — Publication-gate I/O rule conflicted with deterministic document-blob ABA safety

**Gap:** the draft simultaneously said “no blob I/O inside the publication gate” and required deterministic automatic-document blobs to be deleted before the gate reopens so a delayed clear/eviction cannot delete a same-key rewrite. Both statements cannot be true.

**Resolution:** Task 6 narrows the rule: no network/blob reads/writes occur in the gate; one bounded existing-file delete for deterministic automatic-document keys is allowed when required for ABA safety. RICC image blobs are generation-unique and always delete outside the gate. ENOSPC relief additionally uses `tryDeleteNowIfUnleased()` and counts only physical bytes actually reclaimed, so a live read lease cannot be mistaken for free space.

### PR-37 — Foreground manifest could become UI-visible before RICC registration

**Gap:** the draft carried `assetManifest` in `ReaderForegroundResult` but did not define who synchronously registers it before ViewModel/UI commit. That violated R2 §14 and left a first-page race where Compose could request an image before RICC knew the committed manifest.

**Resolution:** Task 10 adds non-blocking `ReaderAssetSessionPort`. After `ReaderRouteSession` accepts the semantic foreground commit it assigns a new manifest revision and calls `registerCommitted()` before returning the committed result. Task 11's coordinator installs manifest/presence=`UNKNOWN` synchronously and launches bounded local inspection asynchronously. Feature UI only consumes the already-registered manifest/revision. Same-chapter replacements advance manifest revision without sliding chapter history.

### PR-38 — Process-wide next-chapter speculative concurrency was declared but not enforced

**Gap:** the header froze `MAX_NEXT_CHAPTER_SPECULATIVE_FETCHES=1`, but the arbiter only reserved visible capacity; two sessions could each run one transition fetch and exceed the process-wide cap.

**Resolution:** Task 3 adds `maxSpeculative=1` to `ContentFetchArbiter` and cross-session tests. Task 11 may plan transition work independently per session, but the shared arbiter is the single process-wide enforcement point.

### PR-39 — Transition thresholds and USER_WORK fairness were behaviorally ambiguous

**Gap:** “near end gets stronger transition priority” could be implemented by incorrectly upgrading next-chapter work to `PREFETCH`, contradicting the frozen priority taxonomy. `USER_WORK` aging also lacked a finite threshold, so “bounded fairness” was not executable.

**Resolution:** Task 11 keeps all next-opening network work `SPECULATIVE`: below 8000 bp none; 8000..8999 bp frontier=1; >=9000 bp frontier=4, with active concurrency=1. Task 3 freezes USER_WORK aging at 2,000 ms and allows at most one aged job to claim a non-reserved slot without preempting active work or consuming reserved visible capacity.

### PR-40 — Physical generation ID and session teardown were not fully bounded across process/lifecycle edges

**Gap:** a monotonic-clock nonce can repeat after process recreation and was therefore not strong enough for generation-safe old-cleanup protection. Separately, `releaseSession()` existed in the interface but lacked an executable sequence for cancelling that session's speculative interests and recomputing the multi-session protection union.

**Resolution:** Task 5 introduces `ReaderAssetBlobIdFactory` using logical-key hash + injected random UUID/128-bit nonce, independent of clocks and safe across reconstruction. Task 11 adds an explicit idempotent release sequence: remove only the target session, cancel/unregister its consumers, recompute remaining union, asynchronously reconcile, then trigger bounded store session maintenance.

### PR-41 — Non-persistent identity needed runtime isolation, not merely “no disk write”

**Gap:** two private/non-persistent manifests with otherwise identical source/release/locator facts could still collide in process-scoped single-flight or Coil memory even though durable persistence was disabled. That would violate the security-scope invariant without ever touching disk.

**Resolution:** Task 2 gives every effective `TRANSIENT_ONLY` manifest a fresh `ReaderRuntimeAssetScopeId` and includes it in image-set/page key framing, regardless of whether identity mode itself is trusted, locator-bound, or non-persistent. The scope is process-local, non-secret, never durable, and not restored after process death. Task 9 single-flight and Task 12 Coil keys therefore cannot cross-join separate transient/private runtime scopes.

### PR-42 — Equal auth summaries could hide credential replacement, and in-flight private bytes could cross logout

**Gap:** current `PluginSessionSummary` exposes only status/expiry, so a replacement login can be semantically different while comparing equal. Even after invalidating the manifest, an already-running CRITICAL private image fetch could race to completion and deliver old-account bytes to an old waiter.

**Resolution:** Task 15 adds process-local `credentialGeneration` incremented on every credential-authority mutation and observes `(status, generation)`, not status alone. Security invalidation clears future account-scoped durable rows and calls Task-9 `invalidateSecurityScopedSource`, which atomically hard-cancels matching **non-public** producers/waiters regardless of QoS priority and checks an invalidation bit before shared completion. Public work is unaffected; shared write authority independently prevents late durable resurrection.

### PR-43 — Locator-bound refresh could repeatedly return to stale keys

**Gap:** under `LOCATOR_BOUND`, one locator change changes the image-set namespace and can therefore re-key the entire manifest. Retrying only the failed page under a new key while leaving the committed asset manifest/UI on the old revision would cause repeated refreshes and could decode refreshed bytes under an obsolete Coil memory key.

**Resolution:** Tasks 11, 13, and 14 now define a guarded same-selected-release delivery-manifest replacement. A compatible changed-delivery refresh atomically replaces the current asset manifest at a new revision without changing Reader semantic route/history. Feature Reader observes the new manifest snapshot and constructs new `ReaderPageAssetRequest` values before decode. The stale request ends as `ReaderAssetFailure.Superseded`; old keys are never aliased or populated with refreshed bytes.

### PR-44 — Route-session and delivery-refresh manifest revisions could diverge

**Gap:** the draft let `ReaderRouteSession` increment its own manifest counter on foreground commits while `ReaderAssetCoordinator.replaceDeliveryManifest()` independently incremented the same conceptual revision during locator refresh. After a refresh advanced `1 -> 2`, the next route-session commit could also propose `2`, allowing a stale presentation/request to collide with a newer manifest revision.

**Resolution:** Task 10 now treats the route-session value as a proposal and requires `ReaderAssetSessionPort.registerCommitted()` to return the effective revision. Task 11 is the monotonic registration authority for asset-manifest revisions: `max(current + 1, proposal)` on semantic registration, `current + 1` on guarded delivery replacement. `ReaderRouteSession` stores the returned value before exposing the foreground result. HES/semantic route authority remains with ReaderRouteSession; only the asset-manifest revision sequence is centralized.

### PR-45 — Locator refresh orchestration could create a coordinator/loader cycle or bypass committed-release authority

**Gap:** making `ReaderAssetLoader` call back into `ReaderAssetCoordinator` to replace a manifest would create an ownership/DI cycle, while letting RICC refetch by release ID alone would bypass `ReaderRouteSession`'s authority over the current committed release and graph object.

**Resolution:** Task 14 moves refresh orchestration to `ReaderAssetCoordinator` and binds a narrow per-session `ReaderSelectedReleaseRefreshPort` implemented by `ReaderRouteSession`. The session verifies the expected manifest revision/release, resolves the exact `ChapterRelease` from its current graph, and delegates an exact-release remote refresh to `ReaderRouteCoordinator` through source lane + CRITICAL arbiter without HES planning. The loader remains one-way beneath the coordinator.

### PR-46 — Automatic document cache could resurrect after clear during remote fetch

**Gap:** capturing `AutomaticCacheWriteAuthority` only when `DownloadAwareReaderDocumentStore.write()` begins is too late. A Reader document fetch started before clear/quota-zero could finish afterward, capture a fresh post-clear authority, and repopulate the cache even though it belongs to the pre-clear remote generation.

**Resolution:** Task 6 adds an opaque `ReaderDocumentDurableWriteIntent` seam in `:reader`. `ReaderRouteExecutor` captures it immediately before each source fetch attempt and passes that exact token to `writeWithIntent()` only if that attempt succeeds with a persistable document. The Downloads implementation wraps the shared automatic-cache authority and never refreshes a stale/null token during write. Clear/quota-zero during the remote operation therefore revokes the eventual document publication exactly like the image path.

### PR-47 — Physical EMERGENCY degradation order was referenced but not executable

**Gap:** saying maintenance may degrade protection “according to R2 §37” still left implementers to choose when/how far to cross progress/recent/active protection classes and risked an unbounded low-storage loop on the visible path.

**Resolution:** Task 15 now freezes a maintenance-only degradation sequence after ordinary unprotected victims: `PROGRESS_PROTECTED_DOCUMENT -> RECENT_HISTORY_2 -> RECENT_HISTORY_1 -> ACTIVE_CONSUMED -> ACTIVE_INTERACTIVE`, never explicit downloads/read leases. Each pass stops when reserve is restored or after 32 victims and reschedules if emergency remains. The synchronous ENOSPC retry path from Task 8 never enters these protected classes.

### PR-48 — Identity safety and persistence permission were separated in prose but not in runtime state

**Gap:** a source may provide strong stable identity while explicitly forbidding persistence. The draft manifest/key/commit facts carried identity mode and security scope but no persistence mode, so `TRUSTED_STABLE + Public + NON_PERSISTENT permission` could accidentally capture durable authority.

**Resolution:** Task 2 adds explicit `ReaderAssetPersistenceMode { DURABLE_AUTOMATIC, TRANSIENT_ONLY }` across manifest, key framing, and commit facts. Identity mode, security scope, and persistence permission are resolved independently. Every transient manifest gets a fresh runtime-isolation scope and Task 8 refuses durable authority solely from `persistenceMode`, even when identity is trusted. Schema 12 records persistence mode for durable rows so metadata policy remains auditable.

### PR-49 — Active protection facts were only published on session release

**Gap:** the store/budget coordinator chooses eviction victims using the union of live Reader protections. The draft updated that union explicitly only during `releaseSession()`, so quota reconciliation while the user was actively reading could classify current/recent pages as ordinary cold history and evict them.

**Resolution:** Task 11 now recomputes protection union synchronously in memory after committed-manifest replacement, accepted viewport changes, consumption promotion, and session release, then launches a coalesced `store.reconcile(currentUnion)` update without blocking render. Task 8/6 require `reconcile()` to replace the latest protection snapshot before scheduling victim selection. Rapid Compose frames remain coalesced; storage is not written per frame.


### PR-50 — Generic image HTTP 401 was over-classified as authenticated-source failure

**Gap:** the draft mapped HTTP 401 from the app image-delivery adapter directly to `Unauthorized`, even though that adapter intentionally injects no plugin authentication headers. A stale signed CDN locator can reject with 401, so this classification could skip the required one-cycle same-selected-release locator refresh and surface a false auth failure.

**Resolution:** Task 12 now maps generic terminal delivery 401/403/404 to `DeliveryRejected(status)` unless an adapter has an explicit authenticated-delivery contract. Task 14 runs the same bounded selected-release refresh for those delivery rejections; only a layer with actual auth semantics may produce `Unauthorized`. Raw status remains delivery evidence, never route truth.

### PR-51 — Selected-release refresh lifecycle extension lacked exact `ReaderAssetSessionPort` signatures

**Gap:** Task 14 said to register/unregister a session refresh port but did not freeze method names/signatures, leaving the coordinator/session lifetime seam underspecified and making intermediate-task implementation ambiguous.

**Resolution:** Task 14 now adds exact non-blocking `registerSelectedReleaseRefreshPort(sessionId, port)` and `unregisterSelectedReleaseRefreshPort(sessionId)` methods, extends `NO_OP` explicitly, registers immediately after session construction, and unregisters before the existing idempotent session-release teardown. The loader remains one-way below the coordinator.

### PR-52 — Durable `sourceNamespace` participated in identity without a frozen derivation

**Gap:** `sourceNamespace` is framed into every durable page key/image-set key, stored in metadata, used by clear/security invalidation scopes, and compared during same-release delivery replacement, but the draft never fixed how it is derived. An implementer could therefore choose `pluginId`, `pluginId + package version`, display/source name, host, or adapter class. Those choices would create incompatible cache generations across normal plugin updates, or worse, let different layers derive different namespaces for the same selected source.

**Resolution:** RICC-v1 now has exactly one source-namespace authority. Task 2 defines typed `ReaderAssetSourceNamespace`, constructible for runtime use from the canonical `PluginId` of the **exact `ReaderDocumentSource` that produced the selected document**. Task 10 propagates that exact `sourcePluginId` beside `imageSourcePolicy`, asserts it matches the selected release's `pluginId`, and derives the manifest namespace once in `ReaderAssetManifestFactory`; all later keying, storage, clear, refresh, and security paths consume the already-derived value. Durable Room metadata serializes exactly `sourceNamespace.value`. Plugin/package/runtime version, display name, host, source label, adapter class, and locator never participate, so ordinary source upgrades preserve identity. V1 intentionally has no implicit source-version field: if a source deliberately changes the identity semantics associated with its canonical `PluginId`, that release must first land a reviewed RICC key-schema/namespace-version migration; silently appending or replacing the namespace with a version is forbidden.

## Normative Coverage Matrix

This matrix is the final coverage audit. “Task” means the implementation task that owns the executable contract; global constraints/header policy also apply to every row. R2.2 wins wherever it narrows/replaces R2.

### R2 coverage

| R2 sections | Normative area | Owning plan tasks |
|---|---|---|
| §1–§8 | purpose, authority, baseline, goals/non-goals, invariants, module ownership, runtime architecture | Global Constraints, Master Mapping, Tasks 1–16; architecture proof in Task 16 |
| §9–§13 | full stable identity, exact producing-source namespace, security/variant namespace, trust modes, image-set fingerprint, chapter manifest | Tasks 1–2, 10 |
| §14–§17 | foreground registration ordering, local presence bootstrap, prefetch artifact, prefetched-vs-committed authority | Tasks 8, 10–11 |
| §18–§20 | recent committed history vs graph adjacency, asset-level viewport, consumed-after-presentation semantics | Tasks 10–11, 13 |
| §21–§27 | working set, retention classes, deterministic eviction, rolling frontier, transition thresholds, network classes, cache-pressure admission | Tasks 6–8, 11, 15 |
| §28 | finite encoded payload / validation safety | Tasks 2, 9, 12 |
| §29 | process single-flight, cancellation, promotion | Task 9; arbiter demand promotion in Task 3 |
| §30–§31 | source lane, HES probe separation, one global arbiter, priorities/fairness | Task 3 |
| §32–§33 | viewport/chapter/graph/prefetch supersession and multi-session semantics | Tasks 10–11, 14–15 |
| §34–§37 | unified automatic budget, progress protection, hysteresis, active overflow/emergency storage | Tasks 6–8, 15 |
| §38 | Room schema 12 / migration / indexes / secret-free metadata | Task 4; integrity linkage in Task 5 |
| §39–§41 | store port, atomic storage/read lease, degraded pass-through/ENOSPC | Tasks 2, 5–8 |
| §42–§44 | same-selected-release locator refresh, finite failure taxonomy/retries, HES health boundary | Tasks 9, 14 |
| §45–§47 | hygiene/orphan repair, metadata write amplification, wall-vs-monotonic clocks | Tasks 2–3, 8–9, 15 |
| §48–§50 | Coil boundary, page-local retry, critical render path | Tasks 9, 12–13 |
| §51 | clear automatic cache | Tasks 6, 8, 15 |
| §52 | observability/metrics | Task 15; acceptance counters in Task 16 |
| §53–§58 | acceptance + identity/security + concurrency + viewport + persistence/quota + recovery tests | Focused RED/GREEN tests in Tasks 1–15; end-to-end proof in Task 16 |
| §59 | architecture gates | Tasks 13, 16 |
| §60 | rollout/migration with naturally warming empty RICC table and degraded pass-through | Tasks 4, 8, 15–16 |
| §61 | initial policy defaults / implementation-selected constants | Frozen Implementation-Policy Values; enforcement in Tasks 3, 6, 9, 11 |
| §62 | existing debt interaction | Task 3 resolves participating concurrency scope; Task 7 fixes quota/progress wiring; Global Constraints preserve WorkManager/non-participating debt |
| §63 | deferred extensions | Global Constraints / YAGNI boundaries; no task introduces deferred systems |
| §64 | Definition of Done | Task 16 |
| §65–§66 | R2 self-review/freeze assumptions | Plan Self-review PR-01..PR-52 and this freeze audit |
| §67 | plan gate | This document; production implementation starts only after this plan |

### R2.2 hardening coverage

| R2.2 section | Hardening contract | Owning plan tasks |
|---|---|---|
| §1–§2 | precedence and baseline corrections (`image-bearing`, module-set wording, process recreation scope) | Header/Global Constraints/Master Mapping, Tasks 13, 16 |
| §3 | additional invariants: explicit identity authority, fail-closed private scope, no resurrection, no nested global admission, finite keys/payloads | Tasks 1–3, 6, 8–9, 15 |
| §4 | identity trust, canonical producing-source namespace, locator/key encoding, schema version, golden vectors | Tasks 1–2 |
| §5 | security scope + shared policy/write authority + anti-resurrection | Tasks 2, 6, 8, 15 |
| §6 | four-state local presence including `LOCAL_UNAVAILABLE` | Tasks 2, 8–9 |
| §7 | `ReaderNetworkState.UNKNOWN` policy | Task 11 |
| §8 | explicit session close + multi-session protection union | Tasks 10–11, 13 |
| §9 | arbiter permit lifetime/order/no nesting | Tasks 3, 9, 12, 14 |
| §10 | fairness/preemption hardening | Task 3; security hard-cancel exception in Tasks 9, 15 |
| §11 | atomic final quota reservation/publication | Tasks 6, 8 |
| §12 | existing progress repository is sole progress truth | Task 7 |
| §13 | process recreation means image-byte reuse after semantic reconstruction, not offline semantic cold-open | Task 16 |
| §14 | consumption durability and coalesced timestamps | Tasks 8, 11, 13 |
| §15 | finite manifest/page cardinality | Tasks 1–2; payload bound in Tasks 9, 12 |
| §16 | schema 12 integrity/checksum amendment | Tasks 4–5, 8 |
| §17 | asset-store contract amendment / clear scopes / local unavailable | Tasks 2, 6, 8, 15 |
| §18 | additional acceptance/regression tests | Focused tests Tasks 1–15; Task 16 matrix |
| §19 | corrected architecture gates / module-set not immutable edge graph | Tasks 13, 16 |
| §20 | revised DoD | Task 16 |
| §21–§22 | hardening resolution/freeze assumptions | Plan Self-review PR-01..PR-52; final audit below |
| §23 | implementation-plan gate | This document |

## Plan Freeze Result

Final mechanical and semantic audit requirements for this frozen plan are:

- all 16 tasks have explicit file ownership, RED command, implementation contract, GREEN command, and commit boundary;
- no unresolved placeholder marker, deferred-fill instruction, task-by-analogy shortcut, or implementation-policy hole exists;
- every `Modify:`/`Regenerate:` path exists in current master or is created by an earlier task; every planned new file is named explicitly;
- task dependencies are forward-consistent: identity/contracts -> arbiter/storage/schema -> unified budget/store -> loader -> manifest/runtime -> app/feature -> refresh/security -> acceptance;
- no normative claim treats only image-only documents as non-persistable; the plan consistently uses **image-bearing** documents;
- no second `ReaderAssetPolicyEpoch` exists; document and image automatic writes share `AutomaticCacheWriteAuthority` and one publication/revocation gate;
- no raw 401/403/404 delivery status is treated as auth/route truth in the generic image adapter; only explicit auth semantics or same-selected-release refresh may conclude `Unauthorized`, stale delivery, or route invalidation;
- `sourceNamespace` has one typed V1 authority only: exact producing `ReaderDocumentSource.pluginId -> ReaderAssetSourceNamespace`; no plugin/package version, display metadata, host, adapter class, or locator can silently fork durable identity, and any future semantic namespace break requires reviewed migration;
- `LOCATOR_BOUND` refresh that changes locator facts replaces the committed asset manifest before new bytes decode; old key/memory-key aliasing is forbidden;
- non-public credential mutation invalidates runtime scopes and hard-cancels in-flight non-public bytes before waiter delivery; public cache remains independent from auth-session churn;
- RICC runtime clock usage, ENOSPC retry count, payload/cardinality bounds, process-wide speculative concurrency, and session teardown are all finite/test-visible.

**Freeze decision:** no unresolved Critical/High architecture, identity, security, quota, storage, lifecycle, refresh, concurrency, or plan-executability contradiction remains in the reviewed plan. Any new Critical/High issue discovered during implementation stops the current task; if it changes a normative contract, update R2/R2.2/this plan first, then rerun every affected focused and broad gate before proceeding.
