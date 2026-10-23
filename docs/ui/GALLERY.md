# Standalone UI Gallery — post-G4 reference

**Lifecycle:** Preview. **Scope:** developer-only composition samples and
synthetic fixtures. This synchronized reference does not reopen G4, promote any
component to Stable, or finish Phase 07/04's final catalog/deprecation map.
See [component ownership](COMPONENTS.md), [contributing](CONTRIBUTING.md),
[presentation patterns](PATTERNS.md) and [responsive contracts](RESPONSIVE.md).

## Launch and controls

From repository root, with the pinned FVM SDK:

```powershell
fvm flutter run -d windows -t tool/ui_gallery/main.dart
# Or replace windows with the ID of an available Android device.
```

The standalone `HikariGallery` keeps dark/OLED and accent seeds, enabled/loading,
stress text, reduced motion, RTL, 1x/2x/3x scaling, local widths from 220 to
1440dp, and compact/expanded Source Picker samples. RTL, scale and reduced-motion
overrides wrap the Gallery Navigator, including root dialogs and sheets. The **Showcase** choice
switches among the families below instead of mounting every sample in one long
list. The local width is a preview constraint; it never resizes the host window
or simulates physical IME/Back/TalkBack performance.

| Showcase | Canonical Preview APIs demonstrated | Preview file | Focused regression |
|---|---|---|---|
| Foundations | Semantic color swatches, Material typography with foundation spacing/radius | `tool/ui_gallery/foundation_preview.dart` | `test/core/ui/gallery/hikari_gallery_composition_test.dart` |
| Controls & feedback | HikariButton, HikariIconButton, HikariChoiceMenu, HikariFilterChip, HikariChipRow, HikariSearchField, HikariSegmentedControl, HikariAsyncStateView, HikariNoticeBanner, HikariListRow, HikariSectionHeader | `tool/ui_gallery/main.dart` | `test/core/ui/hikari_public_api_test.dart` |
| Media | HikariArtwork, HikariPosterCard, HikariLandscapeCard, HikariChapterRow, HikariAdaptiveGrid, HikariHorizontalShelf | `tool/ui_gallery/media_preview.dart` | `test/core/ui/media/hikari_media_gallery_test.dart` |
| Page & navigation | HikariPageScaffold, HikariPageHeader, HikariContentRegion, HikariNavigationShell, HikariNavigationDestination; five/six destinations exercise bottom bar, rail and drawer | `tool/ui_gallery/page_navigation_preview.dart` | `test/core/ui/gallery/hikari_gallery_composition_test.dart` |
| Surfaces | HikariDialog, HikariBottomSheet, showHikariConfirmationDialog, showHikariAdaptive, typed completed/cancelled results | `tool/ui_gallery/surface_preview.dart` | `test/core/ui/gallery/hikari_gallery_composition_test.dart` |
| Picker (independent launch actions) | Production CatalogSourcePicker content with synthetic empty SourceRegistry in forced compact sheet / expanded dialog | `tool/ui_gallery/picker_preview.dart` | `test/core/ui/surfaces/hikari_gallery_surfaces_test.dart` |

The page/navigation fixture is deliberately **not** the app's Router, retained
tab host or domain source identity. It is a bounded shell demo whose selection
lives only in the preview. The modal demos settle interaction results only; they
do not grant native permissions, install plugins, persist settings or fetch data.
The picker uses feature-owned VM/sample records, which is why it remains in
`tool/ui_gallery/picker_preview.dart`, outside canonical core UI.

## Verification and deferred work

```powershell
fvm flutter test -r expanded test/core/ui/hikari_public_api_test.dart test/core/ui/gallery/hikari_gallery_composition_test.dart test/core/ui/media/hikari_media_gallery_test.dart test/core/ui/surfaces/hikari_gallery_surfaces_test.dart
fvm flutter test -r expanded test/ui_documentation_contract_test.dart test/architecture_guard_test.dart
fvm flutter analyze --no-pub
fvm flutter test --no-pub
```

This document records intended **test commands**, not claimed pass results.
The real Windows/Android gallery, physical focus/keyboard, TalkBack/Narrator,
IME and device performance require separately dated test evidence. Phase 07/04
still owns the final linkable component catalog, all Stable promotion decisions,
remaining deprecation map and complete end-to-end documentation audit.
