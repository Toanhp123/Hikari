# Repository Current State

Date: 2026-08-11
Purpose: single source of truth for the implemented repository boundary.

## Executive state

- Product baseline: Android-native, local-first unified novel library design.
- Package namespace and application ID: `app.openstory`.
- Current production Gradle graph: 11 modules.
- Wave 01-05 implementation and checkpoints remain historical delivery evidence.
- Architecture Baseline 2: **ACCEPTED**.
- Wave 06 Tasks 01-06: **VERIFIED**; Wave 06 is complete.
- Wave 07 Tasks 01-06: **VERIFIED**; Wave 07 is complete.
- Wave 08 Tasks 01-06: **IMPLEMENTATION PRESENT**; checkpoint verification remains open.
- Current active boundary: **Wave 08 checkpoint verification**.
- Wave 06-11 implementation plans are rebaselined to the approved post-Baseline-2
  capability/module evolution in
  `../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`.

## Independent version spaces

| Surface | Current baseline |
|---|---|
| Application | `versionCode = 1`, `versionName = 1.0` |
| Room database | schema 5 current; schemas 1-4 remain frozen historical exports |
| Plugin protocol | major 1, JavaScript-only Baseline 2 protocol |
| Repository index | schema 1 |
| Plugin package | JavaScript-only `.osp` layout with detached SHA-256 and optional detached Ed25519 signature |

These versions are independent. A change in one does not imply a change in another.

## Current production graph

| Module | Current responsibility |
|---|---|
| `:app` | Android entry points, Hilt composition, Navigation 3 routes/back stack, thin WorkManager adapters |
| `:core:common` | `Outcome`, clocks, stable cross-capability IDs, narrow dispatcher abstraction |
| `:catalog` | Story/catalog models, repository/source contracts, matching, ranking, refresh/search/details |
| `:feature:catalog` | Home, Search, Story, Library, and mapping-review Compose presentation and UI state |
| `:storage:room` | Private Room schema/entities/DAOs/transactions and persistence adapters |
| `:plugins:api` | Pure plugin manifest, wire protocol, package, and repository contracts |
| `:plugins:runtime` | Package lifecycle, JavaScript isolation, bounded capabilities, runtime facade and persistence SPI |
| `:library` | Library membership/status, pure explainable matching, bounded plugin content-source search, and protected content-mapping policy/services |
| `:chapters` | Chapter-label normalization, provider-neutral release sources, deterministic aggregation, synchronization policy, and repository contracts |
| `:reader` | Sanitized document loading, deterministic release selection/fallback, and exact progress policy/contracts |
| `:feature:reader` | Restorable Reader state and accessible structured-text Compose presentation |

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
- Catalog Home, Search, and Story details are source-preserving, cache-first, and exposed
  through catalog-owned repository/services.
- Matching and aggregate ranking are deterministic and preserve source scores/scales.
- Home, Search, and Story presentation is owned by `:feature:catalog` with Hilt ViewModels,
  lifecycle-aware state collection, cancellation, cached-content retention, and isolated
  operation failures.
- Room schema 5 stores the Baseline-2 catalog/runtime state, metadata-only Library
  membership, protected content mappings, chapter graphs, aggregation overrides, and sync
  state, and canonical plus exact-release reading progress; schemas 1-4 remain historical
  exports and schema 1 remains byte-frozen. Room entities/DAOs stay
  private to `:storage:room`.
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
navigation, restorable state, and accessible Compose rendering. Downloads, periodic
background sync, authentication, notifications, and release hardening remain outside the
implemented Wave-08 boundary.

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
presentation in `:feature:catalog`. Wave 08 implementation is now present at eleven modules
and Room schema 5. Repository-local architecture/package/source checks pass; Gradle, lint,
Detekt, connected instrumentation, and device checkpoints remain open because this execution
environment could not download the pinned Gradle distribution.

Evidence:

- `../internal/checkpoints/wave-06-task-01-metadata-only-library.md`
- `../internal/checkpoints/wave-06-task-02-library-presentation.md`
- `../internal/checkpoints/wave-06-task-03-content-story-matching.md`
- `../internal/checkpoints/wave-06-task-04-content-source-search.md`
- `../internal/checkpoints/wave-06-task-05-protected-content-mappings.md`
- `../internal/checkpoints/wave-06-task-06-mapping-review-url-import.md`
- `../internal/checkpoints/wave-08-reader-and-reading-progress.md`

## Source-of-truth rule

When documents disagree:

1. Approved product design owns scope and domain invariants.
2. Repository code and tests own what is physically implemented.
3. Accepted checkpoint evidence owns whether a gate passed.
4. This file owns the current implementation position.
5. Archived documents are historical context, not execution entry points.

See `document-governance.md` for the complete precedence policy.
