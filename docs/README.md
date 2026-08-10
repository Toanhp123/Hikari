# Documentation

Use this directory instead of hunting through dated plans.

## Start here

1. **[PROJECT-HANDBOOK.md](PROJECT-HANDBOOK.md)** — complete project orientation and rules.
2. **[project/current-state.md](project/current-state.md)** — exact implementation position for this snapshot.
3. **[implementation/current-roadmap.md](implementation/current-roadmap.md)** — wave sequence and current execution path.
4. **[superpowers/plans/2026-08-09-ab2-r6-acceptance-and-freeze.md](superpowers/plans/2026-08-09-ab2-r6-acceptance-and-freeze.md)** - active Architecture Baseline 2 acceptance plan.
5. **[internal/checkpoints/architecture-baseline-2-r5.md](internal/checkpoints/architecture-baseline-2-r5.md)** - accepted R5 cleanup evidence and R6 entry boundary.

## Normative product and requirement documents

- [Approved product design](project/approved-product-design.md)
- [Architecture Baseline 2 design](superpowers/specs/2026-08-09-architecture-baseline-2-design.md)
- [Requirement coverage](project/requirement-coverage.md)
- [Documentation precedence/governance](project/document-governance.md)

## Detailed implementation plans

The original eleven wave plans are retained under `implementation/waves/` with lifecycle
headers as historical delivery plans. Architecture Baseline 2 R6 is the active plan;
Wave 06 remains frozen until its acceptance checkpoint passes.

## Public/plugin documentation

`plugin-sdk/` contains the SDK-facing protocol/versioning, JavaScript runtime, package,
repository-index, and contract-testing rules.

## Evidence and history

- `internal/checkpoints/` — checkpoint evidence; do not rewrite `NOT RUN` into `PASS` without reviewed execution evidence.
- `internal/archive/pre-baseline-development/DOCUMENTATION-AUDIT.md` — historical findings that motivated the consolidation.
- `internal/archive/` — raw supplied planning/review packages retained unchanged for provenance.
- `superpowers/` — current source-local baseline design material only; superseded remediation records are archived under `internal/archive/pre-baseline-development/`.

## One rule

If two documents seem to disagree, read `project/document-governance.md` before acting.
