# Hikari UI/UX Foundation Review Pack

Status: Consolidated review baseline  
Review date: 2026-10-07  
Code baseline: `feat/ui-ux-hardening` at `08a091e`  
Comparison baseline: `dev` at merge base `8498679`

## Purpose

This pack consolidates:

- the deep Claude audit of the current UI/UX hardening branch;
- a second-pass review against the source at `08a091e`;
- corrections where the original audit was too strong, incomplete, or mixed facts with recommendations;
- a target UI architecture for Hikari;
- a smaller implementation sequence and acceptance checklist.

It is intended to become the decision baseline before additional visual polish or app-wide design-system migration.

## Executive decision

Keep the current visual direction and the new design-system foundation. Do not redesign Home or replace the existing feature state architecture.

Before migrating more screens, complete a **UI foundation correctness pass**:

1. preserve mounted tab state across responsive navigation changes;
2. establish a single appearance source of truth for OLED and accent seed;
3. remove legacy theme fallbacks from shared primitives used by migrated UI;
4. correct badge color pairing and critical accessibility seams;
5. make component layout depend on local constraints rather than full window width;
6. replace fixed universal bottom-navigation clearance with runtime obstruction handling;
7. validate large/nonlinear text, keyboard/focus, semantics, and breakpoint behavior;
8. profile blur/image costs before performance-driven visual changes.

The project should **not** commit to native `NavigationBar` / `NavigationRail` yet. Native navigation is the leading simplification candidate, but it must win a focused comparison against the current custom presentation on visual fidelity, semantics, keyboard/focus support, code size, responsive behavior, and performance.

## Pack contents

- `CONSOLIDATED_AUDIT.md` - final reviewed findings and priorities.
- `TARGET_ARCHITECTURE.md` - intended long-term UI/theme ownership.
- `MIGRATION_PLAN.md` - five-phase implementation sequence.
- `ACCEPTANCE_CHECKLIST.md` - concrete verification matrix and exit gates.
- `DECISIONS.md` - accepted, deferred, rejected, and explicitly non-goal decisions.

## Authority rules

This pack distinguishes four levels of statement:

- **Confirmed defect** - source structure is sufficient to establish incorrect behavior or broken contract.
- **Architectural/accessibility debt** - current behavior may work but ownership or interaction contracts are unsuitable as a permanent design-system foundation.
- **Risk requiring validation** - source shows an expensive or fragile mechanism, but impact is not proven without runtime testing/profile evidence.
- **Recommendation** - a preferred future direction, not a defect.

Implementation work must preserve this distinction. Do not turn recommendations into mandatory rewrites without new evidence.

## Primary non-goals

Do not use this review as justification to:

- replace `ChangeNotifier` ViewModels or constructor injection;
- add a global state package;
- add nested navigators or a routing package for the five root tabs;
- create a universal media-card/shelf DSL;
- autoplay the Home hero;
- merge catalog metadata contracts with playable/readable source contracts;
- clamp text scaling to protect fixed geometry;
- remove blur, gradients, clips, or repaint boundaries only on theoretical performance grounds;
- flatten `app/theme/design_system/` merely for aesthetics;
- force reader prose palettes to follow application chrome/OLED colors.
