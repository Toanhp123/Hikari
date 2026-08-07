# Pre-MVP Baseline 1 Checkpoint

Date accepted: 2026-08-07
Status: **PASS - Baseline 1 accepted as the starting point for Wave 04 Task 03**

This record contains only observed command results. Pre-baseline developer databases,
selector fixtures, packages, and emulator installs are intentionally not migrated.

## Baseline identity

| Item | Observed value |
|---|---|
| Branch | `refactor/pre-mvp-baseline-1` |
| Implementation commit range | `923191b..635addb` |
| JDK | OpenJDK Temurin 17.0.20+8 |
| Gradle | 9.5.0 |
| Android Gradle Plugin | 9.3.0 |
| Project Kotlin plugin | 2.4.10 |
| Gradle embedded Kotlin | 2.3.20 |
| Application | version code 1 / name 1.0 |
| Room | schema 1; exported file list: `1.json` |
| Selector | `SelectorDefinition.CURRENT_SCHEMA_VERSION = 1` |
| Plugin API | major/minor compatibility, baseline major 1 |
| Repository index | schema 1 |

## Verification evidence

| Command or gate | Result | Observed evidence |
|---|---|---|
| `bash ./scripts/verify-baseline-architecture.sh` | PASS | Baseline architecture verified. |
| Focused Gradle suites for database, plugin API, network, plugin host, and test fixtures | PASS | Strict dependency verification completed with exit code 0. |
| `bash ./scripts/verify.sh` | PASS | Architecture, source layout, shell contracts, unit tests, lint, Detekt, assembly, and Room schema stability passed under JDK 17. |
| `bash ./scripts/checkpoints/database.sh` | PASS | Database instrumentation ran 18 tests on each required API level. |
| Application checkpoint invoked by `database.sh` | PASS | App instrumentation and launcher smoke passed on both required API levels. |
| Final stale architecture scan across `core`, `app`, and `sample-plugins` | PASS | No old selector runtime symbols or V1/V2 generation names matched. |
| Worktree check before recording evidence | PASS | `git status --short --branch` reported only the branch header and no changes. |

The focused module command was:

```powershell
./gradlew.bat --no-daemon --dependency-verification strict `
  :core:database:testDebugUnitTest `
  :core:plugin-api:test `
  :core:network:test `
  :core:plugin-host:test `
  :test:fixtures:test `
  --stacktrace
```

## Android evidence

| Required level | Device | Observed API | Application checkpoint | Database instrumentation |
|---|---|---:|---|---|
| API 26 | `emulator-5554` | 26 | PASS, 2 instrumentation tests plus launcher smoke | PASS, 18 tests |
| API 37 | `emulator-5556` | 37 | PASS, 2 instrumentation tests plus launcher smoke | PASS, 18 tests |

The serials were supplied through `ANDROID_SERIAL_API_26` and
`ANDROID_SERIAL_API_37`; they are environment configuration and are not committed.

## Accepted boundary

Baseline 1 now has one active Room schema, one active typed selector schema, no legacy
selector execution pipeline, neutral plugin-registry records, separated network and
installer policies, capability-named verification, no tracked IDE state, and clean
Detekt/source-layout gates.

Known next work remains Wave 04 Task 03: endpoint-wide selector evaluation budgets,
typed binding evaluation, Catalog and Content mapping, final wire DTO validation,
selector plugin adapters/factory, and cancellation/redaction checkpoint evidence.
