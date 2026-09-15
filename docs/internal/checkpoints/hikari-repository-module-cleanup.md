# Hikari Repository Module-Local Cleanup Checkpoint

Date: 2026-09-15
Status: **CLEANUP GOVERNANCE READY / NO MODULE CLEANUP STARTED**

## Authority

- Source placement and package ownership: `../../project/file-package-ownership-policy.md`
- Cleanup campaign constraints: `../../superpowers/specs/2026-09-15-hikari-module-local-cleanup-design.md`
- Current execution routing: `../../implementation/current-roadmap.md`
- Production dependency direction: `../../../config/architecture/module-boundaries.json`

## Accepted boundary

- Step 3 remains accepted through Task 10.
- Step 3 Task 11 remains not started during the cleanup interlude.
- Wave 0 repository-truth repair is the prerequisite baseline for this cleanup campaign.
- No module source tree has been reorganized by this governance checkpoint.
- No production behavior, module include, or dependency edge is changed by this checkpoint.

## Governance decisions

1. Cleanup proceeds one primary module per explicitly authorized turn.
2. Module ownership is decided before package/file placement.
3. Generic convenience buckets are not valid cleanup destinations.
4. Objective source-layout rules remain executable gates; responsibility judgments remain review decisions.
5. A target package/file tree must be reviewed before implementation for each selected module.
6. Each selected module receives a bounded implementation plan after its audit; there is no all-module
   placeholder plan and no automatic advance to the next module.
7. Task 11 resumes only after the user explicitly ends the cleanup interlude.

## Governance verification

The documentation-policy change is considered ready when all of the following are fresh and green:

- `git diff --check` for the governance patch;
- canonical routing/authority consistency checks;
- `bash scripts/tests/v2-source-layout-policy-test.sh`;
- `bash scripts/tests/v2-verification-entrypoints-test.sh`.

Fresh governance verification in the patch workspace on 2026-09-15:

- `git diff --check` — **PASS**;
- changed-path scope audit — **PASS**, documentation/agent/navigation surfaces only;
- `settings.gradle.kts` and `config/architecture/module-boundaries.json` byte comparison against the
  Wave 0 baseline — **UNCHANGED**;
- changed-document Markdown link existence audit — **PASS**;
- authority/Task 11 freeze consistency audit — **PASS**;
- `bash scripts/tests/v2-source-layout-policy-test.sh` — **PASS**;
- `bash scripts/tests/v2-verification-entrypoints-test.sh` — **PASS**.

These checks validate governance wiring only. They do not imply that current module-local structural debt
such as the known `core:artwork` large-file violation has been remediated.

## Exact resume boundary

**STOP: no module cleanup is started by this checkpoint.**

The next turn must explicitly name one primary module, read the canonical file/package policy, audit that
module's current tree and immediate consumers, propose the target tree, obtain approval, and only then
write/execute that module's bounded implementation plan.
