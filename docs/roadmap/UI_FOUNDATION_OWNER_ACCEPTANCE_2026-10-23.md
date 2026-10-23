# UI Foundation G0–G2 owner acceptance — 2026-10-23

**Decision maker:** Hikari project owner. **Decision date:** 2026-10-23.
**Decision:** **G0, G1 and G2 OWNER-ACCEPTED** for the purpose of continuing the
approved UI rebuild. **Phase 03 Task 01 is authorized** after incorporating this
handoff patch and running its new focused regression test. Phase 03 must stop at
G3; this decision does not preapprove G3–G7.

## Scope and evidence boundaries

The owner reports that their runtime/manual checks were satisfactory and
explicitly accepts the earlier phases for continuation. This is an **owner
acceptance**, not an independently recorded, exhaustive **technical gate PASS**.
Preserve the dated automated results and historical `NOT RUN`, `NOT MEASURED`
and `LOST COMPARABLE BASELINE` entries in the [Phase 00](../ui/BASELINE_RUNS.md),
[Phase 01](UI_FOUNDATION_PHASE_01.md) and
[Phase 02](UI_FOUNDATION_PHASE_02.md) reports. The owner has not supplied
per-case device logs, screenshots or matched profile traces for every originally
pending matrix item; do not fabricate them or rewrite historic execution rows.

- **G0:** baseline/ADR owner handoff accepted. Physical *pre-migration* captures
  cannot be reconstructed after migration; record new captures as post-migration.
- **G1:** implemented Foundation/controls/gallery accepted for continuation.
  Preview API status remains Preview; no automatic Stable promotion.
- **G2:** typed surfaces, navigation/page shell and Source Picker pilot accepted
  for continuation. Remaining platform evidence is tracked, not silently PASS.
- **Known defect:** Catalog Detail whole-page hero/notice overflow at 375dp,
  3x text scale is **not accepted as correct behavior**. It has a named fix and
  regression gate in [Phase 04 Task 05](../superpowers/plans/2026-10-22-hikari-ui-04-browse-detail-migration.md).
- **Escape/MenuAnchor:** Phase 02 follow-up adds focused regression coverage for
  keyboard dismissal priority. This patch does **not** claim those newly added
  tests were executed locally; run them before Phase 03 implementation and fix
  within the owning UI component only if they reproduce a failure.
- **Native trust and performance:** Plugin installation/OS consent remains
  application/native-owned under ADR-025. Phase 05/G5 and Phase 07/G7 must still
  collect relevant native E2E and matched device/profile evidence before making
  their own technical acceptance claims. Old `NOT MEASURED` values remain unknown.

## Approved ADR-026 refinement

The owner confirms the Phase 02 `dart:ui` exception for **Flutter engine rendering
inside `core/ui/**`**, including the approved `ImageFilter` navigation blur.
This does **not** allow `dart:io`, FFI, platform service access or business
ownership inside shared UI. Continue to enforce the scoped guard and document
any future expansion separately. The existing `app_navigation_shell.dart` is
app composition (destination identity/controller/retained children), not a
throwaway wrapper. Legacy `hikari_confirmation_dialog.dart` and
`hikari_scaffold.dart` are **temporary compatibility adapters**, both due for
removal in **07/01** once all consumers migrate.

## Transfer and execution requirements

Phase 03 can proceed with the canonical Preview APIs, exact current legacy
exemptions and this accepted handoff. Before implementation, rerun the new
Escape/MenuAnchor tests plus relevant surface/architecture checks; record the
result and any repair in the Phase 03 execution report. Keep G3 review separate.
Do not use owner acceptance to erase a known defect, weaken tests, or declare
unmeasured hardware outcomes as numeric PASS.
