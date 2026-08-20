# Documentation Governance and Precedence

Date: 2026-08-20
Status: **CANONICAL documentation policy**

## Purpose

The repository retains approved greenfield plans, source-local remediation records,
checkpoint evidence, and raw review packages. They remain useful for audit, but they are
not equal execution instructions.

## Precedence by question

### What product are we building?

Use `approved-product-design.md`. Later implementation documents may clarify mechanics
but may not silently change product scope or domain invariants.

### What is implemented now?

Use `current-state.md`, then inspect code and tests. A checked plan item is not proof that
code exists, and code existence is not proof that a checkpoint passed.

### What is implemented next?

Use `../implementation/current-roadmap.md`, then the active owning-wave plan. Waves 06-09,
the Design System Foundation, the Product UI checkpoint, and the Discover semantic-feed redesign
are complete. The next capability boundary is **Wave 10** in
`../implementation/waves/wave-10-background-sync-auth-and-notifications.md`; it has not started.

Architecture Baseline 2 remains accepted. Its design and acceptance checkpoint own the architecture
foundation, while Wave 01-09 and Product UI plans/checkpoints are retained as delivery records. The
post-baseline architecture design owns module evolution and cross-wave continuity for Waves 06-11.
The 2026-08-19 Discover spec owns the current Discover-specific presentation/data contract without
changing capability ownership. Its canonical architecture path remains
`../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`.

The Baseline 1 decision in `pre-mvp-baseline-1.md` overrides pre-baseline instructions
that retain development-only database migrations or selector generations.

### Has a gate passed?

Use `../internal/checkpoints/`. A required command remains `NOT RUN` until its actual
output is reviewed and recorded.

### How does a public plugin contract work?

Use `../plugin-sdk/`. SDK documents must match `:plugins:api`, the JavaScript runtime
protocol, and tested fixtures.

## Supersession map

| Historical source | Current interpretation |
|---|---|
| 2026-08-03 Waves 01–03 plans | Intent and coverage history; implementation is already present |
| 2026-08-03 Wave 04 selector sketch | Historical; superseded by the Baseline 2 JavaScript protocol/runtime |
| 2026-08-06 generation-based selector review package | Historical design provenance retained under `../internal/archive/`; no active selector contract |
| Source-local Wave 01–03 remediation specs/plans/checkpoints | Archived under `../internal/archive/pre-baseline-development/`; do not execute again |
| Pre-baseline Room migration instructions | Historical only; Baseline 2 schema 1 is frozen and current schemas evolve contiguously from it |
| Historical checkpoint `NOT RUN` entries | Evidence history; never infer `PASS` from later source changes |
| Project-wide Baseline 1 refactor plan | Execution record for the one-time reset; not the next feature plan after completion |
| Wave 05 checkpoint and pre-reset Wave 06 entry instruction | Historical execution evidence; Wave 06 is reopened only through the accepted Baseline 2 boundary |
| Architecture Baseline 2 R0-R6 plans | Accepted one-time architecture reset record; not the active feature plan after R6 |
| 2026-08-12 Product UI design/plan | Accepted/completed broader presentation baseline; Discover-specific source/category composition is superseded by the 2026-08-19 Discover spec |
| 2026-08-19 Discover semantic-feed design/plan | Accepted implementation record and current Discover-specific contract; checkpoint evidence is `discover-semantic-feed-redesign.md` |
| Wave 10 schema `6 -> 7` planning text | Superseded by current Room schema 7; Wave 10 notification persistence is rebased to `7 -> 8` |

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
- When a scoped design supersedes only one surface, add an explicit notice to the older design instead of rewriting unrelated historical requirements.
- When a checkpoint is accepted, update its evidence record and `current-state.md` together.
- When a module is added, update settings, architecture policy, module documentation,
  tests, and current state in one reviewed change.
