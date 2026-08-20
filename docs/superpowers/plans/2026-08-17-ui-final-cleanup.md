# UI Final Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Finish the UI architecture/performance cleanup by resolving the remaining responsive defects, eliminating the last reader scroll hot path, removing dead design-system primitives, reducing oversized UI files, and simplifying chapter/download action chrome without changing unrelated behavior.

**Architecture:** Keep `core:designsystem` as the sole owner of shared visual primitives, keep feature screens declarative and responsibility-focused, and keep high-frequency reader position tracking outside repeated coroutine job creation. Responsive fixes must use existing Hikari tokens/components and preserve current accessibility/test tags where possible.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AndroidX Lazy layouts, coroutines/Flow, Detekt, Roborazzi, shell verification gates.

## Global Constraints

- Do not introduce new spacing/shape/color/shadow values outside existing Hikari semantic tokens.
- Do not add nested vertical scrolling, nested clickable ownership, or new Material elevation shadows.
- Preserve stable lazy keys/content types and current feature/domain behavior.
- Keep the Story compact CTA readable without text wrapping.
- Keep reader persisted position precise while avoiding one coroutine cancel/relaunch per scroll frame.
- Remove only design-system APIs/tokens with no production callers in this source tree.

---

### Task 1: Lock remaining UI contracts in policy tests

**Files:**
- Modify: `scripts/tests/ui-shared-component-policy-test.sh`

**Interfaces:**
- Consumes: existing source-tree assertions.
- Produces: static guards for responsive Story actions, chapter filter consistency, dead primitive removal, reader progress pipeline, and screen decomposition.

- [x] Add failing assertions for the target architecture.
- [x] Run `bash scripts/tests/ui-shared-component-policy-test.sh` and confirm failure on the current baseline.
- [x] Keep the assertions narrowly tied to the final-cleanup requirements.

### Task 2: Fix Story and Chapter responsive/action hierarchy

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryHeroContent.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterFiltersSheet.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterReleaseRow.kt`
- Modify: relevant Compose tests/snapshots only if contract changes require it.

**Interfaces:**
- Consumes: `HikariPrimaryAction`, `HikariUtilityAction`, `HikariInlineAction`, `HikariFilterChip`.
- Produces: non-wrapping compact Story CTAs and a simpler responsive chapter action hierarchy.

- [x] Add/adjust tests for compact action labels and chapter filter/action semantics.
- [x] Implement compact Story actions as a vertical stack while keeping wide actions horizontal.
- [x] Replace the raw unavailable checkbox/text pair with `HikariFilterChip` in a wrapping layout.
- [x] Move bulk/chapter download controls to inline/quiet actions rather than full-width pill bars.
- [x] Run focused static/component tests.

### Task 3: Remove reader scroll coroutine churn

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/progress/ReadingProgressService.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/progress/ReadingProgressServiceTest.kt`
- Modify only if needed: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderContent.kt`

**Interfaces:**
- Consumes: `ProgressUpdate`, `ReadingProgressRepository`, `CoroutineScope`.
- Produces: one long-lived conflated/debounced progress pipeline, immediate `flush()`, and no cancel/relaunch job per `update()`.

- [x] Write a failing test proving rapid updates are conflated and the latest exact position is saved after debounce/flush.
- [x] Implement a single long-lived update channel/flow consumer.
- [x] Preserve completion monotonicity and explicit flush semantics.
- [x] Run reader unit tests.

### Task 4: Remove dead design-system primitives and token

**Files:**
- Delete: `core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariContentAction.kt`
- Delete: `core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariContentActionTone.kt`
- Delete: `core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariOfflineState.kt`
- Modify: `core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariVisualTokens.kt`
- Modify: tests/docs/policy references to these dead APIs.

**Interfaces:**
- Consumes: production-call-site scan proving zero callers.
- Produces: smaller public design-system API with no dead outlined action/offline-state abstraction or unused `onArtworkInverse` token.

- [x] Make policy/tests fail while dead APIs remain.
- [x] Delete dead primitives and update tests/docs.
- [x] Remove `onArtworkInverse` and update any constructor/test fixtures.
- [x] Run design-system static/unit gates available locally.

### Task 5: Decompose oversized Discover/Home UI files

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt`
- Create: focused Discover UI helper files.
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreen.kt`
- Create: focused Home section/helper files.

**Interfaces:**
- Consumes: existing internal models/callbacks.
- Produces: screen entry files focused on orchestration, with visual sections/helpers moved to package-internal files.

- [x] Add policy limits/assertions for screen ownership.
- [x] Move Discover categories/source filters/feedback/formatting into focused files without behavior changes.
- [x] Move Home shelves/summary/card/feedback into focused files without behavior changes.
- [x] Ensure no new dependency cycles or duplicated shared styling.

### Task 6: Remove redundant layout node and finalize verification

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/download/DownloadActionSheet.kt`
- Modify: `docs/ui/design-system.md` and policy/tests as needed.

**Interfaces:**
- Produces: direct single-action rendering without redundant `Row` container.

- [x] Remove the single-child Row while preserving modifier/spacing/test behavior.
- [x] Run `bash scripts/tests/ui-shared-component-policy-test.sh`.
- [x] Run `bash scripts/tests/ui-token-policy-test.sh` and `bash scripts/verify-ui-tokens.sh`.
- [x] Run `git diff --check`.
- [x] Attempt focused Gradle tests/compile; if sandbox networking blocks the wrapper, report that limitation explicitly.
- [x] Generate one patch against the clean ZIP baseline and validate it with `git apply --check` on a fresh extraction.
