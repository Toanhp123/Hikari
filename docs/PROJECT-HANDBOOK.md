# OpenStory / Hikari Project Handbook

Date: 2026-09-15
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

Implementation note: this section describes approved product scope, not implemented-now state.
`project/current-state.md` is authoritative for which capabilities are currently present; future
Reader/content/plugin scope here does not make those runtimes active in the current Step 3 graph.

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
Coroutines              1.11.0
kotlinx.serialization   1.11.0
Artwork loading         Coil 3.5.0
DataStore               1.1.3
```

`gradle/libs.versions.toml` is authoritative for dependency pins. Historical/future architecture
may mention WorkManager, Hilt, Backdrop, Roborazzi, production JavaScript plugin execution or other
libraries that are not part of the current production graph; do not infer current ownership from
those older descriptions.

## 5. Architecture

The accepted current repository is a modular monolith. App-owned composition/navigation sits above
feature presentation; features depend on capability domain/runtime contracts; persistence remains in
capability-owned storage modules; small cross-capability mechanics live in reviewed `core` modules.

The exact current graph is intentionally not copied into this handbook:

- included modules: `../settings.gradle.kts`;
- direct dependency/forbidden-import policy: `../config/architecture/module-boundaries.json`;
- implemented responsibilities and live/retained/quarantined status: `project/current-state.md`.

Current Step 3 ownership includes separate Catalog and Library domain/storage/runtime stacks,
feature-owned Discover/Home/Story presentation, app-owned route/composition mechanics,
`:core:artwork` for process artwork work, and `:core:designsystem` for domain-neutral shared visual
policy. Retained/quarantined `:catalog:model`, `:catalog:engine`, `:reader:engine`, and `:plugins:api`
are not proof of release runtime reachability.

Do not recreate a shared cross-domain database, generic service locator, feature-to-feature dependency,
or plugin-host runtime merely because broader product architecture describes later capabilities.

### Source layout and module-local cleanup

Source moves, renames, package reorganization, and responsibility splits are governed by
`project/file-package-ownership-policy.md`. Decide module ownership before package shape, avoid generic
helper/common buckets, keep test-only support in test source sets, and do not extract a shared/core
abstraction without real neutral production consumers. Objective rules belong in executable gates;
responsibility judgments remain review decisions.

## 6. Plugin execution model

The approved product architecture keeps Catalog and readable-content plugin contracts independent,
even when one future package can expose both kinds. `:plugins:api` is currently retained as a
pure-JVM protocol boundary and is used by the deterministic Catalog integration-test edge; a
production plugin runtime is **not** admitted through Step 3 Task 10.

Historical/broader designs specify JavaScriptEngine isolation, host-owned allowlisted networking,
redirect/budget checks, bounded package activation and secret-safe diagnostics. Those rules remain
future capability constraints, not evidence that `:plugins:runtime`, unrestricted networking, or
production JavaScript execution exists in the live graph today.

## 7. Current execution position

Current execution changes too quickly to duplicate safely in this handbook. Use
`implementation/current-roadmap.md` as the canonical next-work authority and read only its `Current
position` range first. Follow the checkpoint/owning plan named there for the exact resume boundary.
Use `project/current-state.md` for implemented repository state.

For agentic work, root `../AGENTS.md` defines the narrow-first discovery and self-review contract.
Historical capability descriptions below remain orientation only and must not override the canonical
roadmap/checkpoint.

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
| CCE | provider-agnostic canonical Story reconciliation/fusion engine; Phases 0-7 / Tasks 1-42 verified/closed on schema 9 |
| CED | canonical engine leases, outbox, and bounded foreground convergence on schema 10 |
| 10 | local scheduling, guarded source login and deduplicated notifications |
| 11 | security/performance/accessibility/docs/reproducible APK hardening |

This table is capability/history orientation only. Detailed current lifecycle/status: `implementation/current-roadmap.md`.

Wave 06-11 module ownership and dependency evolution is fixed by
`superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`. Later
plans must not recreate removed shared-domain, shared-database, plugin-host, or generic
synchronization layers.

## 9. Verification model

The repository separates focused agent-owned checks, current host verification, connected/device
acceptance, and checkpoint evidence. `scripts/verify-fast.sh` is the normal host loop and
`scripts/verify.sh` is the canonical full host entrypoint. Both run an explicit current static
contract set and then one top-level Gradle invocation; historical Step 2 freeze scripts are not
wildcard-discovered as current law.

Gradle owns the live architecture/build-surface checks (`verifyArchitecture`, module boundaries,
production package structure, Step 3 build surface) and the Step 3 fast/full module aggregates.
Connected tests, screenshots, migrations, benchmarks and profiles remain task/checkpoint-owned and
must not be inferred from host implementation presence.

A requirement is not considered checkpoint-proven solely because implementation exists. Evidence
files under `internal/checkpoints/` retain `PASS`, `FAIL`, `NOT RUN`, or `NOT APPLICABLE` states.

## 10. Documentation map

Read narrowly in this order:

1. `project/current-state.md` — what is implemented now.
2. `implementation/current-roadmap.md` — read `Current position` first for current work and resume routing.
3. The checkpoint and owning plan named by `Current position` — exact execution/evidence boundary.
4. `project/document-governance.md` — precedence when documents disagree.
5. `project/file-package-ownership-policy.md` — only when source placement/package cleanup is in scope.
6. `project/approved-product-design.md` — product/domain baseline and accepted amendments.
7. The specific architecture/design section required by the active task — do not load all specs by default.
8. `plugin-sdk/` only when changing public plugin contracts/packages.
9. `internal/checkpoints/` when deciding whether a gate is proven.
10. `internal/archive/` only for historical provenance or a concrete contradiction/root-cause trail.

For agentic work, root `../AGENTS.md` owns the context-budget and evidence-expansion rules.

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

Do not encode a task/wave-specific next action in this handbook. Read `implementation/current-roadmap.md`
`Current position`, then follow its named checkpoint and owning plan. Historical roadmap/capability
sections in this handbook are orientation only and never override that route.
