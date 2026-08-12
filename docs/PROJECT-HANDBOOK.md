# OpenStory / Hikari Project Handbook

Date: 2026-08-12
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

Included at completion: combined/per-catalog discovery, local Library, catalog/content
plugin lifecycle, story matching, recent/full/incremental chapter sync, canonical chapter
aggregation, text reader and source switching, exact progress, cache/downloads, local
scheduled updates/notifications, guarded WebView source login, URL import/manual mapping,
and open-source APK distribution.

Excluded from MVP: accounts/cloud sync, centralized plugin moderation, manga image reader,
anime functionality, TTS/audiobook/translation/AI summaries, social features, native-code
plugins, unrestricted JavaScript, automatic access-control bypass and cross-device download
transfer.

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

**Wave 09 and Design System Foundation complete; approved Product UI implementation is active.**

Architecture Baseline 2 is accepted after local, API 26/API 37, launcher, plugin runtime,
Room, Compose, and final ownership verification.

Wave 06 Tasks 01-06 are verified and Wave 06 is complete: metadata-only Library membership,
Library presentation, pure explainable content-story matching, bounded quick/deferred plugin
content search, protected Room-backed mappings/rejections, and mapping review/URL import.
Wave 07 and Wave 08 are verified and complete. Wave 09 completed cache/download
namespaces, quotas, integrity, reconciliation, offline reading, Room schema 6,
and its checkpoint. The between-wave Design System Foundation was accepted on
2026-08-12. The approved ReDantotsu-inspired Product UI redesign is now the active
presentation checkpoint. Product UI Task 1 (rendering/screenshot toolchain) and Task 2
(reproducible target-pack rendering pipeline) are verified. Task 3 (shared artwork state
and stable fallbacks) is next. Wave 10 remains the next capability wave and has not started.

## 8. Roadmap

| Wave | Outcome |
|---|---|
| 01 | reproducible build, architecture guardrails, navigation shell, common primitives |
| 02 | canonical domain + durable local Room state |
| 03 | historical plugin contracts and package/repository validation, superseded by Baseline 2 |
| 04 | secure plugin execution, update/rollback, diagnostics and host facade |
| 05 | combined/per-catalog Home, search, filters, rankings, story metadata |
| 06 | immediate local Library + explainable content-source matching |
| 07 | multi-source chapter synchronization and canonical release grouping |
| 08 | text reader, release selection/switching and exact progress |
| 09 | cache/download namespaces, quotas, integrity and offline reading |
| 10 | local scheduling, guarded source login and deduplicated notifications |
| 11 | security/performance/accessibility/docs/reproducible APK hardening |

Detailed lifecycle/status: `implementation/current-roadmap.md`.

Wave 06-11 module ownership and dependency evolution is fixed by
`superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`. Later
plans must not recreate removed shared-domain, shared-database, plugin-host, or generic
synchronization layers.

## 9. Verification model

The repository separates development feedback, full host verification, Android
instrumentation, and acceptance checkpoints. `scripts/verify-fast.sh` is the local
development loop: repository/static gates, architecture verification, local tests,
Detekt, and Room schema stability. `scripts/verify.sh` remains the canonical full host
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
3. `project/approved-product-design.md` — complete product/domain baseline.
4. `superpowers/plans/2026-08-12-redantotsu-inspired-product-ui-implementation-plan.md` — active Product UI execution plan.
5. `superpowers/specs/2026-08-12-redantotsu-inspired-product-ui-design.md` — approved Product UI behavior and visual scope.
6. `superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md` when changing post-baseline module ownership.
7. `plugin-sdk/` when changing public plugin contracts/packages.
8. `internal/checkpoints/` when deciding whether a gate is proven.
9. `internal/archive/` only for historical provenance.

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

Continue with Product UI Task 3, shared artwork state and stable fallbacks, from
`superpowers/plans/2026-08-12-redantotsu-inspired-product-ui-implementation-plan.md`.
Tasks 1-2 are verified in `internal/checkpoints/product-ui-task-01-toolchain.md` and
`internal/checkpoints/product-ui-task-02-target-pack.md`. During implementation use
`./scripts/verify-fast.sh` for iteration and run `./scripts/verify.sh` once as the full host
gate before closing the next checkpoint boundary.
