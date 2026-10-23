# ADR-026: UI foundation rebuild

Status: **Accepted** (2026-10-22).
Approved by: Hikari project owner (explicit approval in the 2026-10-22 project conversation)
Approval date: 2026-10-22
Approval scope: UI architecture and clause-level supersession in this ADR, not a claim
that unrun physical/device checks passed. G0's separately recorded owner exception
and authorization to start Phase 01 are in [BASELINE_RUNS](../ui/BASELINE_RUNS.md).

## Context and authority

Phase 00 records the live implementation; it does not ship the new UI system.
The [v4.2 design spec](../superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md)
is the accepted architecture target. The [master](../superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md)
owns execution order and G0–G7; the [Phase 00 plan](../superpowers/plans/2026-10-22-hikari-ui-00-baseline-and-adr.md)
owns this baseline. Source/tests remain evidence of current behavior.

## Accepted decision

- `core/ui/foundation` owns tokens, semantic ThemeExtensions and responsive policy.
  `app/theme` composes one root ThemeData from that authority; it is not a token
  dependency of shared UI or features. No foundation code is introduced here.
- `core/ui` depends on Flutter and its own presentation modules, never app,
  features, domain, application or infrastructure. Foundation tokens cannot
  import component/widget families. Feature mapping converts domain values to
  presentation values; shared ChapterRow must not interpret domain models.
- Phase 02 refinement (**owner ratified 2026-10-23**, following autonomous
  architecture review): shared Flutter chrome in `core/ui/**` may import
  `dart:ui` for engine rendering (including ImageFilter for approved docked-bar
  blur), not as a general platform-API exemption. This is not OS access: `dart:io`, FFI and platform packages
  remain forbidden in core UI. Pure core/domain/application and app/theme retain
  their existing platform restrictions; architecture guard tests enforce scope.
  [Dated owner acceptance](../roadmap/UI_FOUNDATION_OWNER_ACCEPTANCE_2026-10-23.md)
  authorizes this clause and Phase 03 continuation; future expansions require
  separate review.
- Features depend on shared UI and domain/application contracts, not `app/**`
  or infrastructure. App owns routing/destinations and supplies callbacks.
- Dialog and BottomSheet are independent transient families. Adaptive presenter
  chooses between `dialog` and `bottomSheet`; Drawer is navigation, not a result
  mode. `sidePanel` is not shipped in the v4.2 enum.
- Dialog/sheet/adaptive entrypoints require explicit `useRootNavigator`, forwarded
  identically across modes. Shared host owns active route, result and common
  focus/dismiss mechanics; feature owns VM, validation, authorization and stale
  identity/generation checks. Unknown exit cause is `routeDismissed`, not guessed
  Back/barrier/drag. `HikariCompleted(null)` is not cancellation. Exactly-once
  settlement, nested navigators, disposal and concurrent exits require tests.
- Resize keeps active route and content state, reflowing within safe-area/IME
  bounds. A replacement route is not the default responsive mechanism.
- Phase 02 compiler/widget tests finalize public Dart signatures before consumer
  migration. Phase 00 document signatures are not executable API proof.
- Use Material primitives when sufficient; no forwarding-widget proliferation,
  new state framework, new dependency or permanent compatibility layer.
  Transitional exports may only re-export the single token authority and expire
  by 07/01. No such bridge is added in Phase 00.

## Clause-level supersession (effective 2026-10-22 approval)

| Existing decision/current clause | Accepted replacement | Preserved |
|---|---|---|
| ADR-003 core/ui → app/theme allowance | Reverse dependency: app/theme → foundation | All non-UI layer, pure-Dart, platform and plugin guards |
| ADR-003 patterns → domain allowance | Feature-owned domain mapping; domain-free shared UI | Domain contracts and application authority |
| ADR-003 features → app/theme allowance | No feature → app/**; shared foundation imports | App composition and explicit feature callbacks |
| ADR-013 independent unwired foundation | Already superseded by ADR-014; no parallel new authority | Historical record remains intact |
| ADR-014 token placement and preserve-custom-navigation scope | Foundation token authority; shared navigation mechanics, app-owned selected destination | One inherited root ThemeData, accent/OLED semantics, retained tab state |
| ADR-015 direct Material semantic roles | Retained; only location/ownership changes | ColorScheme/TextTheme, no parallel HikariColors adapter |
| ADR-016 theme hardening | Retained, extended through staged migration | Status extension validation, scoped evidence limits |
| ADR-017 existing page-specific header/navigation mechanics | Canonical shell/header contract in 02/06–07 and feature waves | Semantics, safe-area clearance, progress and content state |
| Current DESIGN_SYSTEM/UI_ARCHITECTURE/PRESENTATION feature-owned modal route/focus | Shared hosts own common route/focus/results; features own content and authorization | Feature workflows, meaningful native/specialist exceptions |

No historical ADR is deleted; only the listed conflicting UI clauses are superseded.
The existing implementation remains a migration baseline until its owning task changes it.

## Explicitly protected decisions

**ADR-009** stable source identity, continuation and local identity are unchanged.
**ADR-023** Reader-first Continue opens the exact saved chapter, not Detail first.
**ADR-024** disposable chapter-list snapshots, sequence validation and cache
semantics remain unchanged; cache does not promise durable downloads.
**ADR-025** plugin management, exact APK package/family/current verified signer,
Novel artifact tuple/origins, native consent/admission, frozen runtime and
uncertain-outcome mutation locks remain application/native authority. UI route
completion, a boolean, or shared presenter result cannot grant native trust or
prove installation. Library/Progress survive plugin removal. Windows/iOS native
management remains unsupported. Persistence, cache budgets, provider-private
state and source leases are not superseded by this UI ADR.

## Migration, exceptions and evidence

[Migration matrix](../ui/MIGRATION_MATRIX.md) lists current paths, owners,
consumer tasks, coverage and manual-review limits. Staged guards freeze explicit
legacy import/modal paths, with owner and expiry; new paths cannot inherit a
blanket legacy allowance. Framework tooltips/menus/snackbars, specialized
reader/player canvases and OS-owned consent are documented exceptions, not a
second shared modal framework. Existing UI remains compilable during migration.

[Foundations](../ui/FOUNDATIONS.md), [responsive contracts](../ui/RESPONSIVE.md)
and [component ownership](../ui/COMPONENTS.md) describe the accepted target,
not shipped implementation or compiler-verified public signatures.
[Baseline runs](../ui/BASELINE_RUNS.md) separates widget/fake-gateway evidence from
unmeasured device, visual and profile evidence. The owner approved this ADR
explicitly; that approval does not turn missing test evidence into a pass.
