# Hikari Design System

Status: **canonical root theme** (consolidated from the earlier parallel design foundation).

Public entry: `lib/app/theme/hikari_theme.dart`. One `HikariTheme.darkTheme(oled:, accentColor:)` is created at the application root. Home, navigation and SearchBar inherit it; they must not construct scoped application themes. [ADR-014](../decisions/ADR-014-canonical-theme-consolidation.md) records the consolidation; [ADR-013](../decisions/ADR-013-design-system-foundation.md) documents the original independent foundation.

## Product language

Artwork-first discovery, quiet chrome, dense metadata without tiny text, and restrained Catppuccin-inspired dark surfaces. The default user accent seed is `#B4BEFE`, with accent choices passed **once** as raw seeds to `HikariTheme.darkTheme`. `ColorScheme.fromSeed` generates accessible semantic color pairs; its generated `primary` is never recycled as a seed. The OLED variant makes the page and lowest surface pure black while preserving separate raised tonal surfaces.

Material 3 `ColorScheme`, `TextTheme` and native component themes own presentation semantics. Do not create a custom control for a behavior Material already handles unless product needs justify it. Shared filter and icon controls now delegate interaction, focus and semantics to native `FilterChip` / `IconButton`, retaining Hikari presentation where required. Reader canvases (OLED, charcoal, paper) remain separate from application chrome.

## Canonical API

| Responsibility | API | Contract |
| --- | --- | --- |
| Root/native theme | `HikariTheme.darkTheme(oled:, accentColor:)` | One app-owned `ThemeData`; native widget themes and semantic surface roles |
| Native colors | `Theme.of(context).colorScheme` | Primary/secondary/on-role pairs, tonal surfaces, outline/error/inverse roles |
| App color semantics | `Theme.of(context).colorScheme` | All common UI colors and foreground/background role pairs, no parallel adapter |
| Warning/info | `context.hikariStatusColors` | Required `HikariStatusColors` extension registered by root; missing extension throws, never silently falls back |
| Media identity | `mediaTypeBadgeColors(context, type)` | Shared Anime/Manga/Novel container/foreground pair for media badges |
| Typography | `HikariTypography.textTheme` / `Theme.of(context).textTheme` | 12dp minimum semantic metadata, Material text slots, platform font fallback |
| Spacing | `HikariSpacing` | xs/sm/md/lg/xl/xxl/xxxl = 4/8/12/16/24/32/48dp |
| Radii | `HikariRadius` | sm 8, md 14, lg 16, xl 24, `pill` StadiumBorder; xs 4 for small inner badges |
| Shared dimensions | `HikariSize` | Touch 48, field 48, icon 24; intrinsic poster/landscape ratios remain component-owned |
| Motion | `HikariMotion` | 150 interaction / 200 exit / 300 standard / 400 emphasized; responsive to disableAnimations/reduceMotion |
| Layout | `HikariBreakpoints` | compact <600, medium <840, expanded <1200, wide >=1200; content max 1440 |
| Reader palette | `NovelReaderTheme` (feature-owned) | OLED, charcoal and paper prose surfaces remain independent of app chrome |

Presentation widgets use inherited `Theme.of(context).textTheme` for fonts/weights; `HikariTypography.textTheme` defines the base scale in one place. `HikariColors` and `context.hikariColors` are removed: a second copy of Material semantics only increases drift and failure modes. `ColorScheme` supplies ordinary surfaces, text, primary/error and scrim; `HikariStatusColors` adds only warning/info container pairs. Shared `SliderThemeData` lives in the root theme; feature sliders do not rebuild a separate presentation theme. Do not construct a scoped `ThemeData` or put semantic hex colors into features. Media badges must use both members of `mediaTypeBadgeColors(context, type)` instead of guessing contrast from a single tint. Feature-specific visual geometry remains local. Artwork shadows, video black, and reader prose palettes are not app-chrome colors.

Use local `LayoutBuilder` constraints for a component's responsive content decisions; only root navigation classifies app window width. Effective navigation/safe-area/keyboard obstruction is runtime layout data, **not** an app-wide spacing token. Bottom obstruction is handled by the owning scrollable and scaffold, not by a fixed global spacer.

## Component behavior

- Media-type filter rows: keep the native `FilterChip` dimensions and padding from the root theme, show text-only labels, and use `HikariSpacing.sm` (8dp) **between** adjacent chips in Home, Library, Local and both Search screens. On narrow screens the row scrolls horizontally; do not shrink individual chips to fit.
- Action types: native FilledButton, OutlinedButton, TextButton; minimum 48dp touch targets; preserve focus, keyboard and disabled state semantics. Shared filter/icon actions delegate focus, semantics and keyboard behavior to native Material controls.
- Sections: semantic TextTheme slots, feature-owned heading/metadata and actions. No generic shelf DSL or mega-card component.
- Cards: stable artwork aspect ratio, separate scaled title area, useful error/fallback imagery, no default shadow; full labels available to assistive technology even when visual copy truncates.
- Search: native `SearchBar` theme is root-owned. `HikariSearchBar` debounces edits, while Catalog and Source searches run only on explicit submit (Enter or Search action); source results may stream in while slower providers are pending.
- Loading/empty/error states: preserve feature state snapshots and retries; keep paired semantic status colors.
- Dialogs/sheets: use native component styling, safe area and content scrolling. Routes and focus restoration remain feature-owned.
- Navigation: the current custom docked bar/rail remains in this pass; separately evaluate native presentation against visual fidelity and accessibility, not by assumption.
- Player/reader: immersive black and paper/charcoal prose are reading/playback surfaces, not forced to use app page `surface`.

## Compact selectors and browsing modes (2026-10-21)

- Use Material `SegmentedButton` for a small exclusive **view mode** (for example Available / Installed / Updates), not `FilterChip`. When width or text scale makes three segments unsafe, use the same single-select menu control as a compact fallback.
- `HikariChoiceMenu<T>` in `core/ui/components` is the shared, **controlled** Material `MenuAnchor` wrapper for single choices in filters, forms and settings. It renders a minimum 48dp trigger, selected check mark and bounded scrollable menu; callers own values, option lists, labels and callbacks. A null option can mean "All"; without a null option, null means "not selected". A `shortLabel` affects only trigger copy. The component accepts external `errorText` and `enabled` but never runs validation.
- For validated choices (for example Add Repository ecosystem), compose the **same** `HikariChoiceMenu<T>` inside Flutter `FormField<T>` and wire `field.value`, `field.didChange`, and `field.errorText`. The form alone declares its validator, reset/save policy and domain checks. Do not duplicate the dropdown UI or add a second bespoke selection component for forms.
- Plugin view modes filter different evidence: Installed can include native packages with no repository offer or source-language metadata. Therefore the Language facet is only shown for Available/Updates; its options are scoped to the active view and ecosystem, never inferred from unrelated repositories. Search input does not silently reset a still-valid language facet.
- Catalog Source Picker and Add Repository both reuse `HikariChoiceMenu` while retaining their own source-selection workflow or form validation. Library/Local media-type chips remain `HikariChipRow`; no global replacement is intended.

## Transient surfaces: dialog, sheet, menu and feedback

Choose the native Material surface for the *interaction*, not for visual novelty:

- **Confirmation** for an intentional potentially consequential change: use
  `showHikariConfirmation` (`core/ui/components/hikari_confirmation_dialog.dart`).
  It uses the root `DialogThemeData`, scrollable `AlertDialog`, an explicit Cancel
  action and a semantic `ColorScheme.error` treatment for destructive commits.
  Only an explicit Confirm result is `true`; Escape/Back/barrier/Cancel are
  cancellation. Feature code owns the operation, post-await revalidation and
  whether dismissing the barrier is acceptable.
- **Forms and complex consent**: keep feature-owned native dialogs with input
  validation and dynamic disabled actions. Never generalize away required checkboxes,
  exact artifact identity, foreground/epoch checks or native system approval.
  The shared confirmation shell never authorizes APK installation or code execution.
- **Modal bottom sheet** for contextual selection, controls or a long contents
  list on compact screens. Prefer root `BottomSheetThemeData`, `useSafeArea` and
  a bounded scrollable body. Use `isScrollControlled` where the body scrolls;
  ensure short content does not consume the entire viewport. A compact-source
  picker may remain a bounded `Dialog` on expanded screens when it preserves the
  exact same selection result/validation as the phone sheet.
- **MenuAnchor** for short anchored choices, `ExpansionTile` for in-place
  secondary details, and theme-owned `SnackBar`/`ScaffoldMessenger` for brief
  nonblocking feedback. Do not build a generic modal router or snack-bar wrapper
  without a demonstrated shared need.
- Let the owning route manage focus restoration, keyboard insets, barrier
  policy and navigation. Do not change saved data when a user merely dismisses a
  transient surface. Native Android installer confirmation is an operating-system
  authority and must not be replaced by Flutter UI.

Regression coverage for common confirmations includes explicit consent versus
Cancel/Back/barrier, destructive color, narrow screens and large text. Feature
flows also test disposed-route guards, ownership/evidence changes during consent,
keyboard for forms, and long sheet lists. All shared controls inherit the root
Material 3 theme rather than creating scoped color/typography palettes.

## Quality and outstanding work

Consolidation **does not certify** accessibility, layout resilience, or performance. Maintain tests for actual root inheritance (including accent/OLED), semantic pairs, width boundaries, reduced motion and existing search behavior. Profile blur/image decode on device before claiming a performance regression or replacing image policy. Historical audits cover resize state retention, text scaling, badge contrast, rail selection and bottom insets; current controls retain native focus/selection behavior. Native navigation remains a separate design decision; TalkBack/device validation remains manual.

## Reference principles

- [Flutter Material ThemeData](https://api.flutter.dev/flutter/material/ThemeData-class.html)
- [Flutter ColorScheme](https://api.flutter.dev/flutter/material/ColorScheme-class.html)
- [Flutter adaptive best practices](https://docs.flutter.dev/ui/adaptive-responsive/best-practices)
- [Flutter accessibility](https://docs.flutter.dev/ui/accessibility-and-internationalization/accessibility)
- [Flutter performance best practices](https://docs.flutter.dev/perf/best-practices)

The earlier parallel foundation was intentionally separate for its first milestone; the consolidation does not rewrite that history. All current source references and design guidance should use the canonical `hikari_*` APIs above.

## Theme hygiene (2026-10-08)

- `HikariTheme.defaultAccentSeed` and `HikariTheme.accentPresets` own the raw appearance choices. Settings offers a Default swatch to restore a nullable seed; OLED/accent preferences persist in an app-support JSON file loaded before `runApp`, with no added package dependency.
- `MaterialApp` creates one active dark `ThemeData`, and Home/navbar/SearchBar inherit that root without scoped reconstruction.
- `HikariChip` delegates interactive filter selection to `FilterChip`; `HikariIconButton` delegates action handling to native `IconButton`. Both retain their existing feature call signatures.
- The dormant global `HikariReadingPalette` was removed. Novel prose continues to use its existing `NovelReaderTheme` without changes to rendered reading colors.
- Old snapshots and ADRs are historical evidence, not authoritative status for the current implementation.

## Canonical consumer migration (2026-10-08)

- All feature pages and core presentation controls obtain ordinary colors directly from `Theme.of(context).colorScheme` and typography from `Theme.of(context).textTheme`. `HikariColors` compatibility extension has been deleted rather than kept as a second semantic vocabulary.
- `context.hikariStatusColors` requires the status extension installed by `HikariTheme.darkTheme`. Widget tests of status-aware UI must wrap their content in the canonical app theme. An incorrect MaterialApp setup fails loudly.
- `mediaTypeBadgeColors(context, MediaType)` is the only resolver for Anime/Manga/Novel badge background + foreground colors; feature pages may vary placement and geometry, not their semantic role pairing.
- Fixed image/video scrims and NovelReaderTheme prose canvases are intentional media-surface exceptions; app controls inside readers and player still inherit Material colors/text styles.
- No additional UI color/palette abstraction or third-party theme package is necessary. [ADR-015](../decisions/ADR-015-native-color-scheme-consumers.md) records this API cleanup.

## Interactive artwork and scrollable collections (2026-10-08)

- Interactive artwork uses a **local Material ink plane above opaque image layers**;
  a page-level ancestor Material cannot guarantee visible ripples through image
  paints. The ink plane's clip and artwork stroke share the poster radius.
- Draw poster outlines on top of artwork, with one anti-aliased clip for the
  image, badges and progress. Do not assume a `DecoratedBox` border beneath a
  clipped opaque child will stay visible.
- Do not add an unconditional `RepaintBoundary` to every reusable poster;
  slivers already manage boundaries for their lazy items. Verify unusual
  custom animated layers in DevTools before adding more.
- Poster/banner decode widths are rounded to a small physical-pixel bucket
  based on the actual layout width and device pixel ratio, capped at 2048 px.
  Keep loading/error fallback content and independent hero identity intact.
- Source and candidate menus use lazy builders when data size can grow with
  installed extensions. Each interactive row owns its Material and clip.
- Glass blur remains a deliberate product effect. No performance claim or
  blanket replacement is authorized without real-device profile measurements.

References:

- [Flutter InkWell clipping and opaque graphics](https://api.flutter.dev/flutter/material/InkWell-class.html)
- [Flutter Ink paint order](https://api.flutter.dev/flutter/material/Ink-class.html)
- [Flutter performance best practices](https://docs.flutter.dev/perf/best-practices)
- [Flutter ListView.builder](https://api.flutter.dev/flutter/widgets/ListView/ListView.builder.html)

## Compact management headings (2026-10-20)

`HikariSectionHeader` in `core/ui/patterns` owns the compact semantic label
style formerly local to Settings, with optional description. Actions remain
caller-owned siblings; this header has no trailing-content API. Settings and Plugin Management reuse it; detail, search and Home
headers retain their existing feature-specific behavior. This is a small
proven-shared primitive, not a generic section/card framework. Plugin-specific
status notices, journal rows and workflow grouping stay feature-owned; prefer
existing Material controls and theme tokens for their rendering.

## Shared control catalog and ownership (2026-10-21)

Hikari remains **Material 3 first**: keep one root theme, avoid extra UI
libraries, and use a core wrapper only when it provides a stable app-level
contract not already provided by a themed Material widget. Prefer composition
(the controlled-value approach used by Radix Select and shadcn forms) over
feature-specific forks and long lists of mode flags.

| Need | Reusable API | Ownership rule |
| --- | --- | --- |
| Common primary/secondary/ghost/destructive action | `HikariButton` | Caller owns command/disabled state; native button owns focus and keyboard |
| Icon action | `HikariIconButton` | Caller supplies tooltip and command; 48dp minimum target |
| Form-like text entry | Native `TextFormField` | Caller owns controller/value and validation; root `InputDecorationTheme` already supplies consistency |
| Search | `HikariSearchBar` | Caller owns query/search behavior; local filtering may set zero debounce, remote search remains explicit-submit |
| Single choice | `HikariChoiceMenu<T>` | Controlled `value` + `onChanged`; `null` meaning is defined by options/caller; `FormField<T>` performs validation |
| Media-type filters | `HikariChipRow` + `HikariChip` | Feature owns selected filters and results; horizontal scrolling preserves chip touch size |
| Exclusive view modes | Material `SegmentedButton` | Feature owns selection; use `HikariChoiceMenu` when horizontal space or text scale is insufficient |
| Simple consequential confirmation | `showHikariConfirmation` | Caller owns revalidation, consent and eventual operation; dismissal always cancels |
| Contextual modal sheet | `showHikariModalSheet` | Helper sets route safe-area, drag and scroll defaults; feature owns body height, keyboard padding, focus, semantics and result |
| Section title | `HikariSectionHeader` | Use only for semantic groups, not to repeat a route title |
| Complex editor, multi-step source picker or consent | Feature-owned native form/dialog/sheet | Do not build a generic multi-mode form or modal workflow framework |

`HikariChoiceMenu` must never parse URLs, select sources, mutate application
state or invent domain validation. Plugin filters, Catalog Source Picker and
Add Repository share this selector; the last composes native `FormField` and
owns validation, save and reset. Values are controlled, including explicit null
options. Missing values show a placeholder; empty options disable the trigger
without hiding supplied errors. Compact triggers use local constraints and
natural width, not window-width guesses or intrinsic sizing. Menu labels wrap
inside a bounded scrollable Material menu. Native focus returns to the trigger
after selection or Escape. No per-option disabled API is needed by current callers.

`HikariSearchBar` listens to owned or external controllers. External text edits
rebuild actions and cancel stale debounce without emitting searches; selection-only
changes preserve pending text callbacks. Replacing controllers detaches listeners;
caller controllers are never disposed. Submit cancels debounce and emits once.
`AsyncStateView` shares one scrollable centered presentation for built-in loading,
empty and error states, including short bounded and unbounded/sliver parents.
Content and custom loading widgets remain caller-owned.

Use native controls directly when theme covers the contract. A pass-through
`HikariTextInput` adds no consistency benefit and is removed; repository URL and
Novel origin fields use themed `TextFormField`, preserving caller validation.
Shared names describe one behavior (`ChoiceMenu`, `SearchBar`), not feature modes.

```dart
TextFormField(
  decoration: const InputDecoration(labelText: 'HTTPS repository URL'),
  validator: validateRepositoryUrl, // Feature-owned domain validation.
);
FormField<String>(
  builder: (field) => HikariChoiceMenu<String>(
    label: 'Language', value: field.value, options: languageOptions,
    onChanged: field.didChange, errorText: field.errorText,
  ),
);
```

Player controls suspend auto-hide throughout a scrub gesture and restart it on
release; this presentation lifecycle does not change playback or persistence policy.

Sheets and dialogs must keep native Material semantics. A sheet with long
content must provide its own bounded scrollable body; the shared route helper
does **not** infer list size, keyboard padding or dismissal policy. Native APK
installer consent must never be replaced by an app-controlled confirmation.
Existing feature controls such as reader typography and media artwork remain
feature-owned because they are not general app input patterns.

Reference behavior and architecture: [Flutter Material widgets](https://docs.flutter.dev/ui/widgets/material),
[Flutter FormField](https://api.flutter.dev/flutter/widgets/FormFieldState-class.html),
[Flutter modal sheets](https://api.flutter.dev/flutter/material/showModalBottomSheet.html),
[Radix Select](https://www.radix-ui.com/primitives/docs/components/select).
