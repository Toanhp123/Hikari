# Canonical engine audit hardening — 2026-08-24

## Scope

This checkpoint covers the no-schema-migration audit hardening pass for canonical catalog
reconciliation and fusion. It intentionally leaves schema evolution and production-scale
benchmarking to a later change.

## Findings addressed

1. A strong source match could hide a hard identifier/content/lineage conflict from another
   source already owned by the same Story. Story-level aggregation now downgrades that outcome
   to invariant-blocked review and retains the conflict evidence.
2. Reconciliation case fingerprints represented only the winning pair. They now bind the policy,
   ranked shortlist, decision, eligibility, and winning lead so runner-up changes can create a new
   durable revision.
3. Candidate retrieval and ingest evidence collection scanned all source records. The in-memory
   index now maintains work-identifier, title-token, author, and Story buckets with stale-key
   removal on upsert.
4. Fusion fingerprints included resolution timestamps, causing refresh-only rebuild churn, while
   persisted collection order used a different comparator. Timestamps are excluded from semantic
   fusion fingerprints and normalized display ordering is shared by fusion and Room round-trips.
5. Summary refreshes with omitted identifiers could erase identifiers learned from full details.
   Summary evidence is now monotonic once full metadata exists; full detail responses remain
   authoritative and can explicitly retract identifiers.
6. Reconciliation revisions were hydrated in full merely to compute a count, and queue projection
   performed per-case revision queries. COUNT and batched current-revision/count queries replace
   those paths.
7. All-Story canonical projection performed per-Story point reads. Stories, entries, identifiers,
   active generations, and provenance are now projected in bounded batch queries.
8. Canonical promotion compared only the active generation ID and reused stale sources after a
   failed promotion. Promotion now validates active generation, source fingerprints, preference
   revision, and identity revision atomically; its single retry rereads all inputs.

No Room entity/table/index was added or changed.

## Verification performed in the patch environment

- PASS: all scripts under `scripts/tests/*.sh` (32 shell policy tests).
- PASS: `scripts/verify-source-layout.sh`.
- PASS: `scripts/verify-package-boundaries.sh`.
- PASS: `scripts/verify-structural-suppressions.sh`.
- PASS: `scripts/verify-room-schema-stability.sh`.
- PASS: `scripts/verify-current-architecture.sh`.
- BLOCKED: `GRADLE_USER_HOME=/tmp/hikari-audit-gradle ./gradlew :catalog:test
  :storage:room:testDebugUnitTest --no-daemon`; the wrapper attempted to download Gradle 9.5.0
  from `services.gradle.org`, but the audit environment had no route to that host.

The blocked Gradle command is not claimed as passing.

## Local verification runbook

From the project root, with Gradle dependencies available:

```bash
./gradlew :catalog:testDebugUnitTest --no-daemon
./gradlew :storage:room:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogRepositoryTest,app.openstory.storage.room.catalog.RoomCanonicalCatalogRepositoryTest,app.openstory.storage.room.catalog.RoomCanonicalProjectionQueryTest
./scripts/verify-fast.sh
```

For the repository's complete device matrix and full gate:

```bash
./scripts/instrumentation/storage-room.sh 26
./scripts/instrumentation/storage-room.sh 37
./scripts/verify.sh
```

## Patch application

Apply from the root of the original extracted archive:

```bash
patch --dry-run -p1 < hikari-canonical-engine-audit-hardening.patch
patch -p1 < hikari-canonical-engine-audit-hardening.patch
```

If the target tree is a Git worktree, `git apply --check` and `git apply` may be used instead.
