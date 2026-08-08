# Wave 05 Task 03 — Catalog Matching and Aggregate Ranking Verification

Date: 2026-08-09
Status: **VERIFIED — ACCEPTED FOR TASK 04 ENTRY**

## Scope

This evidence record covers Wave 05 Task 03 only: the pure Kotlin/JVM `:core:matching`
module, deterministic catalog identity resolution, transparent aggregate ranking, module
governance, and the guarantee that Task 01 Room persistence/schema is not changed. It
does not accept the Wave 05 checkpoint as a whole.

## Implementation evidence

- `:core:matching` is declared as a JVM module with direct production project dependencies
  limited to `:core:common` and `:core:model`.
- `CatalogStoryResolver` implements the Task 01 `CatalogCanonicalResolver` port without
  importing Room, plugin host/API, Android, or feature modules.
- Normalized title plus compatible author evidence may auto-link; author conflicts,
  missing author evidence, and equal-strength ambiguous candidates remain separate/reviewable.
- Ambiguous fallback reuses an already-existing deterministic source-isolated canonical ID
  instead of attempting to create a duplicate story row.
- Approved trusted direct mappings are explicit source-to-target identities, validated as
  one target per source, and cannot override a content-type conflict.
- Aggregate ranking normalizes score/scale only into a derived ordering value, ignores
  missing scores rather than inventing them, preserves each original `CatalogEntry`, and
  uses catalog-priority weights plus stable story/source tie-breakers.
- No `core/database` production/schema file is modified by Task 03.

## Accepted verification

Executed on the target Windows checkout:

```bash
./gradlew :core:matching:test \
  --tests app.openstory.matching.CatalogStoryResolverTest.sameTitleDifferentAuthorDoesNotAutoMerge \
  --stacktrace
./gradlew :core:matching:test --stacktrace
./scripts/check-module-dependencies.sh
./scripts/verify.sh
```

Observed: all commands finished successfully, module-boundary verification reported 9 modules,
and repository verification reported `Room schema export remained stable during verification.`

## Current evidence

| Gate | Result | Note |
|---|---|---|
| Matching source compilation/smoke behavior in patch-construction sandbox | PASS | Kotlin compiler smoke exercised author conflict, normalized title+author resolution, candidate-order independence, trusted mapping/content-type gating, source-isolated reuse, weighted ranking, missing-score neutrality, and source contribution ordering. |
| Source layout / baseline architecture shell gates | PASS | `verify-source-layout.sh` and `verify-baseline-architecture.sh` passed on the Task 03 tree. |
| Room schema fingerprint | PASS | `verify-room-schema-stability.sh` remained `5e9d68ea455032869af2a493b2dda89e93eb41e853326c8859a17a36195ea914`; Task 03 changes no database/schema file. |
| Static module contract | PASS | Settings and architecture policy contain the same 9 modules; `:core:matching` direct project dependencies exactly match `:core:common`/`:core:model`; forbidden imports are absent. |
| Patch-construction sandbox Gradle gate | BLOCKED | Wrapper attempted to download Gradle 9.5.0 but the sandbox could not resolve `services.gradle.org`; this did not provide target verification evidence. |
| Focused resolver test on target checkout | PASS | `:core:matching:test --tests app.openstory.matching.CatalogStoryResolverTest.sameTitleDifferentAuthorDoesNotAutoMerge` finished `BUILD SUCCESSFUL`. |
| Complete `:core:matching` module test suite on target checkout | PASS | `:core:matching:test` finished `BUILD SUCCESSFUL`. |
| Architecture Gradle gate on target checkout | PASS | `scripts/check-module-dependencies.sh` finished `BUILD SUCCESSFUL`; `verifyModuleBoundaries` reported 9 modules. |
| Repository `scripts/verify.sh` on target checkout | PASS | Source layout and baseline architecture passed; application identity and 9-module boundaries passed; repository Gradle verification finished `BUILD SUCCESSFUL`; Room schema export remained stable. |

## Exit condition

Satisfied. Wave 05 Task 03 is verified and Wave 05 Task 04 may begin. This record accepts
Task 03 only; it does not accept the Wave 05 checkpoint as a whole.
