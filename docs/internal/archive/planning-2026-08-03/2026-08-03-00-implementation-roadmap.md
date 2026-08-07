# Android Unified Novel Library Implementation Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Execute one wave at a time with `superpowers:subagent-driven-development` or `superpowers:executing-plans`. A wave is not complete until its checkpoint evidence is reviewed.

**Goal:** Deliver the approved Android unified novel library MVP through small, independently testable increments while preserving the catalog/content separation and MangaDex-style chapter aggregation model.

**Architecture:** A modular Kotlin/Compose application uses domain-first interfaces, Room persistence, isolated plugin hosts, deterministic story/chapter matching, a local reader/cache, and WorkManager-based update checks. Each wave establishes contracts consumed by later waves; no later UI may bypass those contracts.

**Tech Stack:** Kotlin 2.4.10, JDK 17, Gradle 9.5, AGP 9.3.0, Compose BOM 2026.06.00, Navigation 3 1.1.4, Room 2.8.4, WorkManager 2.11.2, AndroidX JavaScriptEngine 1.1.0, Hilt, coroutines, kotlinx.serialization.

## Planning Assumptions

- This is a greenfield Android repository because the source tree was not available while planning.
- Namespace and application ID are `app.openstory` unless the repository already reserves a different ID before Wave 01 starts.
- Min SDK 26 keeps WebView, encrypted local storage, and background execution behavior manageable without legacy compatibility branches.
- Compile/target SDK 37 are used so the first release is built against the current Android platform baseline.
- Exact third-party versions are centralized in `gradle/libs.versions.toml`; no feature module writes literal versions.
- All public plugin/package formats are versioned from day one even while the app remains pre-1.0.

## Module Map

```text
:app                         Composition root, navigation, app process
:core:common                 Result/error/time/hash primitives
:core:model                  Pure domain models and IDs
:core:database               Room schema, DAOs, migrations, repositories
:core:network                Allowlisted HTTP client and response limits
:core:plugin-api             Stable plugin contracts and package schemas
:core:plugin-host            Installer, registry, selector engine, JS sandbox
:core:matching               Catalog-to-content story matching
:core:aggregation            Chapter normalization and release grouping
:core:reader                 Reader document model and sanitization
:core:files                  Cache/download storage and quotas
:sync                        Foreground and WorkManager synchronization orchestration
:feature:home                Combined and per-catalog discovery
:feature:library             Local library and statuses
:feature:story               Story detail, mappings, chapter/release list
:feature:reader              Compose text reader and progress controls
:feature:plugins             Install/update/rollback/diagnostics UI
:feature:settings            Language, scheduling, storage, notification settings
:test:fixtures               Shared deterministic plugin/catalog/chapter fixtures
:benchmark                   Startup, large-library, and reader benchmarks
```

## Wave Sequence

| Wave | Plan | Role | Starts only after | Exit proof |
|---|---|---|---|---|
| 01 | `2026-08-03-01-foundation-and-architecture.md` | Establish reproducible build, module boundaries, navigation shell, error/result primitives, and CI | Approved design | Clean build, tests/lint in CI, app launches to shell |
| 02 | `2026-08-03-02-domain-and-local-storage.md` | Define canonical domain and durable Room schema/repositories | Wave 01 | Migration-tested database and local library round trip |
| 03 | `2026-08-03-03-plugin-contracts-and-packages.md` | Freeze catalog/content contracts and portable plugin package/repository formats | Wave 02 | Contract fixtures validate and malformed packages are rejected |
| 04 | `2026-08-03-04-plugin-host-and-security.md` | Install, update, rollback, execute selector/JS plugins under strict capabilities | Wave 03 | Sandboxed fixture plugin works; denied domain/filesystem tests pass |
| 05 | `2026-08-03-05-catalog-home-and-discovery.md` | Produce usable Home, search, filters, rankings, and story metadata from catalog plugins | Wave 04 | Bundled catalog populates combined/per-source Home |
| 06 | `2026-08-03-06-library-and-story-matching.md` | Add metadata-only stories immediately and map them to content plugins deterministically | Wave 05 | Add-to-library completes without waiting; mappings are reviewable |
| 07 | `2026-08-03-07-chapter-sync-and-aggregation.md` | Build recent/full/incremental sync and MangaDex-like canonical chapter release groups | Wave 06 | Multiple sources collapse into correct chapter groups without false merges |
| 08 | `2026-08-03-08-reader-and-reading-progress.md` | Read, switch releases, navigate, and restore exact canonical/release progress | Wave 07 | Reader journey survives process death and source switching |
| 09 | `2026-08-03-09-cache-downloads-and-storage.md` | Separate disposable cache from explicit offline downloads with integrity and quotas | Wave 08 | Offline reading works; cache cleanup never removes downloads |
| 10 | `2026-08-03-10-background-sync-auth-and-notifications.md` | Schedule local checks, support WebView login sessions, and notify only meaningful changes | Wave 09 | Periodic sync is idempotent and notifications are deduplicated |
| 11 | `2026-08-03-11-hardening-open-source-release.md` | Security, scale, accessibility, diagnostics, docs, signing, reproducible APK release | Wave 10 | Release candidate passes acceptance journey and audit checklist |

## Critical Dependency Chain

```text
Build/module boundaries
  -> Domain IDs and Room schema
    -> Plugin contracts/package schema
      -> Secure plugin execution
        -> Catalog discovery
          -> Story matching
            -> Chapter aggregation
              -> Reader progress
                -> Offline storage
                  -> Background/auth/notifications
                    -> Release hardening
```

Parallel work is allowed only inside a wave when tasks do not share contracts. Do not start a later wave because its UI appears independent; later waves depend on persistence and error semantics established earlier.

## Product-Level Invariants

1. `CanonicalStory` is independent of any single catalog or content source.
2. `CanonicalChapter` is the progress unit; `ChapterRelease` is a selectable source/language/group publication.
3. A source disappearing never deletes user progress, explicit downloads, or the canonical chapter immediately.
4. New release does not automatically mean new canonical chapter.
5. Plugin code cannot directly access Room, arbitrary files, Android services, or undeclared network domains.
6. User corrections outrank automated matching and survive resynchronization.
7. Library add is immediate and supports metadata-only state.
8. All background behavior is local, observable, retry-safe, and manually reproducible.
9. Explicit downloads and automatic cache have different retention policies.
10. The app never attempts to bypass paywalls, DRM, CAPTCHAs, or access controls.

## Cross-Wave Quality Gates

Every wave must provide:

- Focused unit tests for each business rule.
- Integration tests at every adapter boundary introduced by the wave.
- Deterministic fixtures; no routine test depends on live third-party websites.
- Room schema JSON committed for every database version.
- Structured errors that reach diagnostics without leaking cookies or chapter content.
- One independently reviewable commit per task.
- A clean `./gradlew clean testDebugUnitTest lintDebug --stacktrace` run.

## Final MVP Acceptance Journey

```text
Launch clean install
→ Home loads from bundled catalog plugin
→ Search/open a light novel or web novel
→ Add it to Library immediately
→ Content plugins find one or more candidate mappings
→ Latest releases appear, then full history fills in
→ Equivalent releases group under canonical chapters
→ User opens a preferred-language release
→ Reader saves canonical chapter, exact release, and position
→ User downloads chapters and reads with networking disabled
→ Local scheduled refresh finds a new canonical chapter or preferred-language release
→ Exactly one meaningful local notification appears
→ Plugin failure leaves existing metadata, progress, and downloads usable
```

## Execution Handoff

Start with `2026-08-03-01-foundation-and-architecture.md`. At every wave checkpoint, review the commit range and test evidence before starting the next plan. The recommended execution mode is a fresh implementation agent per task with a reviewer gate between tasks.
