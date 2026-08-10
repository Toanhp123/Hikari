# Repository Current State

Date: 2026-08-11
Purpose: single source of truth for the implemented repository boundary.

## Executive state

- Product baseline: Android-native, local-first unified novel library design.
- Package namespace and application ID: `app.openstory`.
- Current production Gradle graph: 8 modules.
- Wave 01-05 implementation and checkpoints remain historical delivery evidence.
- Architecture Baseline 2: **ACCEPTED**.
- Wave 06 Tasks 01-03: **VERIFIED**.
- Current active boundary: **Wave 06 Task 04 - search content plugins in quick and deferred stages**.
- Wave 06 is in progress; metadata-only membership, Library presentation, and pure
  content-story matching are implemented and verified, while Tasks 04-06 remain.
- Wave 06-11 implementation plans are rebaselined to the approved post-Baseline-2
  capability/module evolution in
  `../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`.

## Independent version spaces

| Surface | Current baseline |
|---|---|
| Application | `versionCode = 1`, `versionName = 1.0` |
| Room database | schema 2 current; schema 1 remains the frozen Architecture Baseline 2 schema |
| Plugin protocol | major 1, JavaScript-only Baseline 2 protocol |
| Repository index | schema 1 |
| Plugin package | JavaScript-only `.osp` layout with detached SHA-256 and optional detached Ed25519 signature |

These versions are independent. A change in one does not imply a change in another.

## Current production graph

| Module | Current responsibility |
|---|---|
| `:app` | Android entry points, Hilt composition, Navigation 3 routes/back stack |
| `:core:common` | `Outcome`, clocks, stable cross-capability IDs, narrow dispatcher abstraction |
| `:catalog` | Story/catalog models, repository/source contracts, matching, ranking, refresh/search/details |
| `:feature:catalog` | Home, Search, Story, and Library Compose presentation and UI state |
| `:storage:room` | Private Room schema/entities/DAOs/transactions and persistence adapters |
| `:plugins:api` | Pure plugin manifest, wire protocol, package, and repository contracts |
| `:plugins:runtime` | Package lifecycle, JavaScript isolation, bounded capabilities, runtime facade and persistence SPI |
| `:library` | Library membership/status plus pure explainable content-story matching; plugin search and protected mappings arrive in Tasks 04-05 |

The exact dependency policy is `../../config/architecture/module-boundaries.json`. Package
rules additionally keep feature code away from storage/runtime, catalog away from Compose
and Android context, and Room imports limited to reviewed capability contracts plus the
runtime persistence SPI.

## Implemented product boundary

- Bundled MyAnimeList catalog package uses protocol `1` and the same runtime path as any
  third-party package.
- Catalog Home, Search, and Story details are source-preserving, cache-first, and exposed
  through catalog-owned repository/services.
- Matching and aggregate ranking are deterministic and preserve source scores/scales.
- Home, Search, and Story presentation is owned by `:feature:catalog` with Hilt ViewModels,
  lifecycle-aware state collection, cancellation, cached-content retention, and isolated
  operation failures.
- Room schema 2 stores the Baseline-2 catalog/runtime state plus metadata-only Library
  membership; schema 1 remains byte-frozen and Room entities/DAOs remain private to
  `:storage:room`.
- Metadata-only Library membership is local and idempotent. Task 03 adds only the narrow
  `:library -> :catalog` dependency needed for catalog content types/matching evidence;
  membership operations still have no plugin/runtime dependency.
- Library presentation lives in `:feature:catalog`, combines membership with one bulk
  catalog-owned display projection, keeps filtering/sorting local, uses stable `StoryId`
  keys, and represents metadata-only entries as `NO_MAPPING` instead of an error.
- Content-story matching is pure, deterministic, explainable, and policy-versioned in
  `:library`; content-type conflicts reject, direct evidence may auto-link only when no
  type conflict exists, author conflicts prevent automatic linking, and missing optional
  evidence is not treated as negative evidence.
- Plugin JavaScript receives only the host-controlled HTTP, HTML query, and safe-log
  capabilities with allowlists, budgets, cancellation, and managed-credential isolation.

Plugin-backed content-source search, protected mapping persistence/review, URL import,
chapter synchronization, Reader, downloads, background sync, authentication, notifications,
and release-hardening behavior remain outside the implemented Task-03 boundary.

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
Wave 06 Task 04 is the next implementation entry.

Evidence:

- `../internal/checkpoints/wave-06-task-01-metadata-only-library.md`
- `../internal/checkpoints/wave-06-task-02-library-presentation.md`
- `../internal/checkpoints/wave-06-task-03-content-story-matching.md`

## Source-of-truth rule

When documents disagree:

1. Approved product design owns scope and domain invariants.
2. Repository code and tests own what is physically implemented.
3. Accepted checkpoint evidence owns whether a gate passed.
4. This file owns the current implementation position.
5. Archived documents are historical context, not execution entry points.

See `document-governance.md` for the complete precedence policy.
