# Hikari UI Rebuild — Master Implementation Plan

> **For Gravity:** Use `superpowers:executing-plans` to work through this plan sequentially with explicit review gates; do not start agents, worktrees, commits or branches without separate user authorization. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Execute Hikari UI Foundation v4.2 as a complete and maintainable rebuild across Android and Windows, with one canonical UI system and zero permanent bespoke shared UI mechanisms.

**Architecture:** This master governs eight independently reviewable workstreams (00–07), each containing TDD-style tasks, typed interface handoffs and acceptance gates. Foundations precede modal and navigation mechanics, which precede card/layout primitives; feature migrations then consume those contracts, followed by deletion and hardware-based release verification.

**Tech Stack:** Flutter 3.47.5, Dart >=3.13.4, Material 3, existing ChangeNotifier/ViewModel, Flutter test/analysis, Android+Windows native builds, DevTools/profile.

**Spec:** `docs/superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md` (v4.2); repository snapshot `Hikari-dev(20261021-163525).zip` reviewed statically.

Live Phase 00: [baseline runs, owner handoff and deferred evidence](../../ui/BASELINE_RUNS.md),
[migration matrix](../../ui/MIGRATION_MATRIX.md),
[accepted ADR-026](../../decisions/ADR-026-ui-foundation-rebuild.md),
[reviewed handoff](../handoff/README_GRAVITY.md).
Original G0 handoff (2026-10-22) deferred physical evidence. Subsequent G1/G2
execution reports documented missing hardware evidence truthfully.

**Current decision (2026-10-23): G0, G1 and G2 are OWNER-ACCEPTED for
continuation; Phase 03 Task 01 is authorized** after running the new focused
Escape/MenuAnchor regression. See [dated approval and exceptions](../../roadmap/UI_FOUNDATION_OWNER_ACCEPTANCE_2026-10-23.md),
[Phase 01 execution](../../roadmap/UI_FOUNDATION_PHASE_01.md) and
[Phase 02 execution](../../roadmap/UI_FOUNDATION_PHASE_02.md).
This is **not** retrospective technical PASS for original `NOT RUN` or
`NOT MEASURED` rows. Catalog Detail 3x hero/notice has an explicit Phase 04
Task 05 remediation; G3–G7 acceptance remains independent.

## Global Constraints

- This is an intentional **rebuild**, not a minimal diff or historical-ADR preservation exercise. Supersede conflicting UI decisions in an accepted ADR; keep non-UI source identity, cache, progress, plugin native trust and user consent contracts intact.
- Source tree supplied is a zip snapshot, not a live Git revision. All file names are grounded in that snapshot or explicitly prefixed `Create:`; validate current checkout before execution.
- No worktree/isolation, automatic branches, commits, pushes or merges; tasks have evidence checkpoints instead of git commands (user workflow preference).
- No separate state-management framework or new dependencies without an explicit technical decision.
- No legacy UI compatibility layer at final acceptance. Transitional bridges may exist only within named migration tasks with an expiry.
- Foundation components own common presentation mechanics. Features own content, domain mapping and authorization. The app owns ThemeData composition/navigation source-of-truth.
- Every task starts with test failure (except baseline characterization/documentation steps where naturally already green), has one reviewer-sized deliverable, and ends with self-review and evidence.

## Review Focus

- **Null as valid selection:** `HikariCompleted(null)` must never mean cancellation (Plan 02 Task 01/04).
- **Modal concurrent dismissal:** Back/barrier/drag competing with submit must produce only one result; when the framework does not expose the exact cause, report `routeDismissed` (Plan 02 Task 02–04).
- **Source identity and async stale outcomes:** old picker/plugin result may not act on a newly selected media source (Plan 02 Task 08/09; Plan 05 Task 03/05).
- **Nonlinear text scaling / short-window constraints:** 3x text and IME must not hide critical actions (Plans 01–03 and 07 E2E).
- **Zero permissions regression:** APK/Novel trust and native consent remain outside shared modal host (Plan 05 and 07 E2E).

---

## 1. Execution graph and ownership

```text
00 Baseline/ADR/regression fixtures
  └─ 01 Foundation tokens + controls + gallery
       ├─ 02 Surface system + adaptive presenter + navigation/page shell + Pilot A
       └─ 03 Media/artwork/layout + Pilot B  (needs 02 page shell for Pilot B)
            └─ 04 Waves A/B — Home, Library, Local, Search, Catalog & source detail
                 └─ 05 Wave C — Settings, Plugin Manager, APK/Novel operations
                      └─ 06 Wave D — Readers, remote chapter routes, Player
                           └─ 07 Legacy deletion, strict guards and device acceptance
```

**Parallelism policy:** policy/model tests and media anatomy can progress after 01, but foundation public signatures freeze before consumer migration. Do not independently edit shared `lib/core/ui` contracts in different agents. No parallel subagents/worktrees by default under user's preference; run sequentially unless later explicitly authorized.

## 2. Plan files (read in order)

| Workstream | Plan | Tasks | Review gate |
|---|---|---:|---|
| 00 | [Baseline & ADR](2026-10-22-hikari-ui-00-baseline-and-adr.md) | 7 | route + modal inventory, behavior baselines, superseding ADR, staged guard |
| 01 | [Foundation & controls](2026-10-22-hikari-ui-01-foundation-controls.md) | 7 | one token authority, stable controls, gallery and tests |
| 02 | [Surfaces, navigation & Source Picker](2026-10-22-hikari-ui-02-surfaces-shell-picker.md) | 9 | Dialog/Sheet/Drawer, typed presenter, Pilot A E2E |
| 03 | [Media & layouts](2026-10-22-hikari-ui-03-media-layouts.md) | 8 | poster/artwork/grid/shelf, domain-free chapter row, Pilot B |
| 04 | [Home, Library, Local, Search & Detail](2026-10-22-hikari-ui-04-browse-detail-migration.md) | 8 | Wave A/B all canonical + source identity regression |
| 05 | [Settings & plugin manager](2026-10-22-hikari-ui-05-settings-plugins.md) | 6 | Wave C no bespoke modal, native trust test gates |
| 06 | [Readers & Player](2026-10-22-hikari-ui-06-reader-player.md) | 6 | Wave D immersive exceptions + reader progress preserved |
| 07 | [Cleanup & verification](2026-10-22-hikari-ui-07-cleanup-verification.md) | 7 | zero old UI APIs, all strict guards, Android/Windows E2E/profile |

**Total: 58 reviewer-sized tasks**. Complete by gate, not by elapsed time or superficial analyzer success.

## 3. Canonical type and ownership contract (keep stable across plans)

```dart
// lib/core/ui/surfaces/adaptive/hikari_presentation_models.dart
// (Exact public API is finalized by Plan 02 Task 01, before consumers migrate.)
enum HikariPresentationPurpose { confirmation, selection, form }
enum HikariPresentationMode { dialog, bottomSheet }
enum HikariDismissReason { cancelAction, closeButton, back, barrier, drag, routeDismissed, routeDisposed }
sealed class HikariPresentationResult<T> { const HikariPresentationResult(); }
final class HikariCompleted<T> extends HikariPresentationResult<T> {
  const HikariCompleted(this.value);
  final T value;
}
final class HikariCancelled<T> extends HikariPresentationResult<T> {
  const HikariCancelled(this.reason);
  final HikariDismissReason reason;
}

// Note: Drawer is navigation state, not HikariPresentationResult.
// Feature owns VM and validates result against source/series generation.
// All dialog/sheet/adaptive helpers require useRootNavigator; no mode-dependent defaults.
// Ambiguous external dismissal -> routeDismissed (never fabricate back/barrier/drag).
```

**Dependency graph:** `core/ui/foundation → Flutter`; `core/ui/{controls,surfaces,layout,media,content} → foundation + Flutter`; `features → core/ui + domain/application abstractions`; `app/theme → core/ui/foundation`; `app/navigation → core/ui/navigation + feature routes`. Banned: `core/ui → app/theme|features|domain|application|infrastructure`, `features → app/**|infrastructure` and `app/theme → feature UI`.

**State ownership:** feature query/data/series/source = feature ViewModel; focus/text input controller = creating widget; active modal route/dismiss/result = shared host; destinations/selected tab = app shell; plugin install/consent = application+native gateway.

## 4. Migration coverage and non-negotiable E2E

| Route family | Owner task(s) | Must not regress |
|---|---|---|
| Home | 04/01 | Continue-first; artwork; discovery; lazy shelf |
| Library | 03/07, 04/02 | all-origins confirmation, saved identity, cancel |
| Local Media | 04/03 | local scan/cache/identity |
| Catalog Search & Source Search | 03/07, 04/04 | explicit submit, stale response, independent source-first path |
| Catalog Detail + Picker | 02/08–09, 04/05 | tabs/scroll; language; source; manual search; Reader |
| Remote manga & novel series detail | 04/06 | source-first detail distinct from catalog-first |
| Continue Reading | 04/07 | direct Reader exact source, fallback/cache UX |
| Settings | 05/01 | dark/OLED and cache accordion persisted |
| Plugin Manager | 05/02 | installed/update filters; repository typed forms |
| APK & Novel Actions | 05/03–05 | source/package trust, native user consent |
| Manga/remote manga Reader | 06/01–02 | progress, order, gestures, prefetch and cache |
| Novel/publication/remote novel Reader | 06/03–04 | typography, chapter progress and offline states |
| Player | 06/05 | immersive video surface and engine lifecycle |
| App Shell / Drawer / Header | 02/06–07 | destination state preserved on resize and focus |

**19 page files discovered in the supplied snapshot (not proof of exactly 19 navigable routes):** `catalog_detail_page`, `catalog_search_page`, `home_page`, `library_page`, `local_media_page`, `manga_reader_page`, `novel_reader_page`, `publication_reader_page`, `player_page`, `apk_actions_page`, `novel_actions_page`, `plugin_manager_page`, `continue_reading_page`, `manga_chapter_reader_page`, `manga_series_page`, `novel_chapter_reader_page`, `novel_series_page`, `settings_page`, `source_search_page` (all under `lib/features/**`). Plan 00 Task 01 must enumerate their **full paths**, plus overlays, top-level shell and any added routes found in the live checkout before coverage can reach 100%.

## 4A. Spec-to-task traceability (coverage of v4 §§0–20)

| Spec v4 section | Primary implementation plan/task | Acceptance evidence |
|---|---|---|
| §0 intent, authority and non-goals | 00/05, master constraints | Superseding ADR, domain/security contracts unmodified |
| §1 snapshot audit | 00/01–04, 00/07 | Complete inventory and baseline |
| §2 dependency graph and folder design | 01/01–07, 07/01–03 | No forbidden import/export or old UI paths |
| §3 tokens, component states, artwork, geometry | 01/01–06, 03/01–03 | Theme, control, geometry tests |
| §4 Dialog/Sheet/Drawer/presenter/picker | 02/01–09 | Typed result, cancellation, nested navigator, Pilot A |
| §5 shell/nav/constraints/input | 02/06–07, 03/04–05 | Focus and reflow tests |
| §6 ViewModel/presentation state/async | 02/08, 04/01–07, 05/01–05 | No BuildContext in ViewModel; stale generation tests |
| §7 every feature screen | 04/01–08, 05/01–06, 06/01–06 | All 19 routes marked migrated |
| §8 rebuild phases and gates | Master G0–G7, 00–07 | Blocked phases remain open |
| §9 quality + Definition of Done | All task steps, 07/05–07 | Full tests/builds, hardware E2E and profile |
| §10 conflicting ADR cleanup | 00/05, 07/04 | Clause-level supersession and updated docs |
| §11 external design-system lessons/catalog | 01/06, 03/08, 07/04 | Gallery and stable component catalog |
| §12 component API, ownership, naming | 01/03–06, 02/01–04, 03/01–06 | Typed contracts, discoverable public exports |
| §13 adaptive surface and page shell | 02/01–07 | Pure resolver, correct drawer semantics, constraints |
| §14 state transitions and errors | 01/05, 02/08–09, 04–06 | Loading/empty/refresh/offline/stale response tests |
| §15 accessibility, localization readiness | 01–03 component fixtures, 07/06 | TalkBack, Windows keyboard, RTL, large text |
| §16 guard/performance/testing tiers | 00/06–07, 03/08, 07/02–07 | Strict static checks + matched profile |
| §17 component lifecycle/governance | 01/06, 07/04 | Draft/Preview/Stable/Deprecated rules |
| §18 pilot migrations | 02/08–09, 03/07–08, 05/01–05 | Pilot A/B/C E2E checkpoints |
| §19 adversarial risks | Every plan Review Focus, 07/01–07 | No unreviewed exceptions/regressions |
| §20 final acceptance | 07/05–07 | Dated acceptance report and G7 signoff |

This table is the traceability baseline; if a task reveals an undocumented consumer, add that path to `MIGRATION_MATRIX.md` before implementation on it. No section is satisfied solely by this table.

## 5. Phase gates and stop rules

**Actual repository first:** The archive is evidence, not the execution checkout. At G0 compare live `.fvmrc`, `pubspec.yaml`, existing ADR numbers, route inventory, tests and uncommitted changes against the snapshot; on mismatch, update only affected plan paths and the migration matrix *before* running a task. Do not invent current build/test results. Source ZIP is not required for implementation if the live repository is available.

**Checkable gate semantics:** A gate passes only when (a) machine-verifiable checks have recorded exit codes, (b) manual/platform checks have dated device evidence, and (c) no mandatory pending item remains. `NOT RUN`, `BLOCKED`, `NOT APPLICABLE` and `KNOWN ISSUE` are **not** PASS. Baseline/characterization tasks may start GREEN; do not manufacture a RED by changing working behavior or weakening tests.

**Owner-scoped G0 transition (2026-10-22):** The owner accepted the G0 handoff
and explicitly authorized Phase 01 **with physical baseline evidence deferred**;
see [dated decision and outstanding checks](../../ui/BASELINE_RUNS.md).
The original G0 technical gate is **not marked PASS**; status is
`OWNER-ACCEPTED / PHYSICAL EVIDENCE DEFERRED`. This exception allows only Phase 01
to start and does not waive security, regressions or G1–G7 acceptance criteria.
All pending native, accessibility, visual and performance checks retain their
`NOT RUN`/`NOT MEASURED` statuses and required later owners/deadlines.


- **G0:** Real branch/snapshot documented, all 19 page entrypoints inventoried, modal imports/calls mapped, ADR drafted/approved and baseline tests recorded.
- **G1:** Foundation compiles with one token source; dark/OLED/theme tests, component gallery and import guard pass.
- **G2:** Typed Dialog/Sheet/Drawer/shell tests pass; Pilot A proves real feature selection and cancellation with responsive focus and no double result.
- **G3:** Media artwork/card/grid/shelf stress tests pass; two real consumers prove identity/lazy layout; domain-free core rows.
- **G4:** All browse/detail routes migrated; no direct modal routes or stale shared geometry in those paths; workflow tests pass.
- **G5:** Settings and Plugin Manager actions migrated; cancellation and APK/LN safety tests pass.
- **G6:** Readers/player migrated; specialized canvases documented and behavior tests pass.
- **G7:** No old UI compatibility components remain; strict guard + full tests + Android/Windows builds + accessibility/E2E/profile reports pass.

**Stop immediately if:** a migration weakens source identity/consent/progress, a feature introduces duplicate modal machinery, a test must be deleted to make architecture look green, or a proposed shared API forces feature-domain types into core UI. Fix architecture or make an explicit reviewed product decision; do not waive regressions silently.

## 6. Commands and verification protocol

```powershell
# Per task: format only the changed Dart paths listed in the active task.
# Phase gate: verify all source/test files are formatted without modifying unrelated files.
fvm dart format --output=none --set-exit-if-changed lib test
fvm flutter analyze --no-pub
fvm flutter test -r expanded
fvm flutter build apk --debug
fvm flutter build apk --release
fvm flutter build windows --debug
```

Run only commands supported on the actual host. In a Windows checkout, prefer the project's `tool/check.ps1` where it adds meaningful coverage; capture executable versions/exit codes. Android hardware gate: Redmi Note 9S (LineageOS); desktop gate: Windows resize/keyboard/hover. Flutter test green does **not** substitute for native plugin installer, actual source reading or TalkBack verification.

## 7. End-of-task evidence template

```text
Task / path:
Implemented API and old->new callers:
RED test name and reason:
GREEN focused tests / analyzer:
Full gate status:
Visual/keyboard/device observations:
Remaining legacy consumers:
Known risks, exceptions and pending hardware checks:
Self-review (ownership, API shape, stale async, performance):
```

## 8. Plan use and handoff

**Handoff order for Gravity / other implementation agents:** read `README_GRAVITY.md` → this master → design spec → the active phase plan → actual code/test baseline. Never execute tasks using only the master table. A phase is blocked while its prerequisites are incomplete. Human approval is required for changing public cross-phase APIs, adding dependencies, weakening security/source identity constraints, or declaring an exception.


1. Review this master and the first plan before authorizing implementation; the user's approved spec remains authoritative.
2. Execute one task at a time with Superpowers TDD, then review diff and run focused gates; advance G0→G7 in order.
3. Do not spawn worktrees or automatically commit. Do not claim a gate PASS
   with pending mandatory evidence. The explicitly dated owner-accepted G0
   transition above is a narrow authorization, not a technical PASS.
4. Each task's Files/Interfaces/Steps are implementer instructions; adjust file names only when a path differs in the live checkout and record the mapping.
5. Once G7 passes, finalize docs and acceptance report, including any accepted *technical* exceptions with owner/tests/revisit criteria. No indefinite compatibility branch.

**Execution recommendation:** use sequential Native execution with a reviewer at each phase gate, since the surface/media contracts share cross-cutting interfaces and uncontrolled parallel edits can conflict. If the user later authorizes subagent-driven review, delegate one task per agent and never create isolated worktrees.
