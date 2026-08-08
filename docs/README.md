# Documentation

Use this directory instead of hunting through dated plans.

## Start here

1. **[PROJECT-HANDBOOK.md](PROJECT-HANDBOOK.md)** — complete project orientation and rules.
2. **[project/current-state.md](project/current-state.md)** — exact implementation position for this snapshot.
3. **[implementation/current-roadmap.md](implementation/current-roadmap.md)** — wave sequence and current execution path.
4. **[implementation/waves/wave-05-catalog-home-and-discovery.md](implementation/waves/wave-05-catalog-home-and-discovery.md)** — active implementation plan; Tasks 01-04 have implementation present; Tasks 02-03 are verified and Task 04 verification is open.

## Normative product and requirement documents

- [Approved product design](project/approved-product-design.md)
- [Pre-MVP Baseline 1 decision](project/pre-mvp-baseline-1.md)
- [Requirement coverage](project/requirement-coverage.md)
- [Documentation precedence/governance](project/document-governance.md)

## Detailed implementation plans

The original eleven wave plans are retained under `implementation/waves/` with lifecycle
headers. They remain useful as self-contained TDD plans, but Waves 01–04 are historical
for this source snapshot and Wave 05 is active.

## Public/plugin documentation

`plugin-sdk/` contains the SDK-facing API versioning, declarative selector schema,
package format and repository-index rules.

## Evidence and history

- `internal/checkpoints/` — checkpoint evidence; do not rewrite `NOT RUN` into `PASS` without reviewed execution evidence.
- `internal/archive/pre-baseline-development/DOCUMENTATION-AUDIT.md` — historical findings that motivated the consolidation.
- `internal/archive/` — raw supplied planning/review packages retained unchanged for provenance.
- `superpowers/` — current source-local baseline design material only; superseded remediation records are archived under `internal/archive/pre-baseline-development/`.

## One rule

If two documents seem to disagree, read `project/document-governance.md` before acting.
