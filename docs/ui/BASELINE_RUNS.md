# Phase 00 baseline runs

Date: 2026-10-22. Branch `feat/ui-foundation-rebuild`;
HEAD `8c07d34e88eeae586ab7d74cfe2cffe20701136d`.
Initial working tree: untracked user-supplied `docs/handoff/` only.
Windows 11 Pro 10.0.26300, FVM 4.3.1, Flutter 3.47.5 stable
(framework 6a19cca564), Dart 3.13.4 windows_x64, DevTools 2.60.0.
No dependencies, production UI, domain, persistence or native security changed.

## Recorded commands and outcomes

| Scope | Command | Actual outcome |
|---|---|---|
| Pre-change | `fvm flutter analyze --no-pub` | exit 0, no issues, 5.9s |
| Pre-change regression | `fvm flutter test -r expanded test/architecture_guard_test.dart test/features/catalog/catalog_detail_page_test.dart test/features/catalog/catalog_source_picker_view_model_test.dart test/library_page_test.dart test/home_page_test.dart test/plugins/apk_actions_page_test.dart test/plugins/novel_actions_page_test.dart test/navigation_shell_test.dart test/theme_test.dart` | exit 0 |
| 00/01 inventory RED | `fvm flutter test -r expanded test/ui_audit_inventory_test.dart` | exit 1, five expected missing-inventory assertion failures; later matrix contract also failed on empty output |
| 00/01 inventory GREEN | same command after implementation | exit 0, 6 tests; analyzer clean 4.5s |
| 00/02 characterization | `fvm flutter test -r expanded test/features/catalog/catalog_detail_page_test.dart test/features/catalog/catalog_source_picker_view_model_test.dart` | exit 0, 58 tests; analyzer clean 4.6s |
| 00/03 characterization | `fvm flutter test -r expanded test/library_page_test.dart test/home_page_test.dart test/reader_first_sequence_test.dart test/chapter_reader_routes_test.dart` | exit 0, 33 tests; analyzer clean 4.8s |
| 00/04 characterization | `fvm flutter test -r expanded test/plugins/apk_actions_page_test.dart test/plugins/novel_actions_page_test.dart test/navigation_shell_test.dart` | exit 0, 56 tests; analyzer clean 4.8s |
| 00/05 docs RED | `fvm flutter test -r expanded test/ui_documentation_contract_test.dart` | exit 1; expected missing ADR assertion, dependent ADR read failed |
| 00/05 docs GREEN | same command after canonical docs created | exit 0, 3 tests |
| 00/06 guard RED | `fvm flutter test -r expanded test/architecture_guard_test.dart` | exit 1, three expected missing-enforcement assertions; later two targeted failures proved app/theme inversion and part bypass |
| 00/06 inventory follow-ups RED | inventory test command | exit 1 for missing shared-domain dependency, then exit 1 for missing PopScope/pop inventory |
| 00/01+05+06 GREEN | `fvm flutter test -r expanded test/ui_audit_inventory_test.dart test/ui_documentation_contract_test.dart test/architecture_guard_test.dart` | exit 0, 28 tests |
| 00/07 theme + guard + docs | `fvm flutter test -r expanded test/architecture_guard_test.dart test/theme_test.dart test/ui_documentation_contract_test.dart` | exit 0, 24 tests before two additional guard regressions; analyzer clean 4.8s |
| Exact snapshot contract RED | `./tool/check.ps1` after adding full matrix comparison | exit 1; 1074 passed, 1 skipped, 1 failed: PowerShell CRLF output differed from renderer LF; content unchanged. Contract now normalizes CRLF only. Analyzer clean 3.7s. |
| Final full workspace gate | `./tool/check.ps1` after newline normalization and final guard/matrix assertions | exit 0; locked dependencies; 330 Dart files format-checked, 0 changed; analyzer clean 3.7s; 1075 passed, 1 skipped; both whitespace checks clean |
| Full workspace gate, second run | `./tool/check.ps1` after membership fixtures and simplification | exit 0; 330 Dart files, 0 format changes; analyzer clean 3.8s; 1075 passed, 1 skipped; whitespace checks clean |
| Full workspace gate, first run | `./tool/check.ps1` | exit 0; locked dependencies; 330 Dart files format-checked, 0 changed; analyzer clean 3.8s; 1074 passed, 1 skipped; whitespace checks clean |
| Catalog/Source membership follow-up | `fvm flutter test -r expanded test/library_page_test.dart test/features/catalog/catalog_detail_page_test.dart` | exit 0, 49 tests; characterization GREEN, no production fix |
| Windows compile | `fvm flutter build windows --debug --no-pub` | exit 0; built debug hikari.exe, 19.7s; app NOT RUN |
| Android compile | `fvm flutter build apk --debug --no-pub` | exit 0; assembleDebug 49.9s; app NOT INSTALLED/RUN |
| Android release compile | `fvm flutter build apk --release --no-pub` | exit 0; assembleRelease 61.7s; 115.5MB APK; NOT INSTALLED/RUN |
| Graph navigation refresh | `graphify update .` | exit 0; final AST-only refresh 8445 nodes; 4 Kotlin parse warnings, graph not behavioral proof |

Skipped test: opt-in `test/plugins/upstream_manifest_smoke_test.dart`, requiring
`HIKARI_MANGA_MANIFEST`, `HIKARI_MANGA_METADATA`, `HIKARI_NOVEL_MANIFEST` local
upstream fixtures. No upstream parsing/network/native pass inferred.

Characterization begins GREEN, not artificial RED. Catalog cancellation does not
open Reader/write preference; manual search retains source set/language. Existing
identity A/B/A, exact candidate, saved choice and tabs/scroll regressions retained.
Library refresh preserves saved artwork; ContinueShelf forwards exact chapter and
source without fallback openMedia. This widget callback test is not app/native E2E.
Plugin Cancel/Back tests use fake gateways; existing native-denial/stale tuple
fixtures simulate authority and do not establish actual Android security behavior.
Navigation retained-child test covers all width boundaries through 1440.

## Current implementation discrepancies

- 19 page files, not 19 route registrations. App uses imperative routes and
  dynamic reader builders; [matrix](MIGRATION_MATRIX.md) records reviewed targets.
- Handoff originally under `docs/handoff/`; all three inputs deliberately moved
  to `docs/superpowers/handoff/`, with historical evidence preserved and live links added.
- Historical audit item 1 leaves Navigator parameter optionality open; its final
  v4.2 correction and normative spec require explicit `useRootNavigator`.
- Home Featured currently precedes Continue; Continue-first is target 04/01.
- Tokens remain app/theme; patterns remain domain-aware; no target migration yet.
- No current light-theme factory; reader paper palette is not app light support.
- Existing picker landscape test suppresses hero-specific layout errors. Its
  GREEN result proves picker scope, not full-page 3x hero accessibility.
- Lexer is not type resolution; dynamic aliases/interpolated execution remain
  manual review. Findings include definitions/references, not exact call counts.

## Inventory totals

372 lexical findings: 19 page files, 12 route-constructor references, 17 modal
entrypoint references, 55 overlay references, 174 imports, 7 exports, 20 shared
files, 32 Navigator references, 21 exit references, 4 PopScope policies, 5 route
observations, 2 navigation surfaces, 2 shell references, 1 navigation owner and
1 retention reference. These are not counts of distinct registered routes or
executed calls. Frozen staged allowlist contains 75 exact legacy sites, each
linked to matrix owner and removal task.

## Task self-review (sequential, autonomous; not owner approval)

| Task | Changed deliverable / reviewed contract | Evidence / remaining callers and limits |
|---|---|---|
| 00/01 | stdlib lexer, deterministic inventory tests, full matrix; manually traced app builders, shell, picker, plugin routes and shared overlays | RED/GREEN above. All 19 page files represented; lexical references are not resolved call counts. Dynamic builder review remains mandatory at migration. No source moved. |
| 00/02 | Catalog detail cancellation and exact source/language forwarding assertions; added Catalog membership fixture | GREEN characterization. Existing picker ViewModel tests already cover required state contracts and were rerun unchanged, not edited merely to satisfy a file list. Existing source/series stale and tab-scroll fixtures retained. |
| 00/03 | Home exact saved chapter callback, Library artwork refresh and Source Search membership fixture | GREEN characterization. Catalog/Source/Library/Local share add/cancel/remove behavior; existing `test/features/library/library_button_view_model_test.dart` covers races. `test/reader_first_sequence_test.dart` and `test/chapter_reader_routes_test.dart` cover reader-first routing/cache. Offline/device behavior is not newly fixed or proven. |
| 00/04 | APK Cancel/Back, Novel Cancel, shell boundary widths; reused existing Novel Back fixture | Fake gateways only. Existing native denial, stale tuple and identity-change fixtures retained. Actual OS installer, trust, admission and keyboard/focus checks NOT RUN. No native authority moved. |
| 00/05 | Initially proposed ADR, foundations/responsive/components references, current-vs-target headers, links and handoff placement | Docs contract GREEN; clause review preserves ADR-009/023/024/025. ADR-026 was vacant in checkout. Approval was missing at initial run; the owner approved it later on 2026-10-22 (see addendum below). |
| 00/06 | staged guard, four negative `.dart.txt` fixtures, frozen exact legacy sites | RED/GREEN proves relative/export/part and component-family inversion. Existing app/theme checker now permits foundation composition, not arbitrary shared widgets. Frozen IDs refer to matrix owner/removal task; regeneration alone cannot bless new violations. Legacy source stays unchanged. Lexer is not security/type analysis. |
| 00/07 | OLED characterization, current-vs-historical performance distinction, metadata and capture protocol | Theme tests GREEN; host debug builds succeeded. No screenshot, device interaction or timing fabricated. Physical baseline remains incomplete. |

Correctness review: no `lib/**`, native, dependency, persistence or cache edits;
existing assertions retained. Later source edits must remove/review exact legacy
sites at their assigned task, no later than 07/01. No permanent exemption added.
Ponytail review removed duplicate newly added Novel Back coverage while preserving
the original Back test and new Cancel/admission assertions. No further safe cut
identified in requested inventory/guard harness. Self-review is not independent
review or owner approval.

## Reproducible visual capture protocol

Device discovery: `fvm flutter devices` listed Windows desktop; `adb devices -l`
returned an empty device list. Windows OS reported 10.0.26300.9711. Android Redmi
Note 9S unavailable. Windows compilation is not interactive keyboard evidence.

| Capture axis | Required fixture / metadata | Current result |
|---|---|---|
| Build | exact Git HEAD + dirty diff hash, Flutter/Dart, command, debug/profile, device model, OS, renderer, DPR | host/SDK above; screenshot build NOT RUN |
| Theme | dark and OLED, default accent and raw seed 0xFF06B6D4 | unit expectations verified; screenshots NOT RUN |
| Width | 375/840/1440 logical pixels; boundaries 599/600/601 and 839/840/841 | shell retention simulated; screenshots NOT RUN |
| Text | 1x/2x/3x, locale, platform/nonlinear scaling setting | existing picker cases simulated; whole-app visual grid NOT RUN |
| Fixtures | Home Featured/Continue, populated/empty Library, Catalog Information/Chapters, source language/candidates/manual search, plugin consent, reader | deterministic fake fixtures in tests; captured images NOT RUN |
| State | selected tab, query, scroll, pending form, modal open during resize, short landscape, safe area and visible IME | partial widget coverage only; actual input/device checks NOT RUN |
| Accessibility | Android TalkBack/Back and Windows Tab/Shift+Tab/Enter/Escape/focus restoration | NOT RUN |
| Performance | matched profile build/device/thermal/cache/dataset, cold + two warm runs, frame counts/p50/p95/p99 and memory | NOT MEASURED |
| Light | application light theme | NOT APPLICABLE to current code; no light factory |

Capture steps:
1. Use isolated test data, never real plugin credentials or personal Library data.
   Record HEAD and dirty patch SHA-256; use the same fixture/dataset for both runs.
2. Build/run `fvm flutter run -d windows --profile` or
   `fvm flutter run -d <android-device-id> --profile`. Record actual device and mode;
   debug timings are not performance baseline. Do not infer Windows profile results
   from debug compilation.
3. Set logical viewport using measured content dimensions and DPR, not guessed
   physical screen size. Repeat 375/840/1440 at 1x/2x/3x in dark/OLED; add boundary,
   landscape and IME cases. Record theme, accent, locale, text-scale method and route.
4. Save full-window captures with names
   `<device>_<route>_<width>x<height>_<scale>_<theme>_<state>.png`, actual timestamp
   and SHA-256. Android capture may use `adb -s <id> shell screencap -p <device-path>`
   then `adb -s <id> pull <device-path> <local-path>`. Inspect each image; retain
   overflow/focus defects as KNOWN ISSUE, never silently crop them out.
5. Follow [performance protocol](../quality/UI_PERFORMANCE_BASELINE.md) for trace
   collection and matched-run comparisons. Record absent traces NOT MEASURED.
   Native consent tests require separate dated device evidence; a screenshot or
   fake-gateway pass cannot establish authorization safety.

Android debug build emitted existing JDK restricted-native-access, Kotlin Gradle
Plugin migration and SDK XML version warnings. Build exit 0 does not erase these
warnings. Android release compilation also succeeded with the first two warning
families. Windows release build, app launch and physical-platform verification NOT RUN.
Historical performance values are not measurements of this branch.

## Owner approval and G0 exception — 2026-10-22

**Decision:** The Hikari project owner explicitly approved
[ADR-026](../decisions/ADR-026-ui-foundation-rebuild.md) and authorized
proceeding from Phase 00 to Phase 01 on 2026-10-22. This is an **owner-accepted
G0 handoff with a documented device-evidence deferral**, **not** a claim of
technical G0 PASS under the original all-checks-complete rule.
The owner accepted the missing *baseline measurement* risk to permit Phase 01
foundation work. The unchanged automated evidence above belongs to the prior
Phase 00 checkout; no additional Flutter run/build is claimed by this addendum.

**Deferred, not waived as test results:** real Android app launch and native APK
installer/Novel admission and consent checks; Android TalkBack; Windows
interactive keyboard/focus/resize; screenshots including large text/IME;
matched Android/Windows profile frame and memory timings. All remain
**NOT RUN / NOT MEASURED** on the recorded baseline. Fake gateways and host
builds do not prove any of them.

**Conditions of owner authorization:**

- Phase 01 may begin after this approval patch is applied and its documentation
  consistency checks pass. Phase 01 cannot claim G1 PASS without satisfying its
  own applicable gate; approval does not preapprove G1–G7.
- Record the original pre-migration physical captures **before touching each
  affected UI surface whenever a device is available**. If implementation has
  already changed that surface, record a **LOST COMPARABLE BASELINE** limitation;
  never fabricate or retrospectively label a new screenshot as old behavior.
- Attempt Windows interactive focus/keyboard/resize and Android visual/TalkBack
  checks at G1/G2 as relevant. Missing relevant device proof remains a blocker
  to the corresponding later gate; do not silently carry it to final acceptance.
- Complete actual APK/native/Novel admission consent checks before claiming
  the relevant plugin migration gate (G5) or G7. Preserve ADR-025 and do not
  weaken any native trust checks because this G0 exception exists.
- Re-run analyzer, architecture guard, focused/full tests and build checks in
  the **actual Phase 01 checkout**; the reported 1,075 tests are historical,
  not a substitute for verifying the approval/hardening edits.

**Gate state at original handoff:** `G0 OWNER-ACCEPTED / PHYSICAL EVIDENCE DEFERRED`.
**Current owner decision (2026-10-23):** G0 accepted for continuation alongside
G1/G2; see [owner decision](../roadmap/UI_FOUNDATION_OWNER_ACCEPTANCE_2026-10-23.md).
**Next allowed work:** Phase 01 Task 01 under the master plan, **not** blanket
approval for subsequent phases. All unmeasured values remain unmeasured.
