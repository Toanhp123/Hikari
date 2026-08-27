# Adaptive Reader Continuity / HES-v1 — M3 Implementation Checkpoint

**Date:** 2026-08-25
**Status:** **VERIFIED / CLOSED**
**Scope:** HES-v1 M3 Tasks 12–16.
**Next:** M4 Task 17 is **UNBLOCKED / READY TO START**. M4 must continue to preserve the M3 compatibility boundary until its own adaptive-eligibility contracts are introduced.
**Wave 10 relationship:** HES continues under the explicit Wave 10 acceptance-rebase. This checkpoint does not close the still-open Wave 10 final host/API 26/API 37 acceptance matrix.

## Implemented M3 Boundary

M3 adds typed Reader observations, semantic materialization validation, and process-scoped source health/runtime ownership while preserving the M1 compatibility decision order. It intentionally does not enable M4 adaptive eligibility/ranking.

Implemented facts:

- `ReaderAttemptFailure` carries release/source/access identity, typed `SourceObservation`, `RecoveryScope`, legacy code/retryability, and remote attempt origin where applicable.
- `ReaderSourceFailureClassifier` uses exact code entries for the complete current Reader-reachable `CONTENT_CHAPTER` runtime/HTTP/output/policy code inventory. Unknown-code transport fallback is allowed only when the Reader adapter proves the failure originated at the remote source invocation boundary; no prefix/substring classifier is used.
- Local materialized reads distinguish exact missing blob, storage/client I/O, and confirmed fingerprint/decode corruption. Missing/I/O observations remain non-penalizing and never assert corruption. Only a materialized exact locator proven invalid is quarantined/marked known-invalid.
- Cancellation from local read, source fetch, cache write, or quarantine propagates. Non-cancellation quarantine and automatic-cache write failures are best-effort and cannot convert a valid remote semantic document into failure.
- `SourceHealthReducer.v1()` is pure and bounded: integer EWMA, threshold/count opening, exponential capped cooldown, at most 20 successful remote latency samples, nearest-rank latency percentiles, and explicit NORMAL versus HALF_OPEN_PROBE authority.
- A late NORMAL remote success may contribute reliability/latency but cannot close/reset an OPEN/HALF_OPEN failure cycle. Only a successful held HALF_OPEN probe owns closure authority.
- `ReaderSourceHealthRegistry` is process-memory-only and keyed by `SourceOperationKey`; snapshots advance cooldown state before exposure and record origin as startup-neutral versus process-observed.
- `ReaderSourceExecutionLimiter` owns one Reader REMOTE lane per `sourceId`, independent of source object identity, plus one HALF_OPEN probe lease per `SourceOperationKey`. Foreground wins over same-source prefetch; preemption cancels Reader-owned child work rather than the caller/session job.
- `RouteSnapshotAssembler` supplies explicit wall clock, graph/plan revision, existing source availability, process health, and held-probe permission. `ReaderRouteCoordinator` records typed observations and releases probe leases in finalization.
- Decision trace remains observational. M3 source availability/health facts do not alter the M1 compatibility ranking/order; M4 owns adaptive eligibility/ranking, hysteresis policy consumption, prefetch, and hedging.
- No module or Room schema change is introduced; the graph remains 17 production modules plus `:benchmark`, Room schema 11.

## Self-Review / Contradiction Closure

The M3 implementation was reread against the R2 design, Tasks 12–16, the M1/M2 compatibility checkpoint, and current Reader/runtime code. The following gaps/conflicts were corrected before packaging:

1. Current `CONTENT_CHAPTER` failures cross runtime output/protocol/bridge/capability/HTTP boundaries, so the classifier inventory is locked to the exact reachable code set rather than a hand-picked subset or code-prefix heuristic.
2. Legacy local-read handling quarantined an exact fingerprint on generic read exception. M3 now quarantines only a materialized exact locator whose fingerprint/decode validation proves corruption; missing blob and storage I/O are typed non-penalizing failures with remote recovery allowed.
3. Local missing/I/O initially recovered correctly but produced no typed observation. The executor now emits `LocalFailure.MissingBlob` or client-scoped `RuntimeFailure.Unexpected` while preserving the legacy façade's remote-attempt failure surface.
4. A process limiter keyed by source instance would violate the one-Reader-REMOTE-per-source rule when registries recreate source objects. Lanes are keyed by stable `PluginId` instead.
5. Foreground preemption must not cancel the caller/session job. The limiter cancels Reader-owned child work so prefetch observes typed `ReaderPrefetchPreemptedException` without poisoning its parent scope.
6. HALF_OPEN authority cannot be inferred merely from source state. The assembler obtains a real process probe lease and only the first planned attempt for that source is marked `HALF_OPEN_PROBE`; unused/finished leases are released by coordinator finalization.
7. A late NORMAL success during an OPEN cycle cannot seize probe authority. Attempt origin is carried into observations and reducer logic reserves close/reset authority for successful HALF_OPEN probes.
8. M1 compatibility tests previously rejected `SOURCE_UNAVAILABLE`, while Task 16 requires actual availability in snapshots before M4. M3 treats availability/health as observational facts only; compatibility decision ordering is unchanged and adaptive eligibility remains disabled until M4.
9. Final local compilation exposed a Kotlin inference error in the limiter's synchronized lane block. The block now has explicit `Unit` typing and the broader M3 main-source compile check is clean.
10. Coroutine-test `runCurrent()` usage in the new limiter suite is explicitly opted in with the repository-standard `ExperimentalCoroutinesApi` annotation.

No known M3 design/plan contradiction remains in the accepted implementation. Authoritative developer-host Gradle verification is complete and the M3 acceptance boundary is closed.

## Pre-Host Supplied-Environment Verification Evidence

Before developer-host verification, the supplied archive contained no `.git` metadata and the execution environment could not download the uncached Gradle 9.5.0 wrapper distribution because outbound resolution of `services.gradle.org` was unavailable. At packaging time these checks were therefore recorded only as fallback evidence and did **not** by themselves establish Gradle GREEN or VERIFIED/CLOSED. The later developer-host gate below supersedes that acceptance limitation.

The following independent checks were run on the final M3 source tree before host verification:

### Reader-reachable failure inventory

The source inventory and exact classifier table were compared directly.

```text
reachable codes: 51
classified codes: 51
missing entries: 0
extra entries: 0
```

### Local Kotlin source compilation fallback

Relevant M3 `:reader:engine` and Reader main sources were compiled with the locally installed Kotlin compiler. A minimal compatibility shim was used only for the Kotlin-2 built-in copy-visibility annotation that the older local compiler does not recognize; the shim is not part of this patch.

```text
M3_MAIN_KOTLINC_OK
```

This is a syntax/type fallback check only and is not a substitute for the repository's Gradle/Kotlin 2.4.10 build.

### Focused semantic harnesses

A disposable harness exercising reducer OPEN/HALF_OPEN transitions, exact classification, validation, process registry/limiter, and foreground preemption completed with:

```text
M3_HARNESS_OK
```

A second disposable RED/GREEN harness specifically locking typed local missing-blob and local-storage-I/O observations completed with:

```text
M3_LOCAL_OBSERVATION_OK
```

Neither harness is shipped in the patch.

### Repository shell architecture gates

```bash
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/verify-current-architecture.sh
```

Result:

```text
verify-package-boundaries.sh contract verified.
Package boundary policy verified.
Current architecture verified: 17 production modules, 1 android-test module, Room schema 1..11.
```

## Developer-Host Gradle Gate — VERIFIED 2026-08-25

The repository owner applied the M3 patch on branch `feature/adaptive-reader-continuity` and ran the complete recorded host gate with Gradle 9.5.0 available.

The first host attempt exposed one test-only Kotlin 2 type-inference blocker in `ReaderRouteExecutorCompatibilityTest`: the expected `listOf(SourceObservation.Success.Local)` was inferred as `List<SourceObservation.Success.Local>` while the actual value is `MutableList<SourceObservation>`. The compatibility assertion was corrected to declare the expected list as `listOf<SourceObservation>(...)`. No production behavior was changed by that hotfix.

After that correction, every required M3 Gradle gate completed with `BUILD SUCCESSFUL`:

```text
:reader:testDebugUnitTest --tests '*ReaderSourceFailureClassifierTest* --tests '*ReaderSourceFailureInventoryTest*  GREEN
:reader:testDebugUnitTest --tests '*ReaderSourceFailure*'                                              GREEN
:plugins:runtime:testDebugUnitTest                                                                    GREEN
:reader:testDebugUnitTest --tests '*ReaderDocumentValidatorAdapterTest* --tests '*ReaderDocumentRepositoryTest*'  GREEN
:reader:testDebugUnitTest --tests '*ReaderDocument* --tests '*ReaderRouteExecutor*'                   GREEN
:downloads:testDebugUnitTest --tests '*DownloadAwareReaderDocumentStoreTest*'                         GREEN
:reader:engine:test --tests '*SourceHealthReducerTest*'                                               GREEN
:reader:engine:test verifyArchitecture                                                                GREEN
:reader:testDebugUnitTest --tests '*ReaderSourceHealthRegistryTest* --tests '*ReaderSourceExecutionLimiterTest*'  GREEN
:app:compileDebugKotlin                                                                               GREEN
:reader:engine:test --tests '*ReaderDecisionTraceTest*'                                               GREEN
:reader:testDebugUnitTest --tests '*RouteSnapshotAssemblerTest*'                                     GREEN
:reader:engine:test :reader:testDebugUnitTest                                                         GREEN
```

Architecture verification in the same host run reported:

```text
Application identity verified as app.openstory.
Module architecture verified for 18 modules.
verify-package-boundaries.sh contract verified.
Package boundary policy verified.
Current architecture verified: 17 production modules, 1 android-test module, Room schema 1..11.
```

This host evidence closes the acceptance item that remained open in the packaged M3 implementation checkpoint. It does not substitute for the separately open Wave 10 final host/API 26/API 37 acceptance matrix.

## Commit Consolidation

The implementation plan lists one logical commit per Task 12–16. This patch intentionally packages the whole M3 boundary together for the repository owner to apply and verify as one coherent change. It does not claim those per-task commit commands were executed inside the supplied archive.

## Remaining External Boundary

Wave 10 final acceptance remains **OPEN**. The complete Wave 10 developer-host plus API 26/API 37 matrix still must be rerun on the final HES-containing tree according to its existing acceptance-rebase checkpoint. M3 verification cannot substitute for that matrix.

## Decision

**M3 is VERIFIED/CLOSED.** Tasks 12–16, the compatibility boundary, host Gradle gate, and architecture/package checks are accepted. **M4 Task 17 is UNBLOCKED / READY TO START.** Wave 10 final host/API 26/API 37 acceptance remains independently open under its acceptance-rebase.
