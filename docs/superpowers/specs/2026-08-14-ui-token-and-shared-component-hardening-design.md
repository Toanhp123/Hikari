# Hikari UI Token and Shared Component Hardening Design

## Goal

Make the accepted Product UI redesign enforce a single visual source of truth: production Compose code must consume Hikari theme/design tokens for visual values, and repeated visual patterns must be implemented once as shared components rather than customized independently by screens.

## Non-goals

- Do not change capability, storage, plugin, navigation, or persistence ownership.
- Do not add a production module.
- Do not intentionally redesign the accepted Product UI hierarchy or copy.
- Do not pull Wave 10 Settings or Wave 11 Plugins forward.

## Token model

`:core:designsystem` remains the owner of application-wide visual decisions. The theme is extended with:

- spacing tokens for the approved spacing scale used by the accepted redesign;
- semantic dimensions for minimum touch targets, chrome, icon geometry, poster/media geometry, and reader/content insets;
- semantic shape tokens for artwork, cards, pills, navigation, search, and controls;
- semantic opacity tokens for glass, artwork scrims, secondary-on-artwork content, borders, and subdued surfaces;
- semantic color tokens for content/scrims rendered over artwork plus the deterministic artwork fallback palette;
- semantic typography tokens for artwork hero labels/titles/actions and reader body/note text;
- centralized responsive breakpoints, including the existing 412 dp, 520 dp, and 600 dp transitions.

Literal token values are allowed only in token-definition files under `core/designsystem/.../theme/`. Production UI consumers use those tokens. `Dp.Zero`/`Color.Unspecified` may be used where they express absence rather than a visual choice.

## Shared component model

Stable repeated domain-neutral presentation moves to `:core:designsystem`:

- a shared circular Hikari icon/action button owns the 48 dp target, circular shape, optional accent border, focus plumbing, semantics, and content alignment;
- a generic inline feedback row owns the repeated text + optional retry/action treatment;
- glass surface rendering owns blur, border, shadow, alpha, and shape tokens rather than receiving ad-hoc visual values from features where a standard variant exists;
- icon/glyph drawing uses shared design-system glyphs/tokens rather than feature-local copies of stroke widths and hit-target geometry.

Domain-aware reusable cards remain in `:feature:catalog/ui/components`; they may consume theme tokens but must not move domain models into the design system.

## Migration rules

Production Compose code in `:app`, `:feature:catalog`, `:feature:reader`, and `:core:designsystem` must not introduce local `.dp`/`.sp` visual literals, `RoundedCornerShape(...)`, direct palette colors, literal alpha copies, or local font family/weight decisions outside approved theme token definitions. Existing accepted geometry is preserved by mapping current values to named tokens instead of normalizing values by eye.

Component-specific responsive behavior may use a centralized breakpoint token even when it is not a global `HikariWindowClass` boundary. This preserves the accepted 520 dp content transition while eliminating feature-local breakpoint ownership.

## Enforcement

Add `scripts/verify-ui-tokens.sh` and a contract test. The gate scans production Compose source and fails closed when forbidden visual literals are introduced outside the token-definition allowlist. It is wired into `run_repository_static_gates`, so both `verify-fast.sh` and `verify.sh` enforce the rule.

## Validation

- Contract-test the new static gate in RED/GREEN order.
- Unit-test theme token availability and approved responsive breakpoints.
- Compile/test `:core:designsystem`, `:feature:catalog`, `:feature:reader`, and `:app`.
- Run Detekt and repository static gates.
- Run Roborazzi verification when the existing snapshot tooling is available in the sandbox.
