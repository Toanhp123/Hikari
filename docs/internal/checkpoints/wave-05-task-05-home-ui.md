# Wave 05 Task 05 — Combined and Catalog-Specific Home UI Verification

Date: 2026-08-09
Status: **VERIFIED**

## Scope

This evidence record covers Wave 05 Task 05 only: presentation state, combined and
catalog-specific Compose Home screens, source switching, story-navigation callbacks,
accessible card semantics, partial-refresh status, stable lazy rendering, and the cover
renderer seam. It does not accept Task 06 search/story detail or the Wave 05 checkpoint.

## Implementation evidence

- `HomeScreenState` keeps refresh/selection/report state in the presentation layer while
  Task 04 `HomeUiModel` remains an immutable cached-domain projection.
- `HomeViewModel` eagerly observes cached Home, preserves cached sections during refresh,
  prevents overlapping refresh launches, stores the latest partial refresh report, and
  keeps catalog selection independent from cached data.
- `HomeRoute` switches between combined and catalog-specific views and emits canonical
  `StoryId` navigation callbacks; it imports no Room entity, DAO, plugin contract, or
  network client.
- The existing `:feature:home` architecture rule is advanced from Task 04 domain-only
  mode by allowing Compose imports while retaining the explicit ban on database entities;
  direct project dependencies remain unchanged.
- `HomeScreen` and `CatalogHomeScreen` use vertical lazy containers with fixed-size
  horizontal story cards and stable canonical story keys.
- `HomeCard` exposes title, content type, source section, score value/scale, and score
  source in semantics without relying on color.
- Partial refresh failures remain non-blocking and explicitly state that cached content
  is still available.
- Cover rendering is an injected UI seam with a deterministic placeholder default; Task
  05 introduces no image-network dependency and performs no network access from Compose.
- Task 05 changes no Room entity, DAO, database version, or committed schema file.

## Required target verification

Run on the JDK 17 target checkout:

```bash
./gradlew :feature:home:testDebugUnitTest \
  --tests app.openstory.home.ui.HomeViewModelTest.cachedSectionsRemainVisibleDuringRefresh \
  --stacktrace
./gradlew :feature:home:testDebugUnitTest :feature:home:connectedDebugAndroidTest --stacktrace
./scripts/check-module-dependencies.sh
./scripts/verify.sh
```

Expected: all commands finish successfully, Home Compose instrumentation tests pass,
module-boundary verification remains at 10 modules, and Room schema stability is
unchanged.

## Current evidence

| Gate | Result | Note |
|---|---|---|
| Focused `HomeViewModelTest.cachedSectionsRemainVisibleDuringRefresh` | PASS | Standard dependency verification enabled; focused cached-content-during-refresh behavior passed on the target checkout. |
| Focused `HomeViewModelTest.sourceSelectionDoesNotMutateCachedHome` | PASS | Selection propagation is scheduler-synchronized in the test and cached Home remains unchanged. |
| `:feature:home:testDebugUnitTest` | PASS | Complete Task 05 Home unit suite passed. |
| `:feature:home:compileDebugAndroidTestKotlin` | PASS | Compose instrumentation source compiled successfully; the legacy `createComposeRule` deprecation is a non-blocking warning. |
| `:feature:home:connectedDebugAndroidTest` | PASS | 4 `HomeScreenTest` cases passed on `Pixel_10_Pro(AVD) - 17` after aligning Espresso with the repository Android 17-compatible version. |
| Dependency verification | PASS | Verification metadata was extended with SHA-256 entries for newly resolved Task 05 Android/Compose/test artifacts; subsequent normal builds passed without `--write-verification-metadata`. |
| `scripts/check-module-dependencies.sh` | PASS | Application identity and architecture verified for 10 modules. |
| Repository `scripts/verify.sh` | PASS | Contract scripts, source layout, baseline architecture, module boundaries, Detekt/lint, and affected repository verification all passed. |
| Room schema stability | PASS | Verification reported `Room schema export remained stable during verification.` No Room schema change is part of Task 05. |

## Exit condition

Wave 05 Task 05 is verified. Wave 05 Task 06 may begin. This record does not accept the
Wave 05 checkpoint; remaining Wave 05 work still requires its own implementation and gates.
