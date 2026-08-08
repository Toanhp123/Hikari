# Wave 05 Task 02 — Bundled Default Catalog Verification

Date: 2026-08-09
Status: **PASS — Task 02 verified; Wave 05 Task 03 may begin**

## Scope

This evidence closes Wave 05 Task 02 only. It verifies the deterministic bundled default
Catalog package, package inspection, idempotent bootstrap/install behavior, normal update
policy delegation, Android instrumentation behavior, repository-wide quality gates, and
Room schema stability. It does not accept the Wave 05 checkpoint as a whole.

## Executed evidence

| Command | Result | Evidence |
|---|---|---|
| `./gradlew :core:plugin-host:testDebugUnitTest --tests app.openstory.plugin.host.install.BundledDefaultCatalogPackageTest --stacktrace` | PASS | `BUILD SUCCESSFUL`; bundled package contract test executed. |
| `./gradlew :core:plugin-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugin.host.install.BundledPluginBootstrapperTest --stacktrace` | PASS | 5 tests finished on Android API 17 emulator target and 5 tests finished on Android 8.0.0 emulator target; `BUILD SUCCESSFUL`. |
| `./gradlew :core:plugin-host:testDebugUnitTest :core:plugin-host:connectedDebugAndroidTest --stacktrace` | PASS | Plugin-host unit/instrumentation gate completed; JavaScriptEngine-only instrumentation remains skipped on the Android 8.0.0 emulator as expected; `BUILD SUCCESSFUL`. |
| `bash ./scripts/verify.sh` | PASS | Source layout, baseline architecture, application identity, 8-module boundary verification, repository verification/lint gate, and Room schema stability completed successfully. |

## Verified Task 02 boundary

- The bundled default Catalog package is deterministic and validated through the normal
  package verifier rather than a privileged parser path.
- First-run bootstrap uses the existing installer lifecycle and repeated bootstrap is
  idempotent.
- User-disabled state remains registry-owned and is not re-enabled by bootstrap/update.
- A newer bundled package delegates to the normal update/capability-diff service; expanded
  access remains review-gated rather than being staged directly.
- The Detekt `ReturnCount` violation in `BundledPluginBootstrapper.ensurePackage()` was
  removed without changing branch behavior.
- Repository-wide verification leaves the committed Room schema export stable.

## Exit

Wave 05 Task 02 is verified. The next implementation boundary is **Wave 05 Task 03 —
deterministic catalog matching and aggregate ranking**.
