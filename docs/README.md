# Documentation

Use this directory instead of hunting through dated plans.

## Start here

1. **[PROJECT-HANDBOOK.md](PROJECT-HANDBOOK.md)** — complete project orientation and rules.
2. **[project/current-state.md](project/current-state.md)** — exact implementation position for this snapshot.
3. **[implementation/current-roadmap.md](implementation/current-roadmap.md)** — wave sequence and current execution path.
4. **[implementation/wave-04-selector-v2-runtime.md](implementation/wave-04-selector-v2-runtime.md)** — active implementation plan.

## Normative product and requirement documents

- [Approved product design](project/approved-product-design.md)
- [Pre-MVP Baseline 1 decision](project/pre-mvp-baseline-1.md)
- [Requirement coverage](project/requirement-coverage.md)
- [Documentation precedence/governance](project/document-governance.md)

## Detailed implementation plans

The original eleven wave plans are retained under `implementation/waves/` with lifecycle
headers. They remain useful as self-contained TDD plans, but Waves 01–03 are historical
for this source snapshot and Wave 04 has a current Selector V2 continuation plan.

## Public/plugin documentation

`plugin-sdk/` contains the SDK-facing API versioning, declarative selector schema,
package format and repository-index rules.

## Evidence and history

- `internal/checkpoints/` — checkpoint evidence; do not rewrite `NOT RUN` into `PASS` without reviewed execution evidence.
- `internal/DOCUMENTATION-AUDIT.md` — findings that motivated this consolidation.
- `internal/archive/` — raw supplied planning/review packages retained unchanged for provenance.
- `superpowers/` — source-local remediation specs/plans retained for historical review and tests; they are no longer the first navigation path.

## One rule

If two documents seem to disagree, read `project/document-governance.md` before acting.
