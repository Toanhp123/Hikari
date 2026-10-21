# Shared Component Audit — October 2026

Scope: Hikari core UI, Plugin Manager, Catalog Source Picker, Novel Reader,
Library/Local/Settings. This document records reuse decisions, **not** a new
design system or architecture decision. The canonical rules live in
[`DESIGN_SYSTEM.md`](../architecture/DESIGN_SYSTEM.md).

## Inventory and decisions

| Existing pattern | Current direction | Reason |
| --- | --- | --- |
| Material `ThemeData`, spacing, radii, status roles | Keep | Theme already provides visual defaults and dynamic accent/OLED behavior |
| `HikariButton` and `HikariIconButton` | Reuse selectively | Variants and touch sizes are stable; feature-specific actions can remain native controls when they use themed defaults |
| `HikariSearchBar` | Keep | Search submission semantics differ from local filtering; wrapper supports both without changing remote queries |
| `HikariChipRow`, `HikariChip` | Keep | Existing Library/Local/Catalog media filters use these; do not replace with segmented controls |
| `HikariChoiceMenu<T>` | Promote / migrate | Plugin view fallback, ecosystem and language filters, Catalog Source Picker, Add Repository use typed controlled values |
| Native `TextFormField` | Remove pass-through `HikariTextInput` | Exactly two callers (repository URL and Novel origins) already inherit root input theme; no extra wrapper contract justified |
| `showHikariConfirmation` | Keep | Library, Settings and plugin confirmation share cancellation/display behavior |
| `showHikariModalSheet` | Introduce selectively | Catalog picker and both Novel reader sheets shared identical scroll/safe-area route setup; body constraints stay local |
| `HikariSectionHeader` | Keep | Semantic sections shared across Settings/Plugins; not repeated page titles |
| `PluginActionSection`, picker details, Reader preferences | Feature-owned | Their content and layout semantics are specific to a feature |

## Deliberately not generalized

- No `HikariEverythingDialog`, form renderer, generic data card or second theme.
- No wrapper around `ScaffoldMessenger` until a cross-feature failure pattern
  needs central policy; the root `SnackBarTheme` already controls appearance.
- Keep `SearchBar` separate from `TextFormField` because remote search has
  explicit submission and debounce policy.
- Keep protected native installation confirmation, precise origin consent and
  pending/unknown operation UX inside the owning workflow.

## Acceptance matrix

- Values and errors are externally controlled; `FormField` owns validation,
  save and reset; a `null` *All* option differs from a missing form selection.
- Native 48dp targets, keyboard/focus, screen-reader state and text scaling.
- Choice menus with 30+ items remain scrollable on 320dp screens.
- Form dialogs remain usable with the keyboard open.
- Sheet opening, explicit result, Back/barrier dismissal and scroll behavior.
- Existing Plugin inventory, owner/trust, Manga source picker and Reader
  behavior must not change during presentation cleanup.
- Run project-pinned format, analyze and focused Flutter tests. Integration
  gates, APK/device smoke, TalkBack and performance profiling remain separate;
  no device-specific prerequisite or runtime certification is implied.

## Evidence and limits

Source audit covered Home page/widgets, Catalog search/detail/source picker,
Library, Local, Source Search, Settings, Manga reader, series pages/content,
Novel/Publication readers, Plugin pages/model/widgets and Player controls.
Theme tokens and core presentation patterns were inspected. This is bounded
cross-feature sampling, not a claim every repository file or test was reviewed.
Tests outside touched flows were sampled. No real-device, TalkBack or profiling
validation was performed.

Confirmed regressions: selector empty-options assertion and local-width overflow;
search programmatic text changes leaving stale actions/debounce; built-in async
empty/error overflow at 320dp width, 2x text and 240dp height; player auto-hide
removing controls during a slow scrub. Focus/keyboard, dynamic/null selection,
FormField reset/save, bounded menu scrolling, sheet cancellation/insets and
confirmation cancellation have focused widget coverage. Cursor-only controller
updates must preserve pending debounce. Settings confirmation stacking was not
established through ordinary modal interaction; no change made.

All AsyncStateView callers checked: Catalog/Source search, Library, Local,
Home library error and discovery error. Local uses SliverFillRemaining and Home
uses unbounded sliver boxes; shared scroll path retains both layouts. Search
callers remain Plugin local filtering and explicit-submit Catalog/Source search.
Selector callers remain Plugin browse filters, repository form and Catalog picker.

### External comparison (coordinator-retrieved primary source)

- [Flutter Compass theme](https://raw.githubusercontent.com/flutter/samples/main/compass_app/app/lib/ui/core/themes/theme.dart)
  and [TagChip](https://raw.githubusercontent.com/flutter/samples/main/compass_app/app/lib/ui/core/ui/tag_chip.dart):
  root ThemeData/ThemeExtension and core UI ownership fit existing Hikari boundaries.
- [Immich theme](https://raw.githubusercontent.com/immich-app/immich/main/mobile/lib/theme/theme_data.dart)
  and [dropdown](https://raw.githubusercontent.com/immich-app/immich/main/mobile/lib/widgets/common/dropdown_search_menu.dart):
  central native component themes and lazy lists are useful comparisons, not APIs
  to copy. Fixed-size samples and initialSelection/local-state dropdowns do not
  satisfy Hikari's nullable controlled-value contract.
- Flutter FormField reset, MenuAnchor childFocusNode and MediaQuery viewInsets
  provide native ownership primitives. Web controlled/focus patterns inform
  behavior only; no parallel design framework, dependency or web API introduced.

| Primary reference | Adopt | Do not copy / claim |
| --- | --- | --- |
| [Radix Select](https://www.radix-ui.com/primitives/docs/components/select) | Controlled value, keyboard navigation and focus return | Web primitives or parallel framework |
| [shadcn forms](https://ui.shadcn.com/docs/forms/react-hook-form) | Compose field value/change/error; form owns reset | Separate bespoke validated selector |
| [React Aria Button](https://react-spectrum.adobe.com/react-aria/Button.html) and [Select](https://react-spectrum.adobe.com/react-aria/Select.html) | Distinguish controlled/default state and pending behavior | Claim Hikari retains focus while loading; native disabled semantics differ |
| [MUI Button](https://mui.com/material-ui/react-button/) and [Select](https://mui.com/material-ui/react-select/) | Loading blocks duplicate actions; equality contract matters | JavaScript object identity API; Dart option equality remains caller-defined |
| [Android window classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes) and [font scaling](https://developer.android.com/about/versions/14/features#non-linear-font-scaling) | Responsive layout and large/nonlinear text testing | Whole-window width as local control constraint; linear widget tests certify Android nonlinear scaling |
| [Flutter ThemeData](https://api.flutter.dev/flutter/material/ThemeData-class.html), [accessibility](https://docs.flutter.dev/ui/accessibility-and-internationalization/accessibility), [performance](https://docs.flutter.dev/perf/best-practices), [viewInsets](https://api.flutter.dev/flutter/widgets/MediaQueryData/viewInsets.html), [FormField](https://api.flutter.dev/flutter/widgets/FormFieldState-class.html) | Root component themes, native semantics, caller-owned insets and reset/save | Device accessibility or performance certification from widget tests |

### Integration verification — 2026-10-21

- `tool/check.ps1`: exit 0. Locked dependencies resolved; 327 Dart files passed
  formatting with no changes; analyzer reported no issues; full unit/widget suite
  including architecture guard passed **1052 tests, 1 skipped**. Staged and
  unstaged whitespace checks passed. The skipped test is not counted as passed.
- `fvm flutter build apk --debug --no-pub`: exit 0, debug APK built. Gradle
  reported Java native-access, future built-in Kotlin migration and SDK XML
  version warnings; these were not changed as part of presentation work.
- `fvm flutter build windows --debug --no-pub`: exit 0, debug executable built.
- Worker focused suite: 90 passed. RED logs demonstrated original selector,
  search, async-state and slow-scrub failures, plus the selection-only debounce
  regression found during review; final integration suite includes those tests.
- Correctness and subsequent Ponytail simplification review retained native
  ownership, removed the pass-through text-input wrapper, and found no further
  justified abstraction or optional migration.
- `graphify update .`: exit 0; AST graph refreshed. Four existing Kotlin files
  reported partial extraction, so source/tests remain authoritative. Documentation
  semantic extraction was not refreshed by this AST-only command.

Build success is not runtime validation. No Android device, TalkBack, Android
nonlinear font-scaling, iOS build, real-app visual smoke or device profiling was
verified. Graph artifacts and build output remain ignored, outside the patch.
