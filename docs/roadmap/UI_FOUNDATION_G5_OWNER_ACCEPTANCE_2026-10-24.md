# Hikari UI Foundation G5 owner acceptance — 2026-10-24

**Decision maker:** Hikari project owner. **Decision date:** 2026-10-24.

**Decision:** Phase 05 Tasks 05/01–05/06 implementation is accepted for
continuation; **G5 OWNER-ACCEPTED / PHYSICAL EVIDENCE DEFERRED**.
The owner reported that testing was satisfactory and requested closure of
Phase 05 in preparation for **Phase 06 Task 01**. This is an owner-scoped
continuation decision, **not a claim that the full technical G5 gate passed**.
Phase 06 implementation remains **NOT STARTED** at this handoff; G6/G7 need
separate evidence and decisions.

## Scope accepted

- Wave C: Settings, Plugin Manager, repository/details typed dialogs, APK and
  Novel actions, security-sensitive cancellation/stale-state checks, and the
  eight retired Wave C modal exemptions.
- The follow-up `GlobalKey` contract fix is **part of the accepted Phase 05
  source scope**: `ApkActionsPage` and `NovelActionsPage` retain their caller
  `Key` while replacing feature-owned, consent-bearing session state on changes
  to management/plugin identity. Regression coverage lives in
  `test/plugins/hikari_plugin_surface_flow_test.dart`.
- Legacy debt remains limited to the existing inventory/contract exceptions:
  two Reader sheet exemptions belong to Phase 06; `HikariRefreshAction` and
  nonvisual helper retirement remain Phase 07/01 responsibilities.
- No authorization to weaken Android installer trust, Novel evaluation consent,
  package/source identity, Library/progress, Reader-first or native journals.

## Evidence, status and deferred verification

- Claude's **pre-GlobalKey-fix** automated handoff recorded `./tool/check.ps1`
  exit 0, **1,306 passed / 1 skipped**, Flutter analyzer no issues and
  formatting clean (390 files); see [Phase 05 execution](UI_FOUNDATION_PHASE_05.md).
  These numbers must **not** be represented as a fresh run on the final
  GlobalKey-fix or acceptance-document revision.
- The owner subsequently reported that **their tests were satisfactory**.
  No commands, final counts, exit codes, device/OS/build identifiers, test case
  outcomes or logs were supplied with that statement. Record this as
  **OWNER-REPORTED PASS (UNSPECIFIED SCOPE)**, not independently verified
  Flutter/Android/Windows E2E evidence.
- Android native installer/removal/evaluation, unknown-source permission,
  interrupted native journal recovery, real IME/gesture behavior, Android
  TalkBack, Windows keyboard/Narrator and matched device performance are
  **NOT DOCUMENTED / NOT MEASURED** where dated, case-specific artifacts are
  absent. Android/Windows/iOS builds were **NOT RUN in Claude's handoff**;
  no later build evidence has been attached to this acceptance record.
- **Disposition:** The owner authorizes *continuation* to Phase 06 with the
  above evidence gaps tracked as bounded Phase 07 verification debt, **not as
  PASS**. The UI Foundation G7 release acceptance must collect or formally
  disposition this evidence before claiming final release readiness. The
  project owner remains accountable for the acceptance/defer decision;
  execution/measurement belongs to Phase 07 device verification tasks.

## Next phase handoff

1. Start Phase 06 at [Reader & Player plan](../superpowers/plans/2026-10-22-hikari-ui-06-reader-player.md), **Task 06/01**, using the final Phase 05 source (including the GlobalKey fix).
2. Preserve two remaining Reader sheet exemptions until their 06/03–06/04
   consumers migrate; do not delete living Reader/Player code prematurely.
3. Preserve the original Phase 05 report and its verification provenance;
   this decision only changes continuation authorization, not historical facts.
4. Phase 07 device acceptance must revisit the checklist at
   [Phase 05 manual sequence](../ui/PATTERNS.md#manual-device-acceptance-not-replaced-by-widget-tests).

**G5 status:** `G5 OWNER-ACCEPTED / EVIDENCE DEFERRED`.
**Technical full-gate status:** `INCOMPLETE — documented physical/platform evidence pending`.
**Phase 06:** `AUTHORIZED / NOT STARTED`.
