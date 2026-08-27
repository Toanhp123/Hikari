# Adaptive Reader Continuity / HES-v1 — M0 Constitutional Guardrails Checkpoint

**Date:** 2026-08-25
**Status:** **VERIFIED / CLOSED**
**Scope:** HES-v1 M0 Tasks 3–5 only.
**Next:** M1 — Legacy-Compatible Pure Reasoner, Tasks 6–8.
**Wave 10 relationship:** HES proceeds under the explicit Wave 10 acceptance-rebase. This checkpoint does not close Wave 10 final host/API 26/API 37 acceptance.

## Accepted M0 Boundary

M0 establishes the constitutional Reader/HES boundary only. It does not enable adaptive Reader production behavior.

Accepted source facts:

- `:reader:engine` exists at `reader/engine` and uses `id("openstory.kotlin.jvm")`.
- Engine production project dependencies are exactly `{ :core:common }`.
- `:reader` consumes `:reader:engine` through `implementation`, not `api`.
- No downstream production module directly imports HES engine types.
- Engine source-boundary guards cover `reader/engine/src/main` explicitly, including fully-qualified forbidden references and effect-capable `:core:common` clock/dispatcher symbols.
- Current architecture is 17 production modules plus the `:benchmark` Android test/performance module.
- Room remains schema 11; no `MIGRATION_11_12` or schema-12 ownership is introduced.
- M0 defines the pure fixed-point/version/policy contracts, immutable routing/health facts, and decision/trace contracts required by the R2 design.
- M0 does not implement M1 compatibility planning, M2 coordinator/session execution, M3 health behavior, or later adaptive production routing.

## Developer-Host Verification Evidence

The following commands were run on the developer host from branch `feature/adaptive-reader-continuity` with Gradle 9.5.0.

### Constitutional build-logic tests

```bash
./gradlew :build-logic:test \
  --tests '*ModuleGraphTest*' \
  --tests '*ModuleBoundaryVerifierTest*' \
  --no-daemon
```

Result:

```text
BUILD SUCCESSFUL in 22s
3 actionable tasks: 2 executed, 1 up-to-date
Configuration cache entry reused.
```

No previously observed `TaskContainer.create()` deprecation warning appeared after the M0 cleanup patch.

### Pure engine compile/tests and architecture verifier

```bash
./gradlew :reader:engine:compileKotlin \
  :reader:engine:test \
  verifyArchitecture \
  --no-daemon
```

Result:

```text
Application identity verified as app.openstory.
Module architecture verified for 18 modules.
BUILD SUCCESSFUL in 28s
12 actionable tasks: 6 executed, 6 up-to-date
Configuration cache entry stored.
```

The 18 modules verified by the architecture task are the 17 production modules plus `:benchmark`.

No previously observed Kotlin generated-`copy()` visibility warning appeared after `HealthPolicy` adopted consistent copy visibility.

### Shell/static contracts and production verifiers

```bash
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/verify-current-architecture.sh
```

Result:

```text
verify-current-architecture.sh contract verified.
verify-package-boundaries.sh contract verified.
Package boundary policy verified.
Current architecture verified: 17 production modules, 1 android-test module, Room schema 1..11.
```

The prior Git-Bash/awk `\:` portability warning does not appear after the cleanup patch.

## M0 Task Closure

### Task 3 — Pure module and constitutional guardrails

**CLOSED.** The module graph, exact dependency policy, implementation-only Reader edge, Gradle constitutional tests, and nested engine source scan are present and verified.

### Task 4 — Fixed-point values, versions, routing policy, normalization

**CLOSED.** `BasisPoints`, routing/version identities, validated policy defaults, route/hedge budgets, and normalized language policy contracts are implemented and pass `:reader:engine:test`.

### Task 5 — Immutable routing/health facts and decision/trace contracts

**CLOSED.** Candidate/local/remote/continuity/health/snapshot facts plus route attempts, hedge directive, reasons/rejections, decision trace, and reducer interfaces are implemented and pass `:reader:engine:test`.

## Self-Review / Contradiction Closure

M0 self-review closed the following boundary gaps before this checkpoint:

1. The package verifier now scans the nested `reader/engine/src/main` root instead of leaving it outside the existing Reader scan.
2. Engine purity forbids both imports and fully-qualified references to Android/effect packages.
3. Effect-capable `:core:common` clock/dispatcher symbols are forbidden inside the engine despite the module dependency itself being allowed.
4. `:reader:engine` is implementation-only from `:reader`; downstream modules do not use engine DTOs as transport contracts.
5. Decision execution data such as hedge directives is carried by the decision contract rather than existing only in observational trace data.
6. Semantic and access feature vectors remain distinct so later remote hedge policy cannot accidentally reuse local/cache-inflated access facts.
7. Policy/snapshot list inputs are normalized/copied at the pure boundary so caller mutation cannot undermine replay determinism.
8. Room schema ownership remains unchanged at 11 and no HES migration is introduced.
9. The three verification warnings discovered on the developer host were removed and rerun clean: Kotlin copy visibility, Git-Bash awk escaped colon, and deprecated Gradle task creation in the test fixture.

No known M0 contradiction remains between the R2 design, M0 implementation plan, module graph, pure-engine boundary, and the verified source tree.

## Remaining External Boundary

Wave 10 final acceptance remains **OPEN**. The explicit acceptance-rebase still requires the complete Wave 10 host and API 26/API 37 matrix to be rerun on the HES-containing final tree before Wave 10 itself may be marked accepted/closed. M0 verification is not a substitute for that matrix.

## Decision

**M0 is VERIFIED/CLOSED.** The repository may proceed to **M1 Tasks 6–8** without activating later adaptive Reader runtime behavior.
