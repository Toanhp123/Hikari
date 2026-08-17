# Performance Wave P5 Interaction-Jank Checkpoint

Date: 2026-08-17
Status: **IMPLEMENTED; STATIC VERIFICATION COMPLETE; DEVICE A/B MEASUREMENT PENDING**

## Scope

P5 targets interaction spikes observed in a physical-device screen recording after the earlier structural UI cleanup. It does not reopen the design-system cleanup or add a general-purpose data cache. The wave isolates and reduces work attached directly to top-level navigation, first Story-section selection, Discover projection, Reader scroll progress, and repeated Chapter-card drawing.

## Implemented boundary

- Top-level `Discover`, `Home`, and `Library` entries opt out of Navigation 3 full-scene transitions. Focused routes such as Story, Search, and Reader keep Navigation 3 defaults. A benchmark-only intent extra restores the legacy transition path for A/B measurement. The app shell also skips `HikariBackdropHost` entirely on focused routes where floating navigation is absent, avoiding an unused full-screen backdrop capture around Story/Reader content.
- Story waits until after its first rendered frame before prewarming Chapter and Mapping section ViewModels/state collectors. Selecting a section before that frame still initializes only the selected dependency immediately.
- Chapter and Mapping state now distinguish initial loading from a real loaded-empty result, preventing false empty-state flashes while the first repository emission is pending.
- Discover owns one prepared-content pipeline: a Home emission is ranked on the injected Default dispatcher and projected from that same prepared snapshot instead of recombining the raw Home flow with a separately-emitting ranking flow.
- Discover candidate selection uses linear `minWithOrNull` scans instead of sorting complete contribution/candidate collections only to take the first item.
- Reader scroll persistence no longer reacts to viewport offsets every frame. It samples active user scroll sessions at a bounded 100 ms cadence and reports the exact final position when scrolling stops; it does not emit an initial viewport write before restored character offsets are applied. Reader chrome initializes from the persisted progress fraction, while the existing progress service remains responsible for persistence debouncing/flush.
- Reader scaled `TextStyle` instances are remembered per font scale rather than rebuilt independently for every composed block.
- Surface-shadow disabling remains benchmark-only. Production Hikari card shadows are unchanged until the paired Chapter-scroll measurements justify a visual-policy change.
- Backdrop disabling remains benchmark-only. Production Reader glass is unchanged until paired Reader-scroll measurements isolate its cost.

## Added measurement journeys

The Macrobenchmark suite adds focused A/B or warm-path journeys:

- `homeDiscoverWarm`
- `homeDiscoverLegacyTransitions`
- `storyTabSources`
- `storyTabChapters`
- `readerScrollBackdropEnabled`
- `readerScrollBackdropDisabled`
- `chaptersScrollShadowEnabled`
- `chaptersScrollShadowDisabled`

All interaction journeys continue to use `FrameTimingMetric` and the deterministic benchmark-only fixture.

## Static verification performed while producing the patch

- all repository `scripts/tests/*.sh` contracts after normalizing executable bits in the extracted ZIP workspace;
- Performance lifecycle, Wave 4, P1, P2, P3, P4, and P5 policies;
- UI token/shared-component policies;
- package-boundary, source-layout, structural-hard-policy, current-architecture, and Room-schema contract checks;
- pristine patch apply-check and whitespace check.

Focused Gradle compilation/tests were attempted in the sandbox, but the Gradle wrapper could not resolve `services.gradle.org` while downloading Gradle 9.5.0. Kotlin compilation, Roborazzi, Detekt/lint, and physical-device Macrobenchmark results therefore remain developer-machine gates.

## Required developer-machine verification

Compile and run focused tests first:

```bash
./gradlew \
  :feature:catalog:testDebugUnitTest \
  :feature:reader:testDebugUnitTest \
  :reader:testDebugUnitTest \
  :app:assembleDebug \
  recordRoborazziDebug \
  --stacktrace
```

Then run repository verification:

```bash
./scripts/verify.sh
```

Run the focused physical-device Macrobenchmarks before making any production decision about backdrop blur or repeated card shadows. Compare the paired methods on the same device/build/profile state; do not infer a winner from a single iteration.
