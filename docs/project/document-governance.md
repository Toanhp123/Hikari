# Documentation Governance and Precedence

Date: 2026-08-24
Status: **CANONICAL documentation policy**

## Purpose

The repository retains approved plans, remediation records, checkpoint evidence, and raw review
packages. They remain useful for audit, but they are not equal execution instructions.

## Precedence by question

### What product are we building?

Use `approved-product-design.md`. Later implementation documents may clarify mechanics but may not
silently change product scope or domain invariants.

### What is implemented now?

Use `current-state.md`, then inspect code and tests. A checked plan item is not proof that code
exists, and code existence is not proof that a checkpoint passed.

### What is implemented next?

Use `../implementation/current-roadmap.md`, then the active owning plan. Waves 06-09, the Design
System Foundation, Product UI, Discover semantic-feed redesign, and CCE Phases 0-7 / Tasks 1-42 are
complete. The accepted CCE checkpoint closes on Room schema 9.

The separately reviewed Canonical Engine Performance and Durability work advances current source to
schema 10 through `MIGRATION_9_10` for queue leases and the transactional catalog-change outbox.
Its entry verification is accepted. Wave 10 is ready to start under
`../implementation/waves/wave-10-background-sync-auth-and-notifications.md`. Wave 10
enters on schema 10 and notification persistence owns `MIGRATION_10_11`.

Architecture Baseline 2 remains accepted. The broader Wave 06-11 path is
`../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`. The 2026-08-19
Discover spec owns Discover semantic composition. The 2026-08-20 CCE spec owns canonical catalog
identity, reconciliation, fusion, and canonical presentation. The 2026-08-24 durability spec owns
the schema-10 performance/durable-work extension.

The Baseline 1 decision in `pre-mvp-baseline-1.md` overrides pre-baseline instructions that retain
development-only database migrations or selector generations.

### Has a gate passed?

Use `../internal/checkpoints/`. A required command remains `NOT RUN` until its actual output is
reviewed and recorded. Implementation presence does not imply acceptance.

### How does a public plugin contract work?

Use `../plugin-sdk/`. SDK documents must match `:plugins:api`, the JavaScript runtime protocol,
and tested fixtures.

## Supersession map

| Historical source | Current interpretation |
|---|---|
| 2026-08-03 Waves 01-03 plans | Intent and coverage history; implementation is already present |
| 2026-08-03 Wave 04 selector sketch | Historical; superseded by the Baseline 2 JavaScript protocol/runtime |
| 2026-08-06 selector-v2 review package | Historical design provenance under `../internal/archive/`; no active selector contract |
| Pre-baseline remediation plans/checkpoints | Archived; do not execute again |
| Pre-baseline Room migration instructions | Historical only; current schemas evolve contiguously from frozen schema 1 |
| Historical checkpoint `NOT RUN` entries | Evidence history; never infer `PASS` from later source changes |
| Architecture Baseline 2 R0-R6 plans | Accepted one-time reset record; not the active feature plan |
| 2026-08-12 Product UI design/plan | Accepted broader presentation baseline |
| 2026-08-19 Discover design/plan | Accepted Discover semantic-feed contract and execution record |
| 2026-08-20 CCE design + 2026-08-21 plan | Accepted canonical identity/fusion authority; Phases 0-7 / Tasks 1-42 close on schema 9 |
| 2026-08-24 canonical performance/durability design + plan | Current schema-10 queue lease/outbox authority; owns `MIGRATION_9_10`; entry baseline accepted |
| Wave 10 schema `6 -> 7`, `7 -> 8`, `8 -> 9`, or `9 -> 10` text | Superseded. Wave 10 enters schema 10, notification persistence owns `10 -> 11`, and Wave 11 enters schema 11 |

## Lifecycle labels

- **CANONICAL**: current entry point or normative baseline.
- **ACTIVE**: current implementation work.
- **PLANNED**: approved future work.
- **HISTORICAL**: completed or superseded instructions retained for audit.
- **EVIDENCE**: command/result checkpoint record.
- **ARCHIVE**: raw provenance retained unchanged.

## Change rules

- Do not edit historical results to make the timeline cleaner.
- Do not duplicate project status; `current-state.md` owns it.
- Root and docs READMEs link to canonical files instead of duplicating the roadmap.
- Public SDK examples point to tested fixtures where possible.
- When a design correction changes ownership, update this supersession map.
- When a scoped design supersedes one surface, add an explicit notice to the older design.
- When a checkpoint is accepted, update its evidence record and `current-state.md` together.
- When a module is added, update settings, architecture policy, module documentation, tests, and current state in one reviewed change.
