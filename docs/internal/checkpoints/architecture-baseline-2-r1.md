# Architecture Baseline 2 R1 Checkpoint

Date: 2026-08-09
Status: **PASS - R1 accepted; R2 may begin**

## Environment

- Branch: `refactor/architecture-baseline-2`
- Verified implementation base HEAD: `d9e8856`
- Operating system: Windows 11 10.0 amd64
- JDK: Eclipse Temurin 17.0.20
- Gradle: 9.5.0
- Android application identity: `app.openstory`

## Accepted boundary

- The Baseline 2 target module shells are present beside the legacy modules:
  `:catalog`, `:feature:catalog`, `:storage:room`, `:plugins:api`, and
  `:plugins:runtime`.
- Module policy schema 2 supports exact legacy graphs and directional allowlists for
  target modules.
- Target source roots are guarded by package-boundary checks before product behavior is
  migrated into them.
- Generic `Outcome` ownership and stable `StoryId`/`PluginId` ownership are established
  in `:core:common`; the two explicit legacy ID aliases remain migration-scoped until R3B.
- `:catalog` remains a pure JVM shell and does not resolve the Android
  `:plugins:runtime` project during R1. The approved allowlist retains the future
  dependency direction for the R2B integration.
- Wave 06 remains frozen and no `feature/library` production slice exists.

## Verification evidence

| Command | Result | Evidence |
|---|---|---|
| `bash ./scripts/verify.sh` | PASS | Shell contract tests, structural suppression policy, package boundaries, source layout, Baseline 2 architecture, all configured JVM/Android unit tests, Detekt, lint, debug assembly, and Room schema stability completed successfully. Gradle reported `BUILD SUCCESSFUL` with 383 actionable tasks. |
| `./gradlew :plugins:api:test :catalog:test :feature:catalog:testDebugUnitTest :storage:room:testDebugUnitTest :plugins:runtime:testDebugUnitTest --stacktrace` | PASS | All R1 target module suites completed with `BUILD SUCCESSFUL`; empty foundation shells correctly reported `NO-SOURCE` where no behavior exists yet. |
| `test ! -d feature/library` | PASS | No Wave 06 Library module or production directory exists. |

The first full verification attempt exposed three previously unrecorded transitive
artifact checksums required by the new Hilt-enabled feature shell. Gradle generated the
three SHA-256 entries in `gradle/verification-metadata.xml`, the focused feature suite
passed, and the complete verification command was rerun successfully. A legacy network
timing test failed once under the first concurrent full run, then passed in isolation and
in the clean complete rerun without a production change.

The structural-suppression fixture intentionally prints unapproved and stale allowance
diagnostics while verifying that those cases fail closed. Those diagnostics are expected
test evidence and do not represent active policy violations.

## Decision

Architecture Baseline 2 R1 is accepted. Foundation ownership, module direction, and
target source boundaries are active; Wave 06 remains frozen, and the next implementation
boundary is R2 - Plugin Subsystem VNext.
