# Architecture Baseline 2 R3 Checkpoint

Date: 2026-08-10
Status: ACCEPTED

## Closing contract

- Shared identities: `:core:common`
- Catalog models, matching, ranking, repository, and services: `:catalog`
- Room schema 1 and catalog/plugin persistence adapters: `:storage:room`
- Legacy production modules removed: `:core:model`, `:core:matching`, `:core:database`
- Current Home, Search, Story, and app composition use the R3 catalog/storage boundary

## Remediation review

The checkpoint review found and resolved database identity, source-scoped search filters,
canonical Story ID observation, cached Home runtime isolation, coroutine cancellation,
immutable plugin-version provenance, canonical story metadata ownership, and structural
quality issues. The final review found no remaining Critical or Important findings.

## Evidence

| Gate | Result |
|---|---|
| `:core:common:test` | PASS |
| `:catalog:testDebugUnitTest` | PASS |
| `:storage:room:testDebugUnitTest` | PASS (no JVM test sources) |
| `:feature:home:testDebugUnitTest` | PASS |
| `:feature:story:testDebugUnitTest` | PASS |
| `:app:testDebugUnitTest` | PASS |
| `:storage:room:connectedDebugAndroidTest` | PASS, 11 tests |
| `:app:connectedDebugAndroidTest` | PASS; credential/live tests skipped by contract |
| `./scripts/verify.sh` | PASS |
| `./scripts/check-module-dependencies.sh` | PASS |
| `scripts/verify-package-boundaries.sh` | PASS |
| `./scripts/verify-room-schema-stability.sh` | PASS |
| `scripts/verify-structural-suppressions.sh` | PASS |
| Legacy module path assertions | PASS |

The Room schema remains one fresh exported `1.json` under the `:storage:room` schema
directory. Messages emitted by negative structural-suppression fixtures are expected;
the verification command exits successfully.
