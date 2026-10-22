# Responsive UI contracts

Status: **Accepted architecture target (ADR-026); not implemented.**
[ADR-026](../decisions/ADR-026-ui-foundation-rebuild.md) records approval;
[v4.2 spec](../superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md)
owns target detail. [Baseline runs](BASELINE_RUNS.md) records actual evidence.

## Width, constraints and state

Initial window classes: compact <600, medium <840, expanded <1200, wide >=1200;
content maximum 1440. These are starting policies, not proof content fits.
Root navigation uses window constraints; component layouts use local constraints.
Insets and keyboard obstruction are runtime data, never global spacing tokens.

App owns selected destination and lazy retained tab state; shared shell owns
navigation presentation, Drawer mechanics and common header geometry. Drawer is
navigation, not a transient result mode. Current shell uses compact bar below
600 and custom rail from 600; target shared shell is pending 02/06–07.

Active modal keeps route/state on resize; reflow bounded content rather than
pop/reopen. Dialog, BottomSheet and adaptive presenter require caller-specified
`useRootNavigator`; changing width must not switch Navigator implicitly. Nested
Navigator/result/focus restoration and IME tests belong to Phase 02. Unknown exit
cause is `routeDismissed`; null completion is distinct from cancellation.

## Required verification grid

- Widths 375, 599/600/601, 839/840/841, 1200/1440 logical pixels.
- Text scales 1x/2x/3x; nonlinear platform scaling where hardware supports it.
- Short landscape, safe-area and visible keyboard, active-modal resize.
- Selected tab, mounted child, query, scroll, form/selection state and focus.
- Android Back/TalkBack; Windows Tab/Shift+Tab/Enter/Escape, hover, resize.

Widget geometry/semantics evidence is simulated. Physical keyboard, OS IME,
TalkBack, system installer and GPU frame timings require dated device evidence.
Missing screenshots or measurements are NOT RUN / NOT MEASURED, never inferred
from analyzer success. [Migration matrix](MIGRATION_MATRIX.md) assigns owners.
