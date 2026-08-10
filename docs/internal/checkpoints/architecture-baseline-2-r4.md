# Architecture Baseline 2 R4 Checkpoint

Date: 2026-08-10
Status: ACCEPTED

## Closing contract

- Home, Search, and Story presentation: `:feature:catalog`
- Catalog models, repository, matching, ranking, and focused services: `:catalog`
- App composition: Hilt modules in `:app`; no manual application graph or ViewModel factories
- Navigation: canonical routes with NavEntry-scoped Hilt ViewModels
- Story route identity: `StoryId` only; plugin/source identity remains catalog state
- Accepted module graph: 8 modules; legacy `:feature:home` and `:feature:story` removed

## Remediation review

The final deep review found and resolved fresh Search-to-Story resolution, immediate
search cancellation, reachable Story detail refresh, boundary exception containment,
lifecycle-aware state collection, read-only Search state, independent filter/search
failure ownership, and cached Home retention across observation failures. The final
review found no remaining Critical or Important findings.

## Evidence

| Gate | Result |
|---|---|
| `:catalog:testDebugUnitTest` | PASS, 26 tests |
| `:feature:catalog:testDebugUnitTest` | PASS, 21 tests |
| `:app:testDebugUnitTest` | PASS, 18 tests |
| `:feature:catalog:connectedDebugAndroidTest` | PASS, 10 tests on API 26 and API 37 |
| `:app:connectedDebugAndroidTest` | PASS, 8 tests on API 26 and API 37; MAL live/contract tests skipped by contract where unavailable |
| `:app:assembleDebug` | PASS |
| `:build-logic:test` and `detekt` | PASS |
| `./scripts/verify.sh` | PASS |
| Architecture verification | PASS, 8 modules |
| Structural suppression policy | PASS, no retained allowance |
| Legacy presentation/composition removal assertions | PASS |

Room schema export remained stable. Messages emitted by negative structural-suppression
fixtures are expected; the verification command exits successfully.
