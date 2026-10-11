# ADR-016: Theme hardening and verification boundary

Status: Applied to the `feat/ui-ux-hardening` ZIP baseline on 2026-10-08. Flutter-runtime verification pending.

## Contract

- `HikariTheme.darkTheme` in `lib/app/theme/hikari_theme.dart` is the **single owner** of root `ThemeData`, component themes, accent seeds, OLED surfaces, typography and slider treatment.
- App UI consumes `Theme.of(context).colorScheme` and `.textTheme`; only the missing Material warning/info semantic pairs use the required `HikariStatusColors` extension. The old `HikariColors` alias layer was deleted.
- All media identity badges share one container/foreground resolver in `media_type_presentation.dart`. Consumers may vary layout but not invent badge color roles.
- Feature views do not build independent `ThemeData` or define fallback hex/status palettes. Immersive video backgrounds, image scrims and reader-specific prose palettes remain feature-owned content surfaces.

## Self-review

- Inspected changed consumers in Home, navigation, Catalog, source search, Library, local media, Settings, manga reader, novel reader, player, and core UI components.
- Corrected missing `colors` binding in source search, three invalid migrated `surfaceContainerHigh` identifiers, an unintended 48dp passive reader progress strip, and scoped slider styling. Checked import hygiene and consolidated duplicate badge lookups in list cells.
- `test/theme_test.dart`, `test/hikari_design_test.dart` and `test/library_page_test.dart` were updated. `test/media_type_badge_theme_test.dart` and `test/theme_architecture_test.dart` protect semantic pairs and forbid a second theme/palette API.
- Static source scan: 139 production Dart files; no legacy `HikariColors`, no extra root `ThemeData` factory, no color-role misspellings, no direct status fallback or app-chrome hex literals outside theme/reader exceptions.
- Diff whitespace check and application of generated patch against a fresh ZIP baseline are required final verification gates.

## Validation limit

This review environment does **not** include `dart`, `flutter` or an Android emulator. Dart formatting, `flutter analyze`, `flutter test`, `tool/check.ps1`, debug APK compilation, golden screenshots and device performance checks must be executed on a Flutter-equipped workstation before merging. Static checks do not establish that the Flutter suite passes.

Recommended on Windows:

```powershell
fvm dart format .
fvm flutter analyze
fvm flutter test
.\tool\check.ps1
fvm flutter build apk --debug
```

No commit, push or pull request was performed as part of the ZIP patch.
