# Adaptive Reader Continuity / HES-v1 M7.4 AccessReason API Hygiene Design

**Date:** 2026-08-27
**Status:** **VERIFIED/CLOSED — CLOSURE EVIDENCE PROVIDED BY ACCEPTED M7.5 FINAL-TREE VERIFICATION**
**Scope owner:** `:reader:engine` public decision-contract surface and HES governance documentation.
**Baseline:** M7.3 `VERIFIED/CLOSED`; HES-v1 re-frozen at the unchanged V1/module/schema boundary.
**Decision:** **Outcome B — retire `AccessReason` from the exported contract.**

## 1. Goal

Remove the unused `AccessReason` enum from the HES-v1 Reader Engine API without changing routing behavior,
decision replay, route traces, persistence, module boundaries, or any HES/policy version constant.

This phase resolves the API-hygiene debt explicitly deferred by M7.3. It is not a routing feature phase and
must not manufacture a new consumer or a replacement reason taxonomy merely to preserve an unused symbol.

## 2. Evidence and Root Cause

The post-M7.3 source tree proves all of the following:

- `AccessReason` is declared as a top-level public enum in
  `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteDecision.kt`;
- production code has no consumer, producer, field, serializer, persistence adapter, or trace entry for the type;
- `ReaderDecisionTrace` does not carry `AccessReason`;
- the only test reference is an existence/type-separation assertion in `ReaderDecisionTraceTest`;
- `:reader:engine` uses the Kotlin/JVM plugin and is not configured as a published artifact in the repository;
- `:reader` consumes `:reader:engine` internally, and architecture guards keep engine types behind the Reader boundary;
- `AccessReason` is not part of Room, network, plugin protocol, saved state, or any serialized format.

The enum is therefore an exported symbol with no demonstrated semantic owner or runtime consumer.

## 3. Why Retain-and-Make-Semantic Is Rejected

The current enum mixes two different semantic axes:

```text
LOCAL_PREFERRED / REMOTE_PREFERRED
    -> selected access-path preference

SAME_RELEASE_REMOTE_RECOVERY / RANKED_FALLBACK
    -> recovery topology / route position
```

Those facts are already represented by immutable decision data:

```text
RouteAttempt.accessMode
RouteAttempt.role
RouteAttempt.releaseId
ReaderDecisionTrace.routeConstruction
ReaderDecisionTrace.stableRanking
ReaderDecisionTrace.hedgeDirective
ReaderRouteDecision.competitiveSet
ReaderRouteDecision.recoveryChain
```

The mapping is derivable without another enum:

```text
PRIMARY + LOCAL
    -> locally selected primary access

PRIMARY + REMOTE
    -> remotely selected primary access

FALLBACK + same releaseId as primary + REMOTE
    -> same-release remote recovery

FALLBACK + different releaseId from primary
    -> ranked fallback

HEDGE
    -> explicitly represented by AttemptRole.HEDGE + HedgeDirective
```

`AccessReason` is also incomplete for the current M6/M7 routing model because it has no hedge semantic. Wiring it
into the trace would therefore require expanding or overloading the type, creating new API semantics solely to
justify an otherwise-unused abstraction. M7.4 rejects that direction.

## 4. Normative Decision

M7.4 removes:

```kotlin
enum class AccessReason {
    LOCAL_PREFERRED,
    REMOTE_PREFERRED,
    SAME_RELEASE_REMOTE_RECOVERY,
    RANKED_FALLBACK,
}
```

No replacement enum, alias, deprecation shim, trace field, or derived `accessReason` property is introduced.

The durable reason/diagnostic taxonomy after M7.4 is:

```text
DecisionReason
RejectionCode
DiagnosticNote
```

Access-path and recovery explanation remains structural and immutable:

```text
AccessMode + AttemptRole + routeConstruction + stableRanking + HedgeDirective
```

This is the only normative representation of access/recovery topology for HES-v1.

## 5. Contract and Versioning Decision

The following constants remain unchanged:

```text
HesContractVersion.HES_V1
ReaderRoutingAlgorithmVersion.READER_ROUTING_V1
ReaderPolicyVersion.READER_POLICY_V1
HealthPolicyVersion.HEALTH_POLICY_V1
```

Rationale:

- no `ReaderRouteDecision` value changes;
- no `ReaderDecisionTrace` field or replay input changes;
- no ranking, eligibility, hysteresis, hedge, fallback, health, or execution procedure changes;
- no serialized/persisted schema changes;
- no production consumer exists in the repository;
- the module is an internal project dependency, not a configured published library artifact.

The removal is therefore an API-surface hygiene correction, not a routing-contract revision.

If evidence later appears that a binary artifact containing this API was published to external consumers outside
this repository, that compatibility fact supersedes this assumption and requires a separate deprecation/versioning
review. No such evidence exists in the current tree.

## 6. Test Strategy

Do not replace the old existence-only `AccessReason` assertion with an absence-only repository test.

TDD for the removal uses a temporary source-surface probe outside the repository tree:

```text
before removal -> probe fails because AccessReason still exists
remove enum     -> probe passes because the redundant public symbol is absent
```

Long-lived regression confidence comes from semantic tests that already exercise the facts `AccessReason` tried to
summarize:

```text
ReaderDecisionTraceTest
RoutePlannerTest
ReaderRouteEngineContractTest
HedgePolicyTest
ReaderGoldenScenariosTest
ReaderPermutationPropertyTest / ReaderMetamorphicTest via the full engine suite
```

The retained type-separation test is narrowed to actual durable types:

```text
DecisionReason
RejectionCode
DiagnosticNote
```

## 7. Documentation and Governance

M7.4 must update current normative documentation to match the actual API:

- canonical HES-v1 design §63/§65 and SR-27;
- canonical HES-v1 implementation plan Task 5/Task 16 and status header;
- `docs/project/current-state.md`;
- `docs/implementation/current-roadmap.md`;
- new M7.4 checkpoint after final-tree verification.

Historical M7.3 plan/checkpoint text that says `AccessReason` was deferred remains historical evidence and must not
be rewritten to pretend the debt did not exist at M7.3 closure.

The current tree also contains stale M7.3 `IN PROGRESS` prose in `docs/implementation/current-roadmap.md` despite
its own M7.3 table row and `current-state.md` already saying `VERIFIED/CLOSED`. M7.4 corrects that current-surface
contradiction while preserving historical checkpoint wording.

## 8. Scope

In scope:

1. remove `AccessReason` from `:reader:engine`;
2. remove the existence-only test reference;
3. make canonical trace/reason docs match the structural access explanation already implemented;
4. reconcile the stale current-roadmap M7.3 prose;
5. retain M7.4 implementation evidence; final re-freeze verification is performed by M7.5 because additional pre-freeze hardening was discovered before M7.4 closure.

Out of scope:

- new reason enums or trace fields;
- changes to `AccessMode`, `AttemptRole`, `HedgeDirective`, `RouteAttempt`, or `ReaderDecisionTrace` shape;
- ranking/eligibility/hysteresis/hedge/fallback behavior changes;
- runtime identity, scheduler, health, prefetch, cache, or UI changes;
- Room schema or persistence changes;
- module graph changes;
- public HES/policy version bump;
- unrelated source-layout or Detekt debt.

## 9. Acceptance Criteria

M7.4 may close only when all applicable statements are true:

```text
A1  reader production/test source has no AccessReason reference.
A2  no replacement/alias/deprecation shim is introduced.
A3  ReaderDecisionTrace shape is unchanged.
A4  route/hedge/fallback semantics are unchanged and existing tests remain green.
A5  DecisionReason/RejectionCode/DiagnosticNote remain distinct durable contracts.
A6  canonical design no longer lists AccessReason as a normative reason class.
A7  canonical plan no longer claims AccessReason is a produced/required decision contract.
A8  current-state and current-roadmap agree on M7.4 status and M7.3 historical closure.
A9  M7.3 checkpoint remains historical and unrewritten.
A10 HES_V1 / READER_ROUTING_V1 / READER_POLICY_V1 / HEALTH_POLICY_V1 remain unchanged.
A11 production module graph remains 17 modules plus :benchmark; :reader:engine stays JVM-only behind :reader.
A12 Room remains schema 11 with no MIGRATION_11_12.
A13 engine, Reader/downstream, architecture, host, and schema-stability gates used for HES closure are green.
```

## 10. Stop Conditions

Stop and re-audit if retirement requires any of the following:

```text
adding a new reason type to replace AccessReason
adding AccessReason-derived state to ReaderDecisionTrace
changing route ranking or recovery construction
changing any HES/policy version constant
adding publication/deprecation machinery not otherwise present
changing Room/schema/module graph
rewriting M7.3 historical evidence to erase the deferred decision
```

Any of those means the change is no longer API hygiene and requires a separate design.
