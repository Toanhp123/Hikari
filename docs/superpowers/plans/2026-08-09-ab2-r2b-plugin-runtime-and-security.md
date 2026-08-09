# Architecture Baseline 2 R2B - Plugin Runtime and Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the bounded JavaScript runtime, persistence SPI, host capabilities, package lifecycle, built-in provisioning, update and rollback behind the R2A protocol.

**Architecture:** `:plugins:runtime` is the isolated security subsystem. It owns one-isolate-per-operation execution, validated capability messages, HTTP/HTML/log capabilities, managed-credential injection, detached package verification, immutable package storage, built-in provisioning, and redacted diagnostics; it does not depend on catalog or UI.

**Tech Stack:** AndroidX JavaScriptEngine 1.1.0, OkHttp 5.3.0, Jsoup 1.22.2, Bouncy Castle 1.84, coroutines 1.11.0, JVM + Android tests.

## Global Constraints

- Architecture source of truth: `docs/superpowers/specs/2026-08-09-architecture-baseline-2-design.md`.
- This work is pre-Wave-06; do not implement Library, chapter sync, Reader, downloads, background sync, authentication, notifications, or release-hardening behavior.
- Android-native Kotlin remains fixed.
- Package namespace/application ID remains `app.openstory`.
- Minimum SDK remains 26; compile/target SDK remain 37 unless a dedicated architecture decision changes them.
- Build runtime remains JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0, Kotlin 2.4.10.
- Current retained libraries may be replaced only when a plan task explicitly does so; do not change versions opportunistically during this reset.
- Pre-MVP compatibility is intentionally breakable. Do not add permanent `Legacy*`, `Compat*`, `V1/V2` adapters, dual mappers, or Room migrations merely to preserve development-only contracts.
- Temporary migration-scoped bridges are allowed only when this plan names the bridge and its deletion task explicitly.
- Package-first, Gradle-module-second: do not create extra production modules beyond the approved target graph without a new architecture decision.
- TDD is mandatory for behavior changes: focused RED -> smallest GREEN -> affected module suite -> commit.
- Every task ends in a buildable, testable, independently reviewable repository state.
- Do not make a checkpoint green with `TODO()`, `error("not implemented")`, unconditional empty production results, or broad structural suppressions.
- Tests protect revalidated product/security invariants, not historical class shapes.
- Production Room entities/DAOs stay private to the storage adapter.
- Production plugin JavaScript receives only host-controlled capabilities and never Android `Context`, Room, filesystem paths, raw OkHttp, reflection, or plaintext managed credentials.

---
## Entry / Exit Contract

Entry: R2A accepted.

Exit:
- the vNext runtime can verify/install/activate/rollback immutable JS packages;
- runtime output is validated by the R2A protocol before leaving the security boundary;
- undeclared hosts, capability abuse, timeout/cancellation, oversized output and secret leakage are covered by deterministic tests;
- built-in packages go through the same verifier/installer as community packages;
- the legacy plugin host remains active for product flows until R2C performs the consumer cutover.

R2B does **not** close R2 and does not create compatibility adapters around old plugin contracts.

### Task 1: Create runtime facade, typed runtime failures, and persistence SPI

**Files:**
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/PluginRuntime.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/PluginCallResult.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/InstalledPlugin.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/persistence/PluginStateStore.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/persistence/PluginDiagnosticsSink.kt`
- Create: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/PluginRuntimeContractTest.kt`

**Interfaces:**
- Produces:

```kotlin
interface PluginRuntime {
    suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement>

    suspend fun enabled(service: PluginService): List<InstalledPlugin>
}

sealed interface PluginCallResult<out T> {
    data class Success<T>(val value: T) : PluginCallResult<T>
    data class Failure(
        val code: String,
        val retryable: Boolean,
        val safeDetail: String? = null,
    ) : PluginCallResult<Nothing>
}

data class InstalledPlugin(
    val pluginId: PluginId,
    val version: String,
    val services: Set<PluginService>,
)

data class StoredPluginVersion(
    val version: String,
    val packageLocation: String,
    val sha256: String,
    val signerFingerprint: String?,
)

data class StoredPluginState(
    val pluginId: PluginId,
    val services: Set<PluginService>,
    val enabled: Boolean,
    val activeVersion: StoredPluginVersion,
    val previousVersion: StoredPluginVersion?,
    val acceptedNetworkHosts: Set<String>,
)

interface PluginStateStore {
    suspend fun find(pluginId: PluginId): StoredPluginState?
    suspend fun all(): List<StoredPluginState>
    suspend fun replace(state: StoredPluginState)
}

interface PluginDiagnosticsSink {
    suspend fun record(event: PluginDiagnosticEvent)
    suspend fun recent(pluginId: PluginId, limit: Int): List<PluginDiagnosticEvent>
}

data class PluginDiagnosticEvent(
    val pluginId: PluginId,
    val code: String,
    val operation: String?,
    val occurredAtEpochMillis: Long,
    val safeDetail: String? = null,
)
```

`StoredPluginState` exposes no Room type. `PluginDiagnosticEvent` is redacted by construction: it carries no URL query, headers, cookies, response bodies, JavaScript source, or throwable message.

- [ ] **Step 1: Write RED tests**

Create `PluginRuntimeContractTest.kt` with a local `FakePluginStateStore` and a minimal facade implementation that filters state:

```kotlin
class PluginRuntimeContractTest {
    @Test fun enabledCatalogExcludesDisabledAndContentOnlyPlugins() = runTest {
        val states = listOf(
            storedState("org.example.catalog", enabled = true, services = setOf(PluginService.CATALOG)),
            storedState("org.example.disabled", enabled = false, services = setOf(PluginService.CATALOG)),
            storedState("org.example.content", enabled = true, services = setOf(PluginService.CONTENT)),
        )
        val runtime = stateOnlyRuntime(FakePluginStateStore(states))

        assertEquals(
            listOf(PluginId("org.example.catalog")),
            runtime.enabled(PluginService.CATALOG).map { it.pluginId },
        )
    }

    @Test fun failureStringContainsOnlySafeFields() {
        val failure = PluginCallResult.Failure(
            code = "plugin.execution_failed",
            retryable = false,
            safeDetail = "operation failed",
        )
        val rendered = failure.toString()
        assertFalse("secret-cookie" in rendered)
        assertFalse("https://source.example/path?q=secret" in rendered)
        assertTrue("plugin.execution_failed" in rendered)
    }
}
```

The fixture helpers live in this test file; production code must not acquire a test-only state implementation.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :plugins:runtime:testDebugUnitTest   --tests app.openstory.plugins.runtime.PluginRuntimeContractTest   --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Implement contracts only**

Do not implement JavaScript execution yet. Add deterministic fake state store inside the test file.

- [ ] **Step 4: Run runtime local tests**

```bash
./gradlew :plugins:runtime:testDebugUnitTest --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime   plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime
git commit -m "plugins: add runtime facade and persistence spi"
```

### Task 2: Implement capability broker with HTTP, HTML, logging, and managed credentials

**Files:**
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/CapabilityBroker.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginHttpCapability.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginRequestPolicy.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/ManagedCredentialProvider.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/BoundedResponseReader.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/html/HtmlCapability.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/log/SafePluginLogger.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginHttpCapabilityTest.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/capabilities/html/HtmlCapabilityTest.kt`

**Interfaces:**
- `ManagedCredentialProvider.headers(pluginId, host)` returns host-managed headers; plugin script never receives the secret source/value directly.
- HTTP capability revalidates every redirect and applies compressed/decompressed/time/request budgets.
- HTML capability accepts body text + selector request and returns bounded text/attribute results only.

- [ ] **Step 1: Port security tests first**

Write focused tests equivalent to current invariants:

```kotlin
@Test fun redirectToUndeclaredHostFailsClosed()
@Test fun managedCredentialIsSentButNeverReturnedToPlugin()
@Test fun responseBodyOverBudgetFailsBeforeDecode()
@Test fun htmlQueryCapsResultCount()
```

Use MockWebServer for HTTP tests and static HTML fixtures for Jsoup tests.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :plugins:runtime:testDebugUnitTest   --tests '*PluginHttpCapabilityTest'   --tests '*HtmlCapabilityTest'   --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Implement bounded capabilities**

Port the good behavior from current `core/network` and selector HTML adapter, but do not copy their architecture. `CapabilityBroker` dispatches only published methods:

```text
http.execute
html.query
log.safe
```

Unknown methods return `plugin.capability_denied`.

- [ ] **Step 4: Run focused + module tests**

```bash
./gradlew :plugins:runtime:testDebugUnitTest --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities   plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/capabilities
git commit -m "plugins: add bounded host capabilities"
```

### Task 3: Implement isolated JavaScript operation execution

**Files:**
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/JavaScriptEngine.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/AndroidxJavaScriptEngine.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/PluginOperationRunner.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/BridgeMessage.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/RuntimeLimits.kt`
- Test: `plugins/runtime/src/androidTest/kotlin/app/openstory/plugins/runtime/execution/PluginOperationRunnerTest.kt`

**Interfaces:**
- One isolate per invocation.
- Script contract is one nested object whose property path exactly matches `PluginOperation.wireName`:

```javascript
globalThis.openstoryPlugin = {
  catalog: {
    home: async (input) => { /* returns CatalogHomeOutput JSON */ },
    search: async (input) => { /* returns CatalogSearchOutput JSON */ },
    details: async (input) => { /* returns CatalogDetailsOutput JSON */ },
    filters: async (input) => { /* returns CatalogFiltersOutput JSON */ },
  },
  content: {
    search: async (input) => { /* returns content protocol JSON */ },
    story: async (input) => { /* returns content protocol JSON */ },
    chapters: async (input) => { /* returns content protocol JSON */ },
    chapter: async (input) => { /* returns content protocol JSON */ },
  },
}
```

`PluginOperation.CATALOG_HOME.wireName == "catalog.home"` is resolved by traversing `openstoryPlugin.catalog.home`; the runtime never evaluates a caller-provided property path.

The runtime injects exactly this capability surface into each isolate:

```javascript
globalThis.host = {
  http: async (request) => bridgeCall("http.execute", request),
  html: {
    query: async (request) => bridgeCall("html.query", request),
  },
  log: async (event) => bridgeCall("log.safe", event),
}
```

`bridgeCall` is host bootstrap code, not plugin-provided code; plugins cannot replace the native dispatcher or call unpublished capability method names.
- Host calls are JSON messages routed through `CapabilityBroker`.
- Output bytes are bounded first, then `PluginProtocolValidator.validateOutput(operation, payload, manifest.capabilities.network.hosts)` must return no violations before `PluginRuntime.invoke` returns `Success`. Catalog decodes only already schema/host-validated `JsonElement` values.
- `PluginOperationRunner` receives `PluginDiagnosticsSink` and records only redacted `PluginDiagnosticEvent` values for validation/runtime failures. A test marker placed in thrown JavaScript text, request query, or credential value must never appear in `safeDetail`.

- [ ] **Step 1: Write Android RED tests**

```kotlin
@Test fun undeclaredHostCallReturnsDomainDenied()
@Test fun unknownHostCapabilityMethodReturnsCapabilityDenied()
@Test fun timeoutDiscardsOnlyCurrentIsolate()
@Test fun oversizedOutputIsRejected()
```

- [ ] **Step 2: Verify RED on emulator/device**

```bash
./gradlew :plugins:runtime:connectedDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugins.runtime.execution.PluginOperationRunnerTest   --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Implement execution bridge**

The bridge must validate call ID, method, payload bytes and capability before execution. Cancellation must rethrow coroutine cancellation and dispose the isolate.

- [ ] **Step 4: Run local + Android runtime suites**

```bash
./gradlew :plugins:runtime:testDebugUnitTest   :plugins:runtime:connectedDebugAndroidTest --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution   plugins/runtime/src/androidTest/kotlin/app/openstory/plugins/runtime/execution
git commit -m "plugins: execute isolated javascript operations"
```

### Task 4: Rebuild package verify/install/update/rollback around detached provenance

**Files:**
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/PackageArchiveInspector.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/PackageVerifier.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/PluginInstaller.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/PluginPackageStorage.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/TransactionalPluginPackageStorage.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/BundledPluginSource.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/AndroidBundledPluginSource.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/BundledPluginProvisioner.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntime.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/update/PluginUpdateService.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/update/PluginRollbackService.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/install/PackageVerifierTest.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/install/PluginInstallerTest.kt`
- Test: `plugins/runtime/src/androidTest/kotlin/app/openstory/plugins/runtime/install/BundledPluginProvisionerTest.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/update/PluginRollbackServiceTest.kt`

**Interfaces:**
- `PackageVerifier.verify(bytes, artifactProvenance)` hashes bytes before archive parsing.
- Archive rejects absolute paths, `..`, symlinks, duplicate names, excessive entry count/expanded bytes, missing `manifest.json`/`main.js`, and any `selector.json`.
- Installer stages then atomically activates; rollback only selects an already verified immutable installed version.
- Built-in packages use the same verifier/installer as community packages:

```kotlin
data class BundledPluginDescriptor(
    val assetPath: String,
    val pluginId: String,
    val version: String,
    val sha256: String,
)

fun interface BundledPluginSource {
    suspend fun packages(): List<BundledPluginPackage>
}

class BundledPluginProvisioner(
    private val source: BundledPluginSource,
    private val installer: PluginInstaller,
    private val updates: PluginUpdateService,
    private val state: PluginStateStore,
) {
    suspend fun ensureProvisioned(): PluginCallResult<Unit>
}
```

`AndroidBundledPluginSource` reads only descriptor-pinned asset paths and verifies the detached SHA-256 before constructing an install request. `BundledPluginProvisioner` installs a missing built-in, upgrades an older built-in through normal update policy, and never downgrades a newer installed version.

`DefaultPluginRuntime` is created in this task after package storage exists. `enabled(service)` first calls `BundledPluginProvisioner.ensureProvisioned()`, then reads enabled state; `invoke(...)` resolves the immutable active package, loads `main.js` through `PluginPackageStorage`, and delegates to `PluginOperationRunner`.

- [ ] **Step 1: Port RED security/lifecycle tests**

Create focused tests with these concrete assertions (reuse fixture builders inside each test file, not production helpers):

```kotlin
@Test fun checksumMismatchLeavesStateUntouched() = runTest {
    val fixture = installFixture(detachedSha256 = "0".repeat(64))
    assertIs<PluginCallResult.Failure>(fixture.installer.install(fixture.packageBytes))
    assertNull(fixture.state.find(fixture.pluginId))
    fixture.assertStagingEmpty()
}

@Test fun selectorEntryIsRejected() = runTest {
    val fixture = installFixture(extraEntries = mapOf("selector.json" to "{}".encodeToByteArray()))
    assertEquals("plugin.package_layout_invalid", fixture.installer.install(fixture.packageBytes).failureCode())
}

@Test fun rollbackRestoresPreviousImmutableVersion() = runTest {
    val fixture = updateFixture(installedVersions = listOf("1.0.0", "2.0.0"), active = "2.0.0")
    fixture.updates.rollback(fixture.pluginId)
    assertEquals("1.0.0", fixture.state.find(fixture.pluginId)!!.activeVersion.version)
}

@Test fun bundledProvisionerNeverDowngradesNewerInstalledVersion() = runTest {
    val fixture = bundledProvisionerFixture(installed = "3.0.0", bundled = "2.0.0")
    fixture.provisioner.ensureProvisioned()
    assertEquals("3.0.0", fixture.state.find(fixture.pluginId)!!.activeVersion.version)
}
```

The same test classes also include traversal, failed-activation cleanup, capability-expansion review, and normal verifier/installer bootstrap assertions using their respective fixture data.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :plugins:runtime:testDebugUnitTest \
  --tests app.openstory.plugins.runtime.install.PluginInstallerTest \
  --tests app.openstory.plugins.runtime.update.PluginUpdateServiceTest \
  --tests app.openstory.plugins.runtime.install.BundledPluginProvisionerTest \
  --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Implement smallest vNext lifecycle**

Reuse cryptographic primitives where safe, but rewrite types around the new manifest/provenance and persistence SPI. Implement the package reader required by `DefaultPluginRuntime` as a bounded `readEntry(pluginId, version, "main.js")`; callers never receive arbitrary filesystem paths.

- [ ] **Step 4: Run runtime tests**

```bash
./gradlew :plugins:runtime:testDebugUnitTest --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install   plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/update   plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/install   plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/update
git commit -m "plugins: rebuild transactional package lifecycle"
```

