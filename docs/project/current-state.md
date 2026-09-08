# Repository Current State

Date: 2026-09-08
Purpose: single source of truth for the implemented repository boundary.

## Executive State

- Hikari V2 Step 1 - Foundation + Clean Boot is implemented and accepted on branch
  `v2/foundation-clean-boot`.
- Final runtime/source SHA: `eb4d3bfd869a5b9df78a5802de31510b3984c3c3`.
- Acceptance evidence:
  `../internal/checkpoints/hikari-v2-step-1-foundation-clean-boot.md`.
- Startup comparison baseline:
  `../internal/v2/startup-baseline-2026-09-07.md`.
- The active graph is six production modules plus one Android test/performance module.
- `:app` is a minimal V2 shell with zero production project dependencies and no real product
  capability.
- Step 2 is not approved or predetermined. Future capability work must begin with an explicit design
  and satisfy `../internal/v2/capability-admission-contract.md`.

## Active Graph

| Module | Current responsibility |
| --- | --- |
| `:app` | Minimal Android shell, launch-state resolution, static FirstRun/Home surfaces, and startup trace markers |
| `:core:common` | Retained narrow common primitives required by candidate contracts |
| `:catalog:model` | Quarantined pure-JVM Catalog contracts/reference models |
| `:catalog:engine` | Quarantined pure-JVM Catalog algorithms pending a future admission gate |
| `:reader:engine` | Retained HES-v1 pure-JVM routing engine candidate, not reachable from `:app` |
| `:plugins:api` | Retained pure plugin protocol/contract surface, not reachable from `:app` |
| `:benchmark` | Android test/performance module for Baseline Profile and two cold-start measurements |

The exact graph and dependency policy are in `../../config/architecture/module-boundaries.json` and
`../../settings.gradle.kts`. Gradle grouping parents such as `:catalog` and `:reader` are not active
capability modules.

## Runtime Boundary

- Release identity remains `app.openstory`; debug uses `app.openstory.v2dev`; benchmark targets use
  `app.openstory.v2benchmark`.
- `HikariApplication` performs only platform startup plus trace instrumentation.
- `MainActivity` performs window/content setup plus trace instrumentation.
- The first application-owned frame renders without waiting for persisted launch-state I/O.
- Launch state is exactly `Unknown`, `FirstRun`, or `Ready`; persistence owns only
  `initial_setup_completed`.
- Missing state and read failure resolve conservatively to FirstRun; cancellation propagates; a
  failed completion write does not transition to Home.
- Backup is disabled. Same-application-ID V1-to-V2 upgrade/migration is outside Step 1.
- ProfileInstaller is the only classified AndroidX Startup initializer. Architecture verification
  rejects other hidden startup surfaces and forbidden permissions.
- Production startup has no benchmark switch, product repository, engine integration, plugin
  runtime, worker, scheduler, network client, Room database, Hilt graph, or Navigation graph.

## Retention Boundary

- `:reader:engine`, `:plugins:api`, and reviewed narrow `:core:common` primitives are retained
  transplant candidates.
- `:catalog:model` and `:catalog:engine` remain quarantine/reference candidates. Retained tests do
  not constitute runtime admission.
- V1 runtime/integration modules and their build surface are absent from the active graph.
- Detailed KEEP/REDESIGN/DROP ownership is canonical in
  `../internal/v2/v1-salvage-ledger.md`.

## Verification State

The accepted checkpoint records PASS evidence for:

- fast and full repository verification;
- eight startup instrumentation tests;
- startup-only Baseline Profile generation;
- five cold fresh-install and five cold returning-launch iterations;
- all six trace milestones in fresh and returning Perfetto traces;
- architecture, source, structure, dependency, and merged-manifest gates;
- exact seven-module inclusion;
- retained/quarantined module tests;
- final spec-to-implementation self-review and post-documentation fast verification.

The accepted Redmi Note 9S / API 35 medians are `394.210469 ms` for fresh install and
`412.547813 ms` for returning launch. These are empirical same-device comparison points, not
absolute performance thresholds.

## Deferred Product Scope

Step 1 deliberately contains no real Home, Discover, Search, Story, Reader, Library, Downloads,
plugin provisioning/runtime, background scheduling, notifications, deep links, final onboarding,
final design system, final dependency-injection framework, or production upgrade path.

Historical V1 capability, architecture, schema, wave, and performance evidence remains available in
`../internal/checkpoints/` and `../internal/archive/`, but it does not describe the active V2 runtime
tree.

## Source-of-truth Rule

When documents disagree:

1. Approved product design owns product scope and domain invariants.
2. This file, then repository code/tests, owns what is implemented now.
3. Accepted checkpoint evidence owns whether a gate passed.
4. `../implementation/current-roadmap.md` owns the next execution boundary.
5. Archived documents are historical context, not execution entry points.

See `document-governance.md` for the complete precedence policy.
