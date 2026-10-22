# UI Rebuild docs — Independent consistency audit

**Date:** 2026-10-22  
**Inputs:** Hikari UI Foundation Rebuild Spec v4; 2026-10-22 master + eight task plans (58 tasks); `Hikari-dev(20261021-163525).zip`.  
**Output:** reviewed v4.1 handoff (same 58 tasks), master, eight corrected plans, snapshot inventory and Gravity runbook.  
**Verification actually performed:** static source/plan inspection and structural checks; **no Flutter SDK, analyzer, tests, release build or device runtime exercised**.

## Confirmed findings repaired

| Priority | Finding before review | Source/evidence | Resolution in reviewed documents |
|---|---|---|---|
| High | Source Picker described as not popping its own route, conflicting with plan 02/08 | `lib/features/catalog/widgets/catalog_source_picker.dart`, `_close()` calls `Navigator.of(context).pop()`; `catalog_detail_page.dart` has `_showSourceSheet`/`_showSourceDialog` | Spec §§1,4.5,18.1 and baseline inventory correct both ownership paths |
| High | Presenter public enum exposes `sidePanel` despite no SidePanel host in 58 tasks | Spec §4.2/master/plan 02/01 vs plan 02 implementation | Required v4.1 transient modes are `dialog` and `bottomSheet`; navigation Drawer stays distinct; SidePanel requires separate future reviewed change |
| High | Features→`app` restriction inconsistent (`app/theme` only vs architecture says one-way) | Spec §2, master dependency table, guard tasks | Explicitly ban feature→`app/**` including navigation; preserve app-owned routing through provided callbacks/commands |
| High | Phase 01 moves theme tokens but old imports remain until later task; build may break | Source app/theme re-exports tokens; plan 01/01 versus 01/07 | Require all-at-once import migration **or** temporary export-forwarders with single authoritative implementation and deadline 07/01 |
| High | Baseline tasks and hardware measurement tasks demand artificial failing tests | Plan 00 tasks 02–04/07; plan 07 tasks 06–07 | Permit truthful GREEN characterization; split widget test assertions from physical device/profile evidence |
| Medium | Spec conceptual Phase 0–5 numbering and master 00–07 sequence ambiguous | Spec §§8,18.2 versus master dependency graph | Added explicit crosswalk and reordered spec sequencing to match 00–07 execution |
| Medium | `HikariListRow` promised in spec but no task actually implements it | Spec §3.2 vs task lists | Added named UI/content deliverable to plan 01/05 and test expectations |
| Medium | 23 first-use test paths incorrectly labeled as if pre-existing | Paths absent in source ZIP, e.g. `test/ui_audit_inventory_test.dart` | First-use test declarations marked `Create/Test`; actual baseline files remain `Test` |
| Medium | Generic wildcard file paths and formatting all lib/test could cause uncontrolled edits | Plan 01/07, repeated task command | Made path inventory explicit at G0 and generated task-specific `fvm dart format` commands for named Dart files |
| Medium | “19 routes” conflates page Dart files with navigator registrations/overlays | ZIP contains 19 `*page.dart`, not proof of route count | Reworded claim; added full snapshot page-file inventory and live route scanning requirement |
| Medium | Low-level modal helper return types unspecified; could accidentally conflate `null` with cancel | Plan 02/02–03 | Declared `Future<HikariPresentationResult<T>>`, explicit root/nested navigator choice, real dismissal reason interception and at-most-once completion |
| Medium | Spec/source ownership and test guards rely on text inspection without clear limits | Phase 00/06, Phase 07/02–03 | Clarified staged guard and negative fixtures, documenting AST/parser limitations; forbid fake precise results |
| Low | Review-focus examples were listed under Global Constraints in plans 05–07 | Plan structure | Moved to proper Review Focus sections |

## Facts checked against ZIP

- `.fvmrc` specifies Flutter `3.47.5`; `pubspec.yaml` constrains Dart `^3.13.4`.
- `docs/decisions/ADR-025-unified-plugin-management.md` exists; the next available number appears to be `ADR-026` **in the ZIP only**, not guaranteed live.
- `lib/core/ui/components/` and `lib/core/ui/patterns/` both exist; `ChapterRow` matches `MangaChapter` and `NovelChapter` from domain and accepts `Object`.
- There are 19 `*page.dart` files under `lib/features`; full paths enumerated separately.
- Existing source-picker widget `_close()` calls `Navigator.pop`; current catalog page also chooses sheet/dialog via width.
- The actual architecture guard contains legacy import allowances and source-layer checks that need staged migration.

## Remaining implementation-stage decisions (must be resolved with code/tests, not silently invented)

1. Historical v4.1 question, superseded by final v4.2 correction below: `useRootNavigator` is required explicitly for `HikariDialog`, `HikariBottomSheet` and `HikariAdaptivePresenter`. Remaining parameter details and nested Navigator/Back behavior require Phase 02 compile/widget tests; requiredness is no longer open.
2. Event classification for barrier vs Back vs drag under real Flutter route lifecycle, including externally disposed routes; do not infer the reason from a nullable framework result.
3. Exact responsive min/max extents and card height under 2x/3x text scaling on constrained windows. Existing thresholds are starting points, not visual guarantees.
4. Restoration scope for forms/selection routes; standard `showDialog` does not automatically enable state restoration (see official Flutter docs).
5. Whether compile-time AST tooling can be implemented with existing dependencies; if a new dev dependency is needed, request approval or use conservative documented lexer/fixtures, never a naïve grep-only ban.
6. Final device benchmark thresholds and source/plugin live E2E fixtures depend on measured G0 baseline and accessible hardware.

**Sign-off guidance:** The repaired package is appropriate for **Phase 00 kickoff / reviewed planning**. It is **not yet an executable API proof**: G0 must verify current branch and actual tests, and subsequent phase gates must confirm real contracts. No document-only review can guarantee a 58-task implementation is conflict-free when live code changes.

## Verification references

- Flutter adaptive window/local constraints: https://docs.flutter.dev/ui/adaptive-responsive/general
- Flutter architecture/UDF: https://docs.flutter.dev/app-architecture/guide
- Flutter `showDialog` restoration caveat: https://api.flutter.dev/flutter/material/showDialog.html
- Flutter `Navigator.restorablePush`: https://api.flutter.dev/flutter/widgets/Navigator/restorablePush.html

## Final adversarial pass — v4.2 (2026-10-22)

The previous v4.1 package was **not yet fully conflict-free**. The following corrections were made without changing the 58-task scope:

1. **Hidden Navigator mismatch (high):** Flutter's `showDialog` defaults `useRootNavigator` to `true`, while `showModalBottomSheet` defaults it to `false`. The new contract requires a caller-specified `useRootNavigator` for all three presenter entrypoints and identical forwarding through adaptive mode selection. Source: https://api.flutter.dev/flutter/material/showDialog.html and https://api.flutter.dev/flutter/material/showModalBottomSheet.html.
2. **False-precision dismissal contract (high):** Stock framework route completion alone cannot identify whether dismissal was Back, barrier or drag. The contract now emits precise reasons only if observed; otherwise `routeDismissed`, never speculative attribution.
3. **Native builds incorrectly described as a failing unit test (high):** Phase 07 Task 05 is now an evidence-based build/analysis/test gate with real exit codes and host requirements, not TDD for external processes.
4. **Formatting scope contradiction (medium):** Master no longer directs mutating `fvm dart format lib test`; final whole-tree check uses read-only `--output=none --set-exit-if-changed`, while implementation task formatting is limited to changed files.
5. **Misleading directory and task metadata (low):** Removed `side_panel/` from mandatory target tree; clarified an Interface description and re-labeled previously created `test/ui_documentation_contract_test.dart` as a later-phase Test.
6. **Execution style mismatch (medium):** All plan headers now choose native sequential `executing-plans`, consistent with no subagents/worktrees/automatic commits.

**Remaining known risk:** Public Dart APIs are still target signatures, not compiler-verified source; Phase 02 freezes them only after its tests. Cross-phase changes must be reflected in spec, master and affected phase plans before moving forward.

7. **Previously uncreated final acceptance report (high for execution):** Phase 07 Task 05 now explicitly creates `docs/ui/FINAL_ACCEPTANCE_REPORT.md`; Tasks 06–07 modify that same file. Earlier path-scan validation incorrectly missed this first-use `Modify`.
8. **Negative fixture handling:** The baseline guard now names four `.dart.txt` fixture files, and the final guard task names its exact files. This avoids accidentally compiling intentionally forbidden negative examples as Dart source during `flutter analyze`.
