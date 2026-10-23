# UI Foundation Phase 02 execution

Date: 2026-10-23. Branch `feat/ui-foundation-rebuild`.
Baseline HEAD: `fc2cb1cf70a2d350b2d9d96ebc1728fc7aff34ba`; clean checkout.
Contract: [Phase 02 Tasks 01–09](../superpowers/plans/2026-10-22-hikari-ui-02-surfaces-shell-picker.md).

## A. Summary and authorization

**Historical execution-time authorization (before later owner acceptance):**
The owner explicitly requested execution of Phase 02 Tasks 01–09 on 2026-10-23,
with no subagents, worktrees or Git-history mutation. This narrowly authorizes
Phase 02 despite deferred G1 physical/runtime evidence. It does **not** declare
technical G1 PASS, waive acceptance criteria, authorize G2 approval or Phase 03.
[G1](UI_FOUNDATION_PHASE_01.md) was BLOCKED / INCOMPLETE at execution time; its NOT RUN,
NOT MEASURED and LOST COMPARABLE BASELINE limitations are unchanged.
**G2 OWNER-ACCEPTED (2026-10-23) for continuation — implementation and
automated verification completed; historical technical G2 was INCOMPLETE.**
See [owner decision](UI_FOUNDATION_OWNER_ACCEPTANCE_2026-10-23.md). The
original Claude reviews were autonomous self-review; the subsequent approval
is the owner's separate decision, not a result reported by the implementation agent.

## Preflight audit

- Pinned SDK verified: Flutter 3.47.5 / Dart 3.13.4, Windows host.
- Actual Catalog Detail has `_showSourceSheet`, `_showSourceDialog`, width branch,
  nullable result record, selection generation, picker session and cancellation
  restore. Picker has direct Navigator close, local completion flag, sizing and
  language-step PopScope. These stay untouched until Tasks 01–04 are GREEN.
- App retains AppTab/controller, five destinations, compact bar below 600dp,
  custom rail at 600dp and lazy visited IndexedStack children. App root composes
  pages; core must not import app navigation or choose destination state.
- Existing confirmation returns Future<bool>, defaults to root Navigator through
  showDialog and maps all non-true results to false. Transition must preserve
  its parameters and ownership; native authorization stays outside UI.
- All 19 frozen exemptions match current source: six domain imports retained for
  Phase 03; two Catalog modal calls owned by 02/08; eleven remaining feature
  modal/confirmation calls retained for their feature migration (04–06, cleanup
  deadline 07/01). No stale sites or identity/offset drift found by live guard.
  Canonical helper migration does not authorize blanket exemption removal.
- Fresh characterization command: `fvm flutter test --no-pub -r expanded
  test/architecture_guard_test.dart test/features/catalog/catalog_detail_page_test.dart
  test/features/catalog/catalog_source_picker_view_model_test.dart
  test/navigation_shell_test.dart test/library_page_test.dart
  test/plugins/apk_actions_page_test.dart test/plugins/novel_actions_page_test.dart`.
  Exit 0: **144 passed**. Existing behavior characterization, not artificial RED;
  fake plugin gateways do not prove native E2E.
- UX evidence: local UI UX Pro Max focus guidance supports visible modal focus;
  Flutter stack guidance supports slivers/typed arguments. Web focus-obstruction
  recommendations are guidance, not native accessibility certification.

## B. Sequential task evidence

### 02/01 — Typed contract and policy: GREEN

- Added models, immutable request/pure resolver and Listenable once-only controller
  under `lib/core/ui/surfaces/adaptive/`; focused tests under
  `test/core/ui/surfaces/hikari_presentation_policy_test.dart`.
- Initial RED was missing-public-API compilation, not a behavioral assertion.
  Corrected a test-only `num`/`double` list inference error. Subsequent behavioral
  RED proved a sheet override incorrectly bypassed confirmation policy; resolver
  now rejects that override. No test expectations weakened.
- Fresh GREEN: focused contract tests (10) + architecture guard (21), **31 passed**;
  `fvm flutter analyze --no-pub`: **No issues found**. Changed Dart files formatted.
- Self-review: no feature/domain import, no sidePanel, copied immutable supported
  modes; null completion distinct from cancellation; pre-pop permission separate
  from observed external dismissal; disposal settles pending result. Route/focus/
  predictive-Back runtime behavior is not proven by these unit tests and belongs
  to Tasks 02–04/device checks. Existing modal callers untouched.

### 02/02 — Dialog family: GREEN

- Added native DialogRoute host and bounded HikariDialog header/body/actions.
  Explicit Navigator, route-owned controller disposal, closed-loop traversal,
  Escape, observed barrier cancellation and scrolling implemented. Uninstrumented
  successful framework pops stay routeDismissed; no guessed Back reason.
- RED: missing-public-API compilation first; behavioral barrier-reason assertion
  then failed (routeDismissed rather than observed barrier), fixed by native
  ModalBarrier callback. Width test initially measured full-screen Dialog wrapper;
  corrected to measure actual Material card, per pinned Flutter source.
- Fresh GREEN: dialog tests (9), contract tests (10), guard (21): **40 passed**;
  analyzer **No issues found**. Root/nested ownership, teardown settlement, null/
  repeated completion, focus entry/return/Tab and 1000x440 at 3x covered mechanically.
- Self-review: only owned route closes, including when another route sits above it;
  no business imports or legacy caller migration. Physical focus, predictive Back,
  TalkBack remain NOT RUN. No restoration registration claimed.

### 02/03 — Bottom sheet family: GREEN

- Added native ModalBottomSheetRoute with bounded HikariBottomSheet, one IME/
  safe-bottom owner and opt-out for list-owned scrolling. Explicit Navigator,
  controller teardown and conservative routeDismissed reasons preserved.
- Missing-API compilation RED followed by behavioral RED: protected sheet still
  dragged after policy changed. Pinned ModalRoute caches buildPage; internal-state
  refresh only rebuilt barrier. External-state refresh rebuilds native gesture
  handlers before next frame. Regression now proves drag/barrier veto and nested
  Back consumption before pop, rather than trying to undo a completed pop.
- Fresh focused GREEN: **9 passed**; analyzer **No issues found**. 375x640/440/300,
  IME120, 3x text, reachable scroll footer, drag/Back/barrier, disposal and active
  form resize exercised. Physical Android drag/IME/predictive Back NOT RUN.
- Self-review: independent sheet family (no dialog masquerading as sheet), no
  feature imports, no old helper/caller removal. Shared session deduplication and
  stronger route race tests belong to Task 04 before feature migration.

### 02/04 — Adaptive session: GREEN

- Added purpose-based presenter; chooses family once and passes explicit Navigator
  unchanged. Shared internal presentation session replaces duplicated host state
  (not a third surface family). Route restoration unsupported; feature must reopen
  from validated state, never replay business completion.
- Missing-API RED plus two behavioral REDs: dialog listener setState during content
  build, and result awaited before caller route became current. Shared microtask
  handling fixes build safety; public helper waits native route.completed before
  returning settled result. Controller still settles once immediately.
- Fresh GREEN: **58 passed** (all surface tests + architecture guards); analyzer
  **No issues found**. Nine adaptive tests cover compact/expanded root/nested,
  all requested width boundaries while active, retained field/route, nullable
  submit/race, synchronous settlement, await ordering and covered-route ownership.
- Self-review: no business operation on cancellation, no route pop/reopen on
  resize, no unsupported host. Existing production picker untouched until this
  checkpoint. Device behavior remains NOT RUN; APIs remain Preview.

### 02/05 — Canonical confirmation: GREEN

- Canonical typed confirmation uses HikariDialog, safe Cancel autofocus, explicit
  close/cancel, error/onError destructive role. Legacy helper now forwarding only,
  preserves parameters/root Navigator/Future<bool>; remove by cleanup Task 07/01.
  Removed unused legacy widget implementation; no feature call-site mass migration.
- RED: missing canonical public API compilation. Fresh focused confirmation +
  Library/APK/Novel tests: **61 passed**; architecture guard **21 passed**;
  analyzer **No issues found**. Six exits prove true only via explicit confirm.
- Caller audit: APK rechecks epoch/staged/installed/native permission; Novel
  rechecks exact artifact/owner/offer tuple after await. Plugin owner/repository
  and Settings mounted/operation checks untouched. UI result remains interaction,
  never native trust/install success. Fake gateway tests are not native E2E.

### 02/06 — Controlled navigation: GREEN

- Moved approved docked bar/rail rendering into shared shell with ID descriptors,
  pure policy and navigation-only Drawer. App retains AppTab/controller/lazy
  IndexedStack ownership. Five destinations keep bar; compact >5 uses Drawer.
- Missing-API compile RED. Existing navigation characterization remained GREEN.
  Guard caught direct dart:ui import after moving approved blur into core: ADR-026
  now explicitly permits Flutter engine visuals in core UI only; OS/FFI restrictions
  remain. Added scoped positive/negative guard test; no allowlist regeneration.
- Fresh GREEN: shared shell + unchanged app navigation + architecture guards:
  **37 passed**; analyzer **No issues found**. Retained tabs across boundaries,
  controlled selection, Drawer Back, RTL/3x and keyboard mechanically exercised.
- Self-review: no core app import, no shared destination business identity, no new
  route on resize, no visual redesign. Actual Windows pointer/keyboard and Android
  navigation checks remain NOT RUN.

### 02/07 — Page composition: GREEN

- Added page scaffold with optional approved ambient variant, wrapping header and
  bounded content region with lazy sliver form. No feature page mass migration.
  SafeArea/IME ownership explicit; specialized canvases may opt out.
- RED: missing-public-API compilation after correcting test syntax. Fresh GREEN:
  page tests (3) + architecture guard (22): **25 passed**; analyzer clean.
  SafeArea consumption, single sliver scroll owner, compact/wide and 3x actions
  covered. Live shell obstruction and specialized reader device review still pending.

### 02/08 — Production typed picker: GREEN checkpoint

- One `showHikariAdaptive<CatalogSourceSelection>` call, explicit root ownership.
  Legacy dialog defaulted to root; legacy sheet defaulted to nested. The pilot
  deliberately unifies both on root, verified under a nested page Navigator.
  Removed both duplicate modal
  branches, nullable record, direct picker Navigator exits and local settlement flag.
  Typed candidate/manual variants preserve language, immutable source ID set and
  metadata. Coordinator retains cancellation restore, generation checks, serialized
  preference writes and reader authorization boundaries. ViewModel unchanged.
- Behavioral RED: production 640x320 opened sheet, not short-height policy dialog.
  Migration fixes it. Catalog detail + picker VM + architecture: **82 passed**.
  Analyzer initially caught two brace lints, corrected; subsequent analyzer clean.
- Standalone content tests now supply route controller through test-only harness;
  original callbacks/assertions retained. Filtered-search assertion now waits for
  route teardown before follow-up (public helper contract), not immediate tap.
- Removed exactly two retired Catalog modal exemptions; remaining **17** retain
  six domain imports and eleven other feature sites. No allowlist regeneration.

### 02/09 — Production integration: automated GREEN; physical acceptance incomplete

- Nested production fixture uses real coordinator, picker VM, resolver and search
  use cases with fake source/persistence/reader boundaries. Final **25 tests** cover
  both initial families, active boundary resize, language/manual source IDs, nested
  Back, explicit root ownership, Tab/Escape/focus return, repeated settlement,
  cancellation restore, exact Reader source/cover, no autoplay, resolver replacement,
  mismatched cover rejection and pending resolver disposal.
- Behavioral RED exposed stale resolver replacement committing old choice (one
  preference save instead of zero). Coordinator now invalidates generation when
  resolver identity changes. Direct-VM candidate test initially waited on unresolved
  resolving animation; fixture corrected to pump once before explicit completion.
- Six stress cases verify modal receives 1x/2x/3x, RTL and reduced-motion settings,
  then resize to 440dp height with injected IME120 and safe insets. Caller detail
  chrome is explicitly unscaled; this is not whole-page 3x acceptance. Initial
  whole-page stress revealed existing detail hero/notice overflows at 3x. Those
  files are outside this migration and remain a recorded G2 limitation.
- Gallery contains real picker content with empty fake inventory, compact/expanded
  preview overrides and two widget tests. Actual Windows interaction evidence is
  recorded below. Phase 01 Gallery dropdown polish remains untouched.

## C. Presentation architecture

- Curated `hikari_ui.dart` exports Preview models, controller, policy, independent
  dialog/sheet families, confirmation, navigation and layout. Internal session is
  not exported. No app, feature, domain or native-authorization imports enter core UI.
- `HikariCompleted(null)` remains distinct from cancellation. Controller accepts
  one completion/cancellation; later calls return false. Pending disposal settles
  with `routeDisposed`; unobserved framework exits use `routeDismissed`.
- Pure initial policy uses selection sheet below 840dp with usable height at least
  360dp; confirmation/form default to dialog. Invalid dimensions, impossible IME,
  empty supported modes and invalid overrides reject predictably. Active mode is
  never recalculated on resize. Drawer is navigation, not a result surface.
- Default chrome scrolls header/body/actions together; raw adaptive content owns
  its list viewport. Host owns route bounds, IME and safe-bottom treatment.

## D. Navigator and lifecycle

- Every new modal entrypoint requires `useRootNavigator`. Presenter forwards it
  unchanged; native route owns controller disposal. Feature disposes its VM in
  `finally`. Public Future returns after `route.completed`, not before teardown.
- Settlement closes only owned route, using `removeRoute` when covered rather than
  popping unrelated route above it. Resize preserves route/controller/content and
  feature state. Tests include form state and focus, plus production VM/language.
- Permission is set before pop through PopScope/controller policy; no didPop=true
  undo. First picker language Back consumes nested step; second may dismiss.
- Review regressions fixed dynamic protected-dialog barrier semantics and native
  sheet handle bypass: disabled drag alone did not disable Flutter handle detector.
- Dialog traversal is closed-loop; confirmation initially focuses Cancel. Escape,
  focus entry/return and safe confirmation exits have mechanical coverage.
  No automatic modal restoration or business/native-action replay is registered.

## E. Navigation and page shell

- Core renders supplied IDs/labels/icons and callback. App retains AppTab mapping,
  controller, selection and lazy visited IndexedStack. Stable Row/Expanded ancestry
  preserves retained pages when bar/rail mode changes.
- Below 600dp, five destinations retain approved docked bar; compact overflow uses
  navigation Drawer. At 600dp and above, shared custom rail remains approved chrome.
- RTL regression proved rail missed physical right safe inset (892 versus allowed
  876 at 900dp width/right inset24); Directionality-aware SafeArea fixed it.
- Page scaffold, wrapping header and bounded box/sliver region are compositional.
  Lazy sliver remains lazy. Width regression fixed 1392 versus intended 1440 content
  bound. Legacy HikariScaffold forwards to canonical scaffold; ambient-on legacy
  default and ambient-off canonical default are preserved. Adapter expires 07/01.
- Reader canvases and specialist NestedScrollView pages were not mass-migrated.

## F. Source Picker migration

- Removed duplicate `_showSourceSheet`/`_showSourceDialog`, width branch, nullable
  result record, content-local completion flag and direct picker Navigator exits.
- Feature-only sealed candidate/manual variants freeze source IDs/language before
  VM reset. Existing picker VM/business logic was not rewritten or moved to core.
- Coordinator retains session concurrency, mounted/series/resolver generations,
  cancellation restore, exact type/source/item/title/cover validation and serialized
  preference writes. Manual-search await rechecks generation before side effects.
- Selection saves once and updates chapter state; it does not autoplay Reader.
  Explicit Start reading test asserts exact source and cover at Reader callback.
  Resolver replacement and disposal cannot commit stale choice or open Reader.
- Plugin trust, native consent, installer, domain/application/persistence production
  logic, progress and other feature dialogs/sheets remain outside this change.

## G. Guards and migration matrix

- Removed exactly two retired Catalog modal exemptions: **17 remain**, consisting
  of six legacy domain imports for Phase 03 and eleven later feature sites. No new
  exemption, wildcard or allowlist regeneration was used to accept violations.
- ADR-026 and guard agree on scoped `dart:ui` engine visuals in `core/ui` only;
  `dart:io`/FFI and upward business imports remain prohibited. Positive/negative
  tests cover UI versus other layers.
- Inventory assigns exact Phase 02 owners, Preview status, adapter expiry and
  production Navigator audit. Regenerate Markdown with
  `fvm dart run tool/ui_audit_inventory.dart --markdown`; default output is JSON.
- One intermediate full gate failed because JSON output accidentally replaced
  Markdown matrix; exact-site guard caught missing row. Correct Markdown generation
  restored it; focused guards then full gate passed. No guard expectation weakened.
- Canonical docs updated: PRESENTATION, ADR-026, COMPONENTS, RESPONSIVE, PATTERNS,
  MIGRATION_MATRIX and master-plan gate note. This report owns execution evidence.

## H. Verification

### Automated evidence — 2026-10-23

Environment: Windows 11 Pro 10.0.26300.9711, FVM Flutter 3.47.5 / Dart 3.13.4.
No dependency or state-management framework added; `pubspec.lock` unchanged.

| Check | Observed result | Scope / limit |
| --- | --- | --- |
| Final `tool/check.ps1` | Exit 0; **1199 passed, 1 skipped** | Full suite, analyzer, read-only format and whitespace checks |
| Analyzer within final gate | No issues found, 4.0s | Static analysis, not device acceptance |
| Read-only format within gate | 125 + 108 + 134 files, zero changed | No repository-wide formatting mutation |
| Production picker integration | 25 passed | Real feature flow with fake external boundaries |
| Gallery surface tests | 2 passed within full suite | Empty inventory preview, no native workflow |
| Final docs/inventory/architecture recheck | 38 passed after report update | Relative links, deterministic rows and exact exemptions |
| Final `git diff --check` | Exit 0 | Tracked-file whitespace |
| `graphify update .` | Exit 0; 8877 nodes, 14292 edges, 444 communities | AST refresh; not behavioral proof |
| Android debug build | Exit 0, 17.1s | Current production source |
| Android release build | Exit 0, 55.5s, 115.9MB | Compile/package, not release runtime/profile |
| Windows production debug build | Exit 0, 13.0s | Compile/link |
| Windows Gallery debug build | Exit 0, 11.5s | Separate Gallery entrypoint |

Four builds ran before final six test-only stress additions; production source did
not change afterward. Final full gate includes those additions and assertions that
modal really receives scaling/RTL/reduced-motion settings. Earlier 1193/1 and
Phase 01 1112/1 are historical checkpoints, not substituted for final 1199/1.

Build warnings remain: Gradle/JDK native-access warning and Flutter future Built-in
Kotlin migration warning. No unrelated build configuration suppression added.
Existing skipped test remains skipped; no new skip added for this task. Graph
refresh retained four existing Kotlin parser warnings and a community-label refresh
notice; no LLM relabel performed. Graph evidence is navigation-only and may be partial
for those Kotlin files, not a substitute for source/build/runtime verification.

### Coverage boundaries

- Active resize: 375, 599/600/601, 839/840/841, 1200/1440, returning to compact.
- Mechanical null/race/teardown/disposal/root/nested/protected Back/barrier/drag,
  focus/Tab/Escape, safe confirmation and lazy page retention tests pass.
- Short-height/IME and 1x/2x/3x stress is targeted coverage, not Cartesian coverage
  of every page/theme/accent/OLED/platform/input combination. Whole Catalog detail
  3x hero/notice overflow remains; unscaled caller is explicit in stress test name.
- Reduced motion and RTL are injected in picker stress cases; physical OS settings,
  contrast measurements and full assistive-technology matrix are not certified.

### Actual Android runtime

- Detected Redmi Note 9S, Android 15/API35 over wireless ADB. Updated current debug
  APK with `adb install -r`: Success. No uninstall, clear-data or permission change.
- Launched actual `io.github.toanhp123.hikari`; cold start reported Status ok,
  TotalTime6035ms. This is launch timing, not a performance profile.
- Navigated Search, focused field and observed native keyboard; content reflowed.
  Back hid IME, kept Search route and restored docked navigation. Screenshots were
  opened and inspected, not accepted merely because capture command succeeded.
- Production device Source Picker selection, predictive gesture Back, modal drag,
  TalkBack, physical 3x/RTL and native plugin installation/OS consent: **NOT RUN**.

### Actual Windows Gallery runtime

- Launched final Gallery executable. Native pointer opened compact sheet, Escape
  dismissed it, pointer opened expanded dialog. Same empty picker content visible.
- Resized active dialog to 800x500; dialog stayed open and Close remained visible.
  Tab visibly focused Close. Enter sent; later restored-window screenshot showed
  Gallery with no dialog. Intermediate minimized screenshot was black and discarded
  as evidence. This is limited observed interaction, not full focus certification.
- UI Automation exposed FLUTTERVIEW without child controls. Native Win32 input and
  screenshots used as fallback. `9remote` is configured but connection failed
  ECONNREFUSED; it is not unconfigured. `/run-skill-generator` could capture this
  fallback recipe separately; no skill was created as part of this phase.
- Full Shift+Tab/Space/hover/wheel/screen-reader grid and production Windows picker
  source workflow: **NOT RUN**. Gallery cannot prove installer/trust/OS authorization.
- Screenshots remain local TEMP evidence, not committed or externally published.

## I. Self-review

- Correctness review covered typed settlement, route teardown/covered ownership,
  pre-pop guards, protected handle/barrier, root/nested choice, content/inset bounds,
  app state retention, source generations/identity, adapters, exports and guards.
- Actual behavioral RED/GREEN fixes include invalid confirmation override, dialog
  barrier reason, dynamic sheet veto, build-time notification, teardown ordering,
  short-height picker policy, stale resolver identity, content max width, RTL rail
  inset and protected dialog barrier affordance. Compile-only missing-API RED and
  characterization GREEN are explicitly distinguished above.
- Test harness corrections: Library IconButton finder scoped to LibraryButton;
  pending chapter spinner test uses bounded frame wait for route teardown rather
  than global pumpAndSettle. Fixture errors were fixed without weakening behavior.
- Ponytail pass after correctness: keep native route machinery and required safety
  tests; no speculative state framework/side panel/dependency. Duplicate ambient
  scaffold implementation already removed by forwarding adapter (net 52 lines cut
  in that file). No further safe simplification finding. Compatibility remains
  temporary, not a parallel canonical implementation.
- No agents/worktrees, branch changes, commits, pushes, merges or history rewrite.
  No generated builds, screenshots, caches or secrets enter requested patch.
  Reviewable patch includes tracked and new task files; no staging required.

## J. G2 gate — execution-time assessment

**At the time of Claude execution:** BLOCKED / INCOMPLETE. The separate
2026-10-23 owner decision below subsequently accepted G0–G2 for continuation;
this original assessment is retained as historical evidence, not current status.

Outstanding acceptance:

1. Physical Android picker predictive Back/IME/drag and TalkBack, including
   protected language step and source-selection cancellation/return.
2. Production Windows picker keyboard/focus/pointer matrix and assistive technology.
3. Full required theme/accent/OLED/text/input combinations and physical safe-area
   checks; existing Catalog detail 3x hero/notice overflows require later scoped fix.
4. Native installer/trust/OS authorization E2E where required by retained G1 contract;
   fake gateways, Gallery and widget tests are not replacements.
5. Retained G1 **NOT RUN**, **NOT MEASURED**, **LOST COMPARABLE BASELINE** evidence.
   No comparative performance or full accessibility PASS is inferred.

These gaps block acceptance; they do not authorize widening Phase 02 or approving
G2 autonomously. APIs remain Preview.

## K. Phase 03 handoff — execution-time status

At execution time not authorized; subsequently **authorized by the project
owner on 2026-10-23**, subject to the follow-up regression described below.
No Phase 03 implementation is included in the Phase 02 patch. Six legacy domain imports
remain under Phase 03 ownership. Later feature raw-modal removal must use this
pilot's typed/lifecycle pattern and preserve each feature's authorization checks.
No automatic migration, permanent compatibility facade or Gallery dropdown polish.

## Owner acceptance and follow-up — 2026-10-23

The project owner reports satisfactory runtime/manual checks and explicitly
**accepts G2 for continuation into Phase 03**. This does not change the
execution-time `NOT RUN`/`NOT MEASURED` rows or turn missing original captures
into evidence. Full decision: [G0–G2 acceptance](UI_FOUNDATION_OWNER_ACCEPTANCE_2026-10-23.md).

A new focused Escape/MenuAnchor keyboard-priority regression was added after
the originally reported 1,199-test run. It has **not** been run in the patch
authoring environment; rerun before Phase 03 work. Catalog Detail whole-page
3x hero/notice overflow remains a **known defect** assigned to Phase 04 Task
05, not a completed G2 visual acceptance.
