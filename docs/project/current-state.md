# Repository Current State

Date: 2026-09-13
Purpose: single source of truth for the implemented repository boundary.

## Executive State

- Hikari V2 Step 2 - Discover + Story Detail Foundation is implemented and accepted on branch
  `v2/discover-story-foundation`.
- Final runtime/source SHA: `13af96625a93b3e45f7d7db18e539286ce075c79`.
- Acceptance evidence:
  `../internal/checkpoints/hikari-v2-step-2-discover-story-foundation.md`.
- Step 2 performance/profile evidence:
  `../internal/v2/catalog-step2-performance-baseline-2026-09-08.md`.
- The active graph is eleven production modules plus one Android test/performance module.
- The app-reachable capability is a production-shaped Discover + metadata-only Story Detail
  vertical slice for Manga and Light Novel.
- Step 2 is not a ship-ready production remote-catalog release. Release owns no production remote
  acquisition source, plugin runtime, concrete network client, or `INTERNET` permission.

## Active Graph

| Module | Current responsibility |
| --- | --- |
| `:app` | Minimal V2 shell, launch-state handoff, root theme, and Catalog entry |
| `:core:common` | Narrow shared primitives used by admitted and retained contracts |
| `:core:designsystem` | Work-free shared theme, tokens, state, skeleton, and pull-refresh primitives |
| `:catalog:domain` | Pure identity, provenance, bounds, semantic sections, and Catalog ports |
| `:catalog:storage` | Room schema, atomic bounded Discover/Story persistence, retention, and keyed reads |
| `:catalog:runtime` | Demand activation, acquisition/import orchestration, mutation/pin ownership, and fixtures |
| `:feature:catalog` | Discover, Manga/Light Novel navigation, metadata-only Story Detail, and bounded cover UI |
| `:catalog:model` | Quarantined pure-JVM reference models, unreachable from the app production graph |
| `:catalog:engine` | Quarantined pure-JVM reference algorithms, unreachable from the app production graph |
| `:reader:engine` | Retained HES-v1 candidate, unreachable from the app production graph |
| `:plugins:api` | Retained plugin protocol; used by the Catalog integration-test edge only |
| `:benchmark` | Android test/performance module for profiles, startup, Catalog journeys, and aged-state checks |

The exact module and dependency policy is canonical in
`../../config/architecture/module-boundaries.json` and `../../settings.gradle.kts`. The app has one
product edge to `:feature:catalog` and one presentation-infrastructure edge to
`:core:designsystem`. The admitted Step 2 capability graph flows through domain/runtime/storage;
quarantined and retained candidates remain unreachable from release execution.

## Runtime Boundary

- Release identity remains `app.openstory`; debug uses `app.openstory.v2dev`; benchmark targets use
  `app.openstory.v2benchmark`.
- Step 1 launch-state behavior remains intact: the first application-owned frame does not wait for
  persistence, and Catalog activates only after the existing Ready/first-frame handoff.
- Discover reads one durable bounded semantic snapshot for the selected media type. Manga and Light
  Novel are both enabled top-level destinations with Popular, Latest Updates, and Top Rated sections.
- `Absent` and `Published(empty)` are distinct durable states. Only `Absent` may bootstrap, and an
  unavailable release acquisition binding returns typed `SourceUnavailable` without hidden work.
- Story Detail is metadata-only, keyed by explicit source identity, and reads bounded persisted
  detail/child collections. Chapters, Reader, progress, Library, and Downloads are outside Step 2.
- Discover publication and Story Detail persistence are atomic and bounded. Acquisition validation,
  mutation ownership, active pins, retention, image decode/cache work, and remote-cover policy have
  explicit ceilings and cancellation/failure semantics.
- Discover and Story UI consume real Room-backed state. Debug/benchmark seed data enters through the
  production-shaped source -> executor -> importer -> Room boundary; release contains no seed.
- The reviewed MangaUpdates JavaScript integration is deterministic `androidTest`-only proof using
  controlled transport. It does not admit a production plugin executor or live network dependency.

## Presentation And Performance

- `HikariTheme` is installed once at the app root. Discover and Story consume the admitted shared
  Design System while feature-specific media navigation and geometry remain in `:feature:catalog`.
- The accepted visual surface supports compact and wide layouts, stable skeleton geometry, bounded
  cover loading, pull refresh, retained failure content, recreation, and Discover -> Story -> Back
  continuity.
- Generated Baseline and Startup Profiles are byte-identical at `20,874` rules each, SHA-256
  `797b58730c732777698f3aba24dc6ea11302cd8bfa4b17e75c351568f4ac54eb`.
- Hard correctness, query/work, transport/decode, cache/resource, terminal ownership, navigation,
  profile-generation, and structural gates pass.
- Accepted deferred performance debt remains explicit: Story Detail CPU/overrun P95 is
  `47.175 / 40.432 ms` for memory-hit, `41.906 / 41.678 ms` for disk-hit, and
  `66.212 / 59.902 ms` for Story-back; startup TTID is `507.138 ms` fresh and `467.346 ms`
  returning. These values are not relabeled as PASS or hidden by relaxed thresholds.

## Verification State

The accepted checkpoint records PASS evidence for:

- the complete 68-row R2.8 acceptance matrix;
- focused domain/runtime/feature/build-logic tests and Android-test compilation;
- architecture, foundation, module graph, package-SCC, source-layout, Detekt, and release-cleanliness gates;
- full fast and full repository shell verification;
- connected Room/feature/app correctness on API 26 and API 37;
- visual acceptance and screenshot matrices for the final Task 14 surface;
- deterministic MangaUpdates real-JavaScript integration on Redmi Note 9S / API 35;
- profile generation, startup traces, nine Catalog benchmark journeys, aged-state bounds, and
  repeated-navigation terminal ownership.

The immutable Step 1 startup baseline remains at
`../internal/v2/startup-baseline-2026-09-07.md`. Step 2 evidence does not rewrite its accepted
measurements or boundary.

## Deferred Product Scope

Production remote acquisition/plugin execution, live network provisioning, Search, Chapters,
Reader UI/content, Library, Downloads, background scheduling, notifications, deep links, final
onboarding, and production upgrade/migration remain outside the accepted Step 2 boundary.

Any next capability requires its own approved design and admission. The accepted Step 2 checkpoint
and roadmap pointer are evidence/routing records, not authorization to begin that work.

## Source-of-truth Rule

When documents disagree:

1. Approved product design owns product scope and domain invariants.
2. This file, then repository code/tests, owns what is implemented now.
3. Accepted checkpoint evidence owns whether a gate passed.
4. `../implementation/current-roadmap.md` owns the next execution boundary.
5. Archived documents are historical context, not execution entry points.

See `document-governance.md` for the complete precedence policy.
