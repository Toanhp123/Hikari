# OpenStory / Hikari Project Handbook

Date: 2026-08-07  
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
```

Where an approved product document uses broader component terminology, these pins describe
the repository implementation baseline, not a product-scope rewrite.

## 5. Architecture

```text
Compose UI
  -> presentation/navigation
    -> application use cases
      -> pure canonical domain / matching / aggregation
        -> repositories and secure plugin host
          -> Room / app-private files / WorkManager / bounded network / sandbox
```

Current repository modules are documented in `project/current-state.md`. Future modules
are created only when their wave starts; the full target graph is in
`implementation/current-roadmap.md`.

## 6. Plugin execution model

A package may expose `CATALOG`, `CONTENT`, or both kinds, but contracts remain independent.

### Declarative plugins

- Schema V1 is a bounded linear request/DOM/text pipeline and remains backward compatible.
- Schema V2 is endpoint-oriented with typed, closed output bindings capable of describing
  every current Catalog and Content wire DTO.
- V2 contracts and install-time validation are Wave 03-owned and already present.
- V2 runtime evaluation/mapping/final validation are Wave 04-owned and are the active work.

### JavaScript plugins

The approved Wave 04 architecture requires AndroidX JavaScriptEngine isolation and a small
host capability bridge. JavaScript must not obtain Android classes, reflection, process,
arbitrary files, databases or unrestricted networking. Full JS sandbox implementation
comes after the current Selector V2 runtime boundary.

### Network/security

All plugin networking goes through host-owned allowlisting, redirect checks and budgets.
Output URL validation must use the same validation policy **without performing a fetch**.
Diagnostics must never expose credentials, cookies, raw HTML/chapter text, raw private
URLs or raw cursor values.

## 7. Current execution position

**Wave 04 / Task 03 — Selector V2 runtime execution.**

Do not restart Wave 01–03 from the old planning package. Do not recreate Selector V2
contracts. Continue from `implementation/wave-04-selector-v2-runtime.md`.

After Task 03, finish Wave 04 JavaScript isolation, update lifecycle and unified
host/diagnostics, then pass the Wave 04 checkpoint before starting Wave 05.

## 8. Roadmap

| Wave | Outcome |
|---|---|
| 01 | reproducible build, architecture guardrails, navigation shell, common primitives |
| 02 | canonical domain + durable local Room state |
| 03 | stable plugin contracts, Selector V2 public schema, package/repository validation |
| 04 | secure plugin execution, update/rollback, diagnostics and host facade |
| 05 | combined/per-catalog Home, search, filters, rankings, story metadata |
| 06 | immediate local Library + explainable content-source matching |
| 07 | multi-source chapter synchronization and canonical release grouping |
| 08 | text reader, release selection/switching and exact progress |
| 09 | cache/download namespaces, quotas, integrity and offline reading |
| 10 | local scheduling, guarded source login and deduplicated notifications |
| 11 | security/performance/accessibility/docs/reproducible APK hardening |

Detailed lifecycle/status: `implementation/current-roadmap.md`.

## 9. Verification model

The repository separates fast verification, Android instrumentation and wave-specific
checkpoint gates. `scripts/verify.sh` is the common fast gate. API 26/API 37 launcher and
wave-specific database/plugin contract gates are exposed through `scripts/verify-*.sh`.

A requirement is not considered checkpoint-proven solely because implementation exists.
Evidence files under `internal/checkpoints/` retain `PASS`, `FAIL`, `NOT RUN`, or
`NOT APPLICABLE` states.

## 10. Documentation map

Read in this order:

1. `project/current-state.md` — what exists now and what remains.
2. `implementation/current-roadmap.md` — where to continue and wave sequencing.
3. `project/approved-product-design.md` — complete product/domain baseline.
4. Active implementation plan (`implementation/wave-04-selector-v2-runtime.md` now).
5. `plugin-sdk/` when changing public plugin contracts/packages.
6. `internal/checkpoints/` when deciding whether a gate is proven.
7. `internal/archive/` only for historical provenance.

`project/document-governance.md` defines precedence when documents disagree.

## 11. Contributor execution rules

- One independently reviewable behavior/task at a time.
- TDD: focused RED, minimal GREEN, focused suite, affected module suite.
- Deterministic fixtures; routine tests never call live third-party websites.
- Persistence changes require migration tests and committed schema JSON.
- Public plugin contract changes require deterministic contract fixtures and versioning.
- No later wave may bypass an earlier domain/repository/host boundary because its UI is easier to implement directly.
- Commit/checkpoint evidence stays auditable; do not rewrite historical results.

## 12. Next action

Open `implementation/wave-04-selector-v2-runtime.md` and start with its first remaining
production task: shared URL policy and shared wire DTO validation.
