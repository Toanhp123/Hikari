#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
macro="$root/benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt"
top_level_benchmark="$root/benchmark/src/main/kotlin/app/openstory/benchmark/HikariTopLevelCompositionBenchmark.kt"
driver="$root/benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt"
activity="$root/app/src/main/kotlin/app/openstory/MainActivity.kt"
app="$root/app/src/main/kotlin/app/openstory/ui/OpenStoryApp.kt"
nav="$root/app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt"
nav_state="$root/app/src/main/kotlin/app/openstory/navigation/AppNavigationState.kt"
persistent_nav="$root/app/src/main/kotlin/app/openstory/navigation/PersistentTopLevelNavDisplay.kt"
nav_test="$root/app/src/androidTest/kotlin/app/openstory/navigation/AppNavigationTest.kt"
checkpoint="$root/docs/internal/checkpoints/performance-wave-p6.md"
current_state="$root/docs/project/current-state.md"

fail() { echo "Performance Wave P6 policy violation: $1" >&2; exit 1; }

[[ -f "$checkpoint" ]] || fail "P6 checkpoint documentation is missing"
[[ -f "$persistent_nav" ]] || fail "production persistent top-level host is missing"
[[ ! -e "$top_level_benchmark" ]] || fail "retired P6 A/B memory helper is still present"
grep -q 'Performance Wave P6: \*\*PROMOTED TO PRODUCTION\*\*' "$current_state" || fail "repository current-state does not record P6 production promotion"
grep -q 'Status: \*\*PROMOTED TO PRODUCTION\*\*' "$checkpoint" || fail "P6 checkpoint is not closed as production"

# P6.1 is production: no benchmark-only lifetime switch may remain in the app or driver.
if grep -Rq 'persistentTopLevelCompositions' "$app" "$nav" "$activity" "$driver" "$macro"; then
  fail "benchmark-only persistentTopLevelCompositions plumbing still exists"
fi
if grep -Rq 'PERSISTENT_TOP_LEVEL_COMPOSITIONS' "$activity" "$driver"; then
  fail "retired persistent top-level benchmark intent extra still exists"
fi
grep -q 'PersistentTopLevelNavDisplay(' "$nav" || fail "AppNavHost does not use the production persistent top-level host"
if grep -q 'entries = navigator.navigationState.decoratedEntries(provider)' "$nav"; then
  fail "old single-NavDisplay production path is still present"
fi

# Production host retains only visited routes and lays out only the active route.
grep -q 'visitedTopLevelRoutes' "$persistent_nav" || fail "production host does not track visited top-level routes"
grep -q 'mutableStateListOf(navigationState.startRoute)' "$persistent_nav" || fail "production host eagerly composes more than the start route"
grep -q 'route == activeRoute || route in visitedTopLevelRoutes' "$persistent_nav" || fail "production host does not compose the active route while retaining visited routes"
grep -q 'SideEffect' "$persistent_nav" || fail "production host does not persist newly visited routes after composition"
grep -q 'key(route)' "$persistent_nav" || fail "production host does not give retained routes stable composition identity"
grep -q 'Layout(' "$persistent_nav" || fail "production host does not use the active-only layout container"
grep -q 'layoutId(route)' "$persistent_nav" || fail "production host does not identify retained layers by route"
grep -q 'measurable.layoutId == activeRoute' "$persistent_nav" || fail "production host does not restrict measurement to the active route"
if grep -q 'drawWithContent' "$persistent_nav"; then
  fail "production host regressed to draw-only suppression"
fi
grep -q 'clearAndSetSemantics' "$persistent_nav" || fail "production host leaves inactive retained routes in semantics"
grep -q 'decoratedEntriesFor' "$nav_state" || fail "navigation state does not expose per-top-level decorated entries"

# Keep one shipping regression CUJ; retire the A/B-only journeys and helper.
grep -q 'fun homeDiscoverWarm()' "$macro" || fail "production Home/Discover warm regression CUJ is missing"
if grep -Eq 'fun homeDiscoverWarmPersistent\(|fun homeDiscoverWarmMemory\(|fun homeDiscoverWarmPersistentMemory\(|measureHomeDiscoverWarmMemory' "$macro"; then
  fail "retired P6 A/B CUJs are still present"
fi

# Instrumentation locks lazy first composition, retention, and active-only measurement.
grep -q 'fun persistentTopLevelDisplayKeepsVisitedCompositionAlive()' "$nav_test" || fail "composition-lifetime instrumentation test is missing"
grep -q 'fun persistentTopLevelDisplayDoesNotMeasureInactiveVisitedRoute()' "$nav_test" || fail "active-only measurement instrumentation test is missing"
grep -q 'assertEquals(0, discoverCompositions)' "$nav_test" || fail "instrumentation does not guard against eager Discover composition"
grep -q 'assertEquals(0, discoverDisposals)' "$nav_test" || fail "instrumentation does not guard against disposing visited Discover"

echo "Performance Wave P6 policy verified."
