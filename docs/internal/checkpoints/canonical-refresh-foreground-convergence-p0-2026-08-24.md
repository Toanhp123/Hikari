# Canonical refresh foreground convergence P0 — 2026-08-24

## Device evidence

A fresh Discover refresh produced 1,221 canonical decision traces for 271 Stories over 56.163
seconds. MangaUpdates accounted for 246 Stories and 49.791 seconds; MyAnimeList accounted for
25 Stories and 5.745 seconds. The largest inter-event gap was 571 ms, so the dominant delay was
continuous per-Story reconciliation/fusion rather than one long provider stall.

## Change

> Superseded for Discover by the 2026-09-05 performance recovery design. Discover refresh now
> commits provider data and defers all canonical convergence to the durable queue; visible-story
> settlement proceeds independently after refresh. Other foreground paths retain this checkpoint's
> policy.

- Home fetch and Room commits remain complete and deterministic.
- The original Discover policy selected only its visible semantic set: 5 popular, 9 latest updates,
  and 5 top-rated Stories.
- Under the superseding policy, no Discover Story converges synchronously before refresh returns.
- Remaining evidence is coalesced by Story into durable reconciliation/fusion work and scheduled
  once for the existing WorkManager drain.
- Room persists the deferred work batch in one transaction; no entity, table, index, or migration
  changed.
- Details, review, merge, and other correctness-sensitive foreground event paths keep their prior
  synchronous behavior.

## Scale regression coverage

- Catalog orchestration test: 1,000 changed Stories execute zero foreground reconciliation/fusion,
  create one durable batch, and schedule one drain.
- Room instrumentation test: 1,000 work requests persist and are claimable through the batch
  repository contract.
- Discover ViewModel test: a fresh Discover refresh selects zero immediate Stories.

## Verification

The repository static gates and every script under `scripts/tests/*.sh` pass in the patch
environment. Gradle compilation is not claimed here because the environment cannot create the
default wrapper cache and has no local Gradle 9.5.0 distribution.

Run on a development machine from the project root:

```bash
./gradlew :catalog:testDebugUnitTest :feature:catalog:testDebugUnitTest \
  :storage:room:testDebugUnitTest --no-daemon

./gradlew :storage:room:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCanonicalEngineStateTest

./scripts/verify-fast.sh
```

Then clear app data and repeat the Discover trace to compare spinner duration and the number of
foreground `CanonicalDecision` lines.
