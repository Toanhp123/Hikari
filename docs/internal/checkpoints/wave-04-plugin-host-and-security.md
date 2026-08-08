# Wave 04 Plugin Host and Security Checkpoint

Date: 2026-08-08
Status: **PASS - Wave 04 accepted; Wave 05 Task 01 may begin**

## Reviewed boundary

- Branch: `feature/wave-04-task-06-diagnostics-host`
- Verified implementation HEAD: `8b10b27`
- Pull request: `#5`
- Wave implementation range: `origin/master..8b10b27`

## Acceptance evidence

| Requirement | Result | Evidence |
|---|---|---|
| Selector and JavaScript fixtures return the same contract DTO | PASS | `PluginContractParityTest.selectorAndJavaScriptFixturesReturnSameCatalogDetailsContract` |
| Undeclared host, traversal archive, oversized body, timeout, and invalid bridge messages fail closed | PASS | `PluginUrlPolicyTest`, `AllowlistedHttpGatewayTest`, `PackageVerifierTest`, `ZipPackageArchiveInspectorLimitsTest`, `JavaScriptPluginRuntimeTest`, and `JsBridgeProtocolTest` |
| Failed update or rollback leaves the previous plugin usable | PASS | `PluginUpdateServiceTest.failedSmokeTestDiscardsPreparedVersionWithoutActivation` and `PluginRollbackServiceTest.failedActivationKeepsCurrentVersionAndInstalledBytes` |
| Diagnostics contain no fixture secrets | PASS | `PluginDiagnosticsRepositoryTest.diagnosticRedactsSensitiveDetails` |
| Batch callers isolate failures to the offending plugin | PASS | `PluginDiagnosticsRepositoryTest.batchHostCallsSkipOnlyFailingPluginAndRecordDiagnostic` |

## Verification runs

| Command | Result |
|---|---|
| `./scripts/verify-source-layout.sh` | PASS - `Source layout verified.` |
| `./scripts/verify.sh` | PASS - both Gradle verification stages reported `BUILD SUCCESSFUL` |
| `./gradlew clean testDebugUnitTest lintDebug --stacktrace` | PASS - `BUILD SUCCESSFUL`, 138 actionable tasks |
| `./gradlew :core:plugin-host:testDebugUnitTest --tests app.openstory.plugin.host.PluginContractParityTest` | PASS |
| `./gradlew :core:plugin-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugin.host.js.JavaScriptPluginRuntimeInstrumentedTest --stacktrace` | PASS on Android 17 / API 37 - 1 test, 0 failures, 0 errors, 0 skipped |

The Android instrumentation test uses real Binder/WebView callback time through
`runBlocking`; it does not advance coroutine virtual time past the sandbox timeout.

## Decision

All Wave 04 checkpoint requirements are demonstrated against the verified implementation
HEAD. Wave 04 is closed, and the repository execution boundary advances to Wave 05
Task 01: catalog ingestion repository and canonical merge boundary.
