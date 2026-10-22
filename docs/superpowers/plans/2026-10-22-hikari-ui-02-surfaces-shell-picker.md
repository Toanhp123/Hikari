# 02 — Surfaces, Navigation, Page Shell & Source Picker Pilot Implementation Plan

> **For Gravity:** Use `superpowers:executing-plans` to work through this plan sequentially with explicit review gates; do not start agents, worktrees, commits or branches without separate user authorization. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Deliver first-class Dialog/BottomSheet/Drawer, a typed adaptive transient presenter, app navigation shell and the Source Picker reference migration.

**Architecture:** Keep purpose-based policy and route-result machinery in core UI, navigation destinations in the app, and business selection/authorization in the catalog feature. Preserve active transient route across resize instead of recreating it.

**Tech Stack:** Flutter 3.47.5 via FVM; Dart >=3.13.4; Material 3; existing ChangeNotifier/ViewModel and flutter_test; Android + Windows. No new runtime dependency without separate review.

**Spec:** `docs/superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md` (normative); orchestration: `docs/superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md`.

**Prerequisites:** 00 baseline and 01 foundation/controlled inputs accepted.

## Global Constraints

- Deliberate UI rebuild: target architecture takes priority over superseded historical UI decisions, but must preserve domain, persistence, plugin trust, native consent, cache/source identity and reader progress.
- The snapshot is `Hikari-dev(20261021-163525).zip`; validate paths against the actual working branch before modifying. This plan does **not** assert a live Git revision.
- No new branches, no worktrees, no automated commit/push/merge. Work on the user's current checkout; checkpoint through test reports and diff self-review.
- Respect Flutter's existing Material primitives; do not create trivial forwarding widgets. One canonical UI implementation at the end, no permanent compatibility façade.
- Test-first for changed behavior/API: add a focused failing assertion, confirm the expected failure, implement, then rerun. For characterization/baseline or manual evidence, record a truthful GREEN baseline/NOT RUN instead; never invent a RED or weaken tests.
- Review layout at narrow 375dp, breakpoint boundaries 599/600/601 and 839/840/841, wide 1200/1440dp; 1x/2x/3x text scales; short-height/IME as relevant.
- Prefer pure testable policy/resolvers; all feature-specific business authorization stays with its feature/application owner.
- Do not use navigation Drawer as contextual picker; side panel is a separate future contract and not part of transient modal modes until approved.

## Review Focus

- User cancels nullable-value picker → must distinguish Completed(null) and Cancelled.
- Rapid submit/Back race → at-most-once completion and never consent by barrier.
- Nested navigator presentation → exact owning Navigator and focus return.
- Resize during source selection → VM state, selection and route remain stable.
- Android predictive Back/IME and Windows keyboard shortcuts → mechanical tests + device checks.

---
### Task 01: Establish typed results, purposes and pure surface resolver

**Files:**
- Create: `lib/core/ui/surfaces/adaptive/hikari_presentation_models.dart`
- Create: `lib/core/ui/surfaces/adaptive/hikari_presentation_policy.dart`
- Create: `lib/core/ui/surfaces/adaptive/hikari_presentation_controller.dart`
- Create/Test: `test/core/ui/surfaces/hikari_presentation_policy_test.dart`

**Interfaces:**
- Consumes: foundation `HikariBreakpoints`; semantics matrix spec §§4,13
- Produces: `enum HikariPresentationPurpose { confirmation, selection, form }`, `enum HikariPresentationMode { dialog, bottomSheet }`, `enum HikariDismissReason { cancelAction, closeButton, back, barrier, drag, routeDismissed, routeDisposed }`, `sealed class HikariPresentationResult<T>`, `HikariCompleted<T>(T value)`, `HikariCancelled<T>(HikariDismissReason reason)`; `HikariPresentationRequest` fields `purpose, availableWidth, availableHeight, keyboardInset`; `HikariPresentationPolicy` override (only registered/implemented modes allowed); pure `resolvePresentationMode(HikariPresentationRequest request)`; `typedef HikariContentBuilder<T> = Widget Function(BuildContext, HikariPresentationController<T>)`

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/surfaces/hikari_presentation_policy_test.dart`, add tests named for these required assertions: selection at 375dp→bottomSheet; expanded 1440dp→dialog; confirmation always dialog; short-height/IME constraints choose safe bounded variant; Completed(null) is distinct from Cancelled; invalid dimensions are handled predictably
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/surfaces/hikari_presentation_policy_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Implement immutable request fields, pure policy resolver, route-owned once-only controller and builder typedef; no BuildContext in resolver. Reject non-finite/negative dimensions. Only `dialog`/`bottomSheet` may be returned; no unimplemented side-panel branch. Controller supports `complete(T value)`, `cancel(reason)` and optional synchronous `bool Function(HikariDismissReason)` dismissal guard for feature subflows (e.g. picker language view). No OS/context/business imports in policy.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/surfaces/adaptive/hikari_presentation_models.dart lib/core/ui/surfaces/adaptive/hikari_presentation_policy.dart lib/core/ui/surfaces/adaptive/hikari_presentation_controller.dart test/core/ui/surfaces/hikari_presentation_policy_test.dart`, `fvm flutter test -r expanded test/core/ui/surfaces/hikari_presentation_policy_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Lock mode choices with tests before host wiring. No `sidePanel` enum value or resolver branch is permitted without a reviewed, tested host; navigation Drawer is separate. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 02: Build HikariDialog route and accessible container

**Files:**
- Create: `lib/core/ui/surfaces/dialog/hikari_dialog.dart`
- Create/Test: `test/core/ui/surfaces/hikari_dialog_test.dart`

**Interfaces:**
- Consumes: `HikariPresentationResult<T>`; foundation theme and size tokens
- Produces: `Future<HikariPresentationResult<T>> showHikariDialog<T>({required BuildContext context, required bool useRootNavigator, required HikariContentBuilder<T> contentBuilder, ...})` and `HikariDialog` container with header/body/actions slots; `useRootNavigator` required (no framework-dependent default); no null-as-cancel

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/surfaces/hikari_dialog_test.dart`, add tests named for these required assertions: focus enters dialog and returns to invoker; Tab cycles in active dialog; Escape/back/barrier returns Cancelled; safe max width ~560dp; large text and short window scroll; no implicit destructive approval
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/surfaces/hikari_dialog_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Use framework Dialog/FocusTraversalGroup/Semantics with bounded constraints and controller owned by route; document whether root or nested navigator is used and make it explicit. Instrument cancellation actions only where the host can actually observe them; externally indistinguishable dismissal returns `routeDismissed`, not a fabricated reason. Honor the caller-selected Navigator for every mode. Completion must be exactly once.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/surfaces/dialog/hikari_dialog.dart test/core/ui/surfaces/hikari_dialog_test.dart`, `fvm flutter test -r expanded test/core/ui/surfaces/hikari_dialog_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Test barrier/back/focus on both Android logical sizes and Windows keyboard; avoid duplicate barrier labels. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 03: Build HikariBottomSheet route with short-height/IME rules

**Files:**
- Create: `lib/core/ui/surfaces/bottom_sheet/hikari_bottom_sheet.dart`
- Create/Test: `test/core/ui/surfaces/hikari_bottom_sheet_test.dart`

**Interfaces:**
- Consumes: typed result/controller contract; safe area and keyboard viewInsets
- Produces: `Future<HikariPresentationResult<T>> showHikariBottomSheet<T>({required BuildContext context, required bool useRootNavigator, required HikariContentBuilder<T> contentBuilder, ...})` and `HikariBottomSheet` container; `useRootNavigator` required (no framework-dependent default); no null-as-cancel

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/surfaces/hikari_bottom_sheet_test.dart`, add tests named for these required assertions: 375x640 and 375x440 with keyboard never overflow; drag/Back/barrier produce a precise reason only when observed, otherwise `routeDismissed`; footer remains accessible; no double scroll or double pop; on resize route identity persists
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/surfaces/hikari_bottom_sheet_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Centralize max-height, padding, drag handle and semantic title, keep feature content free of Scaffold/modal sizing. Observe drag/barrier/Back where Flutter permits; if the cause is not observable, map the route completion to `routeDismissed` without guessing. Ensure pending futures resolve on externally disposed route. Reuse existing modal tests as regression baseline.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/surfaces/bottom_sheet/hikari_bottom_sheet.dart test/core/ui/surfaces/hikari_bottom_sheet_test.dart`, `fvm flutter test -r expanded test/core/ui/surfaces/hikari_bottom_sheet_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Verify drag-dismiss disallowed on protected pending confirmation; capture text scale 3x and landscape height. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 04: Implement once-only presenter/controller and route lifetime

**Files:**
- Modify: `lib/core/ui/surfaces/adaptive/hikari_presentation_controller.dart`
- Create: `lib/core/ui/surfaces/adaptive/show_hikari_adaptive.dart`
- Create/Test: `test/core/ui/surfaces/hikari_adaptive_presenter_test.dart`

**Interfaces:**
- Consumes: `HikariPresentationPurpose`, `HikariPresentationResult<T>`, low-level `showHikariDialog` and `showHikariBottomSheet`
- Produces: `Future<HikariPresentationResult<T>> showHikariAdaptive<T>({required BuildContext context, required HikariPresentationPurpose purpose, required bool useRootNavigator, required HikariContentBuilder<T> contentBuilder, HikariPresentationPolicy? policy})`; `HikariPresentationController<T>.complete(T value)` / `.cancel(HikariDismissReason)`

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/surfaces/hikari_adaptive_presenter_test.dart`, add tests named for these required assertions: complete then barrier/back yields one Completed; null accepted; no business work on cancel; the identical explicit `useRootNavigator` decision applies to both modes and returns to the original nested/root caller; existing active mode persists across resize with content reflow; controller refuses result after dispose
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/surfaces/hikari_adaptive_presenter_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** One route owns controller and one Future. Pass required `useRootNavigator` unchanged to either helper; never rely on Flutter's divergent defaults. Keep displayed route stable; never show dialog+sheet concurrently. Release resources and dispose focus owners when route ends.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/surfaces/adaptive/hikari_presentation_controller.dart lib/core/ui/surfaces/adaptive/show_hikari_adaptive.dart test/core/ui/surfaces/hikari_adaptive_presenter_test.dart`, `fvm flutter test -r expanded test/core/ui/surfaces/hikari_adaptive_presenter_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Compare named types exactly against Task 01; no business model import in presenter. Document route-restoration requirement and supported strategy. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 05: Migrate confirmation semantics to canonical dialog family

**Files:**
- Create: `lib/core/ui/surfaces/dialog/hikari_confirmation_dialog.dart`
- Modify: `lib/core/ui/components/hikari_confirmation_dialog.dart` (temporary adapter only)
- Create/Test: `test/core/ui/surfaces/hikari_confirmation_dialog_test.dart`

**Interfaces:**
- Consumes: `showHikariDialog<bool>`, existing `showHikariConfirmation` callers
- Produces: `Future<bool> showHikariConfirmation(...)` compatibility during migration; later use explicit `HikariPresentationResult<bool>` when required by feature

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/surfaces/hikari_confirmation_dialog_test.dart`, add tests named for these required assertions: confirm produces true only by explicit confirm; barrier/Escape/Back/close yields false; destructive focus not auto-activated; semantically distinct danger variant; accessible labels and action order
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/surfaces/hikari_confirmation_dialog_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Implement canonical confirmation content atop HikariDialog and have compatibility entrypoint forward during migration; remove old file in cleanup only after all call sites migrate.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/surfaces/dialog/hikari_confirmation_dialog.dart lib/core/ui/components/hikari_confirmation_dialog.dart test/core/ui/surfaces/hikari_confirmation_dialog_test.dart`, `fvm flutter test -r expanded test/core/ui/surfaces/hikari_confirmation_dialog_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Do not conflate display confirmation with APK trust authorization; feature still checks operation context after await. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 06: Build navigation drawer with destinations + single selection owner

**Files:**
- Create: `lib/core/ui/navigation/hikari_navigation_destination.dart`
- Create: `lib/core/ui/surfaces/drawer/hikari_drawer.dart`
- Create: `lib/core/ui/navigation/hikari_navigation_shell.dart`
- Create: `lib/core/ui/navigation/hikari_navigation_policy.dart`
- Modify: `lib/app/navigation/app_navigation_shell.dart`
- Create/Test: `test/core/ui/navigation/hikari_navigation_shell_test.dart`

**Interfaces:**
- Consumes: `AppTab`, `AppNavigationController`, current IndexedStack and 600dp compact rail policy
- Produces: `HikariNavigationDestination({required String id, required String label, required IconData icon, ...})`; shell `selectedId`, `destinations`, `ValueChanged<String> onSelected`; `HikariDrawer` navigation-only, not transient result

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/navigation/hikari_navigation_shell_test.dart`, add tests named for these required assertions: one destination list drives bar/rail/drawer; selecting drawer changes same AppTab state; 5-tab compact bar behaves as before, >5 destinations uses navigation overflow/drawer policy; resize 599→601 preserves loaded IndexedStack children and tab index; Back closes temporary drawer not selected route; keyboard focus valid
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/navigation/hikari_navigation_shell_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Keep `AppNavigationController` and AppTab mapping in app; core shell renders provided IDs and callbacks only. No core import of app layer. Drawer appears only where nav policy warrants it (e.g. compact overflow destinations); define/test `resolveNavigationMode(availableWidth, destinationCount)` as pure policy, not a feature-level condition.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/navigation/hikari_navigation_destination.dart lib/core/ui/surfaces/drawer/hikari_drawer.dart lib/core/ui/navigation/hikari_navigation_shell.dart lib/core/ui/navigation/hikari_navigation_policy.dart lib/app/navigation/app_navigation_shell.dart test/core/ui/navigation/hikari_navigation_shell_test.dart`, `fvm flutter test -r expanded test/core/ui/navigation/hikari_navigation_shell_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run existing `test/navigation_shell_test.dart` and ensure mode change does not push new route or reset tabs; inspect pointer/keyboard usage on Windows. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 07: Implement shared page shell, header and bounded content region

**Files:**
- Create: `lib/core/ui/layout/hikari_page_scaffold.dart`
- Create: `lib/core/ui/layout/hikari_content_region.dart`
- Create: `lib/core/ui/layout/hikari_page_header.dart`
- Create/Test: `test/core/ui/layout/hikari_page_scaffold_test.dart`

**Interfaces:**
- Consumes: foundation gutter/size tokens, destination shell nav clearance
- Produces: `HikariPageScaffold({required Widget body, PreferredSizeWidget? appBar, ...})`, `HikariContentRegion` maxWidth/padding and composable sliver support; `HikariPageHeader` title/subtitle/actions

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/layout/hikari_page_scaffold_test.dart`, add tests named for these required assertions: no double SafeArea/double shell nav padding; Header aligns at compact+expanded; CustomScrollView/slivers can own their scroll; text scale 3 actions reflow rather than clipping
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/layout/hikari_page_scaffold_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Preserve HikariScaffold ambient visual behavior as an approved variant, not obligatory glow every screen; root shell vs route scaffold state ownership explicit.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/layout/hikari_page_scaffold.dart lib/core/ui/layout/hikari_content_region.dart lib/core/ui/layout/hikari_page_header.dart test/core/ui/layout/hikari_page_scaffold_test.dart`, `fvm flutter test -r expanded test/core/ui/layout/hikari_page_scaffold_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Audit NestedScrollView and reader canvases for cases requiring compositional slots rather than one-size wrapper. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 08: Pilot A: one Source Picker content with typed selection

**Files:**
- Create: `lib/features/catalog/presentation/catalog_source_selection.dart`
- Modify: `lib/features/catalog/catalog_detail_page.dart`
- Modify: `lib/features/catalog/widgets/catalog_source_picker.dart`
- Modify: `lib/features/catalog/catalog_source_picker_view_model.dart`
- Test: `test/features/catalog/catalog_detail_page_test.dart`

**Interfaces:**
- Consumes: `showHikariAdaptive<CatalogSourceSelection>` and existing CatalogSourcePickerViewModel candidate/manual search behavior
- Produces: `sealed class CatalogSourceSelection` with typed `CatalogCandidateSelection` and `CatalogManualSearchSelection`; content callbacks complete presenter; coordinator revalidates series/source/generation

- [ ] **Step 1 — Write a failing test:** In `test/features/catalog/catalog_detail_page_test.dart`, add tests named for these required assertions: same picker content in sheet/dialog; cancellation restores prior state without side effects; Back from `choosingLanguage` returns to source choice before dismissing; selected language, source IDs, candidate cover/metadata, manual search and reader source identity preserved; after resize no lost selection
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/features/catalog/catalog_detail_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Delete `_showSourceSheet`, `_showSourceDialog`, direct page-owned Navigator.pop callbacks, `CatalogSourcePicker._close` direct Navigator.pop and width conditional; route solely via `showHikariAdaptive`, with typed dismissal guard for nested language state. Keep ViewModel disposal and `_selectionGeneration` checks.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/catalog/presentation/catalog_source_selection.dart lib/features/catalog/catalog_detail_page.dart lib/features/catalog/widgets/catalog_source_picker.dart lib/features/catalog/catalog_source_picker_view_model.dart test/features/catalog/catalog_detail_page_test.dart`, `fvm flutter test -r expanded test/features/catalog/catalog_detail_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run existing picker tests and full catalog detail test suite. Confirm single route presentation and no stale navigations. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 09: Source Picker integration across focus, keyboard, nested routes and release

**Files:**
- Create: `test/features/catalog/catalog_source_picker_adaptive_test.dart`
- Create: `docs/ui/PATTERNS.md`
- Test: `test/features/catalog/catalog_source_picker_adaptive_test.dart`

**Interfaces:**
- Consumes: Pilot A, typed presenter, shell page and existing catalog-first reader
- Produces: route integration fixture mounted under nested navigator, output verified after dialog/sheet dismissal, gallery sample for both viewport sizes

- [ ] **Step 1 — Write a failing test:** In `test/features/catalog/catalog_source_picker_adaptive_test.dart`, add tests named for these required assertions: open at compact; update language/filter; resize to wide while active; selection persists and returns exact identity once; Tab/Escape/focus return work; software keyboard no overflow; repeated confirm only one open
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/features/catalog/catalog_source_picker_adaptive_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Write route integration fixture with fake candidate/reader loader. Document picker pattern and failure modes in PATTERNS, including manual search and cancel.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/features/catalog/catalog_source_picker_adaptive_test.dart`, `fvm flutter test -r expanded test/features/catalog/catalog_source_picker_adaptive_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Treat integration pass as gate for all later feature raw-modal removal; explicitly distinguish simulated native tests from device checks. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

