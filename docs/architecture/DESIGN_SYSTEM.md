# Hikari Design System Foundation

Status: **canonical migration target; incremental adoption started**.

Public entry: `lib/app/theme/design_system/design_system.dart`. The root app theme and most screens still use the legacy `app/theme/hikari_*.dart` system. The first approved migration seam is `core/ui/components/hikari_search_bar.dart`, which applies the target theme only to its native Material `SearchBar` subtree while preserving existing search behavior. Navigation, settings, persistence and business logic remain unchanged. See [ADR-013](../decisions/ADR-013-design-system-foundation.md) for placement and coexistence rationale.

## Principles

Artwork first; quiet chrome; dense metadata without tiny text. Adopt calm Catppuccin Mocha palette with soft wisteria lavender (`#B4BEFE`) and soothing charcoal surfaces (`#181825` / `#1E1E2E`), eliminating eye strain and chromatic vibration. Density comes from hierarchy and layout, never smaller hit targets. Native Material controls supply focus, keyboard, semantics and disabled behavior. Feature experiences stay feature-owned.

## Repository audit

Audit covered theme/navigation/shared primitives and feature page/widget sources; source inspection is not a rendered accessibility certification. Representative evidence below uses paths relative to `lib/`.

| Area | Existing decisions and inconsistencies | Target |
| --- | --- | --- |
| `app/theme/hikari_*.dart` | Dark/OLED, violet `#8B5CF6`, obsidian `#0B0F17`; 4–48 spacing; 4/8/12/16/20/24/999 radii; 10–32 type; 150/250/350/500 ms motion. Material surface-container roles not fully mapped; muted `#64748B` weak on raised surfaces. Fractional 839–840 width gap in context helpers. | Full native semantic scheme; brighter secondary text; fewer shape roles; 12 minimum metadata; continuous width classification. |
| `core/ui/components/` | Custom gesture buttons/icons/chips coexist with native controls. Button heights 36/44/52, 48 minimum target, fixed label height/ellipsis; 0.97 press scale; primary shadow alpha .35/blur 12. Search fixed 48 high, 14 text, clear icon 18 plus 4 padding. | Native component themes; minimum, not fixed, heights; labeled 48 targets; no default glow. Do not duplicate existing wrappers in this pass. |
| `core/ui/patterns/async_state_view.dart` | Spinner 32/stroke 3; state icon 72/36; title 18/body 13; error alpha .12/.4. `_defaultLoadingShimmer` is a spinner, not shimmer. | Native progress and explicit state contracts below; no shimmer dependency. |
| Home | 16/24 spacing; carousel 16:10 or 16:7; 250 ms transition; arrows on wide layouts; semantic slide count. | Preserve artwork hierarchy and feature geometry; semantic page/section/card type, no new carousel abstraction. |
| Catalog/Search/Detail | Shared poster grids/chips/search; 16 gutters; detail cover 104/132; title 24/32; hard-coded rating yellow `#FACC15`. Picker sheet at smaller widths, dialog at expanded; 78% screen height, 540×640 cap. | Consistent max widths and semantic colors; rating icon plus text, no status meaning inferred solely from yellow. |
| Library / Local | Library rows use 8 radius, container fill/border and 12 subtitle; mixed native/shared state controls. Local uses 96/100 bottom padding. | Same card/list contracts; derive bottom insets from actual navigation and SafeArea during migration, not spacing tokens for 96/100. |
| Settings | Surface cards radius 12; five accent swatches 36 square with repeated generic labels; OLED switch. | Accent is a seed, not unchecked foreground/background override; named selection and 48 targets. |
| Manga / player chrome | Manga blur 12, alpha .85, 48 chapter targets, slider track 3/thumb 6; player controls 52/64, timeline alpha .88, 12 time text. | Opaque high-container controls by default over unpredictable artwork; preserve playback/read workflows and larger primary controls. |
| Novel / publication | Separate OLED/charcoal/paper prose pairs; preference swatches 44 square; font size 12–26; progress label 10; publication padding 20, width 720, line height 1.6. | Independent reading canvas palette retained; user font/line-height settings remain authoritative, controls get 48 targets and labels. |
| Remote series / chapter wrappers | Native Scaffold/AppBar and ListTile, progress indicators, shared metadata header; chapter wrappers reuse manga/novel readers. | Theme native defaults; retain feature-owned chapter availability and progress semantics. |
| Shared artwork / metadata | `media_poster.dart`: total tile 2:3, 12 title/10 subtitle, 90-high gradient, .35 shadow, static gradient skeleton. `media_metadata_view.dart`: 120×160 source artwork and literal 16 padding. | Separate artwork aspect from scaled text height; title below artwork; consistent fallback, no default shadow. |

Additional layout evidence: detail hero 430/390 with text-scaling allowance up to 120; expanded detail uses 2:1 columns with 360 supporting width. Continue cards use 164/196 width and 184 height. Home warning alpha .08/border .28 differs slightly from detail .06–.07/.28–.32. Manga and player intentionally use black immersive canvases; manga zoom 1–4 and player 3-second auto-hide are behavior, not design tokens.

Intrinsic artwork ratios, seek intervals, reader font preferences, safe-area offsets and search debounce are not general spacing/motion tokens. Existing shortcomings above are migration work, not changes made to old screens here.

## Semantic API

`HikariDesignTheme.dark(oled: ..., accentSeed: ...)` builds Material 3 `ThemeData`. Dark and OLED are existing app modes; no speculative app-wide light mode. Paper is a reading canvas, not a light application theme. Generated accent tones deliberately differ from raw selected seeds so paired content stays readable.

Use `Theme.of(context).colorScheme`:

| Role | Native token |
| --- | --- |
| Page/background | `surface` (black in OLED) |
| Recessed/navigation | `surfaceContainerLowest` / `surfaceContainerLow` |
| Cards, fields, rows | `surfaceContainer` |
| Dim / bright surface bounds | `surfaceDim` / `surfaceBright` (pinned independently of accent seed) |
| Dialog/sheet/immersive controls | `surfaceContainerHigh` |
| Strong surface distinction | `surfaceContainerHighest` |
| Primary content | `onSurface` |
| Secondary/muted readable content | `onSurfaceVariant` (one role, not two weak near-duplicates) |
| Required control boundary / subtle divider | `outline` / `outlineVariant` |
| Main action / its content | `primary` / `onPrimary` |
| Selection | `secondaryContainer` / `onSecondaryContainer`, plus check/radio/selected semantics |
| Destructive/error | `error` / `onError`, or container pair |
| Modal barrier | `scrim`; barrier opacity remains native route behavior |
| Feedback inverse surface | `inverseSurface` / `onInverseSurface` |

Material owns standard roles; the small `HikariStatusColors` ThemeExtension adds only warning/info foreground-container pairs that Material lacks. These dark tones are accent-independent and maintain 4.5:1 text contrast. Warning consumers include `home_warning_notice.dart`, `source_search_content.dart`, and catalog-detail notices/warnings; `CatalogDetailProvenance` uses info. Access `Theme.of(context).extension<HikariStatusColors>()!` inside the target theme, then use `warningContainer` / `onWarningContainer` or `infoContainer` / `onInfoContainer` together. No success role is added without a demonstrated consumer. Include icon plus text for status. `HikariDesignTheme.destructiveAction(scheme)` styles a native FilledButton and preserves disabled precedence. Reader prose uses `HikariReadingPalette` background/content pairs, mapped from preferences by the feature during migration (no dependency on persisted enums).

The complete tonal surface family is accent-independent. `surfaceDim` equals the page `surface`; `surfaceBright` equals `surfaceContainerHighest`. OLED makes surface, dim surface and lowest container black while retaining higher tonal containers for separation. Inverse surfaces remain Material-generated paired feedback roles, not part of the obsidian container hierarchy.

Surfaces communicate hierarchy through tone, not default shadows. Cards, app bars, navigation and modal containers have zero elevation. Required outlines use 1 dp; focused fields use 2 dp. Decorative dividers are not promised 3:1 contrast. Avoid text over arbitrary images; use an opaque backing for reliable contrast rather than assuming a translucent scrim solves every image.

## Typography and geometry

Consume the current theme, not static TextStyle colors. Platform font defaults and fallbacks are retained; no font download or unbundled custom-family promise.

| Purpose | TextTheme | Size / weight / line height |
| --- | --- | --- |
| Hero | `displayLarge` | 32 / 700 / 1.25 |
| Page title | `headlineMedium` | 24 / 700 / 1.3 |
| Section | `titleLarge` | 20 / 600 / 1.4 |
| Card/list title | `titleMedium` | 16 / 600 / 1.5 |
| Compact title | `titleSmall` | 14 / 600 / 1.4 |
| Body / secondary body | `bodyLarge` / `bodyMedium` | 16 / 14, regular, 1.5 |
| Metadata/caption | `bodySmall` | 12 / regular / 1.5 |
| Action / compact label | `labelLarge` / `labelMedium` | 14 / 600, 12 / 500, 1.4 |

Other Material slots keep framework defaults; use listed roles for product UI. Essential text never uses 10/11 dp. Do not clamp TextScaler. Cards may truncate visual titles to two lines only if complete title remains available to semantics/detail; prose never truncates. Prefer growing rows/wrapping actions over fixed heights.

`HikariSpace`: micro 4, inline 8, compact 12, content 16, section 24, separation 32, spacious 48. `HikariShape`: small 8, medium 14, large 16, extraLarge 24; `pill` uses StadiumBorder rather than 999 radius. `HikariSize`: target 48, field minimum 48, icons 16/24/32; 2:3 poster and 16:9 landscape, poster grid max extent 200. Small icons never imply small hit targets.

## Adaptive and motion contracts

Use available bounded width from LayoutBuilder; window width only at navigation root. `HikariLayout.classify`: compact <600, medium <840, expanded <1200, wide >=1200 logical pixels. Gutters 16/24/32/32. Invalid/unbounded widths are rejected; callers must choose a bounded layout constraint. Content max 1440, prose max 720, dialog max 560, sheet max 640. These are caps, not required widths or fixed heights.

Compact uses existing bottom navigation, medium and above can retain rail; expanded may show supporting detail columns. Wide does not invent another navigation mode. Grids use available width with max poster extent and 12/16 gaps; calculate title space with scaled text, never a fixed total-card aspect ratio. Narrow windows, landscape height, keyboard insets and fold/resize must still work. Sheets/dialog bodies scroll; bounded width alone is insufficient. Reader max width is a starting cap, not a promised character count across fonts.

`HikariDesignMotion`: interaction 150 ms, standard 300 ms with `Easing.standard`, emphasized entrance 400 ms with `Easing.emphasizedDecelerate`. `duration(context, token)` handles only `MediaQuery.disableAnimationsOf(context)` (Android Remove animations), returning zero for decorative transitions. It is **not a cross-platform reduced-motion API**: Flutter 3.47.5 exposes iOS Reduce Motion separately as `AccessibilityFeatures.reduceMotion`, which this helper does not observe. Before iOS motion migration, handle that signal and its changes at the consuming boundary or extend this helper with focused tests. `accessibleNavigation` is not a substitute. No loops, bounce or autoplay added. Native route/progress motion is not globally disabled by this helper: migrating call sites must select no-transition routes/static loading presentation when requested. Debounce, seek and auto-hide timing remain behavior, not motion tokens.

## Component contracts (not a new widget library)

| Component | Canonical contract for migration |
| --- | --- |
| Actions | FilledButton primary, OutlinedButton secondary, TextButton tertiary, destructive style for destructive intent. Preserve label while busy, disable duplicate invocation, expose progress semantics. Minimum 48, grow with text; visible keyboard focus. |
| Icon actions | Native IconButton; required localized tooltip/semantic label, 48 target, 24 glyph. No gesture-only substitute. |
| Media cards | Artwork with stable aspect ratio/fallback, title below image, compact metadata, optional separately labeled action. No business entity required by visual primitive. Keep existing media patterns until migrated, avoid universal card flags. |
| Section headers | `titleLarge`, section gap 24, trailing native text action; wrap/stack at large text. Feature supplies labels/callbacks. Page titles use `Semantics(headingLevel: 1, ...)`; section titles use level 2 (deeper sections follow the hierarchy, levels 1–6). Nonzero `headingLevel` implies header semantics; do not duplicate an existing native heading node or include the trailing action inside the heading. |
| Chips/tags | FilterChip/ChoiceChip for selection (check plus semantics); noninteractive metadata stays text/tag. Native disabled states, padded targets; don't compress interactions to badge size. Keep native Material 3 chip side defaults (`outlineVariant` for enabled unselected chips, no selected border); no stronger Hikari override. ChoiceChip and FilterChip use the selection foreground/container pair. |
| Search/fields | Native SearchBar or TextField with visible/localized label, search action, labeled clear IconButton. Min 48; no maximum height. Validation beside field; search debounce stays feature/controller-owned. |
| Rows / pickers | Native ListTile/RadioListTile/CheckboxListTile as appropriate. Title + secondary metadata, explicit selection, full row target. Source/language names and selection logic supplied by feature. |
| Loading | Keep existing content during refresh. Native linear/circular progress, determinate if known; one accessible loading announcement. Skeleton, if later justified, preserves artwork/text footprint, is excluded from semantics, static for reduced motion. |
| Empty / error / offline | Title, short explanation and one relevant action; distinguish empty search, unavailable source, offline and failure. Error icon/text, not red alone. No raw exception details in copy. |
| Dialog / sheet | Dialog for focused decision or expanded picker; sheet for contextual compact selection. High surface, 24 radius, width caps, safe area, keyboard-aware scroll, dismiss/back semantics and focus restoration. Caller chooses route; theme cannot enforce route constraints. |
| Navigation / app bar | Native semantics, selected label+indicator, tonal surfaces. Navigation structure and tab state unchanged in this pass. |
| Reader / player | Prose canvas independent of chrome; opaque tonal controls, visible focus, labeled next/previous/play/seek. Immersive hide/show must preserve accessible alternative controls. Reader preferences not reset by app theme. |

## Adoption and evidence

New or migrated UI uses semantic design-system APIs. Do not add arbitrary colors, spacing scales, radii, typography variants or animation durations unless a real missing semantic concept is demonstrated. Do not mix legacy `context.hikariColors` with target ThemeData in one migrated subtree. Migrate shared primitive internals before switching root theme; use an explicitly scoped Theme for incremental adoption. Architecture tests keep the current adopter set explicit so migration cannot spread accidentally.

The first seam is `HikariSearchBar`: it now composes Flutter's native `SearchBar` under a scoped `HikariDesignTheme`, forwarding only the live root's semantic primary color as the temporary accent bridge. Existing debounce behavior is preserved in the wrapper for compatibility during this small migration; it is not a design-system responsibility or motion token and should move to the owning feature/controller when search behavior is next refactored. The target search surface roles are identical in dark and OLED, so this seam does not need to infer legacy OLED state. Remove this bridge when the root theme migrates.

Recommended sequence: finish shared actions/search/state primitives and scoped preview; app theme/navigation; Home + catalog/search; Detail + pickers; Library/Local/Settings; manga/player chrome; novel/publication chrome and prose mapping. Validate each on Android and Windows before broad adoption.

Automated foundation tests protect dark/OLED contrast pairs, reading contrast, fractional widths/invalid inputs, Android-style disabled-animation duration, native text contrast and touch targets/labels at 100% and 200% scaling, state styling, foundation isolation and the explicit migration-adopter allowlist. `HikariSearchBar` tests also verify the scoped target theme, 48dp search minimum, debounce and labeled clear action. They do not certify every future screen. Visually validate violet seed output, raised dark surfaces, OLED black separation, poster density, long localized titles, 200%+ scaling, keyboard focus, paper mode and real artwork overlays during migration. Run TalkBack and desktop keyboard checks; iOS requires macOS/device verification.

## Research and rationale

Reviewed 2026-10-06 using Firecrawl and local UI UX Pro Max. Generic generated red/news palette and marketing-hero layout did not fit Hikari and were rejected; Flutter theme/context guidance retained. Sources inform principles, not copied screens:

- [Flutter Material 3 defaults](https://docs.flutter.dev/release/breaking-changes/material-3-default): ColorScheme/TextTheme/component themes are native composition points.
- [Flutter `SearchBar`](https://api.flutter.dev/flutter/material/SearchBar-class.html) and [scoped themes](https://docs.flutter.dev/cookbook/design/themes): the native bar already exposes controller, submit/change callbacks, leading/trailing actions and semantic Material behavior; a local `Theme` is the supported incremental override boundary while the root remains legacy.
- [Netflix mobile layout](https://help.netflix.com/en/node/575087423404644): current media discovery keeps search/navigation simple and places swipeable shortcuts/filters near discovery rather than turning search into a separate visual system. Hikari keeps its existing search/filter flow; no Netflix styling is copied.
- [Flutter tonal surface roles](https://docs.flutter.dev/release/breaking-changes/new-color-scheme-roles): tone-based surface hierarchy instead of opacity elevation overlays.
- [Flutter accessibility](https://docs.flutter.dev/ui/accessibility): readable contrast, 48 targets, assistive technology and scaling checks. Foundation tests require 4.5:1 for normal text, including primary action text on tonal surfaces, selection and Snackbar action/content pairs. Color tests cover all product seeds (`#8B5CF6`, `#EC4899`, `#06B6D4`, `#10B981`, `#F59E0B`) plus black as a stress seed, in dark and OLED modes. Widget tests include Flutter's `textContrastGuideline`; 3:1 is not sufficient for normal-size action labels.
- [Flutter adaptive best practices](https://docs.flutter.dev/ui/adaptive-responsive/best-practices): touch first, constrain wide content, use available window space, retain state and keyboard support.
- [Android window classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes): 600/840/1200 thresholds. Hikari combines larger classes until a separate layout is justified.
- [Material motion](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration): 300 ms standard and 400 ms emphasized entrance. [Flutter Easing](https://api.flutter.dev/flutter/material/Easing-class.html) supplies native curves. Material token-table scrape was sparse; no unseen table values claimed.
- [Netflix artwork research](https://netflixtechblog.com/artwork-personalization-c589f074ad76): artwork helps discovery; stable recognition and informative imagery matter. No personalization system or Netflix branding adopted.
- [Mihon grid issue #3371](https://github.com/mihonapp/mihon/issues/3371): compact/comfortable/cover-only layouts expose artwork sizing consistency concerns. Use predictable cover geometry without copying density modes. Mihon reader documentation retrieval failed; no reader guideline claimed from that page.
