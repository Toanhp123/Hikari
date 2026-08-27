# Adaptive Reader Continuity / HES-v1 - M7.3 Conformance Repair Checkpoint

Date: 2026-08-27
Status: **VERIFIED / CLOSED**
Freeze state: **HES-v1 RE-FROZEN**

M7.2 remains accepted historical `VERIFIED/CLOSED` evidence. M7.3 did not rewrite or invalidate that
checkpoint; it prospectively reopened HES-v1 only for the four scoped conformance repairs below. The
initial supplied-ZIP verification was environment-limited and correctly left governance open. After that
patch was applied in the real repository, the developer supplied fresh final-tree evidence for every
blocking host/Gradle gate required by the M7.3 implementation plan, so M7.3 is now closed and HES-v1 is
re-frozen.

## Scope / Findings Repaired in the Current Source Tree

Source review of the supplied post-Task-3 tree confirms the intended implementation shape for:

1. zero REMOTE access-weight denominator rejected by `ReaderRoutingWeights`, with
   `CandidateEvaluator` consuming the same canonical `totalRemoteAccessWeight`;
2. foreground runtime completion/failure outcomes carrying `ReaderAttemptIdentity`, with result-owned
   identity consumed by session/coordinator stale-result gates and `ReaderAttemptFailure` remaining an
   identity-free semantic payload;
3. REMOTE `completedAtNanos` captured after successful document validation while source-health latency
   remains based on fetch/source-effect time;
4. the production monotonic scheduler using non-decreasing clamping (`maxOf(raw, previous)`) so equal
   timestamps survive to the normative `completedAtNanos -> PRIMARY -> attemptId` comparator.

These source-level observations are now backed by the fresh final-tree verification matrix recorded below.

## Commit Evidence

The generated source archives do not contain `.git` metadata and the supplied closure transcript does not
include exact Task 1-3 Git SHAs, so this checkpoint does not fabricate them. The intended independently
reviewable commit subjects remain:

```text
fix(reader-engine): reject zero access-weight routing policy
fix(reader): carry execution identity in runtime outcomes
fix(reader): order hedge winners by valid completion
docs(reader): close HES-v1 M7.3 conformance repair   # this closure commit
```

Final-tree verification, not unrecoverable archive provenance, is the closure authority for C1-C18. The
real repository history remains the source of truth for the exact Task 1-3 SHAs.

## Fresh M7.3 Final-Tree Verification Evidence

The following commands were rerun by the developer on the real `feature/adaptive-reader-continuity`
working tree after applying the governance patch. All blocking host/Gradle rows completed successfully.

| Command | Result | Fresh evidence |
|---|---|---|
| `./gradlew :reader:engine:test --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 22s`; 10 actionable tasks |
| `./gradlew :reader:testDebugUnitTest --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 21s`; 65 actionable tasks |
| `./gradlew :downloads:testDebugUnitTest :feature:reader:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugKotlin --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 25s`; 290 actionable tasks |
| `./gradlew :build-logic:test verifyArchitecture --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 25s`; application identity verified; module architecture verified for 18 modules |
| `bash scripts/tests/verify-package-boundaries-test.sh` | **PASS** | package-boundary contract verified |
| `bash scripts/verify-package-boundaries.sh` | **PASS** | `Package boundary policy verified.` |
| `bash scripts/tests/verify-current-architecture-test.sh` | **PASS** | current-architecture mutation contract verified |
| `bash scripts/verify-current-architecture.sh` | **PASS** | `17 production modules, 1 android-test module, Room schema 1..11.` |
| `bash scripts/verify-fast.sh` | **PASS** | `BUILD SUCCESSFUL in 30s`; 392 actionable tasks; Room schema export stable |
| `bash scripts/verify.sh` | **PASS** | `BUILD SUCCESSFUL in 2m 37s`; 743 actionable tasks; structural hard policies/current architecture/Wave 10 production policy verified; Room schema export stable |
| `bash scripts/verify-room-schema-stability.sh` | **PASS** | digest `0c5aced22ed5f88395b422cc4171139e9c9081fbdb266893b37239f587b5fac0` |
| `./gradlew :storage:room:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 21s`; 240 actionable tasks |

The fresh final tree therefore preserves the constitutional boundary:

```text
17 production modules
1 android-test module
18 modules seen by verifyModuleBoundaries including :benchmark
Room schemas 1..11
Room schema digest 0c5aced22ed5f88395b422cc4171139e9c9081fbdb266893b37239f587b5fac0
:reader:engine remains JVM-only behind :reader
no MIGRATION_11_12 / schema-12 change
HES/public V1 version constants unchanged
```

### Connected API 26 / API 37 matrix

No fresh connected-device matrix was included in the M7.3 closure transcript. This is recorded rather than
fabricated. M7.3 changes no Room schema or device-only behavior; the implementation plan makes the fresh
API 26/API 37 rerun conditional on device/emulator availability, while M7.2 device evidence remains
historical evidence only.

## Normative Documentation Reconciliation

The current M7.3 governance patch makes these contracts explicit:

- M7.2 stays historical accepted evidence; M7.3 reopened the freeze prospectively for the scoped repair and is now re-frozen from fresh closure evidence;
- design §44.3 scopes the full identity tuple to foreground execution attempt/results crossing the
  executor/competition/coordinator runtime boundary;
- `ReaderAttemptFailure` remains identity-free semantic payload while `ReaderAttemptOutcome.Failure`
  owns `ReaderAttemptIdentity`;
- prefetch receives no synthetic foreground generation identity;
- design §62 owns the derived invariant
  `health + reliability + latency + cacheUtility > 0` for the REMOTE access-score denominator;
- the canonical M6 wording is non-decreasing/equal-timestamp-preserving and valid completion means the
  timestamp immediately after successful validation, not after fetch;
- both canonical current-status surfaces (`docs/project/current-state.md` and
  `docs/implementation/current-roadmap.md`) now report M7.3 `VERIFIED/CLOSED` and HES-v1 `RE-FROZEN`.

Historical checkpoint text is retained as historical evidence even where it records the understanding at
that older milestone (for example the M6 checkpoint's then-current strictly-increasing timestamp wording).
It is not treated as current normative authority.

## Deferred API Hygiene — `AccessReason`

`AccessReason` remains intentionally unresolved and unchanged. M7.3 does not add a production consumer,
remove/deprecate the enum, or claim this debt is closed. A separate retain-vs-retire contract review is
still required before any later claim that the Reader Engine public API has completed hygiene/finalization.

## Contradiction / Diff Audit

The required final-tree scans were executed after the governance edits.

```bash
rg -n "M0.?M7\.1|M7\.1 VERIFIED|strictly increasing.*completion|never move backward or repeat|M7\.2.*FROZEN" docs reader
rg -n "AccessReason" reader docs/superpowers/specs docs/superpowers/plans
```

Result classification:

- `docs/project/current-state.md` no longer reports M7.1 as the current HES boundary; it reports
  `M0–M7.3 VERIFIED/CLOSED; HES-v1 RE-FROZEN`.
- `docs/implementation/current-roadmap.md`, discovered during Task 4 review as a second canonical current
  status surface, is aligned to the same closed/re-frozen state and has explicit M7.2/M7.3 rows.
- the remaining `M7.2 ... FROZEN` matches are historical M7.2 checkpoint/implementation-plan evidence and
  are intentionally preserved rather than rewritten;
- the remaining `strictly increasing production completion stamps` match is in the historical M6
  checkpoint, which records the understanding at that older milestone. Current normative design/plan text
  is non-decreasing and equal-timestamp preserving;
- the M7.3 implementation plan itself contains old wording only inside the problem statement/test-renaming
  instructions and its audit regex; those are not normative current-state claims;
- `AccessReason` matches remain expected. Source/API was not changed and this checkpoint explicitly defers
  the retain-vs-retire decision.

No current normative/current-status contradiction was found after these edits.

## Closure Decision

Every blocking M7.3 host/Gradle final-tree gate required by Task 4 is now evidenced GREEN. The four scoped
conformance repairs satisfy C1-C18 without changing the HES/public V1 versions, production module boundary,
or Room schema. `AccessReason` remains explicitly deferred and unchanged.

M7.3 is therefore **VERIFIED/CLOSED**, and **HES-v1 is RE-FROZEN** at the unchanged V1 contract,
17-production-module plus `:benchmark`, Room-schema-11 boundary. M7.2 remains historical accepted evidence
and is not rewritten by this closure.
