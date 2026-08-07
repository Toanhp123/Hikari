# Documentation Governance and Precedence

Date: 2026-08-07  
Status: Canonical documentation policy

## Problem this policy solves

The repository accumulated three different document classes:

1. the 2026-08-03 greenfield planning package written before the repository tree existed;
2. source-local remediation specs/plans/checkpoints added while Waves 01–03 were hardened;
3. the 2026-08-06 Selector V2 review package, portions of which were subsequently absorbed into Wave 03.

All three are useful, but treating them as equal execution instructions creates
contradictions and duplicate work.

## Precedence by question

### “What product are we building?”

Use `approved-product-design.md`. The approved baseline is preserved verbatim.
A later implementation document may clarify repository mechanics but may not silently
change product scope or domain invariants.

### “What is implemented right now?”

Use `current-state.md`, then inspect code/tests. A plan checkbox is not proof that
code exists, and code existence is not proof that a checkpoint passed.

### “What do I implement next?”

Use `../implementation/current-roadmap.md`, then the active wave plan. For the
current source snapshot, Selector V2 continuation uses
`../implementation/wave-04-selector-v2-runtime.md`.

### “Has a wave passed its gate?”

Use `../internal/checkpoints/`. Required evidence marked `NOT RUN` remains open
until the checkpoint record is explicitly updated from reviewed command output.

### “How does a plugin/package format work?”

Use `../plugin-sdk/`. These are public SDK-facing documents and should match
`:core:plugin-api` contracts.

## Supersession map

| Older document | Current interpretation |
|---|---|
| Planning-pack `README.md` saying to start Wave 01 | Historical bootstrap instruction; repository has progressed beyond it |
| 2026-08-03 Wave 01–03 implementation plans | Preserve for intent/coverage; source-local remediation governs actual completed repository work |
| 2026-08-03 Wave 04 Task 03 five-file sketch | Expanded by reviewed Selector V2 design because wire-DTO output requires typed V2 bindings |
| 2026-08-06 Selector V2 design | Superseded for ownership/status by source-local `2026-08-07-wave-03-selector-v2-contracts-design.md`; technical runtime requirements remain applicable |
| 2026-08-06 Selector V2 implementation Tasks 1–3 | Already absorbed into Wave 03 remediation in this snapshot; do not execute again |
| 2026-08-06 Selector V2 implementation Tasks 4–9 | Canonical remaining basis for Wave 04 Task 03, repackaged in `wave-04-selector-v2-runtime.md` |
| Historical remediation checkpoint `NOT RUN` statements | Evidence history; do not infer PASS merely because later-wave code exists |

## Lifecycle labels

Every planning document should be understood as one of:

- **CANONICAL** — current entry point or normative baseline;
- **ACTIVE** — current implementation work;
- **PLANNED** — future approved work;
- **HISTORICAL** — completed/superseded execution instructions retained for audit;
- **EVIDENCE** — checkpoint result record;
- **ARCHIVE** — raw package retained unchanged for provenance.

## Change rules

- Do not edit historical evidence to make the timeline look cleaner.
- Do not duplicate project status in multiple files; `current-state.md` owns it.
- Do not duplicate the full roadmap in root `README.md`; link to the canonical docs.
- When a design correction changes wave ownership, update this supersession map.
- When a checkpoint is accepted, update `current-state.md` and the checkpoint record together.
- When a new module is created, update `settings.gradle.kts`, architecture policy,
  README module map, tests and current status in the same reviewed change.
