# Repository Current State

Date: 2026-08-25
Purpose: single source of truth for the implemented repository boundary.

## Executive state

- Product baseline: Android-native, local-first unified novel library design.
- Package namespace and application ID: `app.openstory`.
- Current production Gradle graph: 17 modules.
- Performance tooling graph: 1 `android-test` module (`:benchmark`), excluded from production capability ownership.
- Wave 01-05 implementation and checkpoints remain historical delivery evidence.
- Architecture Baseline 2: **ACCEPTED**.
- Wave 06 Tasks 01-06: **VERIFIED**; Wave 06 is complete.
- Wave 07 Tasks 01-06: **VERIFIED**; Wave 07 is complete.
- Wave 08 Tasks 01-06: **VERIFIED**; Wave 08 is complete.
- Wave 09 Tasks 01-06: **VERIFIED**; Wave 09 is complete.
- Design System Foundation: **ACCEPTED** on 2026-08-12.
- ReDantotsu-inspired Product UI redesign: **ACCEPTED**. The responsive shell, artwork/glass system,
  Discover/Home/Library top-level model, Story/Reader presentation, utility routes, and Roborazzi
  baselines are established; its historical execution plan is no longer the active next-work entry point.
- Discover semantic-feed redesign: **IMPLEMENTED AND VERIFIED**. The current Discover surface is
  `Popular -> Manga | Light Novel -> Latest Updates -> Top Rated`, backed by explicit semantic feed
  metadata, canonical `StoryId` deduplication, source-agnostic projection state, and the schema-7
  semantic-feed persistence introduced by that checkpoint. Manga is enabled/selected; Light Novel
  remains visible but disabled for this delivery.
- Catalog metadata lifecycle unification: **IMPLEMENTED**. `:catalog` owns one
  `CatalogMetadataCoordinator` for `Summary` / `Full` requirements. Full freshness is 24 hours,
  plugin-version mismatch invalidates resolved freshness, stale resolved metadata stays cache-first
  while revalidating, and process-wide single-flight prevents duplicate Details work. Home/Search
  listing payload quality belongs to the plugin operation: missing optional artwork or other
  presentation metadata remains degraded and does not cause host-side Details enrichment.
  `CatalogDetailsLoader` is the sole production Details transport and Room schema 8 stores separate
  Summary/Full provenance while preserving persisted source identity.
- Canonical Catalog Reconciliation & Fusion Engine: **PHASES 0–7 / TASKS 1–42 VERIFIED/CLOSED**.
  The accepted rollout includes the schema-9 canonical foundation, host-owned evidence/reconciliation,
  deterministic Fusion with materialized generations, canonical Story/Search/Discover/Library presentation,
  guarded atomic Story graph merge, durable review, shared evidence-change orchestration, operation-level
  Story Full fallback, post-merge correction review, durable background safety, controlled fail-closed
  reversal, bounded fail-open diagnostics, and final governance/profile/performance certification. This
  accepted rollout closes on schema 9. Accepted evidence is in
  `../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7.md`.
- Canonical Engine Performance and Durability: **ENTRY BASELINE ACCEPTED**.
  That accepted durability boundary is Room schema 10. `MIGRATION_9_10` adds canonical-work leases and the transactional
  catalog-change outbox. All 32 policy scripts, focused host tests, `verify-fast.sh`, `verify.sh`,
  134/134 Room tests on API 26 and API 37, and app instrumentation/launcher smoke on both APIs pass.
- Adaptive Reader Continuity / HES-v1: **M0–M7.5 VERIFIED/CLOSED; HES-v1 FINAL RE-FROZEN / REFERENCE-GRADE**. M7.4 retired the unused `AccessReason`; M7.5 then linearized valid-completion publication against ownership sealing/cancellation, hardened final session result/payload coherence, and removed the remaining obvious test/implementation-only engine exports. The fresh 2026-08-27 final-tree host matrix is GREEN across engine/Reader/downstream tests, architecture/package/current-architecture gates, retained host verification, Room schema stability, and instrumentation compilation. Routing formulas, `ReaderDecisionTrace` data fields, HES/policy versions, the 17-production-module plus `:benchmark` graph, and Room schema 11 remain unchanged. The diagnostic-code namespace naming issue is explicitly deferred to HES-v2. Canonical final-hardening design/plan are `../superpowers/specs/2026-08-27-adaptive-reader-continuity-hes-v1-m7-5-final-freeze-hardening.md` and `../superpowers/plans/2026-08-27-adaptive-reader-continuity-hes-v1-m7-5-final-freeze-hardening.md`.
- Wave 10: **VERIFIED/CLOSED; REQUIRED API 26/API 37 DEVICE MATRIX PASS; FINAL HOST ACCEPTANCE PASS**. Current source is Room schema 11,
  `MIGRATION_10_11` owns Wave 10 notification persistence, and the standalone Detekt, unchanged combined host gate,
  plus package/current-architecture contracts are GREEN in `../internal/checkpoints/wave-10-production-remediation.md`.
  Wave 10 does not redefine `MIGRATION_8_9` or `MIGRATION_9_10`; Wave 11 is unblocked.
- Wave 06-11 implementation plans are rebaselined to the approved post-Baseline-2
  capability/module evolution in
  `../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`.
  Phase 3 closed observe-only reconciliation Tasks 22–25, Phase 4 closed Tasks 26–32 after guarded
  production auto-merge passed its post-enable gate, Phase 5 closed Tasks 33–35, Phase 6 closed Tasks 36–38,
  Phase 7 Task 39 closed durable background maintenance/safety, Task 40 closed controlled reversal, Task 41
  closed structured decision traces/invariant diagnostics, and Task 42 closed final governance plus the
  complete schema/unit/device/app/profile/performance certification matrix on its accepted schema-9 boundary.


- Performance Waves 1-3.5: **VERIFIED** for retained top-level navigation state, lazy Story workloads, Reader chapter reuse, navigation state-layer polish, and one-shot Discover bootstrap.
- Performance Wave 4: **IMPLEMENTED AND RETAINED**. Discover shares one Home observation, Search caches versioned filter definitions, Home/Updates use library-scoped projections, and `:benchmark` owns Macrobenchmark/Baseline Profile tooling. The semantic Discover redesign preserves the single-emission projection contract.
- Performance Wave P5: **IMPLEMENTED AND RETAINED**. Top-level navigation avoids full-scene transitions, Story section data is deferred-then-prewarmed, Discover semantic projection runs on the injected Default-dispatcher boundary, Reader progress is scroll-session sampled, and benchmark-only backdrop/shadow switches remain available for isolation.
- Performance Wave P6: **PROMOTED TO PRODUCTION**. Device A/B confirmed that retaining visited top-level compositions with active-only measurement removes the repeatable ~90 ms Home/Discover rebuild spike: P6.1 reduced CPU P90 by about 68%, median maximum frame by about 59%, and median summed frame CPU by about 12.5% versus the same-build control, with no material RSS regression. Production now retains only visited top-level compositions and measures/places only the active route.

## Independent version spaces

| Surface | Current baseline |
|---|---|
| Application | `versionCode = 1`, `versionName = 1.0` |
| Room database | schema 11 current source; schemas 1-10 remain contiguous historical exports |
| Plugin protocol | major 1, JavaScript-only Baseline 2 protocol; optional bounded catalog external-identifier facts added in canonical-engine Phase 0 |
| Repository index | schema 1 |
| Plugin package | JavaScript-only `.osp` layout with detached SHA-256 and optional detached Ed25519 signature |

These versions are independent. A change in one does not imply a change in another.

## Current production graph

| Module | Current responsibility |
|---|---|
| `:app` | Android entry points, Hilt composition, Navigation 3 routes/back stack, thin WorkManager adapters |
| `:core:common` | `Outcome`, clocks, stable cross-capability IDs, narrow dispatcher abstraction |
| `:core:designsystem` | Domain-neutral theme/tokens, artwork and glass primitives, adaptive layout/content chrome, pull-to-refresh, shared actions/states, equal-width segmented control, static skeleton, and screenshot rendering boundary |
| `:catalog` | Story/catalog models, repository/source contracts, matching/ranking, Home/Search services, unified Summary/Full metadata lifecycle, canonical evidence/read contracts, shared canonical-engine orchestration, durable-work contracts, and local-only bootstrap boundary |
| `:feature:catalog` | Discover, Home, Search, Story, Library, mapping-review, chapter-list, downloads/updates presentation and UI state |
| `:storage:room` | Private Room schema/entities/DAOs/transactions and persistence adapters |
| `:plugins:api` | Pure plugin manifest, wire protocol, package, and repository contracts, including bounded optional catalog external identifiers and opaque latest-update labels |
| `:plugins:runtime` | Package lifecycle, JavaScript isolation, bounded capabilities, runtime facade and persistence SPI |
| `:library` | Library membership/status, pure explainable matching, bounded plugin content-source search, and protected content-mapping policy/services |
| `:chapters` | Chapter-label normalization, provider-neutral release sources, deterministic aggregation, synchronization policy, and repository contracts |
| `:reader` | Sanitized document loading, HES-v1 session/effect coordination, process-scoped source health/execution limits, prefetch/competition, and exact progress policy/contracts |
| `:reader:engine` | HES-v1 pure JVM routing values/policy/facts and deterministic adaptive reasoner; no effects/runtime ownership |
| `:feature:reader` | Restorable Reader state and accessible structured text / vertical image-page Compose presentation |
| `:downloads` | Explicit download state, automatic-cache quota/eviction, Reader resolution, and reconciliation policy |
| `:settings` | Wave 10 typed settings/auth/background/notification policy contracts and persistence-facing ports |
| `:feature:settings` | Independent Wave 10 Settings presentation and status controls |
| `:storage:files` | Atomic opaque chapter-blob persistence, inventory, and low-space admission |

The exact dependency policy is `../../config/architecture/module-boundaries.json`. Package
rules additionally keep feature code away from storage/runtime, catalog away from Compose
and Android context, and Room imports limited to reviewed capability contracts plus the
runtime persistence SPI.

## Implemented product boundary

- The production distribution bundles MyAnimeList (`CATALOG`) and MangaDex (`CONTENT`) through
  one app-owned descriptor registry. Both use protocol `1` and the same package, installation,
  capability, and execution path as third-party packages; neither has a privileged runtime path.
- The bundled registry is an extensible distribution list, not a single-provider architecture
  invariant. Current architecture verification requires every production `.osp` asset to have a
  matching descriptor and rejects undeclared assets.
- MangaDex `1.3.0` exposes online image-page chapter bodies through `content.chapter`. Its manifest
  explicitly opts into host-rendered remote images and disables offline download; Reader image requests
  use bounded in-memory caching and do not bypass Hikari's managed storage quota through a separate
  image disk cache.
- MangaDex@Home image success/failure reporting to `api.mangadex.network/report` is not yet
  implemented. Reader retry refreshes chapter delivery metadata and obtains a fresh base URL, but
  provider load reporting remains a release-hardening follow-up before the MangaDex image path is
  considered provider-complete.
- Catalog Home, Search, and Story metadata are source-preserving, cache-first, and exposed
  through catalog-owned repository/services. `CatalogMetadataCoordinator` is the single metadata
  lifetime owner: `Summary` never triggers Details, `Full` uses a 24-hour TTL, plugin-version mismatch invalidates freshness, and stale resolved data is returned
  immediately while one process-owned revalidation runs in the background. `CatalogDetailsLoader`
  is the only production `CatalogSource.details(...)` caller. Home sections carry explicit
  `CatalogFeedKind` (`POPULAR`, `LATEST_UPDATES`, `TOP_RATED`, `OTHER`), while entries may carry
  normalized publication status and coherent source-reported latest-update metadata.
- Matching and aggregate ranking remain deterministic and preserve source scores/scales. Discover
  deduplicates semantic feeds by canonical `StoryId` before Compose and never infers feed meaning
  from section titles or provider IDs.
- Discover, Home, Search, and Story presentation is owned by `:feature:catalog` with Hilt ViewModels,
  lifecycle-aware state collection, cancellation, cached-content retention, and isolated
  operation failures. Discover uses one outer `LazyColumn`; Popular is a manual pager (max 5),
  Latest Updates is a bounded 3-column grid (max 9), and Top Rated is a ranked list (max 5).
- Room schema 11 is the current source persistence foundation. It retains the Baseline-2 catalog/runtime state, metadata-only
  Library membership, protected content mappings, chapter graphs, aggregation overrides,
  synchronization state, canonical plus exact-release reading progress, Wave 09 cache/download
  metadata, Discover semantic feed/status/latest-update fields, separate Summary/Full catalog metadata
  provenance, canonical-engine queue leases, the transactional catalog-change outbox, and Wave 10
  notification persistence/claim-recovery state. `MIGRATION_9_10` remains canonical durability ownership;
  `MIGRATION_10_11` remains Wave 10 notification-persistence ownership. Schemas 1-10 remain contiguous
  historical exports, schema 9 remains the accepted CCE closeout export, and schema 1 remains byte-frozen.
  Room entities/DAOs stay private to `:storage:room`.
- Metadata-only Library membership remains local and idempotent. After membership commits,
  `LibraryService` may delegate mapping discovery to the Task-04 scheduler; scheduler failure
  does not roll back the committed membership.
- Task 04 adds approved `:library -> :plugins:api` and narrow public-runtime access for
  content-source execution. Library remains forbidden from Room and plugin-runtime
  persistence/install/security internals.
- Library presentation lives in `:feature:catalog`, combines membership with one bulk
  catalog-owned display projection, keeps filtering/sorting local, uses stable `StoryId`
  keys, and represents metadata-only entries as `NO_MAPPING` instead of an error.
- Content-story matching is pure, deterministic, explainable, and policy-versioned in
  `:library`; content-type conflicts reject, direct evidence may auto-link only when no
  type conflict exists, author conflicts prevent automatic linking, and missing optional
  evidence is not treated as negative evidence.
- Plugin-backed content discovery runs in deterministic quick/deferred stages with bounded
  queries/candidates, per-source deadlines, peer failure isolation, and serialized calls per
  plugin/version. Optional URL resolution rejects non-HTTPS, oversized, or undeclared-host
  input before runtime invocation.
- `LibraryMappingWorker` is a thin `:app` WorkManager adapter keyed by stable `StoryId`; it
  delegates Library policy instead of owning matching/search decisions. Automatic mapping
  persistence cannot overwrite `USER_APPROVED` or `USER_URL` mappings.
- Mapping identity is scoped by canonical story and plugin. User approvals and accepted URL
  mappings are protected, approval is idempotent, and rejections are persisted with the
  matcher policy version so a later policy version may reconsider the same candidate.
- Mapping review and URL import presentation live in `:feature:catalog`. The UI calls only
  Library services, surfaces evidence/failures, and routes manual URL resolution through the
  existing HTTPS/declared-host content-source boundary before a protected `USER_URL` write.
- Chapter synchronization consumes only protected Library mappings, isolates source failures,
  commits recent results before full history, resumes full/incremental work from persisted
  source-scoped state, and advances cursors only with the corresponding graph transaction.
- Canonical chapter aggregation is deterministic and provider-neutral. User force-link and
  force-separate overrides outrank automation, missing groups become visible tombstones, and
  Story presentation expands releases without owning aggregation or storage policy.
- Plugin JavaScript receives only the host-controlled HTTP, HTML query, and safe-log
  capabilities with allowlists, budgets, cancellation, and managed-credential isolation.

Wave 08 adds bounded structured-document sanitization before rendering, explained stable
release selection, store-first loading with alternate fallback, exact progress, stable-ID
navigation, restorable state, and accessible Compose rendering. Wave 09 adds atomic opaque
blob storage, schema-6 cache/download metadata, bounded cache eviction, explicit downloads,
offline-first Reader resolution, bulk controls, low-space admission, and race-safe storage
reconciliation. Wave 10 production source now contains local background scheduling, authentication,
notifications, Settings presentation, Reader preferences integration, and schema-11 notification
persistence. Its final complete host/API 26/API 37 acceptance is still open; release hardening remains
outside the accepted boundary.

The accepted Design System Foundation and Product UI checkpoint add the current artwork/glass,
responsive shell, shared content/action/state primitives, and screenshot baseline without changing
capability ownership. The Product UI checkpoint is complete; its 2026-08-12 plan is retained as an
implementation record. The screenshot suite remains pinned to Robolectric SDK 35 because the pinned
Robolectric 4.16.1 cannot select target SDK 37.

The 2026-08-19 Discover semantic-feed follow-up is also complete. It extends the generic catalog
wire/source/domain contract, carries semantic metadata through Home/details ingestion, migrates Room
6 -> 7 with sparse rich-metadata preservation, projects cached semantic feeds on the shared Default
dispatcher, and replaces source/category controls with the current semantic UI. The shared design
system now includes `HikariSegmentedControl` and static `HikariSkeleton` primitives. The later
catalog metadata-lifecycle unification preserved that presentation and the then-current 14-module graph while
advancing Room 7 -> 8 and moving Search, Story, and Discover metadata requirements behind the shared
coordinator. Wave 10 expanded the production graph to 16 modules (`:settings` and `:feature:settings`) and advanced
Room to schema 11; HES-v1 M0 adds `:reader:engine` as the seventeenth production module without
changing that schema. M1–M7.3 remain verified/closed historical milestones. M7.4 retired `AccessReason`, and M7.5 closed the final completion-publication, final-session-coherence, and minimal API-hygiene gaps. The fresh M7.5 final-tree matrix is accepted; HES-v1 is final re-frozen/reference-grade. Wave 10 remains verified/closed.

## Architecture Baseline 2 status

- R0 froze invariants and migration classifications.
- R1 established the target module/build foundation.
- R2 replaced plugin protocol, runtime/security, and bundled reference package boundaries.
- R3 replaced catalog core and Room persistence ownership.
- R4 replaced Home/Search/Story presentation, navigation, and DI composition.
- R5 removed legacy modules/contracts/samples/scripts, rewrote active SDK/governance text,
  froze exact dependency/package rules, and completed the ownership audit.
- R6 passed deterministic local verification, API 26/37 instrumentation, app launch,
  MyAnimeList reference integration, and the final ownership/public-surface audit.

Acceptance evidence:

- `../internal/checkpoints/architecture-baseline-2-r3.md`
- `../internal/checkpoints/architecture-baseline-2-r4.md`
- `../internal/checkpoints/architecture-baseline-2-r5.md`
- `../internal/checkpoints/architecture-baseline-2.md`

The final ownership records are
`../internal/architecture-baseline-2/r5-ownership-audit.md` and
`../internal/architecture-baseline-2/r6-final-audit.md`.

## Verification status

Architecture Baseline 2 acceptance proves repository verification, all local unit suites,
architecture/source/package gates, Detekt, lint, APK assembly, Room schema stability,
runtime/security instrumentation, storage instrumentation, Compose/app instrumentation,
and launcher smoke on API 26 and API 37. Wave 06 Task 01 then passed Library/Room/app
JVM gates, Detekt, Room instrumentation on API 26/API 37, the current-architecture
contract, and full repository verification. Tasks 02-03 subsequently passed catalog/feature/
Library JVM suites plus Detekt, Library Compose instrumentation on API 26/API 37, targeted
and full API-37 app integration reruns after one transient sandbox failure, exact module/
package gates, lint, Room schema stability, and full repository verification with exit 0.
Task 04 then passed focused catalog/feature/Library/plugin/runtime/app JVM suites and
Detekt, app instrumentation on API 37 and API 26, exact eight-module/package/source gates,
lint, dependency verification, Room schema stability, and full repository verification
with exit 0. Task 05 added Room schema 3 and protected mapping/rejection persistence, then
passed focused Library/Room/app JVM gates, Detekt, immutable schema-1/2 checks, and 16/16
Room instrumentation tests on both API 37 and API 26. Task 06 passed focused feature/Library/
app JVM gates and Detekt, 15/15 feature instrumentation tests on API 37 and API 26, app
instrumentation on both API levels, and full repository verification with `exit=0`; current
architecture verification reported 8 modules and Room schema 1..3 with stable schema export.
Wave 06 is therefore complete. Wave 07 then introduced the ninth production module,
`:chapters`, and Room schema 4; its six task commits passed chapters/app/feature JVM suites,
20 Room instrumentation tests, 16 feature instrumentation tests, the ordered app contract/
navigation/launch suite, architecture/package/source gates, Detekt, lint, schema stability,
and full `scripts/verify.sh` with `exit=0`. Deep review kept aggregation/sync policy in
`:chapters`, transactions in `:storage:room`, WorkManager in `:app`, and lazy chapter
presentation in `:feature:catalog`. Wave 08 completed the eleven-module Reader baseline and
Room schema 5. Wave 09 then expanded the production graph to thirteen modules and Room schema
6. Full repository verification, lint, Detekt, exact architecture, schema stability, and
structural hard policies pass. API 26 device suites pass for Catalog, Reader, Room, and app;
Room plus the Wave 09 low-storage contract pass on API 37. Deep review verified that Downloads
owns state/policy, Room owns metadata transactions, file storage owns bytes, Reader consumes
only its store port, and WorkManager remains an app adapter.

Product UI Task 1 then passed strict dependency-metadata bootstrap, the focused build-logic
and screenshot-owner JVM suites, exact 14-module/current-architecture gates, the full
repository Gradle gate, and Room schema stability. Evidence is recorded in
`../internal/checkpoints/product-ui-task-01-toolchain.md`. Product UI Task 2 then passed its
Windows PowerShell target-pack gate after correcting the Edge viewport/canvas mismatch and
a too-strict blank-image heuristic; the final gate renders all 34 required PNGs at exact 2x
dimensions and rejects truly uniform captures. Evidence is recorded in
`../internal/checkpoints/product-ui-task-02-target-pack.md`. Product UI Task 3 then passed the
focused `:core:designsystem:testDebugUnitTest recordRoborazziDebug` gate and the canonical full
`./scripts/verify.sh` gate after two acceptance corrections: pinning Robolectric to SDK 35 for
the screenshot class and replacing an unsigned-byte `0xFF` literal with a named constant for
Detekt. The full gate completed `BUILD SUCCESSFUL` with 623 actionable tasks and stable Room
schema exports. Evidence is recorded in
`../internal/checkpoints/product-ui-task-03-artwork.md`. The repository also exposes
`./scripts/verify-fast.sh` for development feedback while `./scripts/verify.sh` remains the
full acceptance gate; both preserve strict dependency verification.

The Discover semantic-feed checkpoint then passed focused plugin/catalog/Room contract tests,
Room migration/repository instrumentation (10/10), semantic projection/ViewModel/design-system
suites, Discover Compose instrumentation (2/2), app launch/navigation instrumentation (14/14),
Roborazzi compare/record/verify, and a 5-iteration `discoverScroll` Macrobenchmark on Redmi Note 9S
(API 35 test environment). The benchmark snapshot recorded frame CPU P50 11.08 ms, P90 13.08 ms,
P95 13.57 ms, and P99 18.41 ms under `run-from-apk` compilation. The full repository verifier was
updated only where stale policy/schema assumptions still encoded the pre-redesign architecture;
Detekt blockers introduced by the new segmented/skeleton code were fixed without suppressions.
Acceptance details are in `../internal/checkpoints/discover-semantic-feed-redesign.md`.

Evidence:

- `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m0.md`
- `../internal/checkpoints/discover-semantic-feed-redesign.md`
- `../internal/checkpoints/product-ui-redesign.md`
- `../internal/checkpoints/wave-06-task-01-metadata-only-library.md`
- `../internal/checkpoints/wave-06-task-02-library-presentation.md`
- `../internal/checkpoints/wave-06-task-03-content-story-matching.md`
- `../internal/checkpoints/wave-06-task-04-content-source-search.md`
- `../internal/checkpoints/wave-06-task-05-protected-content-mappings.md`
- `../internal/checkpoints/wave-06-task-06-mapping-review-url-import.md`
- `../internal/checkpoints/wave-08-reader-and-reading-progress.md`
- `../internal/checkpoints/product-ui-task-01-toolchain.md`
- `../internal/checkpoints/product-ui-task-02-target-pack.md`
- `../internal/checkpoints/product-ui-task-03-artwork.md`

## Wave 10 execution baseline

Wave 10 implementation entered HES on its 16-production-module / Room-schema-11 boundary. The current
M7.5 final-hardening tree still has 17 production modules because `:reader:engine` was added in M0; Room
remains schema 11. M0 evidence is recorded in `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m0.md`,
M7/M7.1 historical closure evidence in `../internal/checkpoints/adaptive-reader-continuity-hes-v1.md`, M7.2
historical re-freeze evidence in `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-2.md`, and the
final M7.3 conformance re-freeze evidence in `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-3.md`.
M7.4 closure-by-final-tree evidence remains in `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-4.md`; final HES-v1 closure evidence is recorded in `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-5.md`.
Wave 10 final acceptance is closed and recorded in:

- `../superpowers/specs/2026-08-24-wave-10-clean-background-auth-notifications-design.md`
- `../implementation/waves/wave-10-background-sync-auth-and-notifications.md`
- `../internal/checkpoints/wave-10-entry-readiness-2026-08-24.md`
- `../internal/checkpoints/wave-10-production-remediation.md`

Current source contains the Reader preferences port and settings adapter, settings-backed policy
contracts, request-target-scoped plugin credential handling, bounded periodic chapter candidate
selection, transactional chapter-change/notification persistence, notification recovery, Settings
presentation, and the Wave 10 startup hooks. `MIGRATION_10_11` remains the sole Wave 10 schema owner.

Focused host/API 35 remediation evidence exists, and the required API 26/API 37 Wave 10 acceptance matrix
is developer-confirmed GREEN. M7.1 repaired the repository-wide Detekt debt and M7.2 later re-froze HES-v1
from its own fresh accepted matrix. M7.3 reopened only the HES-v1 conformance boundary; that did not rewrite Wave 10's accepted evidence, and its blocking matrix later closed green. M7.4 retired the unused `AccessReason` symbol. M7.5 closed the last Reader Engine hardening boundary from a fresh green final-tree matrix; the unchanged V1/module/schema boundary remains intact and HES-v1 is final re-frozen/reference-grade. Wave 10 therefore remains **VERIFIED/CLOSED; DEVICE MATRIX PASS; FINAL HOST
ACCEPTANCE PASS**.

## Source-of-truth rule

When documents disagree:

1. Approved product design owns scope and domain invariants.
2. Repository code and tests own what is physically implemented.
3. Accepted checkpoint evidence owns whether a gate passed.
4. This file owns the current implementation position.
5. Archived documents are historical context, not execution entry points.

See `document-governance.md` for the complete precedence policy.
