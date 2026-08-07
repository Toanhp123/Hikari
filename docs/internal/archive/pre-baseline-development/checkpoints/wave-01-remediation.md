# Wave 01 Remediation Checkpoint Evidence

Date: 2026-08-06

## Baseline

- Source snapshot: `Hikari-wave-04-task-03-selector-runtime.zip`
- Snapshot ZIP comment/baseline: `05bd13e15688b04f38f76940f5347086ba6d2ba8`
- Remediation scope: architecture policy, application identity, verification,
  API 26/API 37 launch gates, governance documentation

## Evidence states

Use only:

- `PASS` — command ran successfully and its output was reviewed;
- `FAIL` — command ran and failed;
- `NOT RUN` — no execution evidence exists;
- `NOT APPLICABLE` — the item does not apply, with an explanation.

## Local evidence in the review container

| Gate | State | Evidence |
|---|---|---|
| Original module-boundary symptom | PASS | Existing script rejected `:core:plugin-host -> :core:network` before remediation. |
| Pure module verifier red test | PASS | Test compilation failed because policy/verifier types were absent. |
| Pure module verifier behavior harness | PASS | Standalone Kotlin harnesses detected missing/stale modules, denied dependencies, stale production/test allowlist permissions, platform mismatch, fixture leakage, and forbidden imports; the exact current eight-module graph was accepted with zero violations. |
| Application identity red check | PASS | Static assertion failed while `com.example.hikari` remained. |
| Application identity static check | PASS | `app/build.gradle.kts` now contains `app.openstory`; production app files contain no legacy token. |
| Shell syntax | PASS | `bash -n` passed for every verification script. |
| Policy/repository static consistency | PASS | JSON parse, settings/policy module equality, exact production/test direct dependency equality, platform convention checks, forbidden import scan, YAML parse, changed-file whitespace scan, and legacy identity scan passed. |
| Verification script contracts | PASS | Fake `adb`, injected fake Gradle, and isolated checkpoint scripts proved API matching, connected-test/install invocation, launcher status, PID checks, API 26/API 37 serial propagation, execution order, mismatch failure, and missing-serial failure. |
| Gradle wrapper attempt | FAIL | Wrapper could not download Gradle 9.5.0 because the review container cannot resolve `services.gradle.org`; no Gradle task executed. |
| Target JDK 17 fast-verification attempt | FAIL | On Windows, `verifyArchitecture` compiled build-logic, then failed with `module_policy.platform_unresolved: :core`. Root cause: nested Gradle includes synthesize aggregate parent projects such as `:core` and `:test` without build scripts. The architecture snapshot now filters to projects whose concrete build file exists; rerun is required. |
| JDK 17 Gradle fast verification | NOT RUN | Review container provides JDK 21 only and has no cached Gradle 9.5 distribution. |
| API 26 instrumentation | NOT RUN | No Android emulator is attached to the review container. |
| API 37 instrumentation | NOT RUN | No Android emulator is attached to the review container. |
| GitHub Actions checkpoint | NOT RUN | Requires pushing the remediation branch to GitHub. |

## Required commands on the target repository

```bash
./scripts/verify.sh
```

```bash
ANDROID_SERIAL=<api-26-serial> ./scripts/verify-instrumentation.sh 26
ANDROID_SERIAL=<api-37-serial> ./scripts/verify-instrumentation.sh 37
```

Or with both emulators connected:

```bash
ANDROID_SERIAL_API_26=<api-26-serial> \
ANDROID_SERIAL_API_37=<api-37-serial> \
  ./scripts/verify-wave-checkpoint.sh
```

## Reviewer verdict

`NOT RUN` until JDK 17 fast verification, API 26 instrumentation, API 37
instrumentation, and the GitHub Actions checkpoint all pass on the target
branch. Wave 02 remediation must not begin before those gates are reviewed.
