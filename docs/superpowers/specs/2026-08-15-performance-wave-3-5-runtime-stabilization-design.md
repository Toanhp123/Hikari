# Performance Wave 3.5 Runtime Stabilization Design

## Goal

Remove two first-run UX defects before Wave 4 performance measurement: the rectangular top-level navigation state layer and Discover requiring a manual refresh on an empty catalog cache.

## Scope

Wave 3.5 is intentionally narrow. It does not change the Wave 1 multi-back-stack lifecycle, Wave 2 Story workload ownership, Wave 3 Reader session behavior, Discover flow sharing, Search caching, DAO projections, macrobenchmarks, or baseline profiles.

## Floating navigation state layer

The full navigation cell remains the selectable/touch/semantics target. The selectable owns a remembered `MutableInteractionSource` but suppresses its own default indication. A child presentation container clips to `MaterialTheme.hikariShapes.navigationSelection` and renders `LocalIndication.current` from the same interaction source. This preserves press, hover, and focus feedback while constraining visual state to the same rounded pill geometry as selection.

The selected background continues to use the existing semantic color and shape tokens. No feature screen owns hover/focus styling.

## Discover empty-cache bootstrap

`DiscoverViewModel` performs a one-shot bootstrap check after the first observed home-cache emission. It auto-refreshes only when all of the following are true:

- the first home-cache emission is genuinely empty;
- catalog observation has not failed;
- no refresh is already active;
- bootstrap has not already been attempted by this ViewModel instance.

A non-empty cache never triggers bootstrap refresh. A failed bootstrap does not loop. Returning to Discover with the retained Wave 1 ViewModel does not refresh again. Manual pull-to-refresh remains available and keeps its existing guard and failure behavior.

Wave 3.5 deliberately does not refactor the duplicate Discover home observation; Wave 4 remains responsible for that performance optimization.

## Verification

Regression coverage must prove:

- floating navigation keeps one selected item and minimum touch targets;
- the selectable dispatches interactions through a shared source while the visual indication is owned by a clipped selection-shaped child;
- an empty first cache emission triggers exactly one home refresh;
- a populated first cache emission triggers zero automatic refreshes;
- a failed empty-cache bootstrap is attempted once and does not loop;
- an observation failure represented as an empty fallback does not trigger bootstrap network work;
- existing manual refresh behavior remains intact.
