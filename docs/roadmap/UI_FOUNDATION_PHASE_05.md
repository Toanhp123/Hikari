# UI Foundation Phase 05 — Wave C

Date: 2026-10-24. Status: **PHASE 05 COMPLETE / G5 OWNER-ACCEPTED FOR CONTINUATION**.
Gate result: **TECHNICAL G5 INCOMPLETE — mandatory physical/platform evidence deferred, not PASS**.
Owner acceptance: **G5 OWNER-ACCEPTED / EVIDENCE DEFERRED** on 2026-10-24;
see the [dated owner decision](UI_FOUNDATION_G5_OWNER_ACCEPTANCE_2026-10-24.md).
Phase 06: **AUTHORIZED / NOT STARTED** at this handoff. G4 acceptance remains unchanged.

## Contract and baseline

Canonical target: [v4.2 spec](../superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md), [Phase 05 plan](../superpowers/plans/2026-10-22-hikari-ui-05-settings-plugins.md), ADR-026 and ADR-025. Work ran sequentially in the existing checkout, without agents, worktrees, branch changes, commits, pushes or merges. Intermediate checkpoints were autonomous self-review gates, not Owner approvals.

Verified baseline: `feat/ui-foundation-rebuild`, `fe90646cc29df1b1c9ac26f4e0d535a3392629b6`, clean at task start. Flutter 3.47.5 / Dart 3.13.4 via FVM. The execution plan's historical ZIP is not the current checkout. G4 remains accepted and unchanged.

Focused pre-change suite: Settings, Manager page/ViewModel/corrections, APK, Novel, architecture, documentation and inventory: **134 passed**, exit 0. This is baseline evidence, not Phase 05 acceptance.

## Independent source audit

| Owner | Pre-change evidence | Required change / protected boundary |
|---|---|---|
| Settings | Legacy scaffold/confirmation; cache expansion already retained with PageStorage; async measurement generations; app-owned appearance callbacks | Canonical shell and typed confirmation; reject obsolete callback consent; preserve narrow readable width and app persistence |
| Manager | Inline raw repository form; details pops then immediately pushes native-action route; model replaced on management-session change | Feature-owned typed outcomes; await host teardown; revalidate session/model/owner/offer/repository; retain lazy cached listing |
| APK | Foreground epoch, inspected stage, exact owner/offer/signer/install revision; native journal; raw retirement dialog | Canonical shell/confirmation; typed retirement acknowledgments; preserve unknown outcome and native authority |
| Novel | Exact current/previous tuple, owner/offer/epoch checks; inert stage; sticky publication lock; legacy confirmation with custom geometry | Canonical shell/feedback/confirmation; keep evaluation consent separate from staging and runtime activation |
| Common UI | Typed presenter already settles after route teardown; closed-loop focus and safe Cancel; canonical bounded scrollable dialog | Reuse existing implementation; no second modal framework or business authorization in core |
| Architecture | Ten frozen pre-G5 modal exemptions, including two Readers | Remove only migrated Wave C exemptions after verified source audit; Reader/Player remain Phase 06/07 owned |

Ambient policy: explicitly preserve ambient-on shell behavior for these routes. Settings retains its 640dp readable bound; management/action routes retain 720dp inside canonical content regions. Native OS consent is not a Hikari modal.

Protected contracts: ADR-009 stable identity, ADR-023 Reader-first, ADR-010 bounded LNReader runtime, ADR-025 plugin management. No engine, persistence, trust, registry activation, acquisition or installer implementation changed. No dependency or core UI API changed.

## Execution and self-review checkpoints

All six implementation tasks are complete within automated/source-verifiable scope. Physical acceptance remains separate.

| Task | Changed production / evidence files | Result and review |
|---|---|---|
| 05/01 Settings | `settings_page.dart`, `widgets/settings_content.dart`, `test/settings_page_test.dart` | Canonical scaffold/region/notice and explicit typed cache consent. Stale callback test observed RED (`oldCalls` 1 instead of 0), then GREEN. System Back does not clear data or show success. Existing cache totals, expansion and appearance persistence tests retained. Accordion state remains PageStorage-lifetime, not a new persisted setting. |
| 05/02 Manager | `plugin_manager_page.dart`, `widgets/plugin_repository_dialog.dart`, `plugin_details_dialog.dart`, `plugin_repository_section.dart`; Manager page/corrections/composition tests | Repository form returns validated typed values; details returns sealed choices. Host tears down before action-route push. Keyboard Done test observed RED (repository count 0), then GREEN. Captured model/session and exact owner/offer/repository checked after awaits. Existing filter and list-tile primitives reused without unnecessary edits. 5000-entry lazy-list characterization retained; not a device performance benchmark. |
| 05/03 APK | `apk_actions_page.dart`, `widgets/apk_retirement_dialog.dart`, `apk_operation_card.dart`, APK tests | Extracted retirement content; reused identity/action/evidence widgets. Same operation ID with replaced package observed RED (retire gateway called), then GREEN after full displayed-identity checks. Resume/cancel/retire recheck status/package/version/signer/kind/session. Native journal remains authority. ViewModel/native implementation unchanged. |
| 05/04 Novel | `novel_actions_page.dart`, Novel tests | Canonical shell/region/notices/consent; custom 480dp dialog width removed after intended RED. Exact tuple/digest/origins consent, inert stage, previous/current evidence, sticky publication lock and frozen runtime remain. Existing artifact details and ViewModel reused unchanged. |
| 05/05 Cross-surface | `test/plugins/hikari_plugin_surface_flow_test.dart`, `docs/ui/PATTERNS.md`; action-route identity keys | 38 tests cover session/package replacement, Settings→Manager cancellation, focus return, unsupported/no-native-call behavior, route teardown and actual inventory refresh. APK session replacement exposed a real RED: old consent called approval on replacement session. Route state keyed by session/package fixes lifetime using existing disposal. Novel equivalent was GREEN characterization. 27 repository-form cases cover nine widths × 1x/2x/3x with simulated short height, IME inset, RTL and reduced motion. |
| 05/06 Gate audit | `test/architecture_guard_test.dart`, legacy fixture, inventory tool/test, matrix, components and this report; deleted `plugin_notice.dart` | Three intended guard failures observed before enforcement: legacy bridge bypass, remaining Wave C exemptions and dead notice file. Guards now cover relative/package import/export/part and raw modals across all Settings/Plugin Dart files. Inventory migrated-page metadata had intended RED before correction. Final gate automation recorded below. |

**Post-implementation contract hardening (included in final Phase 05 scope):**
`ApkActionsPage` and `NovelActionsPage` preserve caller-supplied `Key`/`GlobalKey`;
a private keyed session child replaces consent-bearing State when management or
plugin identity changes. Regression cases were added to
`test/plugins/hikari_plugin_surface_flow_test.dart`. This is after the original
1,306-pass Claude verification; see the [G5 owner decision](UI_FOUNDATION_G5_OWNER_ACCEPTANCE_2026-10-24.md)
for evidence provenance and remaining final-revision checks.

## Correctness, security and simplification review

- Only `HikariCompleted<bool>(true)` grants confirmation. Cancel/Back/Close/escape/dismissal is not consent; completion is not native success.
- Repository identity validation remains domain-backed and application policy remains authoritative. Dialogs return data; they do not mutate trust or execute code.
- APK and Novel action route state is recreated when session or package changes. Existing disposal invalidates epochs and releases unsubmitted stage/transport resources; submitted native operations remain journal-owned.
- APK retirement acknowledgment does not prove an old Android removal dialog was revoked. Unknown/pending/external outcomes remain distinct from success.
- Novel staging never evaluates JavaScript; exact revision/origin consent, publication-failure lock and restart/frozen-source warnings remain visible.
- No assertions were weakened, no exceptions hidden, no tests removed, no authorization moved to generic UI. Architecture guard failures during decomposition were resolved in production structure, not waived.
- Details snapshot does not expose a live busy state. Ponytail review removed its always-false `busy` parameter and three dead branches: **net 9 lines removed**. Coordinator still rejects busy/stale results. No extra abstraction/dependency introduced.
- Diff review retained native Material controls and existing coherent widgets. `PluginNotice` was deleted after every production consumer migrated; no forwarding replacement added.
- Broader callback-generation redesign, unrelated picker behavior and a full inventory taxonomy rewrite are outside scope. Callback identity check invalidates a different current cache callback; it is not a general settings-session generation protocol.

## Legacy audit and inventory

[Migration matrix](../ui/MIGRATION_MATRIX.md) regenerated after final production formatting: **498 lexical findings**, **77 under Wave C**. Wave C rows: 4 migrated pages, 60 canonical dependencies, 11 `legacy-pending` lexical entries and 2 route constructors requiring manual audit.

The 11 lexical entries do not mean eleven feature modal implementations remain: two `ModalRoute` visibility observations, two `Navigator` route references, six SnackBar/showSnackBar references and one intentional `HikariRefreshAction` shared-adapter import. The scanner's historical status vocabulary is retained; it is not a resolved call graph or G5 acceptance status. Shared refresh adapter remains until Phase 07/01 with current Manager/Reader consumers; this phase does not delete a live shared dependency.

All **eight Wave C modal exemptions removed**. Exactly **two Reader sheet exemptions remain**, owned by later Reader/legacy phases. No feature raw `showDialog`/`showModalBottomSheet`, legacy confirmation/sheet/scaffold bridge or `PluginNotice` remains in Settings/Plugins, enforced recursively by tests. Framework menus/tooltips/snackbars and native OS consent remain intentional mechanisms.

## Verification evidence — 2026-10-24

| Command / scope | Result |
|---|---|
| `./tool/check.ps1` (locked pub get, tracked/untracked Dart format checks, analyze, full tests, unstaged/staged whitespace) | Exit **0**; format 390 files, **0 changed**; analyze **No issues found**; **1306 passed, 1 skipped**; Foundation checks passed |
| `fvm flutter test --no-pub -r expanded` with Settings, Manager page/ViewModel/corrections, APK/Novel pages/gateways, management/foundation/composition/operation-card/acquisition-contract test files | Exit **0**, **141 passed** before final removal of always-false details parameter; final full run covers that simplification |
| `fvm flutter test --no-pub -r expanded test/architecture_guard_test.dart test/ui_documentation_contract_test.dart test/ui_audit_inventory_test.dart test/plugins/hikari_plugin_surface_flow_test.dart` | Exit **0**, **89 passed** after final production simplification and matrix regeneration; includes 38 integration tests |
| `git diff --check` | Exit **0**; Git CRLF→LF notices on three page files are line-ending notices, not failed whitespace checks |
| `graphify update .` | Exit **0**, AST-only. Four existing Kotlin files reported partial extraction; graph is navigation evidence, not native validation. Community labels may be stale; no LLM relabel run. |

Counts overlap; do not add focused counts to full-suite count. One existing opt-in upstream-manifest smoke test skipped without `HIKARI_MANGA_MANIFEST`, `HIKARI_MANGA_METADATA`, `HIKARI_NOVEL_MANIFEST`; external live-provider acceptance is not claimed. Locked resolution reported newer incompatible package versions; dependencies were not upgraded.

No Android/Windows/iOS build performed in Claude's original execution. Flutter tests use simulated gateways/channels and do not certify real installer, native evaluation, runtime ABI, screen reader or platform deployment.

**Provenance:** The 1,306-pass automated run predates the subsequent `GlobalKey`
contract hardening and documentation-acceptance patch. The owner later reported
successful testing, but no repeat command logs/counts were provided here; do not
attribute the original test count to the final revision.

## Physical evidence and Owner decision

Android installer/removal/evaluation, TalkBack, Windows Narrator/physical keyboard, real IME and device performance: **NOT RUN / NOT MEASURED**. Host: Windows 11 Pro 10.0.26300. Configured `9remote` failed to connect (`ECONNREFUSED`); this is a connection failure, not an unconfigured capability. No remote device evidence is available.

Run the [manual acceptance sequence](../ui/PATTERNS.md#phase-05-settings-and-native-plugin-actions) with dated device/OS/build evidence: cancellation, unknown-source permission/foreground, installer denial/interruption/unknown journal outcome, old-removal-dialog acknowledgment, exact Novel revision/origin evaluation and failure locks, 3x text/short height/IME and TalkBack/Narrator/keyboard focus. Widget tests and builds cannot replace these checks.

The [master gate semantics](../superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md) require manual/platform evidence; `NOT RUN` is not PASS. The subsequent [G5 owner decision](UI_FOUNDATION_G5_OWNER_ACCEPTANCE_2026-10-24.md) accepts Phase 05 **for continuation to Phase 06**, explicitly retaining the missing physical/platform evidence as Phase 07 debt. **G5 OWNER-ACCEPTED does not assert full technical G5 PASS.** The owner reported satisfactory testing without case-by-case evidence attached. Phase 06 is authorized but NOT STARTED in this record. G4 history remains unchanged.
