# Hikari Repository Cleanup Wave 0 Design

Date: 2026-09-15
Status: APPROVED SCOPE FOR CLEANUP WAVE 0

## Goal

Restore repository truth before module-local cleanup. Wave 0 changes verification routing and canonical
entry-point documentation only; it does not change production behavior, module ownership, or begin
Step 3 Task 11.

## Scope

1. Current verification entrypoints must run only current, host-static contract scripts before their
   single Gradle invocation. Historical Step 2 freeze checks and standalone Gradle wrapper checks may
   remain for provenance/manual use but must not be reached through the current static-gate runner.
2. `docs/project/current-state.md` must describe the repository accepted through Step 3 Task 10.
3. Root README, project handbook, and Design System policy must stop advertising superseded Step 2/V1
   topology as current repository state.
4. Current module/dependency authority remains `settings.gradle.kts` plus
   `config/architecture/module-boundaries.json`; documentation should link to those authorities rather
   than duplicate a brittle exact graph where unnecessary.

## Non-goals

- No Kotlin/Java production or test-source refactor.
- No module additions/removals/dependency-edge changes.
- No Task 11 Reading Source, Settings, network, Chapter, or Reader work.
- No cleanup of `core:artwork`, `feature:catalog`, `feature:story`, or other module internals.
- No deletion of retained/quarantined modules or historical evidence.
- No claim that user-owned broad/device/performance gates were rerun by this patch.

## Verification topology

`scripts/verification-common.sh` owns an explicit list of current static contract tests. This is
intentional: wildcard discovery cannot distinguish a historical freeze test from a current law and
can accidentally nest Gradle inside the static stage. The current list contains only shell/static
contracts that are valid against the accepted Step 3 Task 10 tree.

The canonical host entrypoints remain `scripts/verify-fast.sh` and `scripts/verify.sh`. Each runs the
shared static stage and then exactly one top-level Gradle command. Gradle remains authoritative for
`verifyArchitecture`, package/module boundaries, Step 3 build-surface verification, build-logic tests,
and the Step 3 module aggregates.

## Documentation rule

`docs/project/current-state.md` owns implemented-now state. `docs/implementation/current-roadmap.md`
owns the next execution boundary and continues to state that Task 11 is not started. README and the
handbook are navigation/orientation surfaces and must not override those files.

The Design System policy is updated from the Step 2 freeze to the accepted Step 3 Task 10 boundary:
domain-neutral dimensions, the 600dp breakpoint, poster primitives, shared controls/navigation/header,
and action/choice sheets are admitted while feature semantics and runtime/work ownership remain
outside `:core:designsystem`.

## Exit criteria

- Verification-entrypoint regression test rejects wildcard static-test discovery and rejects routing
  historical or Gradle-backed scripts through the static stage.
- Current static contract scripts pass when invoked by the shared runner up to any known module-local
  structural debt outside Wave 0.
- Canonical entry-point docs agree on Step 3 Task 10 / Task 11-not-started state.
- Patch contains no production source or module-graph change.

## Follow-on governance

Wave 0 only restores repository truth. Module-local cleanup after Wave 0 is governed by
`../../project/file-package-ownership-policy.md` and
`2026-09-15-hikari-module-local-cleanup-design.md`. Historical Step 2 freeze rules must not be revived
as cleanup conventions. A cleanup may reorganize one authorized module internally, but Wave 0 itself
does not authorize those source changes.
