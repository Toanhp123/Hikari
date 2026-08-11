# Design System Foundation Checkpoint

Date: 2026-08-12
Status: ACCEPTED

## Boundary

Wave 09 verified graph + :core:designsystem

## Production module count

14

## Scope verified

- architecture boundary
- HikariTheme and tokens
- loading/empty/error/offline primitives
- snackbar host
- confirmation/destructive confirmation
- app root migration
- feature:catalog migration
- feature:reader migration
- no screen redesign

## Architecture review

- :core:designsystem project dependencies: none
- capability -> design-system edges: none
- presentation -> design-system edges: app, feature:catalog, feature:reader
- design-system domain/capability imports: none
- Room schema: unchanged
- plugin protocol/runtime: unchanged
- app instrumentation isolation: AndroidX Test Orchestrator, test-only

## Commands

| Command | Environment | Result |
| --- | --- | --- |
| `git status --short` | JDK 17 host | PASS - clean before checkpoint creation |
| `git log -10 --oneline` | JDK 17 host | PASS - task/remediation history reviewed through `342cc26` |
| `java -version` | JDK 17 host | PASS - Temurin 17.0.20 |
| `.\gradlew.bat --version --console=plain` | JDK 17 host | PASS - Gradle 9.5.0, launcher JVM 17.0.20 |
| `.\gradlew.bat :core:designsystem:assembleDebug :core:designsystem:lintDebug :feature:catalog:testDebugUnitTest :feature:reader:testDebugUnitTest :app:testDebugUnitTest --stacktrace --console=plain` | JDK 17 host | PASS |
| `bash scripts/check-module-dependencies.sh` | JDK 17 host | PASS - exact 14-module policy accepted |
| `bash scripts/verify-current-architecture.sh` | JDK 17 host | PASS - 14 modules, Room schemas 1..6 |
| `bash scripts/verify.sh` | JDK 17 host | PASS - architecture, unit tests, lint, Detekt, assemble, and Room schema stability |
| `.\gradlew.bat :core:designsystem:connectedDebugAndroidTest :feature:catalog:connectedDebugAndroidTest :feature:reader:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-configuration-cache --stacktrace --console=plain` | API 26 | PASS - design system 10, catalog 23, reader 5, app 0 failures; JavaScript/live tests skipped where unsupported or disabled |
| `bash scripts/instrumentation/android.sh 26` | API 26 | PASS - launcher `Status: ok`, pid 6756 |
| `.\gradlew.bat :core:designsystem:connectedDebugAndroidTest --no-configuration-cache --stacktrace --console=plain` | API 37 | PASS - 10/10 |
| `.\gradlew.bat :feature:catalog:connectedDebugAndroidTest --no-configuration-cache --stacktrace --console=plain` | API 37 | PASS - 23/23 |
| `.\gradlew.bat :feature:reader:connectedDebugAndroidTest --no-configuration-cache --stacktrace --console=plain` | API 37 | PASS - 5/5 |
| `.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache --stacktrace --console=plain` | API 37 | PASS - 0 failures; two live-network tests skipped by normal configuration |
| `bash scripts/instrumentation/android.sh 37` | API 37 | PASS - launcher `Status: ok`, pid 7246 |
| `bash scripts/verify.sh` after checkpoint creation | JDK 17 host | PASS - full gate exit 0 and Room schema remained stable |

API 37 module instrumentation was run sequentially to avoid known UTP/ADB contention. App tests use AndroidX Test Orchestrator because the AndroidX JavaScript sandbox permits only one isolated process per application process; per-test process isolation prevents Compose lifecycle cancellation from contaminating later plugin contract tests.

## Manual behavior-preservation review

- Home: API 37 light and dark surfaces remained readable; Search, Refresh, catalog switching, cached cards, and story selection remained present.
- Search: API 37 navigation opened the existing `Search catalogs` surface; source identity, filters, result actions, and partial-failure behavior were preserved by the passing catalog instrumentation suite.
- Library: API 37 showed the existing status/sort controls and the true-empty copy `Your Library is empty.` through the shared empty presentation.
- Story: API 37 retained Berserk metadata, source details, Refresh details ownership, and the same destination structure; cached-content failure behavior remains non-blocking in instrumentation coverage.
- Mapping: API 37 retained Reading sources, Find reading sources, URL import, and Resolve URL; approval/rejection and protected mapping actions passed instrumentation.
- Chapter list: API 37 retained unread count, filters, unavailable toggle, and Download visible; populated row expansion/download actions and the empty copy passed instrumentation.
- Reader: deep source review confirmed the valid-document `ReaderContent` branch and lifecycle progress flushing remain unchanged; API 26/API 37 instrumentation passed loading, content, source switching, retry, and navigation assertions.
- top-level navigation: API 37 device review navigated Home, Library, and Plugins; instrumentation also passed destination replacement and activity recreation behavior.
- Theme readability: light and dark Home surfaces were inspected on API 37, with readable content, controls, cards, and navigation in both modes.
- Product flow: no new destination, gesture, domain mapping, or feature-owned consequence was introduced.

## Next boundary

Wave 10
