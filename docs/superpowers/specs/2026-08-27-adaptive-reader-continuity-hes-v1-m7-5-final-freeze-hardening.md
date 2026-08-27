# Adaptive Reader Continuity / HES-v1 M7.5 Final Freeze Hardening Design

**Date:** 2026-08-27
**Status:** **VERIFIED/CLOSED — HES-v1 FINAL RE-FROZEN / REFERENCE-GRADE**
**Baseline:** M7.3 verified/closed; M7.4 API-hygiene implementation present but not yet separately re-frozen.
**Goal:** Close the last correctness and contract-surface gaps found by the final deep audit, then make HES-v1 the frozen Reader reference implementation. After M7.5 closure, further Reader work is logic evolution under an explicit new design/revision, not open-ended defect hunting.

## 1. Scope

M7.5 is intentionally narrow:

1. make valid-completion publication linearizable under real multi-thread scheduling;
2. make the final session completion gate validate result identity and committed payload coherence;
3. finish the remaining obvious public API hygiene that exists only for tests/implementation constants;
4. record the remaining diagnostic-code naming issue as HES-v2 debt rather than destabilizing V1;
5. run one final-tree closure matrix and re-freeze HES-v1.

No ranking, eligibility, continuity, hedge eligibility, recovery ordering, health formula, persistence, UI, module, or schema behavior changes are in scope.

## 2. Competitive Completion Linearization

The previous runtime captured a valid-completion timestamp and recorded it in separate operations. On a multi-thread dispatcher an attempt could be preempted between those operations, allowing callback/publication scheduling to influence the winner.

The final HES-v1 rule is:

```text
validation succeeds
-> attempt ownership enters PUBLISHING
-> registry atomically samples monotonic completion time and records the completion
-> ownership becomes PUBLISHED
-> terminal notification is emitted
```

Timestamp capture and registry insertion are one registry critical section. Ownership and publication are also serialized against winner-side cancellation.

`ReaderAttemptOwnership` has semantic states:

```text
OPEN
PUBLISHING
PUBLISHED
SEALED
CLOSED
```

When the first success notification is observed, competition does not immediately trust a stale registry snapshot. It first seals potential future publications:

```text
OPEN       -> SEALED       (cannot publish later)
PUBLISHING -> wait until publication linearizes
PUBLISHED  -> remain eligible for winner comparison
CLOSED     -> remain closed
```

Only after this seal settles does the arbiter recompute the registry winner using the normative comparator:

```text
completedAtNanos
-> PRIMARY / HEDGE / FALLBACK role
-> stable attemptId
```

Then and only then are non-winning ownerships closed/cancelled.

This preserves equal-timestamp PRIMARY tie semantics without adding an artificial unique timestamp, grace delay, callback-order key, or hidden publication sequence.

## 3. Runtime Envelope Coherence

A `ReaderValidCompletion` is internally valid only when:

```text
identity.attemptId == attempt.attemptId
loaded.release.id == attempt.releaseId
loaded.release.pluginId == attempt.sourceId
loaded.fromStore == (attempt.accessMode == LOCAL)
completedAtNanos >= 0
```

A competitive failure outcome must also match the launched attempt's release, source, and access mode.

These checks make runtime result envelopes self-consistent before they can influence suppression, arbitration, or visible commit.

## 4. Final Session Commit Authority

`ReaderRouteSession` remains the final serialized visible-state authority. Immediately before final state mutation it verifies:

```text
execution context still matches active session/generation/planRevision/target
result.foregroundIdentity == context.foregroundIdentity
```

For `Committed`, it additionally verifies:

```text
result.chapterGroup == execution target chapter group
result.release is a release in that target group
```

A malformed internal delegate therefore cannot commit another chapter/release even when the surrounding closure context is current.

## 5. Final Public API Hygiene

M7.4 already retires `AccessReason`. M7.5 removes three remaining obvious implementation/test-only exports:

- `ReaderDecisionTrace.empty(...)` — test fixture convenience, not a production consumer contract;
- `HedgeOmissionReason.NOT_EVALUATED` — only used by the removed empty-trace helper; production omission is `NOT_ELIGIBLE`;
- public `HES_V1_MAX_HEALTH_FAILURE_THRESHOLD` — becomes private because only `HealthPolicy` implementation owns this bound.

No replacement API is introduced. `ReaderDecisionTrace` data fields remain unchanged.

## 6. Explicit HES-v2 Debt

`DiagnosticNote.code` still uses `RejectionCode`, while `EXPLICIT_RELEASE_NOT_PRESENT` is prohibited as a `CandidateRejection`. The object categories are separated but the code namespace is not ideal.

M7.5 deliberately does **not** introduce `DiagnosticCode` because that would alter a durable V1 trace contract for naming cleanliness rather than correctness. A future HES-v2 review may split the namespace if a concrete consumer justifies it.

This debt is non-blocking for HES-v1 freeze.

## 7. Version and Architecture Stability

The following remain unchanged:

```text
HesContractVersion.HES_V1
ReaderRoutingAlgorithmVersion.READER_ROUTING_V1
ReaderPolicyVersion.READER_POLICY_V1
HealthPolicyVersion.HEALTH_POLICY_V1
17 production modules + :benchmark
Room schema 11 / schemas 1..11
:reader:engine -> :core:common only
:reader -> :reader:engine via implementation
```

M7.5 changes runtime correctness guards and removes unused exported helpers; it does not change the pure route algorithm or serialized persistence.

## 8. Freeze Rule

HES-v1 may be declared final/reference-grade only after fresh final-tree engine, Reader, downstream, architecture, host, schema, and instrumentation-compile gates pass.

After that closure:

```text
no more proactive deep bug-hunt phases are planned for HES-v1;
new work starts from an explicit logic/behavior upgrade goal;
contract-affecting upgrades require a new design/revision decision rather than silent V1 drift.
```
