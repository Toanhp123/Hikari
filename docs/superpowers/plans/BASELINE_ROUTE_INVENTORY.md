# Baseline UI entry-point inventory (snapshot, not live checkout)

> Read-only extraction from `Hikari-dev(20261021-163525).zip`, taken on 2026-10-21. Count is `19` `*page.dart` files, **not** necessarily 19 distinct route IDs or all overlays. The live checkout must be rescanned before G0 approval.

| # | File | Phase owner |
|---:|---|---|
| 1 | `lib/features/catalog/catalog_detail_page.dart` | 02 Pilot A (catalog picker), 03 Pilot B (library/search), 04 migration |
| 2 | `lib/features/catalog/catalog_search_page.dart` | 02 Pilot A (catalog picker), 03 Pilot B (library/search), 04 migration |
| 3 | `lib/features/home/home_page.dart` | 02 Pilot A (catalog picker), 03 Pilot B (library/search), 04 migration |
| 4 | `lib/features/library/library_page.dart` | 02 Pilot A (catalog picker), 03 Pilot B (library/search), 04 migration |
| 5 | `lib/features/local_media/local_media_page.dart` | 02 Pilot A (catalog picker), 03 Pilot B (library/search), 04 migration |
| 6 | `lib/features/manga_reader/manga_reader_page.dart` | 06 |
| 7 | `lib/features/novel_reader/novel_reader_page.dart` | 06 |
| 8 | `lib/features/novel_reader/publication_reader_page.dart` | 06 |
| 9 | `lib/features/player/player_page.dart` | 06 |
| 10 | `lib/features/plugins/apk_actions_page.dart` | 05 |
| 11 | `lib/features/plugins/novel_actions_page.dart` | 05 |
| 12 | `lib/features/plugins/plugin_manager_page.dart` | 05 |
| 13 | `lib/features/reading/continue_reading_page.dart` | 02 Pilot A (catalog picker), 03 Pilot B (library/search), 04 migration |
| 14 | `lib/features/remote_manga/manga_chapter_reader_page.dart` | 06 |
| 15 | `lib/features/remote_manga/manga_series_page.dart` | 02 Pilot A (catalog picker), 03 Pilot B (library/search), 04 migration |
| 16 | `lib/features/remote_novel/novel_chapter_reader_page.dart` | 06 |
| 17 | `lib/features/remote_novel/novel_series_page.dart` | 02 Pilot A (catalog picker), 03 Pilot B (library/search), 04 migration |
| 18 | `lib/features/settings/settings_page.dart` | 05 |
| 19 | `lib/features/source_search/source_search_page.dart` | 02 Pilot A (catalog picker), 03 Pilot B (library/search), 04 migration |

## G0 required additions

- Enumerate actual route registrations and nested Navigator ownership in `lib/app/navigation/**`, **not** only `*page.dart` filenames.
- Enumerate `showDialog`, `showModalBottomSheet`, `showHikariModalSheet`, `OverlayEntry`, custom routes, navigation Drawers, `PopScope`, and existing `Navigator.pop` paths.
- Include feature dialogs/sheets launched from tiles or headers, accessibility overlays, and OS-owned dialogs as either target UI or named external exceptions.
- Record code-owner, replacement component, plan/task ID, test coverage and migration status for every path. Re-run inventory after each phase; unknown route blocks G7.

## Known Source Picker evidence

- `lib/features/catalog/catalog_detail_page.dart`: selects `_showSourceSheet` or `_showSourceDialog` by `context.isExpanded` and owns route/result callbacks.
- `lib/features/catalog/widgets/catalog_source_picker.dart`: `_close()` invokes `Navigator.of(context).pop()` directly and resets picker ViewModel selection. Both the page and picker closure mechanics must move to the typed presenter without losing language-step Back semantics.
