# Hikari UI Token and Shared Component Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all production Compose visual decisions flow through Hikari theme tokens and remove repeated feature-local visual components.

**Architecture:** `:core:designsystem` stays domain-neutral and becomes the sole owner of application visual tokens and repeated domain-neutral presentation. `:app`, `:feature:catalog`, and `:feature:reader` compose those primitives and keep only domain-aware shared components locally. A fail-closed repository static gate prevents future literal visual values from bypassing the theme.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Bash repository verification, JUnit/Robolectric/Compose UI tests, Roborazzi.

## Global Constraints

- Keep exactly 14 production modules.
- Do not change capability/storage/plugin ownership or Room schema.
- Preserve accepted Product UI geometry/copy unless replacing a duplicated implementation with its shared equivalent.
- Production visual literals live only in `core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/` token definitions.
- Repeated domain-neutral patterns must be shared design-system components; domain-aware reusable cards stay in the owning feature.

---

### Task 1: Add fail-closed token policy first

**Files:**
- Create: `scripts/tests/ui-token-policy-test.sh`
- Create: `scripts/verify-ui-tokens.sh`
- Modify: `scripts/verification-common.sh`

**Interfaces:**
- Produces: executable `./scripts/verify-ui-tokens.sh` invoked by repository static gates.

- [ ] **Step 1: Write the failing contract test**

The test copies the verifier to a fixture repository, proves a clean token-based file passes, then adds each forbidden form (`16.dp`, `16.sp`, `RoundedCornerShape`, direct `Color.White`, literal `copy(alpha = ...)`, and feature-local `FontWeight`) and requires the verifier to reject it.

- [ ] **Step 2: Run test to verify RED**

Run: `bash scripts/tests/ui-token-policy-test.sh`
Expected: FAIL because `scripts/verify-ui-tokens.sh` does not exist.

- [ ] **Step 3: Implement the verifier and wire it into static gates**

The verifier scans only production Kotlin under `app`, `feature`, and `core/designsystem`; theme token files are the visual-literal allowlist. Tests are excluded.

- [ ] **Step 4: Run contract test to verify GREEN**

Run: `bash scripts/tests/ui-token-policy-test.sh`
Expected: PASS.

### Task 2: Extend Hikari theme tokens and shared primitives

**Files:**
- Modify: `core/designsystem/.../theme/HikariSpacing.kt`
- Create: `core/designsystem/.../theme/HikariDimensions.kt`
- Create: `core/designsystem/.../theme/HikariVisualTokens.kt`
- Modify: `core/designsystem/.../theme/HikariTheme.kt`
- Modify: `core/designsystem/.../theme/HikariTypography.kt`
- Modify: `core/designsystem/.../theme/HikariShapes.kt`
- Create: `core/designsystem/.../control/HikariIconAction.kt`
- Create: `core/designsystem/.../feedback/HikariInlineFeedback.kt`
- Create: `core/designsystem/.../icon/HikariGlyphs.kt`
- Modify existing design-system product primitives to consume the new tokens.
- Test: `core/designsystem/src/test/kotlin/app/openstory/designsystem/HikariThemeTokensTest.kt`

**Interfaces:**
- Produces: `MaterialTheme.hikariSpacing`, `MaterialTheme.hikariDimensions`, `MaterialTheme.hikariShapes`, `MaterialTheme.hikariOpacity`, `MaterialTheme.hikariColors`, `MaterialTheme.hikariTypography`, `MaterialTheme.hikariBreakpoints`.
- Produces: `HikariIconAction`, `HikariInlineFeedback`, shared glyph composables.

- [ ] **Step 1: Write failing token tests**

Assert the theme exposes the minimum touch target, 520 dp content breakpoint, reader text styles, and semantic artwork colors.

- [ ] **Step 2: Run focused test to verify RED**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests '*HikariThemeTokensTest*'`
Expected: compile/test failure because token APIs are missing.

- [ ] **Step 3: Implement theme tokens and primitives**

Move every visual literal used by product primitives into the theme token definitions. Preserve existing values.

- [ ] **Step 4: Migrate design-system production files**

`HikariGlassSurface`, floating navigation, destination headers, search bar, artwork fallback/backdrop, metadata badge, and cover frame use token APIs only.

- [ ] **Step 5: Run focused design-system tests**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS.

### Task 3: Migrate app/catalog/reader UI and collapse duplicates

**Files:**
- Modify production Compose Kotlin under `app/src/main`, `feature/catalog/src/main`, and `feature/reader/src/main` currently containing visual literals.
- Remove feature-local circular action implementations replaced by `HikariIconAction`.
- Replace repeated inline retry/failure rows with `HikariInlineFeedback` where behavior is equivalent.

**Interfaces:**
- Consumes: theme token extensions and shared primitives from Task 2.
- Produces: feature screens containing no forbidden local visual literals.

- [ ] **Step 1: Run UI token verifier to establish RED against current features**

Run: `./scripts/verify-ui-tokens.sh`
Expected: FAIL with current feature/app literal locations.

- [ ] **Step 2: Migrate app shell and utility UI**

Use tokenized chrome padding/clearance/touch targets and shared design-system icon generation/action primitives.

- [ ] **Step 3: Migrate catalog UI**

Replace local spacing, geometry, shapes, colors, alpha, typography overrides, and breakpoints with tokens. Keep story-aware components in `feature/catalog/ui/components`.

- [ ] **Step 4: Migrate reader UI**

Use reader semantic typography, control metrics, insets, shapes, and shared icon actions.

- [ ] **Step 5: Run verifier to GREEN**

Run: `./scripts/verify-ui-tokens.sh`
Expected: PASS.

### Task 4: Update docs and verify repository

**Files:**
- Modify: `docs/ui/design-system.md`
- Modify: `docs/project/current-state.md`
- Modify: `docs/project/document-governance.md`

**Interfaces:**
- Documents: token-only UI invariant, shared-component rule, allowed Material direct-use boundary, and static gate.

- [ ] **Step 1: Update design-system contract**

Remove the old permission for feature-local visual measurements; document semantic token ownership and shared-component reuse.

- [ ] **Step 2: Run static gates**

Run repository shell tests plus architecture/source/suppression/token gates.
Expected: PASS, aside from explicitly reported pre-existing structural suppression diagnostics accepted by their existing tests.

- [ ] **Step 3: Run Gradle verification**

Run focused module tests/compile, Detekt, and `verify-fast.sh`; run Roborazzi verification if available.
Expected: PASS.
