# Hikari UI Foundation Acceptance Checklist

Use this checklist before declaring the UI foundation ready for wider migration.

## A. Baseline and scope

- [ ] Work is based on the intended branch/commit and current `dev` merge base is known.
- [ ] Changes stay within the current phase; no speculative cross-feature redesign is mixed in.
- [ ] Working tree is clean before and after review/verification, except intended edits.

## B. Navigation shell correctness

- [ ] One mounted shell can resize repeatedly below/above 600dp without disposing visited tab content.
- [ ] Selected tab survives resize.
- [ ] A stateful probe inside a visited tab survives resize.
- [ ] Scroll/text-input probe survives resize where the widget itself normally preserves that state.
- [ ] Lazy first mounting remains intact.
- [ ] No duplicate ViewModel initialization caused by breakpoint crossing.
- [ ] Compact and wide navigation expose exactly one selected destination.

## C. Appearance integration

- [ ] OLED state is explicit input, not inferred from rendered colors.
- [ ] Accent seed is explicit raw configuration, not `Theme.of(context).colorScheme.primary` recycled as a new seed.
- [ ] Default target seed behavior is testable independently from custom accent selection.
- [ ] Home/navigation/SearchBar temporary scoped themes receive the full required appearance inputs while they exist.
- [ ] Migrated Home loading/error/progress/content states use the same target semantic theme.
- [ ] No migrated descendant silently falls back to `const HikariColors.dark()`.

## D. Shared primitives

- [ ] `MediaProgressBar` no longer requires legacy `HikariColors` in migrated target subtrees.
- [ ] `AsyncStateView` migrated path uses target semantic theme roles.
- [ ] Canonical migrated actions use native Material button behavior unless a custom control has a documented product reason.
- [ ] Keyboard activation and visible focus exist for migrated actions on desktop.
- [ ] Disabled/loading behavior prevents duplicate actions and preserves clear semantics.

## E. Color and contrast

- [ ] Warning/info badge fills use container roles, not foreground roles.
- [ ] Badge foreground is paired with its background or explicitly derived safely.
- [ ] Contrast is checked after alpha compositing where translucency is used.
- [ ] Status meaning is not color-only.
- [ ] Text over hero artwork remains readable for real light/dark/random artwork samples.

## F. Responsive layout

- [ ] Navigation mode may use window width.
- [ ] Feature/component variants use local bounded constraints.
- [ ] No child manually subtracts rail width to guess available space.
- [ ] 599/600/601dp behavior is intentional.
- [ ] 839/840/841dp behavior is intentional.
- [ ] Max-width desktop content still chooses variants from its own available width.
- [ ] Rail mode does not retain compact bottom-bar clearance.

## G. Bottom obstruction and safe area

- [ ] One owner defines effective bottom obstruction.
- [ ] Compact navigation height and safe-area inset are accounted for once.
- [ ] Rail mode does not add compact navigation clearance.
- [ ] Final scroll content remains tappable/focusable above obstruction.
- [ ] Keyboard/system inset behavior does not double-count padding.
- [ ] Intentional visual end spacing is separate from navigation avoidance.

## H. Text scaling and accessibility

- [ ] 100% text scale passes.
- [ ] 200% text scale passes.
- [ ] Android nonlinear scaling is exercised.
- [ ] Long anime/manga/light-novel titles are tested.
- [ ] Required actions do not clip or become unreachable.
- [ ] Visual truncation retains a complete accessible name where appropriate.
- [ ] Touch targets meet the project 48dp minimum where applicable.
- [ ] Selected navigation state is exposed to semantics.
- [ ] TalkBack walkthrough completed for Home/root navigation.
- [ ] Windows keyboard-only walkthrough completed.
- [ ] Reduced motion suppresses Hikari-authored decorative transitions appropriately.

## I. Navigation decision spike

- [ ] Native and custom options use the same state/controller/content-host model.
- [ ] Comparison records visual fidelity.
- [ ] Comparison records semantics/focus/keyboard behavior.
- [ ] Comparison records code size and test burden.
- [ ] Comparison records large-text behavior.
- [ ] Comparison records responsive complexity.
- [ ] Comparison records blur/translucency implications.
- [ ] Exactly one production navigation presentation remains after the decision.

## J. Performance evidence

- [ ] No performance claim is based only on source intuition.
- [ ] Android profile-mode trace collected for Home scroll/header/navigation interaction.
- [ ] Cold and warm image scenarios distinguished.
- [ ] UI and raster frame timing inspected against actual refresh rate.
- [ ] Image memory/large decode behavior inspected.
- [ ] Blur comparison performed against an opaque control variant if blur is suspected.
- [ ] No image-cache dependency is added without demonstrated need.
- [ ] No blanket `RepaintBoundary`, clip, gradient, or shadow removal is done without evidence.

## K. Theme consolidation

- [ ] Shared primitives are migrated before switching the root target theme.
- [ ] Root switch does not depend on legacy fallback accessors.
- [ ] Scoped Home/navigation/SearchBar target-theme recreation is removed after root adoption.
- [ ] One application `HikariTheme` remains authoritative.
- [ ] Legacy color/typography authorities are removed only after reference checks.
- [ ] Reader/player content palettes remain independent where product behavior requires it.

## L. Documentation and naming

- [ ] Current design-system adoption boundary is documented accurately.
- [ ] Historical ADR rationale is preserved rather than rewritten.
- [ ] Migration-only comments/allowlists are removed when no longer needed.
- [ ] Rename/move operations have an ownership/readability benefit, not aesthetic motivation alone.
- [ ] `home_library_section.dart` style naming cleanup is performed only when the component is already being touched or the mismatch becomes materially confusing.

## M. Project quality gates

- [ ] Formatter has not changed unrelated files.
- [ ] Analyzer passes.
- [ ] Targeted tests pass.
- [ ] Full tests pass when required by scope.
- [ ] `git diff --check` passes.
- [ ] Project `check.ps1` passes.
- [ ] Required debug build passes.
- [ ] Diff is manually reviewed for scope creep.

## Foundation-ready definition

The UI foundation is ready for broad feature migration when all of the following are true:

1. responsive root navigation does not remount visited feature state;
2. appearance has one explicit source of truth;
3. migrated shared primitives no longer rely on legacy theme fallback;
4. local layout responds to local constraints;
5. navigation obstruction is handled as runtime layout state;
6. large/nonlinear text and keyboard/semantics contracts have targeted evidence;
7. navigation presentation choice is evidence-based;
8. root-theme migration can proceed without a silent dual-authority fallback;
9. performance tuning is based on traces rather than aesthetics or intuition.
