# Pre-MVP Baseline 1 Checkpoint

Date opened: 2026-08-07
Status: **IN PROGRESS - final project-wide checkpoint not yet executed**

This record is updated only from observed command output. Task 15 freezes the final
commit range and changes this status after both required Android API levels pass.

## Baseline identity

| Item | Value |
|---|---|
| Branch | `refactor/pre-mvp-baseline-1` |
| Current recorded range | `923191b..a943282` |
| JDK | OpenJDK 17.0.20 |
| Gradle / AGP / Kotlin | 9.5 / 9.3.0 / 2.4.10 |
| Application | version code 1 / name 1.0 |
| Room | schema 1; only `1.json` active |
| Selector | schema 1 |
| Plugin API | major/minor compatibility, baseline major 1 |
| Repository index | schema 1 |

## Evidence recorded so far

| Command or gate | Result | Notes |
|---|---|---|
| Initial shared `scripts/verify.sh` preflight | PASS | Run before implementation changes. |
| Database unit/schema verification | PASS | New schema 1 structurally compared with the former complete development schema. |
| Database instrumentation on API 26 | PASS | 18 tests on `emulator-5554`. |
| `:core:plugin-api:test` and Detekt during contract/layout tasks | PASS | Canonical selector contract and source layout. |
| `:core:plugin-host:test` and Detekt for document loader | PASS | Loader limits, cancellation, and diagnostics covered. |
| `:core:plugin-api:test :core:plugin-host:test :test:fixtures:test` | PASS | Canonical package fixture and inspector verification. |
| `scripts/tests/verify-baseline-architecture-test.sh` | PASS | Temporary pass/fail fixture contract. |
| `scripts/verify-baseline-architecture.sh` | PASS | Current repository architecture. |
| Focused `ArchitectureSmokeTest` | PASS | Shared verification integration. |
| Final shared verification after all refactor tasks | NOT RUN | Required by Task 15. |
| Database instrumentation on API 37 | NOT RUN | Required by Task 15. |
| Application checkpoint smoke on API 26 and API 37 | NOT RUN | Required by Task 15. |

## Remaining checkpoint work

- complete project-wide ownership, network, installer, verification, and documentation cleanup;
- run all shell contract tests and strict shared Gradle verification under JDK 17;
- run database and application checkpoint scripts on API 26 and API 37;
- record final commit range, device IDs, command results, and clean-worktree evidence.

Known next feature work after approval remains Wave 04 Task 03: typed selector binding
evaluation, Catalog/Content mapping, final DTO validation, and plugin adapters.
