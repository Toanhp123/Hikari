# Wave 11 — Hardening and Open-Source Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the completed MVP into an auditable, accessible, documented, benchmarked, and reproducibly releasable open-source Android APK.

**Architecture:** The final wave closes lifecycle/UI gaps, automates Android/plugin security audits, measures scale, completes localization/accessibility/privacy documentation, publishes contributor/plugin guides, and gates release on deterministic end-to-end fixtures rather than live sources.

**Tech Stack:** Compose/UI tests, Android security configuration, Macrobenchmark/Baseline Profiles, Gradle release builds, GitHub Actions, SBOM/checksum tooling.

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

This wave does not add speculative product features. It proves the existing MVP is safe enough to distribute, understandable to contributors/plugin authors, and resilient under realistic scale/failure conditions.

## Entry Dependencies

- Wave 10 checkpoint is approved.
- All core MVP journeys work in debug builds with fixture plugins.
- No unresolved design deviation remains undocumented.

## Exit Deliverables

- Complete plugin management UI.
- Security audit and release hardening.
- Performance/scale benchmarks and baseline profile.
- Accessibility, English/Vietnamese localization, privacy copy.
- Open-source contributor/plugin documentation.
- Signed APK release and end-to-end acceptance pipeline.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Complete plugin management for URL, local file, and repository installs

**Files:**
- Create: feature/plugins/build.gradle.kts
- Create: feature/plugins/src/main/kotlin/app/openstory/plugins/ui/PluginListViewModel.kt
- Create: feature/plugins/src/main/kotlin/app/openstory/plugins/ui/PluginListScreen.kt
- Create: feature/plugins/src/main/kotlin/app/openstory/plugins/ui/InstallPluginViewModel.kt
- Create: feature/plugins/src/main/kotlin/app/openstory/plugins/ui/InstallPluginScreen.kt
- Create: feature/plugins/src/main/kotlin/app/openstory/plugins/domain/RepositoryIndexService.kt
- Create: feature/plugins/src/main/kotlin/app/openstory/plugins/domain/ImportPluginPackage.kt
- Test: feature/plugins/src/test/kotlin/app/openstory/plugins/ui/InstallPluginViewModelTest.kt
- Test: feature/plugins/src/androidTest/kotlin/app/openstory/plugins/ui/PluginListScreenTest.kt

**Interfaces:**
- Consumes: Installer/registry/update/rollback/diagnostics/session services, Android Storage Access Framework, repository index format, network gateway.
- Produces: Complete plugin lifecycle UI: browse enabled/disabled plugins, add repository, install manifest/package URL, import `.osp`, review trust/capabilities, update, rollback, disable, and remove.

**Acceptance:**
- Remote bytes are downloaded to bounded staging and verified before manifest is trusted.
- File picker grants are temporary unless explicitly persisted and necessary.
- Install confirmation displays source, signer/checksum, runtime, domains, languages, capabilities, and update mode.
- Remove action preserves canonical stories/progress/downloads and marks mappings/releases unavailable.
- Repository failure never disables already installed plugins.

**Implementation notes:**
- Use URL schemes `https` only for remote install/repository; no `file://` or custom downloader execution.
- Display source-code/homepage links as untrusted external navigation.
- Export diagnostics only after redaction and user selection.

- [ ] **Step 1: Write the failing test**

Create `feature/plugins/src/test/kotlin/app/openstory/plugins/ui/InstallPluginViewModelTest.kt`:

```kotlin
package app.openstory.plugins.ui

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class InstallPluginViewModelTest {
    @Test fun newDomainAndUnsignedPackageRequireExplicitConfirmation() = runTest {
        val fixture = installPluginViewModelFixture(unsigned = true, hosts = setOf("source.example"))
        fixture.viewModel.inspect(fixture.packageUri)
        val review = fixture.viewModel.state.value.review!!
        assertTrue(review.warnings.contains(InstallWarning.UNSIGNED))
        assertTrue(review.requiresExplicitConfirmation)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:plugins:test --tests app.openstory.plugins.ui.InstallPluginViewModelTest.newDomainAndUnsignedPackageRequireExplicitConfirmation
```

Expected: **FAIL** because complete install/repository review UI is absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/plugins/src/main/kotlin/app/openstory/plugins/domain/ImportPluginPackage.kt`:

```kotlin
package app.openstory.plugins.domain

class ImportPluginPackage(
    private val bytes: PackageByteSource,
    private val verifier: PackageVerifier,
    private val installer: PluginInstaller,
) {
    suspend fun inspect(uri: Uri): AppResult<InstallReview> =
        bytes.copyBounded(uri).flatMap { verifier.inspect(it) }
    suspend fun install(review: AcceptedInstallReview): AppResult<InstalledPlugin> =
        installer.install(review.toInstallRequest())
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:plugins:test --tests app.openstory.plugins.ui.InstallPluginViewModelTest.newDomainAndUnsignedPackageRequireExplicitConfirmation
./gradlew :feature:plugins:test :feature:plugins:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/plugins/build.gradle.kts feature/plugins/src/main/kotlin/app/openstory/plugins/ui/PluginListViewModel.kt feature/plugins/src/main/kotlin/app/openstory/plugins/ui/PluginListScreen.kt feature/plugins/src/main/kotlin/app/openstory/plugins/ui/InstallPluginViewModel.kt feature/plugins/src/main/kotlin/app/openstory/plugins/ui/InstallPluginScreen.kt feature/plugins/src/main/kotlin/app/openstory/plugins/domain/RepositoryIndexService.kt feature/plugins/src/main/kotlin/app/openstory/plugins/domain/ImportPluginPackage.kt feature/plugins/src/test/kotlin/app/openstory/plugins/ui/InstallPluginViewModelTest.kt feature/plugins/src/androidTest/kotlin/app/openstory/plugins/ui/PluginListScreenTest.kt
git commit -m "plugins: complete community package management"
```

### Task 2: Harden manifest, network, WebView, storage, and release build security

**Files:**
- Create: app/src/main/res/xml/network_security_config.xml
- Create: app/proguard-rules.pro
- Create: config/security/exported-components.allowlist
- Create: scripts/security-audit.sh
- Create: app/src/test/kotlin/app/openstory/security/ManifestSecurityTest.kt
- Create: core/plugin-host/src/test/kotlin/app/openstory/plugin/host/security/SecretLeakScanTest.kt
- Modify: app/src/main/AndroidManifest.xml
- Modify: scripts/verify.sh

**Interfaces:**
- Consumes: All Android components, plugin host/session/logging, release build configuration.
- Produces: Automated hardening gates for cleartext, exported components, backups, WebView debugging, logs/secrets, package signatures, and dependency verification.

**Acceptance:**
- Cleartext traffic is disabled globally.
- Only intentional launcher/deep-link components are exported; allowlist test guards additions.
- Release build disables WebView debugging and strips debug-only diagnostics endpoints.
- No cookie/auth value appears in logs, crash metadata, exported diagnostics, or backups.
- Release minification keeps serialization/Room/Hilt/plugin contracts correctly.

**Implementation notes:**
- Do not add certificate pinning for arbitrary community source domains; it would break legitimate rotations and cannot be centrally maintained.
- Use dependency verification/lockfiles and scan packaged APK permissions/components.
- Run static search against fixtures containing known marker secrets and assert release logs/artifacts omit them.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/app/openstory/security/ManifestSecurityTest.kt`:

```kotlin
package app.openstory.security

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ManifestSecurityTest {
    @Test fun onlyAllowlistedComponentsAreExported() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val exportedNames = ManifestInspector.exportedComponentNames(manifest)
        val allowlist = File("../config/security/exported-components.allowlist").readLines().filter(String::isNotBlank).toSet()
        assertEquals(allowlist, exportedNames)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :app:testDebugUnitTest --tests app.openstory.security.ManifestSecurityTest.onlyAllowlistedComponentsAreExported
```

Expected: **FAIL** because security configuration and exported-component gate are missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `app/src/main/res/xml/network_security_config.xml`:

```kotlin
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors><certificates src="system" /></trust-anchors>
    </base-config>
</network-security-config>
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :app:testDebugUnitTest --tests app.openstory.security.ManifestSecurityTest.onlyAllowlistedComponentsAreExported
bash scripts/security-audit.sh && ./gradlew :app:assembleRelease :app:testReleaseUnitTest lintRelease
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add app/src/main/res/xml/network_security_config.xml app/proguard-rules.pro config/security/exported-components.allowlist scripts/security-audit.sh app/src/test/kotlin/app/openstory/security/ManifestSecurityTest.kt core/plugin-host/src/test/kotlin/app/openstory/plugin/host/security/SecretLeakScanTest.kt app/src/main/AndroidManifest.xml scripts/verify.sh
git commit -m "security: enforce android and plugin hardening gates"
```

### Task 3: Benchmark startup, large libraries, aggregation, sync, and reader rendering

**Files:**
- Create: benchmark/build.gradle.kts
- Create: benchmark/src/main/kotlin/app/openstory/benchmark/StartupBenchmark.kt
- Create: benchmark/src/main/kotlin/app/openstory/benchmark/LibraryScrollBenchmark.kt
- Create: core/aggregation/src/jmh/kotlin/app/openstory/aggregation/ChapterAggregationBenchmark.kt
- Create: test/fixtures/src/main/kotlin/app/openstory/fixtures/LargeLibraryFixture.kt
- Create: scripts/benchmark-baseline.sh
- Test: app/src/test/kotlin/app/openstory/performance/QueryCountPolicyTest.kt

**Interfaces:**
- Consumes: Completed app flows, deterministic fixture generation, Baseline Profile/Macrobenchmark infrastructure.
- Produces: Measured budgets and regression gates for cold startup, Home/Library rendering, 500-story local library, 100k releases aggregation, chapter-list queries, and long reader documents.

**Acceptance:**
- Cold startup does no live catalog/content network call before first frame.
- Library list uses bounded query count independent of story count.
- Aggregation benchmark output is deterministic and memory bounded.
- Reader renders/scrolls a long document without composing all blocks eagerly.
- Baseline profile is generated from launch/Home/Library/story/reader journeys.

**Implementation notes:**
- Record device/emulator model and variance; budgets are reviewed from measured baselines, not invented pass/fail milliseconds.
- Use release/profileable builds for macrobenchmarks.
- Keep benchmark results as CI artifacts and fail only on statistically meaningful regression after baseline is established.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/app/openstory/performance/QueryCountPolicyTest.kt`:

```kotlin
package app.openstory.performance

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryCountPolicyTest {
    @Test fun libraryProjectionUsesSingleObservedQuery() = runTest {
        val fixture = queryCountingLibraryFixture(storyCount = 500)
        fixture.repository.observeLibraryRows().first()
        assertEquals(1, fixture.queryCounter.count)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :app:testDebugUnitTest --tests app.openstory.performance.QueryCountPolicyTest.libraryProjectionUsesSingleObservedQuery
```

Expected: **FAIL** because performance fixtures/budgets and query-count gate do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `benchmark/src/main/kotlin/app/openstory/benchmark/StartupBenchmark.kt`:

```kotlin
package app.openstory.benchmark

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()
    @Test fun coldStart() = benchmarkRule.measureRepeated(
        packageName = "app.openstory",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) { startActivityAndWait() }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :app:testDebugUnitTest --tests app.openstory.performance.QueryCountPolicyTest.libraryProjectionUsesSingleObservedQuery
./gradlew :benchmark:connectedCheck :app:testDebugUnitTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add benchmark/build.gradle.kts benchmark/src/main/kotlin/app/openstory/benchmark/StartupBenchmark.kt benchmark/src/main/kotlin/app/openstory/benchmark/LibraryScrollBenchmark.kt core/aggregation/src/jmh/kotlin/app/openstory/aggregation/ChapterAggregationBenchmark.kt test/fixtures/src/main/kotlin/app/openstory/fixtures/LargeLibraryFixture.kt scripts/benchmark-baseline.sh app/src/test/kotlin/app/openstory/performance/QueryCountPolicyTest.kt
git commit -m "perf: add scale benchmarks and baseline profile"
```

### Task 4: Finish accessibility, localization, privacy copy, and destructive-action safety

**Files:**
- Create: app/src/main/res/values/strings.xml
- Create: app/src/main/res/values-vi/strings.xml
- Create: docs/privacy.md
- Create: docs/content-and-plugin-safety.md
- Create: app/src/androidTest/kotlin/app/openstory/accessibility/CoreJourneyAccessibilityTest.kt
- Create: app/src/test/kotlin/app/openstory/i18n/StringResourcePolicyTest.kt
- Modify: all feature Compose screens to remove hard-coded user-facing strings

**Interfaces:**
- Consumes: All core screens/actions, content/language preferences, diagnostics and permission flows.
- Produces: English/Vietnamese MVP localization, TalkBack/large-font/navigation coverage, clear local-only privacy disclosures, and confirmations that distinguish cache, downloads, Library membership, plugin removal, and logout.

**Acceptance:**
- No user-facing string is hard-coded in Kotlin except fixture/test content.
- 200% font scale preserves primary actions and reader controls without clipping.
- All icons/actions have semantic labels and logical focus order.
- Privacy document accurately states local data, plugin network requests, sessions, downloads, backups, and no cloud/account.
- Destructive confirmations name exact data categories and estimated count/size.

**Implementation notes:**
- Avoid promises about third-party websites beyond host controls; plugin/source operators have their own policies.
- Test keyboard/D-pad focus where practical, not only touch/TalkBack.
- Use Android locale-aware language display names and plural resources.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/app/openstory/i18n/StringResourcePolicyTest.kt`:

```kotlin
package app.openstory.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class StringResourcePolicyTest {
    @Test fun featureSourcesContainNoHardCodedUiText() {
        val violations = File("../").walkTopDown()
            .filter { it.path.contains("/feature/") && it.extension == "kt" }
            .flatMap { UiStringScanner.scan(it).asSequence() }
            .toList()
        assertTrue(violations.isEmpty(), violations.joinToString("
"))
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :app:testDebugUnitTest --tests app.openstory.i18n.StringResourcePolicyTest.featureSourcesContainNoHardCodedUiText
```

Expected: **FAIL** because localization/accessibility/privacy policies are incomplete.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `docs/privacy.md`:

```kotlin
# Privacy

OpenStory works without an account or cloud service. Library metadata, reading progress, plugin configuration, login sessions, cache, and downloads remain on the device. Installed plugins make network requests only to domains declared in their manifests. Login sessions are encrypted, scoped to the plugin and host, excluded from backup, and removable through Logout. The app does not sell data or provide conversation/user data to advertisers; this project operates no analytics backend in the MVP.
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :app:testDebugUnitTest --tests app.openstory.i18n.StringResourcePolicyTest.featureSourcesContainNoHardCodedUiText
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest lintDebug
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml docs/privacy.md docs/content-and-plugin-safety.md app/src/androidTest/kotlin/app/openstory/accessibility/CoreJourneyAccessibilityTest.kt app/src/test/kotlin/app/openstory/i18n/StringResourcePolicyTest.kt all feature Compose screens to remove hard-coded user-facing strings
git commit -m "a11y: localize and document local-first privacy"
```

### Task 5: Publish contributor, architecture, plugin SDK, and security documentation

**Files:**
- Create: CONTRIBUTING.md
- Create: SECURITY.md
- Create: CODE_OF_CONDUCT.md
- Create: LICENSE
- Create: docs/architecture.md
- Create: docs/database.md
- Create: docs/plugin-sdk/quickstart.md
- Create: docs/plugin-sdk/contract-testing.md
- Create: docs/plugin-sdk/security-model.md
- Create: sample-plugins/README.md
- Create: scripts/build-sample-plugins.sh
- Test: docs/DocumentationLinkTest.kt

**Interfaces:**
- Consumes: Approved design, all module/contracts/package formats, sample plugins, contract suite, build commands.
- Produces: Open-source documentation enabling a new contributor to build/test the app and a plugin author to package/test a safe catalog or content plugin.

**Acceptance:**
- Architecture docs state catalog/content/canonical boundaries and dependency direction.
- Plugin quickstart includes one declarative catalog and one JavaScript content fixture with exact package commands.
- Security policy includes responsible disclosure and supported release policy.
- All docs commands execute on a clean checkout.
- License/attribution for bundled dependencies/plugins/content is explicit.

**Implementation notes:**
- Use an OSI-compatible license selected by the project owner; the plan expects the exact license text to be committed before release.
- Generate plugin schema/API reference from source where possible, but keep conceptual security explanations handwritten.
- Document policy that plugins must respect source terms, copyright, and access controls.

- [ ] **Step 1: Write the failing test**

Create `docs/DocumentationLinkTest.kt`:

```kotlin
package docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DocumentationLinkTest {
    @Test fun allRelativeDocumentationLinksResolve() {
        val root = File(".").canonicalFile
        val broken = root.walkTopDown().filter { it.extension == "md" }
            .flatMap { MarkdownLinks.relativeTargets(it).asSequence() }
            .filterNot { it.exists() }.toList()
        assertTrue(broken.isEmpty(), broken.joinToString("
"))
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew test --tests docs.DocumentationLinkTest.allRelativeDocumentationLinksResolve
```

Expected: **FAIL** because open-source contributor/plugin documentation and link validation are incomplete.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `docs/architecture.md`:

```kotlin
# Architecture

OpenStory separates discovery metadata from readable releases. Catalog plugins return catalog-owned records. Content plugins return source stories, release lists, and chapter documents. The host links these records to `CanonicalStory`, groups equivalent `ChapterRelease` rows under `CanonicalChapter`, and stores progress against the canonical chapter while retaining exact release position. Feature modules depend on domain/repository interfaces; plugins never access Room, Android services, or arbitrary files directly.
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew test --tests docs.DocumentationLinkTest.allRelativeDocumentationLinksResolve
bash scripts/build-sample-plugins.sh && ./gradlew test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add CONTRIBUTING.md SECURITY.md CODE_OF_CONDUCT.md LICENSE docs/architecture.md docs/database.md docs/plugin-sdk/quickstart.md docs/plugin-sdk/contract-testing.md docs/plugin-sdk/security-model.md sample-plugins/README.md scripts/build-sample-plugins.sh docs/DocumentationLinkTest.kt
git commit -m "docs: publish contributor and plugin sdk guides"
```

### Task 6: Create reproducible APK release pipeline and end-to-end acceptance gate

**Files:**
- Create: .github/workflows/release.yml
- Create: scripts/release-check.sh
- Create: scripts/verify-reproducible-apk.sh
- Create: app/src/androidTest/kotlin/app/openstory/acceptance/MvpJourneyTest.kt
- Create: app/src/androidTest/kotlin/app/openstory/acceptance/PluginFailureResilienceTest.kt
- Create: CHANGELOG.md
- Create: RELEASE.md
- Modify: app/build.gradle.kts
- Modify: README.md

**Interfaces:**
- Consumes: All application features, deterministic bundled plugins/fixtures, security/performance/accessibility gates, release signing supplied through CI secrets.
- Produces: Tagged release pipeline producing signed APK, checksums, SBOM/dependency report, changelog, and test evidence after full offline-capable MVP journey succeeds.

**Acceptance:**
- Signing keys are never stored in Git or uploaded as ordinary artifacts.
- Release workflow builds from an annotated/protected tag after verification.
- Two unsigned builds from same commit/environment are byte-compared or normalized reproducibility differences are documented and gated.
- APK, SHA-256 checksum, source tag, changelog, licenses/SBOM, and installation instructions are published together.
- Acceptance test covers discovery → Library → mapping → aggregation → reader → progress → offline → local update notification.

**Implementation notes:**
- Use fixture plugins/local mock servers for acceptance; do not depend on live external websites in release gate.
- Perform a manual exploratory pass on at least one API 26 device/emulator and one current API 37 device before public tag.
- Release notes disclose plugin system risk, APK sideloading instructions, requested permissions, and known source-site limitations.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/kotlin/app/openstory/acceptance/MvpJourneyTest.kt`:

```kotlin
package app.openstory.acceptance

@RunWith(AndroidJUnit4::class)
class MvpJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun completeLocalFirstJourney() {
        FixturePlugins.installCatalogAndTwoContentSources()
        compose.onNodeWithText("Trending").assertExists()
        compose.onNodeWithText("Fixture Novel").performClick()
        compose.onNodeWithText("Add to Library").performClick()
        FixtureWork.runInitialStorySync()
        compose.onNodeWithText("Chapter 1").performClick()
        compose.onNodeWithText("Source A · Vietnamese").performClick()
        compose.onNodeWithText("Fixture paragraph one").assertExists()
        FixtureDownloads.downloadCurrentRelease()
        FixtureNetwork.goOffline()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Fixture paragraph one").assertExists()
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.acceptance.MvpJourneyTest
```

Expected: **FAIL** because end-to-end acceptance fixtures and release pipeline are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `scripts/release-check.sh`:

```kotlin
#!/usr/bin/env bash
set -euo pipefail
./scripts/verify.sh
./scripts/security-audit.sh
./gradlew :app:connectedReleaseAndroidTest :benchmark:connectedCheck :app:assembleRelease --stacktrace
./scripts/verify-reproducible-apk.sh
sha256sum app/build/outputs/apk/release/app-release.apk > app-release.apk.sha256
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.acceptance.MvpJourneyTest
bash scripts/release-check.sh
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add .github/workflows/release.yml scripts/release-check.sh scripts/verify-reproducible-apk.sh app/src/androidTest/kotlin/app/openstory/acceptance/MvpJourneyTest.kt app/src/androidTest/kotlin/app/openstory/acceptance/PluginFailureResilienceTest.kt CHANGELOG.md RELEASE.md app/build.gradle.kts README.md
git commit -m "release: produce audited reproducible apk candidate"
```

## Wave Checkpoint

Do not begin `Post-MVP backlog (new spec required before implementation)` until every item below is demonstrated on a clean checkout:

- [ ] Plugin install/update/rollback/remove flows pass with signed and unsigned fixtures.
- [ ] Release APK contains only allowlisted permissions/exported components and no fixture secrets.
- [ ] Large-library/reader benchmarks produce reviewed baselines.
- [ ] Core journeys pass TalkBack/large-font and English/Vietnamese tests.
- [ ] Release check produces APK/checksum/SBOM/evidence from a clean tagged commit.

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
