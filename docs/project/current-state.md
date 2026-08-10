# Repository Current State

Date: 2026-08-10
Purpose: single source of truth for the implemented repository boundary.

## Executive state

- Product baseline: Android-native, local-first unified novel library design.
- Package namespace and application ID: `app.openstory`.
- Current production Gradle graph: 7 modules.
- Wave 01-05 implementation and checkpoints remain historical delivery evidence.
- Architecture Baseline 2 R5: **ACCEPTED**.
- Current active boundary: **R6 - Architecture Acceptance**.
- Wave 06 product implementation remains frozen until R6 is accepted.

## Independent version spaces

| Surface | Current baseline |
|---|---|
| Application | `versionCode = 1`, `versionName = 1.0` |
| Room database | schema 1 |
| Plugin protocol | major 1 |
| Repository index | schema 1 |
| Plugin package | bounded `.osp` ZIP containing `manifest.json`, `main.js`, and optional assets |

These versions are independent. A change in one does not imply a change in another.

## Final production graph

| Module | Current responsibility |
|---|---|
| `:app` | Android entry points, Hilt composition, Navigation 3 routes/back stack |
| `:core:common` | `Outcome`, clocks, stable cross-capability IDs, narrow dispatcher abstraction |
| `:catalog` | Story/catalog models, repository/source contracts, matching, ranking, refresh/search/details |
| `:feature:catalog` | Home, Search, and Story Compose presentation and UI state |
| `:storage:room` | Private Room schema/entities/DAOs/transactions and persistence adapters |
| `:plugins:api` | Pure plugin manifest, wire protocol, package, and repository contracts |
| `:plugins:runtime` | Package lifecycle, JavaScript isolation, bounded capabilities, runtime facade and persistence SPI |

The exact dependency policy is `../../config/architecture/module-boundaries.json`. Package
rules additionally keep feature code away from storage/runtime, catalog away from Compose
and Android context, and Room imports limited to runtime persistence SPI contracts.

## Implemented product boundary

- Bundled MyAnimeList catalog package uses protocol `1` and the same runtime path as any
  third-party package.
- Catalog Home, Search, and Story details are source-preserving, cache-first, and exposed
  through catalog-owned repository/services.
- Matching and aggregate ranking are deterministic and preserve source scores/scales.
- Home, Search, and Story presentation is owned by `:feature:catalog` with Hilt ViewModels,
  lifecycle-aware state collection, cancellation, cached-content retention, and isolated
  operation failures.
- Room schema 1 stores current catalog snapshots/details and plugin runtime state; entities
  and DAOs remain private to `:storage:room`.
- Plugin JavaScript receives only the host-controlled HTTP, HTML query, and safe-log
  capabilities with allowlists, budgets, cancellation, and managed-credential isolation.

Library, chapter synchronization, Reader, downloads, background sync, authentication,
notifications, and release-hardening behavior are not implemented by Architecture Baseline 2.

## Architecture Baseline 2 status

- R0 froze invariants and migration classifications.
- R1 established the target module/build foundation.
- R2 replaced plugin protocol, runtime/security, and bundled reference package boundaries.
- R3 replaced catalog core and Room persistence ownership.
- R4 replaced Home/Search/Story presentation, navigation, and DI composition.
- R5 removed legacy modules/contracts/samples/scripts, rewrote active SDK/governance text,
  froze exact dependency/package rules, and completed the ownership audit.

Acceptance evidence:

- `../internal/checkpoints/architecture-baseline-2-r3.md`
- `../internal/checkpoints/architecture-baseline-2-r4.md`
- `../internal/checkpoints/architecture-baseline-2-r5.md`

The final ownership record is
`../internal/architecture-baseline-2/r5-ownership-audit.md`.

## Verification status

R5 repository verification, all unit suites, architecture/source/package gates, Detekt,
lint, APK assembly, Room schema stability, and structural review are accepted in the R5
checkpoint. R6 owns the complete architecture acceptance run, including connected-device
journeys and the final freeze decision.

## Source-of-truth rule

When documents disagree:

1. Approved product design owns scope and domain invariants.
2. Repository code and tests own what is physically implemented.
3. Accepted checkpoint evidence owns whether a gate passed.
4. This file owns the current implementation position.
5. Archived documents are historical context, not execution entry points.

See `document-governance.md` for the complete precedence policy.
