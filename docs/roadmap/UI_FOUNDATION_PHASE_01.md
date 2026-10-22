# UI Foundation Phase 01 execution

Date: 2026-10-23. Branch `feat/ui-foundation-rebuild`;
HEAD `8c07d34e88eeae586ab7d74cfe2cffe20701136d`, uncommitted workspace.
Scope: Tasks 01–07 of [Phase 01 plan](../superpowers/plans/2026-10-22-hikari-ui-01-foundation-controls.md).
**G1 BLOCKED / INCOMPLETE. Phase 02 NOT STARTED.**

## Authority and execution

[ADR-026](../decisions/ADR-026-ui-foundation-rebuild.md) accepted by owner on
2026-10-22. [G0](../ui/BASELINE_RUNS.md) remains OWNER-ACCEPTED / PHYSICAL
EVIDENCE DEFERRED, not technical PASS. Gates below are autonomous self-review,
not independent review or new owner approval. Work stayed in current checkout;
no agents, worktrees, branches, commits, pushes, merges or dependency additions.
Existing dirty Phase 00 work retained; aggregate patch must not be described as
Phase 01-only provenance.

## Sequential task checkpoints

| Task | Deliverable and caller boundary | RED / GREEN and self-review |
|---|---|---|
| 01 | Five geometry/motion/responsive files in `lib/core/ui/foundation/`; exact values preserved | Canonical ownership RED; existing values/boundaries/invalid widths characterized GREEN. 40 focused tests passed at checkpoint. Temporary aliases removed in Task 07. |
| 02 | Semantic status extension/context getter, raw accent seeds and 15 typography roles in foundation; root ThemeData composer only | Missing ownership RED; 12 focused tests passed. Both dark/OLED × six seeds: tested text pairs ≥4.5:1, outline/canvas ≥3:1; copy/lerp and raw-seed generation protected. Not rendered/device contrast certification. |
| 03 | Canonical button/icon/filter/chip row; native Material activation and states, ≥48dp targets | Ownership and label reflow RED then GREEN; 26 focused/existing tests passed at checkpoint. Retained 8dp chip gaps; no display/filter compatibility adapter. |
| 04 | Controlled search, nullable choice, single selection segments | Ownership RED; long-label hit area RED then GREEN. 20 checkpoint tests passed. Field emits edits immediately, submits explicitly, borrows external controller/focus; requests and debounce remain feature-owned. Explicit Text typography measurement regression also RED then GREEN. |
| 05 | Domain-free notice/async state/section/list row | Ownership RED then GREEN; retained child identity and retry verified. Passive row disabled-semantics regression RED then GREEN. Short 375×320/3x recoverable notice exposed overflow/covered actions; reserved scrollable feedback space regression GREEN. |
| 06 | Curated public barrel, standalone gallery, API/lifecycle docs | Missing API/docs ownership RED then GREEN. Five public API/gallery tests passed including RTL/stress/local widths × 1x/2x/3x; no production import of gallery. All components Preview. |
| 07 | Complete core/features token migration and strict app/theme inversion guard; old aliases/adapters removed | Theme inversion/exemption and alias-removal tests RED then GREEN. No core/features app/theme dependency; only `lib/app/app.dart` consumes composer. Search typed-empty timing regression RED in both routes then GREEN; 26 search/page tests passed. Preview inventory labeling regression RED then 12 inventory tests GREEN. |

Historical checkpoint counts above are not additive. Final full-suite results
below supersede intermediate failing runs. Task checkboxes in original plan
remain specification/checklist text; this record distinguishes implemented work
from mandatory platform evidence still missing.

## Final ownership and migration

- One authority per token under foundation; `HikariTheme.darkTheme` composes
  ThemeData, exports no tokens and carries no accent aliases/action helper.
- Public API and component contracts: [controls](../ui/components/controls.md),
  [contribution rules](../ui/CONTRIBUTING.md). Native Material owns focus, keyboard,
  hover/pressed and disabled mechanics. Features own validation, selected values,
  query/debounce, requests, stale results and authorization.
- HEAD had 58 app/theme consumers outside theme itself: legitimate app root,
  five removed/moved shared implementations and 52 remaining token consumers
  (navigation 1, legacy patterns 7, features 44). All migrated or deleted;
  current app root is sole composer consumer. Exact current paths/statuses:
  [live matrix](../ui/MIGRATION_MATRIX.md); aggregate Git patch preserves before/after.
- Seven app/theme token files deleted. Removed action/icon/choice/section
  forwarders plus old chip and search adapters after their callers migrated.
- Frozen legacy fixture reduced from original G0 75 to 19 reviewed exact sites:
  six domain imports and thirteen modal calls. No wildcard/new exemption or
  inventory-based automatic approval. Offsets changed only with source review.
- Catalog/Source routes preserve 300ms edits including keyboard deletion,
  immediate Clear and explicit submit; selection-only changes retain timers,
  external text edits/disposal cancel stale timers. Plugin local filtering stays
  immediate. No ViewModel/domain/application/native/persistence behavior changed.
- Source Unicode drift found by full regression and diff review; restored
  ellipsis/middle-dot strings. No non-ASCII source/test diff remains.

## Verification evidence

Pinned Flutter 3.47.5 / Dart 3.13.4; Windows 11 10.0.26300.9711.

| Command / scope | Actual result |
|---|---|
| `./tool/check.ps1` after production-source fixes | exit 0: locked pub get; 341 Dart files checked, 0 format changes; analyze no issues (3.6s); 1111 passed, 1 skipped; staged/unstaged whitespace clean |
| `fvm flutter test --no-pub -r expanded test/ui_audit_inventory_test.dart` after Preview reporting correction | exit 0, 12 passed |
| Final `./tool/check.ps1` including Preview inventory and expanded documentation links | exit 0: locked pub get; 341 Dart files checked, 0 changes; analyze no issues (3.9s); 1112 passed, 1 skipped; staged/unstaged whitespace clean |
| `fvm flutter build apk --debug --no-pub` | exit 0, 16.7s; APK built, NOT installed/run |
| `fvm flutter build apk --release --no-pub` | exit 0, 56.1s; 115.5MB APK, NOT installed/run |
| `fvm flutter build windows --debug --no-pub` | exit 0, 12.4s; production executable compiled, NOT interactively run |
| `fvm flutter build windows --debug --no-pub -t tool/ui_gallery/main.dart` | exit 0, 8.9s; gallery compiled, NOT interactively run |
| `fvm flutter devices`; `adb devices -l` | Windows only; Android device list empty |
| Native desktop driver discovery | No WinAppDriver/Appium command available; 9remote connection failed (ECONNREFUSED); no usable GUI interaction driver established |
| `graphify update .` | exit 0; AST navigation refresh only, not behavioral evidence |

Build evidence above follows final production-source Unicode fixes. Later edits
are inventory reporting/docs/tests only. Android build warnings remain: JDK
restricted native access and future Flutter Built-in Kotlin migration. No build
warning was suppressed or unrelated platform migration attempted.

Skipped test: opt-in upstream manifest smoke, requiring external local upstream
fixtures; see [baseline run limits](../ui/BASELINE_RUNS.md). Earlier full run
failed solely on changed Source Search ellipsis; fixed and rerun successfully.
Earlier nullable callback analyzer and old ownership/chip-label assertions also
failed during migration; none are counted as final PASS evidence.

## Responsive/accessibility limits and G1

Widget evidence covers minimum 48dp actions, native keyboard activation,
controller lifetime, choice Escape/focus restoration, retained child element,
large text/short host, boundary classification and gallery stress. Gallery local
width caps are constrained by actual host gutters: choosing 1440 within a 1440
window does not prove a 1440 inner viewport. Stress test scrolls through segments,
not every feedback state combination. Native focus-state assertions are not a
screenshot or focus-indicator contrast measurement.

| Mandatory evidence | Status / blocker |
|---|---|
| Android launch, TalkBack, physical IME, system Back | NOT RUN — no Android device attached |
| Windows Tab/Shift+Tab/Enter/Escape, focus restoration, hover, resize | NOT RUN — no usable native GUI driver; desktop build is not interactive proof |
| Dark/OLED/accents/scales screenshots and rendered state contrast | NOT RUN — no inspected physical/runtime captures |
| Matched Android/Windows profile frames/memory | NOT MEASURED — no matched profile capture |
| Comparable pre-migration physical controls/token captures | LOST COMPARABLE BASELINE — affected UI changed before deferred captures; cannot reconstruct old physical evidence from current screenshots |
| iOS build/runtime | NOT RUN — Windows host; no macOS/iOS runner |

Therefore **G1 BLOCKED / INCOMPLETE**, even with passing automated checks/builds.
All components remain **Preview**, not Stable. No Phase 02 implementation allowed
by this record. Native plugin consent/admission evidence remains owned by G5/G7;
no trust/authorization guarantee inferred from widget tests or this G0 exception.

## Correctness and simplification review

Reviewed ownership/dependency guards, retained identity, focus obstruction,
external resource disposal, feature debounce/submit, native controlled filters,
raw seeds/OLED, release gallery isolation and exact fixture shrinkage. Fixed
observed passive-row semantics, covered feedback, explicit typography and Unicode
regressions with executable tests. Core domain-aware legacy patterns remain
staged, excluded from curated public API. No unrelated cleanup/dependency added.

Final scope audit: no changed dependency manifests/lockfile, build/cache/binary
paths, or private-key/common-token pattern matches in changed files. Pattern
scan is not exhaustive secret detection. Graph output remains Git-ignored.

Ponytail pass after correctness review: native controls retained; no new debounce
utility for two feature owners; no generic async workflow engine, permanent
forwarders or token mirrors. No further safe cut identified within explicit
responsive/accessibility/API requirements. Self-review is not independent review.

## Phase 02 handoff, not authorization to start

1. Close G1 physical/manual evidence or obtain a separately recorded owner
   decision; never relabel NOT RUN as PASS.
2. Preserve canonical imports, Preview public signatures, exact legacy fixture,
   source identity, reader continuation and ADR-025 authorization.
3. Consume foundation for typed dialog/sheet/drawer/navigation work only when
   Phase 02 is authorized. Keep explicit Navigator/result/cancellation contracts.
4. Do not remove later legacy patterns/modal sites opportunistically; use their
   matrix task owners. Stop here at G1.
