# Reader Integration Architecture Cleanup Design

**Status:** Approved for implementation — final `:reader` integration cleanup after HES-v1 Reader Engine freeze.

## Goal

Make `:reader` consume the frozen HES-v1 engine with no avoidable runtime coupling, duplicate presentation facts, irrelevant UI preferences, legacy generated-profile references, or raw JVM monitor hygiene debt.

## Frozen-engine boundary

- `:reader:engine` remains unchanged.
- `HES_V1`, `READER_ROUTING_V1`, `READER_POLICY_V1`, and `HEALTH_POLICY_V1` remain unchanged.
- No ranking, eligibility, hysteresis, health, hedge, trace, or policy formula changes.
- Room schema and module graph remain unchanged.

## R1 — True LOCAL independence

Planning must tolerate `ReaderSourceAvailability.enabledPluginIds()` failure. Cancellation still propagates; any other exception materializes REMOTE access as unavailable while local cache facts remain routable.

Foreground execution must not eagerly enumerate `ReaderDocumentSourceRegistry` merely because a later REMOTE attempt exists. A per-execution lazy resolver loads enabled sources only when the first REMOTE attempt executes, caches the result for that execution, propagates cancellation, and degrades non-cancellation registry failure to an empty source map. Missing source then becomes the existing typed `reader.source_unavailable` attempt failure.

## R2 — Trace is observational only

Runtime control flow must not read `ReaderDecisionTrace`. The executable `ReaderRouteDecision.competitiveSet.primary` is the source of the primary/winner release used to record the active route. Trace remains diagnostics/replay evidence only.

## R3 — Remove duplicated navigation facts

`ReaderForegroundResult.Committed` must not carry `previousChapterId` or `nextChapterId`. Feature Reader already owns reactive chapter navigation projection from its observed chapter order and recomputes adjacency when the graph changes.

## R4 — Narrow routing preferences

Public `ReaderPreferences` remains a UI/settings contract, but internal routing/session context owns only an immutable `languageOrder`. `fontScale` must not enter `ReaderRouteExecutionContext` or `ReaderRoutePlanningContext`; font-only updates must become routing no-ops.

## R5 — Runtime/generated hygiene

`ReaderAttemptOwnership` uses `ReentrantLock`/`Condition` instead of `java.lang.Object.wait/notifyAll`, preserving the exact ownership state machine and blocking semantics without Kotlin platform-class warnings.

The checked-in baseline profile must not reference removed legacy Reader architecture (`ReaderDocumentRepository`, `reader/selection/*`, old ReaderLoadRequest/legacy ReaderViewModel constructor descriptors). This cleanup does not attempt to synthesize new baseline-profile entries; future profile generation remains the authority for additions.

## Acceptance

1. Valid LOCAL reading survives source-availability enumeration failure.
2. A LOCAL primary executes without enumerating source registry even if a REMOTE fallback exists.
3. A REMOTE registry failure becomes typed source-unavailable failure rather than aborting the route.
4. `ReaderRouteCoordinator` has no production read of `decision.trace`.
5. `ReaderForegroundResult.Committed` has no previous/next fields.
6. Routing execution/planning contexts contain no `ReaderPreferences`/`fontScale`.
7. Font-only preference changes do not invalidate/replan routing.
8. Language-order changes retain existing hard-invalidation semantics.
9. `CompetitiveCompletionRegistry.kt` contains no raw `Object.wait/notify` monitor.
10. Baseline profile contains no removed legacy Reader selector/repository descriptors.
11. No `:reader:engine` source changes.
12. Existing Reader/Feature/architecture/schema verification remains green.
