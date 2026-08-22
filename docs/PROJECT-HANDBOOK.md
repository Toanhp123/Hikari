# OpenStory / Hikari Project Handbook

Date: 2026-08-21
Status: **Canonical documentation entry point**

This handbook exists so a contributor or agent can understand the project without
choosing between several equally plausible historical plans. Detailed specifications,
wave plans and evidence remain separate for reviewability, but this file defines how
they fit together.

## 1. Product in one paragraph

OpenStory/Hikari is an Android-only, local-first light-novel/web-novel application that
separates **catalog metadata** from **readable content sources**. Catalog plugins explain
what a work is; content plugins expose readable releases; the host creates app-owned
`CanonicalStory` and `CanonicalChapter` identities, groups multiple source/language
releases under canonical chapters, preserves progress/offline data locally, and never
requires an account or cloud backend for the MVP.

## 2. Product invariants

The approved design establishes these non-negotiable boundaries:

1. `CanonicalStory` is app-owned and cannot be replaced by one catalog/site ID.
2. Catalog and content plugin responsibilities remain separate even when one package implements both.
3. `CanonicalChapter` is the progress unit; `ChapterRelease` is a selectable publication/source/language/group variant.
4. One source disappearing must not immediately delete canonical identity, progress or explicit downloads.
5. User merge/mapping corrections outrank automatic matching and survive synchronization.
6. Plugin code cannot directly access Room, arbitrary files, Android services or undeclared network hosts.
7. Library add is local and immediate; readable-source discovery may happen afterward.
8. Background behavior is local, idempotent, observable and manually reproducible.
9. Automatic cache and explicit offline downloads have different retention semantics.
10. The app does not bypass paywalls, DRM, CAPTCHAs or other access controls.

The full approved baseline is `project/approved-product-design.md`.

## 3. MVP scope

Included at completion: semantic multi-catalog discovery, local Library, catalog/content
plugin lifecycle, story matching, recent/full/incremental chapter sync, canonical chapter
aggregation, text reader and source switching, exact progress, cache/downloads, local
scheduled updates/notifications, guarded WebView source login, URL import/manual mapping,
and open-source APK distribution. The 2026-08-19 Discover amendment replaces the old
source/category-driven primary Discover composition; catalog identity remains available in
data and source-preserving details rather than as the main Discover navigation model.

Excluded from MVP: accounts/cloud sync, centralized plugin moderation, manga image reader,
anime functionality, TTS/audiobook/translation/AI summaries, social features, native-code
plugins, unrestricted JavaScript, automatic access-control bypass and cross-device download
transfer.

Implementation note: the current repository already contains a bounded MangaDex image-page
Reader path. That is an implementation fact beyond the original 2026-08-03 MVP exclusion;
it does not silently amend the release-scope decision. `project/current-state.md` records the
capability, while any decision to make manga image reading normative MVP scope must be approved
separately.

## 4. Repository implementation baseline

```text
Package/application ID  app.openstory
Minimum SDK             26
Compile/target SDK      37
JDK                     17
Gradle                  9.5
AGP                     9.3.0
Kotlin                  2.4.10
Compose BOM             2026.06.00
Navigation              Navigation 3 1.1.4
Room                    2.8.4
WorkManager             2.11.2
Coroutines              1.11.0
kotlinx.serialization   1.11.0
Hilt                    2.60.1
JavaScript sandbox      AndroidX JavaScriptEngine 1.1.0
Artwork loading         Coil 3.5.0
Backdrop effect         Backdrop 2.0.0
Screenshot testing      Roborazzi 1.70.0 + Robolectric 4.16.1
```

Where an approved product document uses broader component terminology, these pins describe
the repository implementation baseline, not a product-scope rewrite.

## 5. Architecture

```text
:app composition
  -> Compose UI (:feature:catalog, :feature:reader) -> :core:designsystem
  -> services/contracts (:catalog, :library, :chapters, :reader, :downloads)
  -> Library membership/status (:library) -> :core:common
  -> storage adapters (:storage:room, :storage:files) -> capability ports
  -> :catalog -> plugin facade (:plugins:runtime) -> wire/package contracts (:plugins:api)
```

The accepted Baseline 2 production graph is the historical seven-module boundary:
`:app`, `:core:common`, `:catalog`, `:feature:catalog`, `:storage:room`, `:plugins:api`,
and `:plugins:runtime`. Wave 06 added `:library`, Wave 07 added `:chapters`, Wave 08
added `:reader` plus `:feature:reader`, and Wave 09 added `:downloads` plus
`:storage:files`, producing the thirteen-module capability graph. The approved
between-wave foundation adds `:core:designsystem`, producing the current
fourteen-module graph. Exact current edges are governed by
`config/architecture/module-boundaries.json`; later modules are created only
through their owning wave or a dedicated approved architecture decision.

Room owns private schema, DAOs, transactions, and persistence adapters. Plugin lifecycle,
JavaScript execution, bounded host capabilities, and runtime persistence SPI belong to
`:plugins:runtime`; `:storage:room` may depend only on reviewed capability contracts and
that persistence SPI surface.

## 6. Plugin execution model

A package may expose `CATALOG`, `CONTENT`, or both kinds, but contracts remain independent.
The current package format contains `manifest.json`, `main.js`, and optional bounded
assets. Plugin operations exchange validated protocol JSON; there is no declarative
selector runtime or compatibility path.

### JavaScript plugins

JavaScript plugins execute through AndroidX JavaScriptEngine isolation and a small
validated host capability bridge. JavaScript does not receive Android classes,
reflection, process, arbitrary files, databases, or unrestricted networking.

### Network/security

All plugin networking goes through host-owned allowlisting, redirect checks and budgets.
Output URL validation must use the same validation policy **without performing a fetch**.
Diagnostics must never expose credentials, cookies, raw HTML/chapter text, raw private
URLs or raw cursor values.

## 7. Current execution position

**Waves 06-09, the Design System Foundation, Product UI checkpoint, and Discover semantic-feed redesign are complete. The Canonical Catalog Reconciliation & Fusion Engine is the active pre-Wave-10 workstream; Phases 0–6 / Tasks 1–38 and Phase 7 Tasks 39–41 are verified/closed on Room schema 9, and Phase 7 Task 42 is active as the final governance/certification gate.**

Architecture Baseline 2 is accepted after local, API 26/API 37, launcher, plugin runtime,
Room, Compose, and final ownership verification. Waves 06-09 are verified and complete;
Wave 09 established the cache/download/offline boundary and Room schema 6. The between-wave
Design System Foundation was accepted on 2026-08-12, and the Product UI checkpoint was
accepted on 2026-08-14 while preserving the 14-module production graph.

The 2026-08-19 Discover semantic-feed redesign is now implemented on top of that UI
baseline. It adds explicit `CatalogFeedKind` semantics, publication/latest-update metadata,
Room migration 6 -> 7, source-agnostic `DiscoverUiState`, the `Manga | Light Novel` media
selector, a manual Popular hero pager (max 5), Latest Updates 3-column grid (max 9), and
Top Rated list (max 5). Discover projection deduplicates by canonical `StoryId`, reads only
cached Home emissions, and runs on the shared Default-dispatcher projection boundary.
Light Novel remains visible but disabled in the current delivery.

Targeted unit/instrumentation suites, app navigation/smoke tests, Roborazzi baselines, and
the `discoverScroll` Macrobenchmark were rerun after the redesign. The 2026-08-20 catalog
metadata-lifecycle unification then advanced Room from schema 7 to schema 8 without changing
the module graph. `CatalogMetadataCoordinator` now owns only `Summary` / `Full` lifecycle
requirements; Full uses 24-hour freshness, plugin-version invalidation, stale-while-revalidate,
retry suppression, and process-wide single-flight. Home/Search listing payload quality remains a
plugin-operation responsibility: missing optional artwork or other presentation metadata stays
degraded and never triggers host-side Details enrichment. `CatalogDetailsLoader` is the sole
production `CatalogSource.details(...)` call site, while persisted source identity is stable across
metadata refreshes. Room schema 8 introduced Summary/Full provenance; the canonical-engine Phase-1 migration subsequently advanced the current repository to schema 9.

The Canonical Catalog Reconciliation & Fusion Engine owns the active pre-Wave-10 boundary.
Phases 0–6 / Tasks 1–38 are verified and closed: the accepted system includes the schema-9 canonical
foundation, host-owned evidence/reconciliation, deterministic Fusion with materialized generations,
canonical Story/Search/Discover/Library presentation, guarded atomic Story graph merge, durable review,
shared evidence-change orchestration, operation-level Story Full fallback, and post-merge correction
review. Phase 7 Tasks 39–41 are also verified/closed, adding durable background safety, controlled
fail-closed reversal for provably safe historical merges, and bounded fail-open decision/invariant
diagnostics while keeping policy engines pure and Room authoritative. Task 42 is active only to certify
the complete migration/unit/device/app/profile/performance matrix and synchronize final governance; it
does not introduce new feature behavior. Wave 10 notification persistence remains rebased to `9 -> 10`;
Wave 11 enters on schema 10 unless another reviewed migration intervenes.

## 8. Roadmap

| Wave | Outcome |
|---|---|
| 01 | reproducible build, architecture guardrails, navigation shell, common primitives |
| 02 | canonical domain + durable local Room state |
| 03 | historical plugin contracts and package/repository validation, superseded by Baseline 2 |
| 04 | secure plugin execution, update/rollback, diagnostics and host facade |
| 05 | catalog Home/search/story services and the historical source-preserving discovery baseline |
| 06 | immediate local Library + explainable content-source matching |
| 07 | multi-source chapter synchronization and canonical release grouping |
| 08 | text reader, release selection/switching and exact progress |
| 09 | cache/download namespaces, quotas, integrity and offline reading |
| UI | accepted design system + Product UI + semantic Discover presentation |
| CCE | provider-agnostic canonical Story reconciliation/fusion engine; Tasks 1–41 verified/closed on schema 9; Task 42 active final certification/governance gate |
| 10 | local scheduling, guarded source login and deduplicated notifications; planned after CCE reaches a compatible schema boundary |
| 11 | security/performance/accessibility/docs/reproducible APK hardening |

Detailed lifecycle/status: `implementation/current-roadmap.md`.

Wave 06-11 module ownership and dependency evolution is fixed by
`superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`. Later
plans must not recreate removed shared-domain, shared-database, plugin-host, or generic
synchronization layers.

## 9. Verification model

The repository separates development feedback, full host verification, Android
instrumentation, and acceptance checkpoints. `scripts/verify-fast.sh` is the local
development loop: repository/static gates (including `verify-ui-tokens.sh`), architecture
verification, local tests, Detekt, and Room schema stability. `scripts/verify.sh` remains the canonical full host
gate and additionally runs Android lint plus app debug assembly. Both paths keep strict
dependency verification; full verification owns `verifyArchitecture` in the same Gradle
invocation to avoid a redundant Gradle startup. Local Gradle build caching and daemon
reuse are enabled for repeated runs. `scripts/verify-architecture-baseline-2.sh` asserts
the exact retained architecture. Reusable device runners live in
`scripts/instrumentation/`.

A requirement is not considered checkpoint-proven solely because implementation exists.
Evidence files under `internal/checkpoints/` retain `PASS`, `FAIL`, `NOT RUN`, or
`NOT APPLICABLE` states.

## 10. Documentation map

Read in this order:

1. `project/current-state.md` — what exists now and what remains.
2. `implementation/current-roadmap.md` — where to continue and wave sequencing.
3. `superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md` — current canonical catalog identity/fusion architecture.
4. `superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md` — active task-by-task execution record.
5. `internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-2.md` — verified Phase-2 Tasks 12–21 evidence.
6. `internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-1.md` — verified Phase-1 Tasks 5–11 evidence.
6. `internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-0.md` — verified Phase-0 Tasks 1–4 evidence.
7. `project/approved-product-design.md` — product/domain baseline plus accepted current amendments.
8. `superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md` — current Discover behavior and semantic-feed contract until canonical read-path cutover.
9. `internal/checkpoints/discover-semantic-feed-redesign.md` — Discover acceptance evidence and benchmark snapshot.
10. `superpowers/specs/2026-08-12-redantotsu-inspired-product-ui-design.md` — accepted broader Product UI baseline; its Discover-specific composition is superseded by the 2026-08-19 spec.
11. `superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md` when changing post-baseline module ownership.
12. `plugin-sdk/` when changing public plugin contracts/packages.
13. `internal/checkpoints/` when deciding whether a gate is proven.
14. `internal/archive/` only for historical provenance.

`project/document-governance.md` defines precedence when documents disagree.

Reusable public contract fixtures belong to `:plugins:api` test resources or owning-module
test builders. There is no cross-feature fixture module, and routine tests do not call live
websites.

## 11. Contributor execution rules

- One independently reviewable behavior/task at a time.
- TDD: focused RED, minimal GREEN, focused suite, affected module suite.
- Deterministic fixtures; routine tests never call live third-party websites.
- Persistence changes require migration tests and committed schema JSON.
- Public plugin contract changes require deterministic contract fixtures and versioning.
- No later wave may bypass an earlier domain/repository/host boundary because its UI is easier to implement directly.
- Commit/checkpoint evidence stays auditable; do not rewrite historical results.

## 12. Next action

Continue Phase 7 Task 42 from
`superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`.
Tasks 1–41 are verified and accepted on Room schema 9. Task 42 owns only final governance plus the
complete schema/unit/device/app/Baseline-Startup-Profile/performance certification matrix; semantic
failures return to the owning earlier task rather than being patched into Task 42. Wave 10 notification
persistence is rebased to `MIGRATION_9_10`; never reintroduce a second meaning for `MIGRATION_8_9`.
Use `./scripts/verify.sh` as the final host gate before the Phase-7/CCE closeout checkpoint is promoted
to verified/accepted.
