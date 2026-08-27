# Adaptive Reader Continuity / HES-v1 - M7.4 AccessReason API Hygiene Checkpoint

Date: 2026-08-27
Status: **IMPLEMENTED / NOT CLOSED**
Freeze state: **HES-v1 FREEZE REOPENED ONLY FOR API HYGIENE**

M7.3 remains historical `VERIFIED/CLOSED` evidence and is not rewritten by this phase. M7.4 implements the
separate retain-vs-retire review that M7.3 explicitly deferred for `AccessReason`.

## Decision

Outcome B was selected: **retire `AccessReason` from the exported Reader Engine contract**.

The current tree provided no production producer/consumer, trace field, persistence/serialization use, or external
publication configuration for the enum. Its values duplicated facts already represented by immutable route data and
were incomplete for the current hedge model. M7.4 therefore removes the symbol rather than manufacturing a trace
consumer or replacement taxonomy.

No replacement enum, alias, deprecation shim, derived property, or `ReaderDecisionTrace` field is introduced.

The durable explanation model remains:

```text
semantic winner reason       -> DecisionReason
candidate/access rejection   -> RejectionCode / CandidateRejection
observational diagnostic     -> DiagnosticNote
access/recovery topology     -> AccessMode + AttemptRole + routeConstruction + stableRanking + HedgeDirective
```

## Source Changes Present

- `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteDecision.kt`
  - removed only the public `AccessReason` enum;
- `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderDecisionTraceTest.kt`
  - removed the existence-only `AccessReason` assertion and retained the durable
    `DecisionReason` / `RejectionCode` / `DiagnosticNote` type contract;
- canonical design/plan updated so `AccessReason` is no longer a live HES-v1 reason contract;
- `docs/project/current-state.md` and `docs/implementation/current-roadmap.md` now agree that M7.3 is historical
  closed evidence while M7.4 is the active narrow API-hygiene boundary;
- M7.3 historical plan/checkpoint files remain unchanged.

## TDD Evidence

A temporary source-surface probe was created outside the repository and intentionally not committed.

Before production removal:

```text
RED: AccessReason is still exported.
exit=1
```

After removal:

```text
GREEN: AccessReason is not exported.
exit=0
```

A final `rg -n "AccessReason" reader` returns no production or test reference.

No long-lived absence-only test was added. Long-lived protection remains the semantic route/trace/hedge/golden
suite rather than an inverse existence assertion.

## Fresh Evidence Available in the Supplied Sandbox

The sandbox cannot bootstrap Gradle 9.5.0 because it has no network route/cache for the wrapper distribution. The
focused Gradle command stopped before task graph execution with:

```text
Downloading https://services.gradle.org/distributions/gradle-9.5.0-bin.zip
java.net.UnknownHostException: services.gradle.org
```

This is an environment blocker, not a project test result. M7.4 is therefore not closed from sandbox evidence alone.

The following fresh source-level gates do pass on the modified tree:

| Gate | Result | Evidence |
|---|---|---|
| temporary `AccessReason` RED/GREEN probe | **PASS** | RED before removal, GREEN after removal |
| local Kotlin compile of complete `:reader:engine` production source | **PASS** | compiled with local Kotlin 1.9 toolchain, coroutine jar, and a test-only compatibility stub for Kotlin 2.x `ConsistentCopyVisibility` |
| `bash scripts/tests/verify-package-boundaries-test.sh` | **PASS** | `verify-package-boundaries.sh contract verified.` |
| `bash scripts/verify-package-boundaries.sh` | **PASS** | `Package boundary policy verified.` |
| `bash scripts/tests/verify-current-architecture-test.sh` | **PASS** | `verify-current-architecture.sh contract verified.` |
| `bash scripts/verify-current-architecture.sh` | **PASS** | `17 production modules, 1 android-test module, Room schema 1..11.` |
| `bash scripts/verify-room-schema-stability.sh` | **PASS** | `0c5aced22ed5f88395b422cc4171139e9c9081fbdb266893b37239f587b5fac0` |

The local Kotlin compile is syntax/source compatibility evidence only; it does not substitute for the repository's
Gradle test suite.

## Version / Module / Schema Audit

M7.4 intentionally leaves these unchanged:

```text
HesContractVersion.HES_V1
ReaderRoutingAlgorithmVersion.READER_ROUTING_V1
ReaderPolicyVersion.READER_POLICY_V1
HealthPolicyVersion.HEALTH_POLICY_V1

17 production modules
1 android-test module
:reader:engine remains JVM-only behind :reader
Room schema 11
no MIGRATION_11_12
```

`ReaderDecisionTrace` data-class shape is unchanged. No routing formula, route-construction rule, fallback order,
hedge policy, health reducer, runtime identity, scheduler, prefetch, cache, or UI behavior was modified.

## Required Fresh Host Closure Matrix

Before M7.4 can be marked `VERIFIED/CLOSED`, run on the real repository tree:

```bash
./gradlew :reader:engine:test --no-daemon
./gradlew :reader:testDebugUnitTest --no-daemon
./gradlew :feature:reader:testDebugUnitTest :app:compileDebugKotlin --no-daemon

./gradlew :build-logic:test verifyArchitecture --no-daemon
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/verify-current-architecture.sh

bash scripts/verify-fast.sh
bash scripts/verify.sh
bash scripts/verify-room-schema-stability.sh
```

Focused engine evidence should also include:

```bash
./gradlew :reader:engine:test \
  --tests '*ReaderDecisionTraceTest*' \
  --tests '*RoutePlannerTest*' \
  --tests '*ReaderRouteEngineContractTest*' \
  --tests '*HedgePolicyTest*' \
  --tests '*ReaderGoldenScenariosTest*' \
  --no-daemon
```

## Contradiction / Scope Audit

Current-tree audit requirements:

```bash
rg -n "AccessReason" reader
rg -n "AccessReason" \
  docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md \
  docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md
rg -n "M7\.3 is IN PROGRESS|M7\.3 must close before" \
  docs/project/current-state.md docs/implementation/current-roadmap.md
```

Expected interpretation:

- no `AccessReason` hit in `reader`;
- canonical design/plan may mention the symbol only as the M7.4 retired/deferred historical API debt, never as a
  live produced/required reason contract;
- no current-status surface claims M7.3 is still active;
- M7.3 plan/checkpoint mentions remain historical evidence and are intentionally preserved.

## Closure Decision

**NOT CLOSED.** The API-hygiene implementation is present and source/static gates are clean, but the required fresh
Gradle/host matrix has not run in this sandbox because Gradle wrapper bootstrap is network-blocked. Until that matrix
is supplied green from the real repository, current governance must remain:

```text
M0–M7.3 VERIFIED/CLOSED historically
M7.4 IN PROGRESS
HES-v1 FREEZE REOPENED ONLY FOR API HYGIENE
```

After fresh host evidence is green, a closure-only docs patch may update this checkpoint and both current-status
surfaces to `M0–M7.4 VERIFIED/CLOSED; HES-v1 RE-FROZEN` without further production code changes.
