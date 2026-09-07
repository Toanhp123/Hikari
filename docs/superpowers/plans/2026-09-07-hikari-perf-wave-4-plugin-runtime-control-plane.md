# Hikari Performance Wave 4 — Plugin Runtime Control Plane Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove plugin control-plane executable/package over-read, terminal-failure retry amplification, and duplicate secure-session/Keystore work without weakening plugin isolation, capability review, authentication, or transient retry semantics.

**Architecture:** Split immutable manifest metadata from executable `main.js` loading, make bundled provisioning descriptor-first and failure-aware, add point authentication-policy access, and consume one coherent secure-session snapshot per authenticated request. Keep JavaScript isolate lifecycle unchanged in this wave; isolate reuse and decrypted credential caching remain evidence-gated risks after structural waste is removed.

**Tech Stack:** Kotlin, Coroutines, existing `PluginRuntime`/package storage, Android Assets, Android Keystore, Hilt, JVM tests, Android instrumentation tests, existing repository verification scripts.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`

## Global Constraints

- Preserve plugin capability/network/authentication validation and sandbox boundaries.
- Never auto-approve capability expansion to avoid `NEEDS_REVIEW` work.
- `PluginStateStore` remains authoritative for enabled state and active package identity on every public runtime operation.
- Cache only immutable package metadata/content or terminal outcomes whose invalidation key is explicit.
- Retryable/transient failures and cancellation must remain retryable; no permanent suppression of an unknown/transient storage failure.
- Do not introduce long-lived plaintext credential caching in this wave.
- Do not pool/reuse JavaScript isolates in this wave.
- The wave must preserve current plugin operation results, login/logout behavior, credential-generation invalidation, and update security semantics.

---

### Task 1: Introduce one immutable manifest resolver shared by runtime and authentication policy

**Files:**
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/packageinfo/PluginPackageIdentity.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/packageinfo/InstalledPluginManifestResolver.kt`
- Create: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/packageinfo/InstalledPluginManifestResolverTest.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntime.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntimePerformanceTest.kt`

**Interfaces:**

```kotlin
data class PluginPackageIdentity(
    val pluginId: PluginId,
    val version: String,
    val packageLocation: String,
    val sha256: String,
)

interface InstalledPluginManifestResolver {
    suspend fun resolve(stored: StoredPluginState): PluginCallResult<PluginManifest>
}
```

The production resolver reads only `manifest.json`, single-flights concurrent reads for one identity, caches successful immutable identities by `pluginId`, and may retain only explicitly nonretryable identity-bound failures. Retryable failures/cancellation are never permanently cached.

- [ ] **Step 1: Write resolver RED tests** for: two reads of the same immutable package perform one `manifest.json` read; concurrent callers share one read; a changed `(version, packageLocation, sha256)` performs a new read; the old identity does not remain the selected cache entry.
- [ ] **Step 2: Add failure-classification RED tests**: a fake storage `Failure(code, retryable = true)` is attempted again on the next call; a deterministic nonretryable malformed/missing-manifest result may be reused only for the same immutable identity; changing identity retries.
- [ ] **Step 3: Run** `./gradlew :plugins:runtime:testDebugUnitTest --tests '*InstalledPluginManifestResolverTest*' --tests '*DefaultPluginRuntimePerformanceTest*' --no-daemon` and verify RED because the resolver does not exist and runtime still owns package-wide loading.
- [ ] **Step 4: Extract `PluginPackageIdentity`** from the private `DefaultPluginRuntime` representation into `packageinfo`; identity must include plugin ID, version, package location, and SHA-256 and must not include mutable enabled state.
- [ ] **Step 5: Implement the resolver** with one cache slot + one in-flight mutex/deferred per installed plugin identity. Preserve `PluginCallResult.Failure.retryable`; do not use the existing `valueOrNull()` flattening pattern to decide terminal caching.
- [ ] **Step 6: Refactor `DefaultPluginRuntime` constructor** to consume the resolver. Do not yet change public `enabled(operation)` behavior beyond compiling against the shared manifest boundary; Task 2 owns lazy executable separation.
- [ ] **Step 7: Run focused tests** plus `:plugins:runtime:testDebugUnitTest`.
- [ ] **Step 8: Self-review** cache cardinality and invalidation: at most installed-plugin cardinality plus completion-bounded in-flight state; active state remains read from `PluginStateStore` each call.
- [ ] **Step 9: Commit** `perf(plugin): add immutable manifest resolver`.

---

### Task 2: Make operation discovery manifest-only and load `main.js` only for invocation

**Files:**
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntime.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/packageinfo/PluginExecutableSourceResolver.kt`
- Create: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/packageinfo/PluginExecutableSourceResolverTest.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntimePerformanceTest.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/PluginRuntimeContractTest.kt`

**Interfaces:**

```kotlin
interface PluginExecutableSourceResolver {
    suspend fun resolve(stored: StoredPluginState): PluginCallResult<String>
}
```

`enabled(operation)` consumes only `InstalledPluginManifestResolver`. `invoke(...)` resolves the manifest, verifies support, then resolves `main.js`. Executable success/terminal failure caching is keyed by the same immutable package identity; transient retryable failure is not permanently cached.

- [ ] **Step 1: Add RED characterization to `DefaultPluginRuntimePerformanceTest`** with a counting `PluginPackageStorage`: first and repeated `enabled(PluginOperation.CONTENT_SEARCH)` may read `manifest.json` but must report **zero** `main.js` reads; `invoke(...)` then reads `main.js` exactly once for the active identity.
- [ ] **Step 2: Add RED tests** for multiple candidate plugins with large fake scripts and assert operation discovery byte count is independent of aggregate `main.js` bytes `JsB`.
- [ ] **Step 3: Add identity/failure tests**: version change reloads manifest+script as needed; nonretryable script failure is stable only for the unchanged identity; retryable script failure retries; disabled state is never cached by the source resolver.
- [ ] **Step 4: Run** `./gradlew :plugins:runtime:testDebugUnitTest --tests '*DefaultPluginRuntimePerformanceTest*' --tests '*PluginExecutableSourceResolverTest*' --tests '*PluginRuntimeContractTest*' --no-daemon` and verify RED on the `main.js` discovery count.
- [ ] **Step 5: Implement `PluginExecutableSourceResolver`** and remove `LoadedPluginPackage(manifest, script)` as the mandatory discovery unit. Manifest and executable source must have separate load paths/caches.
- [ ] **Step 6: Refactor `enabled(operation)`** to `manifestResolver.resolve(stored)` + `manifest.supports(operation)` only.
- [ ] **Step 7: Refactor `invokeStored()`** to resolve manifest first, reject unsupported operation, then resolve `main.js` and call the existing `PluginOperationRunner` unchanged.
- [ ] **Step 8: Preserve result/error semantics** for not-installed, disabled, unsupported operation, malformed manifest, missing script, cancellation, and active-version change.
- [ ] **Step 9: Run full plugin-runtime JVM tests** and compare existing operation contract results.
- [ ] **Step 10: Commit** `perf(plugin): separate manifest discovery from executable loading`.

---

### Task 3: Make bundled provisioning descriptor-first and terminal-failure aware

**Files:**
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/BundledPluginSource.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/AndroidBundledPluginSource.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/BundledPluginProvisioner.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/install/BundledPluginProvisionerTest.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntimePerformanceTest.kt`
- Modify: `scripts/tests/performance-wave-p3-policy-test.sh`

**Interfaces:**

```kotlin
interface BundledPluginSource {
    suspend fun descriptors(): List<BundledPluginDescriptor>
    suspend fun loadPackage(descriptor: BundledPluginDescriptor): PluginCallResult<BundledPluginPackage>
}
```

Provisioning has one completion-bounded in-flight owner and per-bundled-plugin outcome keyed by descriptor identity plus the installed state relevant to the update decision. Required semantic states are `Satisfied`, terminal/user-action-required, and transient retryable; exact internal class names may differ.

- [ ] **Step 1: Rewrite/add source tests** so listing descriptors reads no `.osp` bytes; `loadPackage(descriptor)` reads only the exact descriptor asset, validates the descriptor path, and returns the same provenance bytes as today.
- [ ] **Step 2: Add RED provisioner tests**: an already-current or newer installed version performs zero package-byte reads; missing/update-needed plugin loads exactly one package; peer plugins remain independently processed.
- [ ] **Step 3: Add RED `NEEDS_REVIEW` test**: first automatic provisioning loads/verifies the update and returns `plugin.update_needs_review`; a second unchanged `ensureProvisioned()` returns the same terminal outcome without reading/reverifying `.osp`; a changed relevant installed state or descriptor identity causes reevaluation.
- [ ] **Step 4: Preserve and strengthen retry tests**: a `retryable=true` load/install/update failure is single-flight for overlapping callers but a later call retries; owner cancellation cannot leave a stale in-flight marker; terminal invalid package does not retry until identity changes.
- [ ] **Step 5: Run** `./gradlew :plugins:runtime:testDebugUnitTest --tests '*BundledPluginProvisionerTest*' --tests '*DefaultPluginRuntimePerformanceTest*' --no-daemon` and verify RED against eager `packages()` and success-only memoization.
- [ ] **Step 6: Implement descriptor-first source/provisioning**. Compare descriptor version/current state before calling `loadPackage`; do not read every bundled asset to decide that no update is needed.
- [ ] **Step 7: Replace global `provisioningSucceeded` semantics** with outcome ownership that allows one terminal plugin to remain stable while peers are satisfied. Keep one in-flight attempt owner for concurrent callers and `NonCancellable` cleanup/publication.
- [ ] **Step 8: Bind terminal outcome invalidation** to descriptor identity + relevant installed state. Cheap `state.find(pluginId)` comparison is allowed; repeated `.osp` read/verification is not.
- [ ] **Step 9: Update the historical P3 policy script**: remove implementation-specific `provisioningSucceeded` requirement and replace it with structural checks for descriptor-first source, single-flight cleanup, explicit terminal/retryable outcome handling, manifest-only discovery, and bounded identity caches. Keep all unrelated P3 guardrails intact.
- [ ] **Step 10: Run `bash scripts/tests/performance-wave-p3-policy-test.sh`** plus plugin runtime tests.
- [ ] **Step 11: Self-review security**: no capability expansion is auto-approved, signatures/checksum/package verifier still run whenever bytes are actually applied, and a terminal memo never bypasses verification for a new descriptor identity.
- [ ] **Step 12: Commit** `perf(plugin): make bundled provisioning failure-aware and descriptor-first`.

---

### Task 4: Add point authentication-policy access through the shared manifest resolver

**Files:**
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/auth/PluginSessionService.kt`
- Modify: `app/src/main/kotlin/app/openstory/plugins/runtime/auth/InstalledPackageAuthenticationPolicySource.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/PluginRuntimeModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/settings/RuntimePluginSessionControlAdapter.kt`
- Modify: `app/src/main/kotlin/app/openstory/auth/PluginLoginActivity.kt`
- Create: `app/src/test/kotlin/app/openstory/plugins/runtime/auth/InstalledPackageAuthenticationPolicySourceTest.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/auth/PluginSessionServiceSecurityGenerationTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/auth/PluginSessionRuntimeIntegrationTest.kt`

**Interfaces:**

```kotlin
interface InstalledAuthenticationPolicySource {
    suspend fun find(pluginId: PluginId): InstalledAuthenticationPolicy?
    suspend fun all(): List<InstalledAuthenticationPolicy>
}
```

`find(pluginId)` reads one `PluginStateStore.find()` and one manifest identity through `InstalledPluginManifestResolver`. `all()` is reserved for workflows whose semantic working set is all installed plugins (Settings/session inventory/policy invalidation).

- [ ] **Step 1: Add RED point-scope tests** with many unrelated installed plugins: `find(target)` must call `state.find(target)` only, resolve only target manifest, and never invoke `state.all()`/unrelated manifest reads.
- [ ] **Step 2: Add RED session-service test** proving `sessionFor(request)` uses point policy lookup while `invalidateChangedPolicies()` intentionally uses `all()`.
- [ ] **Step 3: Run** `./gradlew :app:testDebugUnitTest :plugins:runtime:testDebugUnitTest --tests '*InstalledPackageAuthenticationPolicySourceTest*' --tests '*PluginSessionServiceSecurityGenerationTest*' --no-daemon` and verify RED because the interface is list-only.
- [ ] **Step 4: Change the interface** to explicit `find/all`; migrate `DefaultPluginSessionService.sessionFor()` and `policy(pluginId)` to `find`, preserving `requireNotNull` behavior where the old API required installed policy.
- [ ] **Step 5: Refactor `InstalledPackageAuthenticationPolicySource`** to consume the shared manifest resolver rather than raw storage/Json. `find` is point-addressed; `all` may use `state.all()` but reuses resolver cache.
- [ ] **Step 6: Update Settings/login callers** to use `all()` only when they actually enumerate installed policies; use `find(pluginId)` for one-plugin actions.
- [ ] **Step 7: Update Hilt wiring** so one singleton manifest resolver instance is shared by `DefaultPluginRuntime` and authentication policy source; do not create two independent caches/readers.
- [ ] **Step 8: Run app/plugin session/login integration tests** and package-boundary checks.
- [ ] **Step 9: Commit** `perf(plugin): make authentication policy lookup point-scoped`.

---

### Task 5: Consume one secure-session snapshot and one Keystore key acquisition per store operation

**Files:**
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/auth/PluginSessionService.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/auth/PluginSessionServiceSecurityGenerationTest.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/auth/PluginSessionManagedCredentialProviderTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/plugins/runtime/auth/AndroidKeystorePluginSessionStore.kt`
- Modify: `app/src/androidTest/kotlin/app/openstory/plugins/runtime/auth/AndroidKeystorePluginSessionStoreTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/auth/PluginSessionRuntimeIntegrationTest.kt`

**Interfaces:**
- `DefaultPluginSessionService.validSessionRecords()` reads `PluginSessionStore.readAll(pluginId)` once, filters credentials, and derives/publishes the session summary from that same complete records snapshot.
- `AndroidKeystorePluginSessionStore.readAll()`/`replaceAll()` obtains the crypto key at most once per operation that needs crypto and passes it into per-record encrypt/decrypt helpers.
- A package-internal/injected key-provider seam may be added solely to make key-acquisition count deterministic in instrumentation tests; production still uses Android Keystore alias `openstory.plugin.sessions.v1`.

- [ ] **Step 1: Add RED session-service counter test**: one authenticated `sessionFor()` call performs exactly one `store.readAll(pluginId)`; published summary/status/expiry and credential generation remain identical to the current two-read implementation.
- [ ] **Step 2: Add RED edge tests** for expired/mismatched-policy/corrupt-store paths so removing `refreshSummary()` reread does not accidentally summarize only request-filtered records or alter logout/security-generation behavior.
- [ ] **Step 3: Add RED Keystore-store test** using a counting key-provider seam and multiple records: one `replaceAll` obtains one key, one `readAll` obtains one key, every record still decrypts/authenticates correctly, and no secret is exposed by logs/toString.
- [ ] **Step 4: Run** `./gradlew :plugins:runtime:testDebugUnitTest --tests '*PluginSession*Test*' :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugins.runtime.auth.AndroidKeystorePluginSessionStoreTest,app.openstory.auth.PluginSessionRuntimeIntegrationTest --no-daemon` and verify the new count assertions fail against current behavior.
- [ ] **Step 5: Refactor `validSessionRecords()`** to keep one `records` snapshot and call `summaryForRecords(...)` directly; do not call `refreshSummary()` from this request path.
- [ ] **Step 6: Refactor crypto helpers** to `encrypt(record, key)` / `decrypt(stored, pluginId, key)` and acquire the key once in `readAll`/`replaceAll`. Keep `Dispatchers.IO`, AES/GCM parameters, AAD, atomic replace, corruption cleanup, and no-backup storage unchanged.
- [ ] **Step 7: Run all plugin-session JVM/instrumentation tests** and the credential redirect integration tests.
- [ ] **Step 8: Self-review secret lifetime**: no decrypted record list is promoted to a process cache; one-request snapshot lifetime remains bounded to the service call.
- [ ] **Step 9: Commit** `perf(plugin): remove duplicate secure session work`.

---

### Task 6: Close plugin control-plane benchmarks and defer only evidence-gated risks

**Files:**
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntimePerformanceTest.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/install/BundledPluginProvisionerTest.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/auth/PluginSessionManagedCredentialProviderTest.kt`
- Modify: `app/src/androidTest/kotlin/app/openstory/plugins/runtime/auth/AndroidKeystorePluginSessionStoreTest.kt`
- Create: `docs/superpowers/checkpoints/2026-09-07-hikari-perf-wave-4-plugin-runtime.md`

**Interfaces:**
- Deterministic counters cover `Pl`, `JsB`, `Bp`, terminal/retryable state, `Cr`, and `Hr` independently of JavaScript isolate execution.
- Real-device instrumentation records remaining one-snapshot secure-session cost for `RISK-PLUGIN-AUTH-CACHE`.
- Isolate create/evaluate/close timing is measured only after control-plane/package/auth duplicate work is separated, for `RISK-PLUGIN-ISOLATE`.

- [ ] **Step 1: Run plugin control-plane scaling cases** for `Pl = 1/4/16` and increasing fake `JsB`; assert `enabled(operation)` `main.js` read count/bytes stay zero and manifest reads are bounded by candidate plugin count/identity changes.
- [ ] **Step 2: Run bundled state matrix**: current/newer/missing/update-needed/`NEEDS_REVIEW`/terminal-invalid/transient-failure across 1/10/100 public runtime entries. Record descriptor reads, package-byte reads, verification/update calls, and retries.
- [ ] **Step 3: Run authenticated request counters** for `Cr = 0/1/4`, `Hr = 1/10/100`, and unrelated `Pl = 1/4/16`; require one point policy + one session snapshot per request and no dependence on unrelated plugin manifests.
- [ ] **Step 4: On Android device/emulator, record Keystore/session timing** after X18 closure. Do **not** create a decrypted credential cache here; classify `RISK-PLUGIN-AUTH-CACHE` as accepted or promoted for Wave 8.
- [ ] **Step 5: Measure JavaScript isolate create/evaluate/close separately** with manifest/script source already resolved so `RISK-PLUGIN-ISOLATE` is not contaminated by X16–X18. Record evidence only; no pooling in this task.
- [ ] **Step 6: Run** `./gradlew :plugins:runtime:testDebugUnitTest :app:testDebugUnitTest --no-daemon`, the targeted app instrumentation suite, `bash scripts/verify-package-boundaries.sh`, `bash scripts/verify-structural-suppressions.sh`, and `bash scripts/tests/performance-wave-p3-policy-test.sh`.
- [ ] **Step 7: Write checkpoint** with before/after operation counts, security invariants, any promoted risk, and a source scan confirming request-time auth policy lookup is point-scoped and `enabled(operation)` has no `main.js` path.
- [ ] **Step 8: Commit** `test(perf): close plugin control-plane performance wave`.

---

## Wave 4 acceptance gate

- [ ] `enabled(operation)` reads manifest metadata only; aggregate `main.js` bytes `JsB` do not affect discovery work.
- [ ] bundled provisioning checks descriptors/current state before package bytes; current/newer plugins do not read `.osp` assets.
- [ ] unchanged `NEEDS_REVIEW`/terminal bundled state does not reprovision on every runtime call; transient failures remain retryable and concurrent attempts remain single-flight/cancellation-safe.
- [ ] request-time authentication policy for one plugin does not enumerate/read unrelated installed plugin manifests.
- [ ] one authenticated `sessionFor()` performs one secure-session snapshot read and derives summary from that same snapshot.
- [ ] `AndroidKeystorePluginSessionStore` obtains one key per `readAll`/`replaceAll` operation requiring crypto, not once per record.
- [ ] no JavaScript isolate pooling or long-lived plaintext credential cache was introduced without its risk gate.
- [ ] plugin capability/network/auth/session-security regression suites pass.
