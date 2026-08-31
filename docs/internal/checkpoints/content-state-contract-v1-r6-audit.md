# Content State Contract v1 - UX-R6 Adjacent Semantics Audit

- Source revision: `0ab8fd0`
- Spec: `docs/superpowers/specs/2026-08-27-content-state-contract-v1-design.md`
- Plan: `docs/superpowers/plans/2026-08-27-content-state-contract-v1-implementation-plan.md`
- Scope: Search, Mapping, Reconciliation, and Reader compatibility audit only

## Search boundary

- Queries shorter than the searchable threshold remain `SearchResultState.Idle`; they are not pending requests and an idle screen is not an authoritative empty search result.
- A searchable effective request publishes `Active(ContentState.Pending)` synchronously before debounce. Only a completed request with no stories and no provider failures publishes authoritative `Ready(empty)`.
- Partial provider failures remain metadata inside `ContentState.Ready(CatalogSearchResult)` when usable stories exist. A blocking failure uses `ContentState.Failed`, and retry restarts the same effective request.
- Effective request identity is the normalized query plus source-scoped filter values. A new identity invalidates retained results synchronously, stale executions cannot publish, and an equivalent normalized identity preserves the current Ready result without restarting.
- Query editing, debounce, filter discovery and selection, recents, story selection/navigation, and their retry channels remain Search-local command and interaction semantics. They do not belong in a generic CSC reducer.
- Evidence: `SearchViewModelTest` covers synchronous Pending, stale-result invalidation, equivalent-query retention, source-scoped filter invalidation, partial failures, and blocking retry behavior.

## Mapping boundary

- Mapping observation is keyed to the assisted `StoryId`. Its observation lifecycle maps independently to Pending, first blocking Failed, and Ready snapshot states.
- After the first snapshot, an observation failure retains the Ready mapping and surfaces `observationIssue`; it does not erase authoritative content.
- Search, URL resolution, approve, and reject are commands represented by `MappingCommandState`, including busy state, candidates, attempt identity, search failures, and action failures. Commands remain disabled until an authoritative mapping snapshot exists.
- Command failures do not overwrite observation readiness. This command lifecycle is specialized and must remain outside CSC content observation.
- `SEARCHING` has no truthful production domain signal outside the Library enum/label. Library tests explicitly preserve unresolved mappings without manufacturing a SEARCHING state.
- WorkManager exposes mapping enqueue and worker completion, not an observable per-story mapping lifecycle. A domain mapping-status port would be independently justified only by a future product requirement for truthful background status; UX-R6 does not create one, and it would not be part of CSC.

## Reconciliation boundary

- Queue observation maps Pending, Unavailable, and Available to CSC Pending, Failed, and Ready. An empty queue becomes authoritative `Ready(empty)` only after a real snapshot.
- A post-snapshot observation failure retains the Ready queue and reports `observationIssue`. Content retry and observation retry target their distinct observation conditions.
- Resolution is a separate command lifecycle: per-case resolving identity, revision and stale checks, protected mapping conflict selection/resubmission, merge eligibility, invariant blocking, defer timing, and operation-specific failures.
- Resolution retains queue content while work is in progress and disables only affected operations. These semantics are materially different from generic content observation and remain Reconciliation-local.
- Cancellation is rethrown in both observation and command paths rather than converted into user-visible failure state.
- Evidence: `ReconciliationReviewViewModelTest` covers Pending until snapshot, authoritative empty, first blocking failure, observation retry, retained Ready after observation failure, stale revisions, and protected conflicts.

## Reader compatibility

- Reader HES-v1 owns committed document, chapter, release, source, route, and session semantics. `ReaderViewModel` keeps an existing committed document authoritative during a chapter/release transition or retry; only an initial load without committed content uses blocking `loading`.
- Transition target identity remains separate from committed identity. Saved chapter/release keys and the published document change only after a successful commit, while a failed transition retains the prior document and retry targets the failed transition.
- Route execution is scoped by session, generation, plan revision, and target chapter identity. Completion gates and cancellation prevent stale route executions from committing.
- Prefetch is opportunistic background work. It is scheduled from committed identity, cancelled or replaced with its own token/target lifecycle, and cannot replace foreground or committed authority when it fails.
- No Reader production source imports Catalog `ContentState`, `RefreshState`, `ObservationState`, or the Catalog state package. Reader execution states such as Planning, Executing, Recovering, Validating, Committed, Exhausted, and Cancelled describe route-specific work that is not equivalent to CSC.
- The compatible reusable principles are limited to retaining committed usable content during recovery, distinguishing content retry from prefetch/background activity, and scoping retained state to route/session identity. The Reader types and reducers remain Reader-local.
- Evidence: `ReaderViewModelContinuityTest` verifies committed-document retention during transitions, failed-target retry, saved-identity commit timing, release selection continuity, and rejection of stale intermediate commits.

## Promotion decision

Search, Mapping, and Reconciliation validate CSC inside Catalog while retaining materially different feature-local interaction and command lifecycles. Reader shares continuity principles but does not need identical CSC types or semantics. There is therefore no second feature proving a reusable cross-feature contract, and no cross-feature specification should be proposed from UX-R6.

KEEP_FEATURE_LOCAL
