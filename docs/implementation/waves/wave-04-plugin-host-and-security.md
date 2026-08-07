<!--
DOCUMENT LIFECYCLE
Status: ACTIVE WAVE / PARTIALLY IMPLEMENTED
Current repository note: Tasks 01–02 and bounded Selector V1 runtime are present. Selector V2 runtime remains active; use ../wave-04-selector-v2-runtime.md for Task 03 continuation.
Canonical execution status: ../../project/current-state.md
Original planning text below is preserved rather than retroactively rewritten.
-->

# Wave 04 — Plugin Host and Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Install and execute declarative/JavaScript plugins through host-owned capabilities with strict domain, resource, update, and diagnostic controls.

**Architecture:** Immutable plugin versions are verified and staged transactionally. All network access passes through an allowlisted gateway. Selector plugins use a bounded interpreter; JavaScript plugins use isolated capability messages. The registry controls activation/update/rollback and diagnostics remain redacted.

**Tech Stack:** AndroidX JavaScriptEngine 1.1.0, OkHttp adapter, HTML parser adapter, Room plugin state, coroutines, cryptographic checksums/signatures.

## Global Constraints

- Android-only MVP; no account, cloud sync, remote chapter service, or push backend.
- Package namespace: `app.openstory`.
- Minimum SDK: 26. Compile and target SDK: 37.
- Build runtime: JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0.
- Language/UI: Kotlin 2.4.10, Jetpack Compose BOM 2026.06.00, Navigation 3 version 1.1.4.
- Persistence/background: Room 2.8.4 and WorkManager 2.11.2.
- Concurrency/serialization: Kotlin coroutines 1.11.0 and kotlinx.serialization 1.11.0.
- Dependency injection: Hilt 2.60.1.
- JavaScript plugins execute only through AndroidX JavaScriptEngine 1.1.0 with host-controlled capabilities.
- Catalog metadata and readable content remain separate plugin responsibilities.
- Reading progress belongs to `CanonicalChapter`; exact `ChapterRelease` and reader position are also retained.
- No native-code plugins, unrestricted filesystem access, arbitrary Android APIs, or undeclared network domains.
- Every persistence change needs a migration test; every plugin contract needs deterministic fixtures.
- TDD is mandatory: demonstrate the focused failure, implement the smallest behavior, run focused tests, then run the module suite.
- Commit after each task. Do not combine tasks across checkpoints.
- Any deterministic `*Fixture`, fake, or test assertion helper shown in a test block is created in that task’s listed test file or `:test:fixtures`; it must not call live websites.


## Role of This Wave

This wave turns inert public plugin packages into controlled executable adapters. Security boundaries are implemented and tested before Home or Library depends on community code.

## Entry Dependencies

- Wave 03 checkpoint is approved.
- Plugin API/package schemas and contract test fixtures are stable.
- Room plugin/version/diagnostic tables exist.

## Exit Deliverables

- Allowlisted network gateway.
- Transactional installer and registry.
- Declarative selector runtime.
- JavaScript capability sandbox.
- Update/rollback service.
- Redacted diagnostics and unified host facade.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Implement allowlisted HTTP gateway with response and rate budgets

**Files:**
- Create: core/network/build.gradle.kts
- Create: core/network/src/main/kotlin/app/openstory/network/PluginHttpGateway.kt
- Create: core/network/src/main/kotlin/app/openstory/network/AllowlistedHttpGateway.kt
- Create: core/network/src/main/kotlin/app/openstory/network/RequestBudget.kt
- Create: core/network/src/main/kotlin/app/openstory/network/RedactingNetworkLogger.kt
- Test: core/network/src/test/kotlin/app/openstory/network/AllowlistedHttpGatewayTest.kt

**Interfaces:**
- Consumes: Plugin manifest host allowlist, typed `AppResult`, clock/dispatcher primitives.
- Produces: Host-owned network capability that enforces HTTPS hosts, redirect rules, byte/time/request budgets, user-agent policy, and safe diagnostics.

**Acceptance:**
- A redirect to an undeclared host is denied before its body is read.
- Compressed and decompressed body ceilings are enforced.
- Cookies are provided only by the host session store for the exact plugin/host.
- Logs exclude query strings, authorization headers, cookies, and response bodies.

**Implementation notes:**
- Use a dedicated OkHttp client with redirects disabled and process redirects manually through the allowlist.
- Implement per-plugin token bucket limits and a per-operation request ceiling.
- Decode text with declared/content-type charset and cap decoded characters before selector/JS runtime receives them.

- [ ] **Step 1: Write the failing test**

Create `core/network/src/test/kotlin/app/openstory/network/AllowlistedHttpGatewayTest.kt`:

```kotlin
package app.openstory.network

import app.openstory.plugin.api.PluginManifest
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class AllowlistedHttpGatewayTest {
    @Test fun redirectToUndeclaredHostIsDenied() = runTest {
        val server = MockWebServer().apply { enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://evil.invalid/x")); start() }
        val result = testGateway(allowedHosts = setOf(server.hostName)).get(server.url("/").toString())
        assertEquals("plugin.domain_denied", result.errorCode())
        server.shutdown()
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:network:test --tests app.openstory.network.AllowlistedHttpGatewayTest.redirectToUndeclaredHostIsDenied
```

Expected: **FAIL** because the network capability and redirect policy are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/network/src/main/kotlin/app/openstory/network/PluginHttpGateway.kt`:

```kotlin
package app.openstory.network

import app.openstory.common.AppResult

data class PluginHttpRequest(val url: String, val headers: Map<String, String> = emptyMap())
data class PluginHttpResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray)

interface PluginHttpGateway {
    suspend fun execute(request: PluginHttpRequest, budget: RequestBudget): AppResult<PluginHttpResponse>
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:network:test --tests app.openstory.network.AllowlistedHttpGatewayTest.redirectToUndeclaredHostIsDenied
./gradlew :core:network:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/network/build.gradle.kts core/network/src/main/kotlin/app/openstory/network/PluginHttpGateway.kt core/network/src/main/kotlin/app/openstory/network/AllowlistedHttpGateway.kt core/network/src/main/kotlin/app/openstory/network/RequestBudget.kt core/network/src/main/kotlin/app/openstory/network/RedactingNetworkLogger.kt core/network/src/test/kotlin/app/openstory/network/AllowlistedHttpGatewayTest.kt
git commit -m "network: add allowlisted plugin http gateway"
```

### Task 2: Install and register plugin packages transactionally

**Files:**
- Create: core/plugin-host/build.gradle.kts
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/PluginInstaller.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/PackageVerifier.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/registry/PluginRegistry.kt
- Create: core/database/src/main/kotlin/app/openstory/database/repository/PluginStateRepository.kt
- Test: core/plugin-host/src/test/kotlin/app/openstory/plugin/host/install/PluginInstallerTest.kt

**Interfaces:**
- Consumes: Package schemas/validator, Room plugin state tables, filesystem adapter, hashing/signature verifier.
- Produces: Atomic installer that verifies bytes before extraction, stages immutable versions, records provenance, and switches active version only after validation.

**Acceptance:**
- Failed install leaves no active or partially extracted version.
- Package bytes are hashed before parsing untrusted archive metadata.
- Installed files are private to the app and read-only after activation.
- Registry exposes enabled/disabled state and current/previous version.

**Implementation notes:**
- Never extract directly into the active directory. Use `staging/<random>` then atomic rename to `plugins/<id>/<version>`.
- Store trust decision, signer fingerprint, source URL/repository ID, and accepted capability set.
- Prevent downgrade unless it is an explicit rollback to an already installed immutable version.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/install/PluginInstallerTest.kt`:

```kotlin
package app.openstory.plugin.host.install

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class PluginInstallerTest {
    @Test fun invalidChecksumLeavesRegistryUnchanged() = runTest {
        val fixture = installerFixture(expectedChecksum = "00")
        fixture.installer.install(fixture.validPackageBytes)
        assertNull(fixture.registry.find("community.fixture"))
        fixture.assertNoStagingFiles()
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-host:test --tests app.openstory.plugin.host.install.PluginInstallerTest.invalidChecksumLeavesRegistryUnchanged
```

Expected: **FAIL** because installer, package staging, and registry are missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/PluginInstaller.kt`:

```kotlin
package app.openstory.plugin.host.install

import app.openstory.common.AppResult

class PluginInstaller(
    private val verifier: PackageVerifier,
    private val storage: PluginPackageStorage,
    private val registry: MutablePluginRegistry,
) {
    suspend fun install(request: InstallRequest): AppResult<InstalledPlugin> {
        return verifier.verify(request).flatMap { verified ->
            storage.stage(verified).flatMap { staged ->
                registry.activate(staged).tapFailure { storage.remove(staged.location) }
            }
        }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-host:test --tests app.openstory.plugin.host.install.PluginInstallerTest.invalidChecksumLeavesRegistryUnchanged
./gradlew :core:plugin-host:test :core:database:testDebugUnitTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/plugin-host/build.gradle.kts core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/PluginInstaller.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/PackageVerifier.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/registry/PluginRegistry.kt core/database/src/main/kotlin/app/openstory/database/repository/PluginStateRepository.kt core/plugin-host/src/test/kotlin/app/openstory/plugin/host/install/PluginInstallerTest.kt
git commit -m "plugin-host: add transactional package installer"
```

### Task 3: Execute declarative selector plugins through bounded host operations

**Files:**
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorRuntime.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorInterpreter.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/HtmlDocumentAdapter.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/TransformRegistry.kt
- Test: core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/SelectorRuntimeTest.kt

**Interfaces:**
- Consumes: Validated selector schema and allowlisted HTTP gateway.
- Produces: Deterministic selector interpreter with operation, document-size, node-count, regex, and wall-clock budgets that returns plugin wire DTOs.

**Acceptance:**
- Only schema-declared operations execute.
- CSS selectors operate on a host parser document, not WebView JavaScript.
- Malformed nodes produce typed field errors without crashing the app.
- The runtime can be cancelled and releases parser/network resources.

**Implementation notes:**
- Use Jsoup or an equivalent non-WebView parser behind `HtmlDocumentAdapter`; feature/domain modules never depend on it.
- Normalize source-relative URLs through the scoped gateway before returning them.
- Record operation index and field path in diagnostics, not raw page contents.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/SelectorRuntimeTest.kt`:

```kotlin
package app.openstory.plugin.host.selector

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectorRuntimeTest {
    @Test fun runtimeExtractsCardsWithinDeclaredHost() = runTest {
        val runtime = selectorFixture("<article><a href='/n/1'>Novel</a></article>")
        val cards = runtime.executeSearch("Novel").value()
        assertEquals("Novel", cards.single().title)
        assertEquals("1", cards.single().sourceId)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-host:test --tests app.openstory.plugin.host.selector.SelectorRuntimeTest.runtimeExtractsCardsWithinDeclaredHost
```

Expected: **FAIL** because selector operations have no executable host interpreter.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorInterpreter.kt`:

```kotlin
package app.openstory.plugin.host.selector

class SelectorInterpreter(
    private val http: ScopedPluginHttp,
    private val parser: HtmlDocumentAdapter,
    private val transforms: TransformRegistry,
    private val limits: SelectorLimits,
) {
    suspend fun execute(plan: SelectorPlan, input: Map<String, String>): SelectorValue {
        require(plan.operations.size <= limits.maxOperations)
        return plan.operations.fold<SelectorOperation, SelectorValue>(SelectorValue.Input(input)) { value, operation ->
            limits.checkpoint()
            operation.apply(value, http, parser, transforms)
        }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-host:test --tests app.openstory.plugin.host.selector.SelectorRuntimeTest.runtimeExtractsCardsWithinDeclaredHost
./gradlew :core:plugin-host:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorRuntime.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/SelectorInterpreter.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/HtmlDocumentAdapter.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/selector/TransformRegistry.kt core/plugin-host/src/test/kotlin/app/openstory/plugin/host/selector/SelectorRuntimeTest.kt
git commit -m "plugin-host: execute declarative selector plugins"
```

### Task 4: Execute JavaScript plugins inside capability-based sandbox

**Files:**
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/js/JavaScriptPluginRuntime.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/js/JsBridgeProtocol.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/js/JsCapabilityDispatcher.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/js/JsRuntimeLimits.kt
- Test: core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/js/JavaScriptPluginRuntimeTest.kt
- Create: docs/plugin-sdk/javascript-runtime.md

**Interfaces:**
- Consumes: AndroidX JavaScriptEngine, plugin contracts, scoped HTTP gateway, cancellation and budget primitives.
- Produces: Isolate-backed JavaScript runtime where scripts communicate only through validated JSON-RPC-like host capability messages.

**Acceptance:**
- No Java/Kotlin object, Context, filesystem path, Room handle, cookie store, or raw WebView is exposed to script.
- Every bridge message validates method, size, schema, call ID, and plugin capability.
- Timeout/cancellation terminates or discards the isolate and fails only that plugin operation.
- Returned DTOs pass the same host validators as selector plugins.

**Implementation notes:**
- Create one isolate per operation initially; introduce pooling only after benchmarks prove startup cost is material.
- Set maximum source bytes, input/output JSON bytes, host calls, response bytes, and operation duration.
- Hash script bytes in diagnostics so repeated failures can be correlated without logging source code.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/js/JavaScriptPluginRuntimeTest.kt`:

```kotlin
package app.openstory.plugin.host.js

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JavaScriptPluginRuntimeTest {
    @Test fun scriptCannotCallUndeclaredHost() = runTest {
        val runtime = jsFixture(allowedHosts = setOf("allowed.example"))
        val result = runtime.invoke("search", "return host.http({url:'https://evil.invalid'})")
        assertEquals("plugin.domain_denied", result.errorCode())
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugin.host.js.JavaScriptPluginRuntimeTest
```

Expected: **FAIL** because the isolate runtime and capability bridge do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/js/JsCapabilityDispatcher.kt`:

```kotlin
package app.openstory.plugin.host.js

class JsCapabilityDispatcher(
    private val http: ScopedPluginHttp,
    private val limits: JsRuntimeLimits,
) {
    suspend fun dispatch(message: JsBridgeRequest): JsBridgeResponse {
        limits.validateMessage(message)
        return when (message.method) {
            "http.execute" -> http.execute(message.decodeHttp()).toBridgeResponse(message.id)
            "log.safe" -> JsBridgeResponse.ok(message.id, Unit)
            else -> JsBridgeResponse.error(message.id, "plugin.capability_denied")
        }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugin.host.js.JavaScriptPluginRuntimeTest
./gradlew :core:plugin-host:test :core:plugin-host:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/plugin-host/src/main/kotlin/app/openstory/plugin/host/js/JavaScriptPluginRuntime.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/js/JsBridgeProtocol.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/js/JsCapabilityDispatcher.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/js/JsRuntimeLimits.kt core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/js/JavaScriptPluginRuntimeTest.kt docs/plugin-sdk/javascript-runtime.md
git commit -m "plugin-host: add javascript capability sandbox"
```

### Task 5: Implement update policy, capability-diff confirmation, and rollback

**Files:**
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/update/PluginUpdateService.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/update/CapabilityDiff.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/update/PluginRollbackService.kt
- Create: core/database/src/main/kotlin/app/openstory/database/repository/PluginVersionRepository.kt
- Test: core/plugin-host/src/test/kotlin/app/openstory/plugin/host/update/PluginUpdateServiceTest.kt

**Interfaces:**
- Consumes: Transactional installer, registry, package/repository format, and plugin update modes.
- Produces: Update checker that respects manual/ask/automatic modes, blocks silent permission expansion, retains previous version, and can atomically rollback.

**Acceptance:**
- Automatic update proceeds only when signer/trust lineage matches and capabilities/domains do not expand.
- Ask mode returns a review model containing changelog, new domains, new capabilities, and signer change.
- Rollback restores prior active version without deleting current bytes until successful.
- Running operations finish on their pinned version; activation affects subsequent operations.

**Implementation notes:**
- Keep at least one previous successfully activated version; storage policy may retain more when small.
- Never auto-update an unsigned package to bytes from a different origin without review.
- Run contract smoke tests against the staged version before activation.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/update/PluginUpdateServiceTest.kt`:

```kotlin
package app.openstory.plugin.host.update

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginUpdateServiceTest {
    @Test fun automaticModeStopsOnNewDomain() = runTest {
        val fixture = updateFixture(oldHosts = setOf("a.example"), newHosts = setOf("a.example", "b.example"))
        val result = fixture.service.applyAvailableUpdate(UpdateMode.AUTOMATIC)
        assertEquals(UpdateDecision.NEEDS_REVIEW, result.decision)
        assertEquals(setOf("b.example"), result.capabilityDiff.addedHosts)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-host:test --tests app.openstory.plugin.host.update.PluginUpdateServiceTest.automaticModeStopsOnNewDomain
```

Expected: **FAIL** because update policy and capability diff are not implemented.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/update/CapabilityDiff.kt`:

```kotlin
package app.openstory.plugin.host.update

import app.openstory.plugin.api.PluginManifest

data class CapabilityDiff(
    val addedHosts: Set<String>,
    val removedHosts: Set<String>,
    val addedCapabilities: Set<String>,
    val signerChanged: Boolean,
) {
    val expandsAccess: Boolean get() = addedHosts.isNotEmpty() || addedCapabilities.isNotEmpty() || signerChanged
    companion object {
        fun between(old: PluginManifest, new: PluginManifest, signerChanged: Boolean) = CapabilityDiff(
            new.allowedHosts - old.allowedHosts,
            old.allowedHosts - new.allowedHosts,
            new.capabilities.map { it.name }.toSet() - old.capabilities.map { it.name }.toSet(),
            signerChanged,
        )
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-host:test --tests app.openstory.plugin.host.update.PluginUpdateServiceTest.automaticModeStopsOnNewDomain
./gradlew :core:plugin-host:test :core:database:testDebugUnitTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/plugin-host/src/main/kotlin/app/openstory/plugin/host/update/PluginUpdateService.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/update/CapabilityDiff.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/update/PluginRollbackService.kt core/database/src/main/kotlin/app/openstory/database/repository/PluginVersionRepository.kt core/plugin-host/src/test/kotlin/app/openstory/plugin/host/update/PluginUpdateServiceTest.kt
git commit -m "plugin-host: add safe updates and rollback"
```

### Task 6: Persist safe plugin diagnostics and expose host health state

**Files:**
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/diagnostics/PluginDiagnostic.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/diagnostics/PluginDiagnosticsRepository.kt
- Create: core/database/src/main/kotlin/app/openstory/database/dao/PluginDiagnosticDao.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/PluginHost.kt
- Test: core/plugin-host/src/test/kotlin/app/openstory/plugin/host/diagnostics/PluginDiagnosticsRepositoryTest.kt

**Interfaces:**
- Consumes: Plugin registry/runtimes, typed errors, clock, and Room plugin diagnostic table.
- Produces: Unified host facade and bounded redacted diagnostic history for install, execution, sync, auth, update, and rollback events.

**Acceptance:**
- Diagnostics contain plugin/version/operation/error code/duration/time but no cookie, query, chapter body, or page HTML.
- History is capped per plugin and globally.
- Repeated failures produce health state HEALTHY/DEGRADED/DISABLED_BY_USER, never silent global disable.
- One plugin failure never throws through a batch caller.

**Implementation notes:**
- Use structured safe fields rather than sanitizing arbitrary exception strings after the fact.
- Expose exportable diagnostics JSON only after a user action; omit plugin session state and story identifiers by default.
- Record response status categories and retry-after duration, not response bodies.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/diagnostics/PluginDiagnosticsRepositoryTest.kt`:

```kotlin
package app.openstory.plugin.host.diagnostics

import kotlin.test.Test
import kotlin.test.assertFalse

class PluginDiagnosticsRepositoryTest {
    @Test fun diagnosticRedactsSensitiveDetails() {
        val diagnostic = PluginDiagnostic.fromFailure(
            pluginId = "fixture", operation = "chapter",
            code = "network.http_401", unsafeDetail = "Cookie: token=secret https://a.example/x?q=private",
        )
        val serialized = diagnostic.toString()
        assertFalse("secret" in serialized)
        assertFalse("q=private" in serialized)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-host:test --tests app.openstory.plugin.host.diagnostics.PluginDiagnosticsRepositoryTest.diagnosticRedactsSensitiveDetails
```

Expected: **FAIL** because diagnostic persistence and unified host facade are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/PluginHost.kt`:

```kotlin
package app.openstory.plugin.host

import app.openstory.model.PluginId
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.content.ContentPlugin

interface PluginHost {
    suspend fun catalog(id: PluginId): HostedPlugin<CatalogPlugin>
    suspend fun content(id: PluginId): HostedPlugin<ContentPlugin>
    suspend fun enabledCatalogs(): List<HostedPlugin<CatalogPlugin>>
    suspend fun enabledContentSources(): List<HostedPlugin<ContentPlugin>>
}

data class HostedPlugin<T>(val id: PluginId, val version: String, val instance: T)
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-host:test --tests app.openstory.plugin.host.diagnostics.PluginDiagnosticsRepositoryTest.diagnosticRedactsSensitiveDetails
./gradlew :core:plugin-host:test :core:database:testDebugUnitTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/plugin-host/src/main/kotlin/app/openstory/plugin/host/diagnostics/PluginDiagnostic.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/diagnostics/PluginDiagnosticsRepository.kt core/database/src/main/kotlin/app/openstory/database/dao/PluginDiagnosticDao.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/PluginHost.kt core/plugin-host/src/test/kotlin/app/openstory/plugin/host/diagnostics/PluginDiagnosticsRepositoryTest.kt
git commit -m "plugin-host: add redacted diagnostics and host facade"
```

## Wave Checkpoint

Do not begin `2026-08-03-05-catalog-home-and-discovery.md` until every item below is demonstrated on a clean checkout:

- [ ] Fixture selector and JavaScript plugins return the same contract DTOs.
- [ ] Undeclared host, traversal archive, oversized body, timeout, and invalid bridge message tests pass.
- [ ] A failed update leaves the previous plugin usable.
- [ ] Plugin diagnostics contain no fixture secret strings.
- [ ] Host batch calls isolate failures to the offending plugin.

## Full Verification

```bash
./gradlew clean testDebugUnitTest lintDebug --stacktrace
```

Expected: **BUILD SUCCESSFUL**, no ignored failing tests, no unresolved lint errors, and no generated database schema drift.

## Review Packet

Attach to the checkpoint review:

- Commit range for this wave.
- Focused test output for every task.
- Full verification output.
- Any deliberate deviations from the approved design, with rationale and updated spec text.
- Screenshots or screen recordings only when the wave changes visible UI.
