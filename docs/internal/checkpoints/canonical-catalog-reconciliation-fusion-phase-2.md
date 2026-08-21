# Canonical Catalog Reconciliation & Fusion Engine — Phase 2 Checkpoint

Date: 2026-08-21
Status: **PATCHED — VERIFICATION OPEN**
Scope: implementation-plan Tasks 12–21 only. Phase 3 / Task 22 remains blocked until the Phase-2 developer-checkout gates below are reviewed green.

## Normative sources

- Design: `../../superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`
- Plan: `../../superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`
- Prior accepted evidence: `canonical-catalog-reconciliation-fusion-phase-1.md`

## Patched implementation boundary

### Task 12 — provider-agnostic Fusion quality facts

- `FUSION_POLICY_VERSION = 1` and `PRIMARY_SELECTION_POLICY_VERSION = 1` are explicit host policy versions.
- Source usability, freshness, metadata level, exact nine-field primary coverage, and stable `SourceKey` tie-breaking are explicit facts/comparators; enum ordinal and provider-specific weights are not policy inputs.
- `CatalogSourceAvailabilityResolver` derives objective runtime availability/freshness from registry/metadata-policy facts and optional caller failure classification without plugin-ID rules.

### Task 13 — primary selection, hysteresis, and Story-level source preference

- `CatalogFusionEngine` chooses the initial AUTO primary by the quality vector and deterministic `SourceKey` ordering.
- The five normative hysteresis rules are encoded explicitly; one-field coverage/tie churn does not replace a still-eligible current primary.
- PINNED preference selects a usable pinned source, preserves an unavailable pin while using a temporary effective fallback, and naturally returns to the pin when it becomes eligible again.
- Preference writes increment the revision and coalesce one `FUSION_REBUILD` work item without mutating raw source evidence.

### Task 14 — field-specific fusion and immutable provenance

- Primary-oriented scalar, cover, normalized-union, publication-status, latest-update, and score strategies are split into focused helpers.
- Latest-update timestamp/opaque label always come from one source record.
- Canonical score is a normalized unweighted mean over qualified sources; provider weights are absent.
- Field provenance stores the exact contributor `SourceKey` plus the source `fusionFingerprint` used for the candidate.

### Task 15 — validation, promotion, suppression, retention, and retry

- `CanonicalGenerationValidator` rejects Story/source/provenance/content-type/score/title contradictions before promotion.
- `CanonicalFusionService` is local-evidence only and implements the Phase-1 `CanonicalGenerationRebuilder` port.
- Generation IDs are deterministic from Story/time/fingerprint; unchanged meaningful canonical state is suppressed rather than creating visible churn.
- Promoted/unchanged foreground rebuilds clear coalesced `FUSION_REBUILD` work; Preparing/failure leaves work available for retry.
- Promotion remains repository-atomic, previous successful generation retention is bounded, and promotion races reread/retry once.
- App DI binds the concrete Fusion rebuilder/bootstrap; feature modules do not own the engine.

### Task 16 — canonical projection/read API

- `CatalogStoryProjection` is generated from `CanonicalStoryState.Ready`, including canonical status/score semantics.
- Room projection observation reads active canonical generation/provenance rather than rebuilding presentation from raw `catalog_entries`.
- Multi-Story observation is scoped to requested Story IDs; Preparing Stories are omitted instead of falling back to raw provider presentation.

### Task 17 — Story canonical presentation with raw-source inspection

- Story AUTO presentation comes from canonical state/generation only.
- Raw provider records remain visible for explicit source inspection and refresh; they are not an alternate canonical presentation authority.
- PIN/AUTO actions persist preference then request a rebuild; refresh remains source-scoped and rebuilds only after source metadata refresh succeeds.
- Historical Story IDs resolve through the Phase-1 identity/redirect boundary.

### Task 18 — Search Summary persistence and canonical cards

- Search commits provider Summary facts through durable Catalog persistence before presentation.
- Durable `SourceKey -> StoryId` ownership returned by persistence is authoritative; matcher state is refreshed from persisted ownership after each commit so later providers cannot continue using a temporary proposed StoryId.
- Canonical bootstrap/rebuild runs from local committed evidence; Search cards consume canonical projection/presentation only.
- Result selection is navigation-only and does not invoke Details/network enrichment.

### Task 19 — Discover feed semantics plus canonical presentation

- Home Summary/feed kinds continue to own Popular/Latest/Top-Rated membership and ranking semantics.
- Title/cover/status/score presentation is joined from canonical projections keyed by `StoryId`; the old feature-local provider presentation order no longer owns these fields.
- Discover has no Details loader, metadata coordinator, Fusion engine, or Fusion service dependency for card repair.
- The Performance Wave-4 policy gate was updated narrowly to encode this new contract while preserving feed/ranking and no-Details constraints.

### Task 20 — shared projection consumers

- Library consumes canonical projection title/cover/publication-status/score; a cross-source regression locks that behavior.
- Home/Downloads/Updates already consume `CatalogStoryProjectionRepository`; Task 16 therefore cuts those steady read paths over centrally without introducing feature-local source selection.
- Canonical score remains normalized in the domain and is converted to a `/10` value only at presentation formatting boundaries.

### Task 21 — canonical consistency/read-path guard

- `CanonicalPresentationConsistencyTest` uses one canonical generation fixture and checks shared title/cover/status/score semantics across Story/Search/Discover/Library.
- Reflection guards fail if participating feature ViewModels acquire `CatalogFusionEngine` or `CanonicalFusionService` dependencies.
- The steady read path is constrained to active canonical generation/projection; raw source inspection is intentionally excluded from cross-feature presentation equality.

## Sandbox verification evidence

Fresh checks on the Phase-2 working tree reported:

```text
git diff --check
  PASS

source ./scripts/verification-common.sh
run_repository_static_gates
  PASS
  Current architecture: 14 production modules, 1 android-test module, Room schema 1..9

./scripts/verify-room-schema-stability.sh
  PASS
  a5e9f89676504c9a5596c314e72d2216bede87d5e449a71c55a8f502e28ef6a6
```

Supplementary compile/test-oriented checks performed during implementation also compiled the pure Catalog/Fusion boundary and selected Room adapters with minimal framework stubs, and exercised the pure Fusion policy/engine/validator, Fusion service, and Search service test fixtures. Those are useful implementation evidence but **do not replace Gradle or device acceptance**.

A fresh sandbox Gradle attempt remains blocked before project execution because the wrapper cannot resolve the distribution host:

```text
./gradlew :catalog:testDebugUnitTest --offline
  Downloading https://services.gradle.org/distributions/gradle-9.5.0-bin.zip
  java.net.UnknownHostException: services.gradle.org
```

Therefore this checkpoint deliberately does **not** claim the Phase-2 Gradle, connected, macrobenchmark, Detekt/lint/full-verify acceptance gates.

## Required developer-checkout verification

Run these after applying the Phase-2 patch:

```bash
./gradlew   :catalog:testDebugUnitTest   :feature:catalog:testDebugUnitTest   :app:testDebugUnitTest

./gradlew :storage:room:connectedDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCanonicalCatalogRepositoryTest,app.openstory.storage.room.catalog.RoomCatalogRepositoryTest   --stacktrace

./gradlew :app:connectedDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.AppLaunchSmokeTest,app.openstory.navigation.AppNavigationTest   --stacktrace

./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest   '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#homeDiscoverHome'   --stacktrace

./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest   '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#storyTabs'   --stacktrace

./scripts/verify.sh
```

Record benchmark JSON/Perfetto paths if both macrobenchmarks run. If read-path performance regresses beyond normal device variance, inspect traces for repeated source reads/Fusion on steady UI paths before changing correctness policy.

## Explicit non-claims

This patch does **not** implement or enable:

- Phase 3 reconciliation policy/cases/executor behavior beyond the Phase-1 persistence foundation;
- automatic/destructive Story graph merge, merge reversal, or user Review UI;
- WorkManager/background canonical scheduling beyond existing durable work contracts;
- any Room migration beyond accepted schema 9;
- provider-ID weights, provider confidence, or feature-owned metadata Fusion;
- hidden Details enrichment for Search/Discover presentation.

## Closure condition

Phase 2 remains **PATCHED — VERIFICATION OPEN**. Do not start Task 22 until the developer-checkout Phase-2 unit, Room connected, navigation smoke, both specified macrobenchmarks, and canonical `./scripts/verify.sh` evidence have been reviewed. After those are green, update this checkpoint/spec/plan to **VERIFIED — PHASE 2 CLOSED** and make Task 22 the active next step.
