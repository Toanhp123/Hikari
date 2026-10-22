# Foundation controls

Owner: Hikari UI foundation maintainers. Stage: **Preview** for every component
listed here. Public import: `package:hikari/core/ui/hikari_ui.dart`.
No Stable promotion until [contribution checklist](../CONTRIBUTING.md) complete.

## Actions

`HikariButton(label:, onPressed:, variant:, size:, isLoading:, isFullWidth:, icon:)`
uses native filled/outlined/text buttons. Variants primary/secondary/ghost/danger;
small/medium/large preserve padding, all targets at least 48dp. Label wraps.
Loading retains label and disables activation; caller owns pending command/error.

`HikariIconButton(icon:, onPressed:, tooltip:, variant:, size:, iconSize:, color:, hasBadge:)`
retains standard/filled/glass/primary variants and native focus/hover. Supply
meaningful tooltip or icon semantics; badge is visual only, supply status text
when meaningful. Null callback disables. Do not use color as sole status signal.

`HikariFilterChip(label:, selected:, onSelected:, leading:)` is controlled:
callback emits proposed bool, caller updates selection. Null disables; not a
passive metadata badge. `HikariChipRow(children:, spacing: 8)` keeps horizontal
scroll/density. Use native Chip for passive metadata, not disabled filter.

## Inputs

`HikariSearchField(controller:, focusNode:, initialQuery:, onChanged:, onSubmitted:,
onCleared:, hintText:, autofocus:, enabled:)` owns controller/focus only when absent.
External replacement detaches listeners without disposing caller resources.
External edits redraw clear/submit and emit no callback. Initial query seeds only
owned controller creation; it is not a controlled value. Edits/clear emit
`onChanged`; Enter/button emits `onSubmitted` once, not a second edit. Caller
owns debounce, remote request and stale-result policy; field has no timer.
Clear button additionally emits `onCleared` after the empty edit, letting route
owners cancel delayed edits and clear immediately. Keyboard deletion remains a
normal edit (Catalog/Source routes preserve their 300ms delay).

```dart
HikariSearchField(
  controller: queryController,
  onChanged: viewModel.editQuery,
  onSubmitted: viewModel.submitQuery,
)
```

`HikariChoiceMenu<T>(label:, value:, options:, onChanged:, expanded:, enabled:,
errorText:, icon:)` is controlled, including nullable values. Empty options safely
disable; no fallback selection invented. Options carry label/shortLabel/value.
Native MenuAnchor handles keyboard/Escape/focus return; bounded menu scrolls.
Validation belongs to FormField/feature. Trigger may abbreviate; menu retains
full labels. Do not use for huge searchable catalogs without feature filtering.

`HikariSegmentedControl<T>(segments:, value:, onChanged:)` is single controlled
selection backed by SegmentedButton. Null disables. Empty list renders nothing;
nonempty selection must identify provided segment. Local measured labels select
horizontal/vertical arrangement; bounded labels wrap without shrinking targets.
Caller owns segment labels, per-segment enabled values and selected state.

## Feedback/content

`HikariAsyncStateView(status:, child:, message:, emptyTitle:, emptyMessage:,
emptyAction:, onRetry:)` takes presentation enum only. Feature decides loaded,
true empty, refreshing, recoverableError or fatalError. Bounded host required for
retained content. Child remains same element position across loaded,
refreshing and recoverable error. Feedback reserves layout space; a recoverable
notice scrolls within at most half the host height, leaving retained actions
uncovered. Retry never fetches inside shared UI. Offline
message is caller-provided, not inferred from exceptions. Error never means empty.

`HikariNoticeBanner(message:, tone:, action:)` uses info/warning extension pairs
or Material error pair, icon + text and live region. Action is caller-owned.

`HikariSectionHeader(title:, description:, actions:)` exposes semantic heading
and wrapping actions. `HikariListRow(title:, subtitle:, leading:, trailing:,
onTap:, enabled:)` uses growable text slots and native InkWell activation,
minimum 48dp height. Caller must constrain unusually large leading/trailing slots.
No domain model conversion or navigation inside either component.

## Evidence and use

Tests: `test/core/ui/controls/`, `test/core/ui/feedback/`, theme and public API
tests. Examples/knobs: `tool/ui_gallery/main.dart` (dark/OLED, seeds, scale,
local widths, RTL, enabled/loading, stress text, reduced motion, async states).
Native Material remains authority for hover/pressed/focus behavior. Motion helper
honors system reduction; timers for business interaction remain feature-owned.
Physical Android TalkBack/IME and Windows interactive review remain separate
required evidence; tests do not certify them. Do not promote on build success.
