# Wave 10 Entry Readiness - 2026-08-24

Status: **READY TO START FROM CLEAN-ARCHITECTURE REBASELINE**

## Decision

Wave 10 may start from
`../../implementation/waves/wave-10-background-sync-auth-and-notifications.md` together with
`../../superpowers/specs/2026-08-24-wave-10-clean-background-auth-notifications-design.md`. The
schema-10 canonical durability boundary is implemented and its required policy, host, Room, and API
26/API 37 device gates pass. Wave 10 capability code has not started; this checkpoint accepts only
its entry baseline and the corrected implementation direction.

The rebaseline does not claim that every future-facing policy port already exists. Where current
code keeps user policy in `SavedStateHandle`, constructor constants, platform adapters, or an
insufficient host-only callback, Wave 10 creates a capability-owned narrow port and migrates the
current consumer in the same task.

## Accepted entry baseline

- Production graph: 14 modules; Wave 10 introduces `:settings` and `:feature:settings` only at its
  planned module boundary.
- Accepted CCE boundary: Room schema 9, Phases 0-7 / Tasks 1-42 verified and closed.
- Current source boundary: Room schema 10 through `MIGRATION_9_10`.
- Schema-10 ownership: canonical-work leases and the transactional catalog-change outbox.
- Wave 10 persistence: notification delivery uses `MIGRATION_10_11`.
- Wave 11 entry: schema 11 unless another separately reviewed migration intervenes.

## Plan corrections accepted

1. Wave 10 enters schema 10 and owns only the `10 -> 11` notification migration.
2. `MIGRATION_8_9` remains canonical foundation; `MIGRATION_9_10` remains canonical durability.
3. Wave 10 reuses `LibraryMappingScheduler`, `InitialChapterSyncScheduler`, `DownloadScheduler`,
   and `CanonicalEngineWorkScheduler`, including their stable WorkManager unique names.
4. Reader preferences are not treated as an existing completed boundary. `:reader` introduces
   `ReaderPreferencesPort`, `:app` adapts `:settings`, and the current Reader migrates font scale and
   language order away from SavedState-only policy.
5. Automatic-cache quota is not left as a constructor constant. `:downloads` introduces
   `CachePolicyPort`, `:app` adapts typed settings, and the current document store consumes it.
6. Periodic chapter checks use an explicit candidate source, deterministic bounded batches, stable
   unique names, and one continuation chain. Manual refresh may remain direct when it calls the same
   capability engine and needs immediate UI feedback.
7. Authentication exposes session summaries and login/logout through `PluginSessionControlPort`;
   session ownership remains in `:plugins:runtime`.
8. Credential lookup becomes request-target scoped rather than host-only. Authentication declarations
   define exact navigation, completion, credential-host/path, cookie-name, TTL, and policy-fingerprint
   rules.
9. Session persistence requires Android Keystore-backed AES-GCM, no-backup atomic storage, guarded
   single-flow WebView capture, update/rollback invalidation, credential composition tests, and
   removal/logout cleanup.
10. Raw chapter-change evidence is inserted in the same Room transaction as the chapter graph
    mutation. A recoverable notification drain owns classification/delivery state after commit.
11. Notifications require redirect-aware deep-link validation, persisted or collision-resolved IDs,
    permission/channel `IN_APP_ONLY` handling, durable deduplication, and schema-10 preservation.
12. `MIGRATION_10_11` owns both chapter-change outbox and notification-delivery tables; Wave 10 does
    not introduce a second migration for scheduler state.

## Verification evidence

### Repository policy and architecture

- All 32 scripts under `scripts/tests/*.sh`: **PASS**.
- `scripts/verify-current-architecture.sh`: **PASS**; 14 production modules, 1 android-test module,
  Room schemas 1-10.
- `scripts/verify-room-schema-stability.sh`: **PASS**.
- `scripts/verify-source-layout.sh`: **PASS** after behavior-preserving test/helper splits and the
  `CatalogEntryMergePolicy` extraction.
- Focused rerun: `:catalog:testDebugUnitTest`, `:feature:catalog:testDebugUnitTest`, and
  `:storage:room:testDebugUnitTest`: **BUILD SUCCESSFUL**, 174 tasks executed.
- Focused instrumentation compilation/rerun: **BUILD SUCCESSFUL**, 177 tasks executed.

### Canonical host gates

- `scripts/verify-fast.sh`: **PASS**, 327 actionable tasks; Room schema export stable.
- `scripts/verify.sh`: **PASS**, 628 actionable tasks; lint, debug assembly, tests, architecture,
  and Room schema stability completed.

### API 26 device gate

- `scripts/instrumentation/storage-room.sh 26`: **PASS**, 134/134 tests.
- `scripts/instrumentation/android.sh 26`: **PASS**, 5/5 tests; launcher reported `Status: ok`.
- The first Room run exposed a stale `DatabaseBaselineTest` table list that omitted the schema-10
  `catalog_change_outbox`. Schema export, migration, entity, and DAO evidence confirmed the expected
  table; the literal baseline was corrected and the complete gate was rerun successfully.

### API 37 device gate

- `scripts/instrumentation/storage-room.sh 37`: **PASS**, 134/134 tests.
- `scripts/instrumentation/android.sh 37`: **PASS**, 5/5 tests; cold launcher reported `Status: ok`.

## Entry conditions

- [x] Current scheduling, auth ownership, migration contracts, and missing policy-port cleanup are
  reflected in the rebased Wave 10 plan.
- [x] Every policy test under `scripts/tests/*.sh` passes.
- [x] Schema-10 migration/concurrency/outbox Room instrumentation passes.
- [x] API 26 and API 37 Room/app/launcher checkpoint matrix passes.
- [x] `scripts/verify-fast.sh` and `scripts/verify.sh` pass.
- [x] Current state, roadmap, handbook, and this checkpoint record the accepted boundary together.

## Final assessment

The Wave 10 entry baseline is **READY TO START FROM THE CLEAN-ARCHITECTURE REBASELINE**.
Implementation must follow both the focused design and the Wave 10 plan, introduce missing
consumer-owned ports instead of assuming they exist, use `MIGRATION_10_11` for transactional
chapter-change and notification persistence, preserve runtime-owned sessions, and earn its own
task/checkpoint evidence before any Wave 10 capability is called complete.
