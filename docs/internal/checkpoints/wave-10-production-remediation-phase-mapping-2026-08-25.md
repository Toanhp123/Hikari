# Wave 10 Production Remediation Phase Mapping

Date: 2026-08-25

Status: **AUDITED, NOT VERIFIED**

## Purpose

This checkpoint maps the current `feat/wave-10-production-remediation` branch to
`docs/superpowers/plans/2026-08-24-wave-10-production-remediation-implementation-plan.md`.
It separates the committed baseline at `99364fb` from the uncommitted legacy-plan diff so later
work does not treat unverified local edits as completed Redemption tasks.

No Gradle task, unit test, instrumentation test, lint task, application launch, or device gate was
run during this audit.

## Branch Topology

- Common review base: `b1d0c0b`.
- Current branch: `feat/wave-10-production-remediation` at `99364fb` plus an uncommitted diff.
- Prototype branch reviewed by the Redemption plan:
  `feat/wave-10-background-sync-auth-and-notifications` at `20a0b05`.
- The current branch is two commits ahead of the common base; the prototype branch is thirteen
  commits ahead. The current branch did not inherit the prototype implementation.
- Consequently, prototype files named for removal in Redemption Tasks 8, 10-13, and 17 are mostly
  absent here. Those removals are `N/A`, but the production capability required by each task is
  still required.

## Structural Baseline Differences

| Boundary | Redemption assumption | Current branch | Impact |
|---|---|---|---|
| Production modules | 16 modules including `:settings` and `:feature:settings` | 15 modules; `:feature:settings` is absent | Task 15 and Phase 6 need an explicit module restoration/adaptation step |
| Room | Schema 11 prototype requiring repair | Schema 10 | Tasks 16-18 must create the approved schema 11 directly; prototype auth-table removal is `N/A` |
| Plugin auth | Insecure prototype auth/state/storage exists | No production session subsystem exists | Tasks 11, 13-15 are greenfield on this branch |
| Notifications | Transient prototype notifier exists | Only forward-looking RED contract tests exist | Tasks 19-22 are greenfield on this branch |
| Periodic sync | Unbounded prototype engine exists | Bounded replacement was implemented directly in `99364fb` | Prototype removal steps are `N/A`; behavior still needs the gaps below closed |

## Status Vocabulary

- `COMPLETE`: committed implementation materially covers the task contract; verification remains
  required because this audit did not run tests.
- `PARTIAL`: useful committed implementation exists but at least one named requirement is missing.
- `MISSING`: the production capability does not exist on the current branch.
- `CONFLICTING`: the uncommitted diff moves against the Redemption contract and must not be kept as-is.
- `N/A`: a prototype-removal instruction has no matching file on this branch.

## Committed Baseline Mapping (`99364fb`)

| Redemption task | Status | Evidence and remaining work |
|---|---|---|
| Task 1 - Production-graph regression tests | `COMPLETE, INTENTIONALLY RED` | The production graph contracts exist and the runtime-session assertion again describes the required Phase 3 composition. Notification, session, and deep-link assertions remain RED until their owning later phases. |
| Task 2 - Structural production policy | `COMPLETE` | `scripts/check-wave-10-production-policy.sh`, its fixture test, and `scripts/verify.sh` integration exist and target the reviewed shortcuts. |
| Task 3 - Settings migration and corruption recovery | `COMPLETE` | Production DataStore registers `LegacySettingsDataMigration` and `SettingsCorruptionHandler`; `periodicChapterChecksEnabled` and integration coverage exist. |
| Task 4 - Reader first-preference serialization | `COMPLETE` | `ReaderViewModel` waits for the first preference emission before `load`, rethrows cancellation, restores persisted font scale after write failure, and has focused tests for both behaviors. |
| Task 5 - Background policy coordinator | `COMPLETE` | Capability-owned policy/port, app adapter, application-scoped coordinator, startup call, distinct projection, and disable cancellation exist. Keep the committed `periodicChapterChecksEnabled` projection; do not couple it to mapping protection. |
| Task 6 - Candidate planner and cursor | `COMPLETE` | Candidate/cursor models, deterministic 20-item planner, bounded versioned codec, and focused tests exist. |
| Task 7 - Room candidate source | `COMPLETE, UNVERIFIED` | One Room query returns every current Library story, resolves direct redirects, collapses duplicate canonical IDs, uses `MIN` sync time, and has mapping-independence plus redirect/deletion coverage. |
| Task 8 - Bounded dispatcher and continuation | `COMPLETE` | Dispatcher/continuation, stable periodic and per-story names, 20-item planning, cursor continuation, enqueue isolation, and startup scheduling path exist. Prototype engine removal is `N/A` because that engine was never inherited. |
| Task 9 - Auth eligibility vs mapping protection | `COMPLETE THROUGH PHASE 2` | `ChapterSourceEligibility`, isolated source evaluation, cancellation handling, and removal of aggregate notification inputs exist. Mapping protection no longer controls candidate selection or periodic scheduling. The temporary production `ALLOW_ALL` binding is explicitly replaced by Phase 3 Task 15. |

## Uncommitted Diff Classification

### Keep As Phase 3 Input, But Reshape

These edits overlap Redemption Tasks 10 and 12 and can be salvaged only after aligning their exact
interfaces and tests with the copied plan:

- `PluginManifest.kt` authentication validation and SDK authentication documentation.
- `ManagedCredentialRequest` and full-URL lookup in `PluginHttpCapability`.
- `CompositeManagedCredentialProvider` and case-insensitive collision rejection.
- Request-scoped MyAnimeList credentials and DI composition.
- Runtime/API tests for validated targets, denied targets, and provider collisions.

Required reshaping:

- Use `PluginAuthenticationCapability`, `PluginAuthenticationCompletionTarget`, and
  `PluginAuthenticationCredentialTarget` as separate manifest models.
- Use `Set<String>` for navigation hosts and cookie names, and `sessionTtlSeconds: Long`.
- Compute the canonical authentication-policy fingerprint defined by Task 10.
- Keep `Wave10ProductionGraphTest` RED for the required runtime session provider; do not assert that
  the provider is absent.
- Task 12 cannot be called complete until the session-backed provider validates path, TTL, enabled
  state, cookie allowlist, and policy fingerprint.

### Conflicting With Redemption

The four conflicts identified during the initial audit have been reconciled:

- Candidate selection no longer filters by mapping origin.
- Periodic scheduling depends only on `periodicChapterChecksEnabled`.
- Dispatch no longer reloads or propagates the conflated protected-source policy.
- The production graph contract again requires `RuntimeSessionCredentialProvider`.

### Phase 2 Hardening Requiring Separate Review

These local edits are outside Redemption Phase 3-5 and should be reviewed independently before
either keeping or dropping them:

- Expanded central work names/input parsing across mapping, download, canonical, and post-merge work.
- `WorkConstraintsFactory`, scheduling result types, adapter rename, and registrar abstraction.
- Worker decision changes for global failures, source failures, cancellation, and downloads.
- Versioning the WorkManager cursor input key after the committed Phase 2 checkpoint.

They are not prerequisites for Redemption Task 10 and should not be bundled into the Phase 3 auth
checkpoint.

## Remaining Redemption Phases

| Phase/task range | Status | Current-branch interpretation |
|---|---|---|
| Phase 3, Task 10 | `IMPLEMENTED, UNVERIFIED` | Exact manifest authentication models, bounded validation, canonical SHA-256 fingerprint, fixture, and SDK contract are present; bundled manifests remain auth-free. |
| Phase 3, Task 11 | `IMPLEMENTED, UNVERIFIED` | Runtime session-store contract and Android Keystore AES-GCM implementation use `noBackupFilesDir`, per-record AAD, fsync, and atomic replacement. |
| Phase 3, Task 12 | `IMPLEMENTED, UNVERIFIED` | Full request URL validation, collision-safe composition, and policy/path/TTL-scoped Cookie-only session credentials are wired. |
| Phase 3, Task 13 | `IMPLEMENTED, UNVERIFIED` | Session summaries, verified completion, logout, expiry, disablement, and install/update/rollback invalidation hooks are present. |
| Phase 3, Task 14 | `IMPLEMENTED, UNVERIFIED` | Non-exported serialized WebView login capture applies navigation, browser-state, cookie allowlist, and completion guards. |
| Phase 3, Task 15 | `IMPLEMENTED, UNVERIFIED` | `:feature:settings` is restored with a narrow session port; production composes static MAL and runtime session credentials and exposes `auth.getState`. |
| Phase 4, Tasks 16-18 | `MISSING` | Define raw facts, create schema 11 event/delivery durability, and commit facts transactionally. Schema starts at 10 on this branch. |
| Phase 5, Tasks 19-22 | `MISSING` | Implement classification, validated deep links, platform channels/permission/IDs, and durable drain/recovery. |
| Phase 6, Task 23 | `MISSING` | Status ports and error discipline depend on Phase 3-5 production services. |
| Phase 7, Tasks 24-26 | `MISSING` | Integration/device acceptance has not started. |

## Recommended Resume Point

Do not start Redemption Phase 3 on top of the entire current uncommitted diff.

1. Preserve this copied plan and checkpoint.
2. Reconcile the local diff:
   - discard the four explicitly conflicting changes;
   - separate optional Phase 2 hardening from auth work;
   - retain Task 10/12 auth edits only as source material, then reshape them test-first.
3. Close committed Phase 2 Task 7 with redirect/deletion candidate tests and a query that still
   returns every current Library story.
4. Treat Task 9's `ALLOW_ALL` production resolver as an explicit temporary boundary that Phase 3
   Task 15 replaces with installed-manifest/session eligibility.
5. Resume at Redemption Phase 3 Task 10, followed strictly by Tasks 11-15.
6. Before Task 15, add an explicit adaptation subtask to restore the missing `:feature:settings`
   module without importing the prototype's fake auth descriptors or transient state.

## Verification Needed Before Closing The Mapping

After diff reconciliation, run the focused Phase 0-2 gates from the Redemption plan, the production
policy script, architecture verification, and the Room candidate connected test. This checkpoint
does not claim that the committed baseline or local diff compiles or passes tests.
