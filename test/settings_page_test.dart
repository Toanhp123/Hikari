import 'dart:async';
import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';

import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/cache/cache_usage.dart';
import 'package:hikari/features/settings/settings_page.dart';

void main() {
  testWidgets('appearance save status is visible and retry is available', (
    tester,
  ) async {
    var retries = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          appearanceSaveStatus: AppearanceSaveStatus.failed,
          onRetryAppearanceSave: () => retries++,
        ),
      ),
    );
    expect(find.textContaining('Could not save appearance.'), findsOneWidget);
    await tester.tap(find.text('Retry'));
    expect(retries, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('appearance persistence status hides retry while saving', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(appearanceSaveStatus: AppearanceSaveStatus.saving),
      ),
    );
    expect(find.text('Saving appearance…'), findsOneWidget);
    expect(find.text('Retry'), findsNothing);
  });

  testWidgets('saved folder name appears after an async metadata lookup', (
    tester,
  ) async {
    final folder = Completer<String?>();
    var reads = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          onChooseLocalFolder: () async => false,
          loadLocalFolderName: () {
            reads++;
            return folder.future;
          },
        ),
      ),
    );
    expect(find.text('Checking selected folder…'), findsOneWidget);
    folder.complete('Books & comics');
    await tester.pumpAndSettle();
    expect(find.text('Books & comics'), findsOneWidget);
    expect(reads, 1);
  });

  testWidgets('folder choice refreshes displayed name; cancel preserves it', (
    tester,
  ) async {
    var name = 'Old library';
    var changed = false;
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          loadLocalFolderName: () async => name,
          onChooseLocalFolder: () async {
            if (changed) return false;
            changed = true;
            name = 'New library';
            return true;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Old library'), findsOneWidget);
    await tester.tap(find.text('Local Media Folder'));
    await tester.pumpAndSettle();
    expect(find.text('New library'), findsOneWidget);
    await tester.tap(find.text('Local Media Folder'));
    await tester.pumpAndSettle();
    expect(find.text('New library'), findsOneWidget);
  });

  testWidgets('failed folder lookup can be retried without opening picker', (
    tester,
  ) async {
    var reads = 0;
    var picks = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          onChooseLocalFolder: () async {
            picks++;
            return false;
          },
          loadLocalFolderName: () async {
            if (++reads == 1) throw StateError('Provider busy');
            return 'Restored folder';
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.text('Folder unavailable. Check access or try again.'),
      findsOneWidget,
    );
    await tester.tap(find.text('Retry folder check'));
    await tester.pumpAndSettle();
    expect(find.text('Restored folder'), findsOneWidget);
    expect(reads, 2);
    expect(picks, 0);
  });

  testWidgets('stale folder lookup cannot replace a newer selection', (
    tester,
  ) async {
    final first = Completer<String?>();
    final second = Completer<String?>();
    var revision = 0;
    var reads = 0;
    Future<String?> readName() => ++reads == 1 ? first.future : second.future;
    late StateSetter update;
    await tester.pumpWidget(
      MaterialApp(
        home: StatefulBuilder(
          builder: (context, setState) {
            update = setState;
            return SettingsPage(
              localFolderRevision: revision,
              onChooseLocalFolder: () async => false,
              loadLocalFolderName: readName,
            );
          },
        ),
      ),
    );
    update(() => revision++);
    await tester.pump();
    second.complete('Newest folder');
    await tester.pumpAndSettle();
    first.complete('Old folder');
    await tester.pumpAndSettle();
    expect(find.text('Newest folder'), findsOneWidget);
    expect(find.text('Old folder'), findsNothing);
  });

  testWidgets('Settings displays no selected folder when none is saved', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          onChooseLocalFolder: () async => false,
          loadLocalFolderName: () async => null,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.text('No folder selected. Choose one to scan media.'),
      findsOneWidget,
    );
  });

  testWidgets('folder picker failure offers retry without uncaught errors', (
    tester,
  ) async {
    var attempts = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          onChooseLocalFolder: () async {
            if (++attempts == 1) throw StateError('picker unavailable');
            return true;
          },
        ),
      ),
    );
    await tester.tap(find.text('Local Media Folder'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(
      find.text('Could not choose a local folder. Try again.'),
      findsOneWidget,
    );
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(attempts, 2);
  });
  testWidgets('cancelled folder selection does not show a success message', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(home: SettingsPage(onChooseLocalFolder: () async => false)),
    );
    await tester.tap(find.text('Local Media Folder'));
    await tester.pumpAndSettle();
    expect(find.text('Local media folder updated.'), findsNothing);
  });

  testWidgets('successful folder selection confirms the change', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(home: SettingsPage(onChooseLocalFolder: () async => true)),
    );
    await tester.tap(find.text('Local Media Folder'));
    await tester.pumpAndSettle();
    expect(find.text('Local media folder updated.'), findsOneWidget);
  });

  testWidgets('folder picker is disabled while a selection is pending', (
    tester,
  ) async {
    final selection = Completer<bool>();
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          onChooseLocalFolder: () {
            calls++;
            return selection.future;
          },
        ),
      ),
    );
    await tester.tap(find.text('Local Media Folder'));
    await tester.pump();
    expect(calls, 1);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.widgetWithText(ListTile, 'Local Media Folder'), findsOneWidget);
    final tile = tester.widget<ListTile>(
      find.widgetWithText(ListTile, 'Local Media Folder'),
    );
    expect(tile.onTap, isNull);
    selection.complete(false);
    await tester.pumpAndSettle();
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(calls, 1);
  });

  testWidgets('accent swatches use selected seed and named 48dp targets', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    Color? tapped;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(accentColor: const Color(0xFF06B6D4)),
        home: SettingsPage(
          selectedAccent: const Color(0xFF06B6D4),
          onSelectAccent: (color) => tapped = color,
        ),
      ),
    );
    final cyan = find.bySemanticsLabel('Cyan accent');
    expect(cyan, findsOneWidget);
    expect(
      tester.getSemantics(cyan).flagsCollection.isSelected,
      Tristate.isTrue,
    );
    expect(tester.getSize(cyan).width, greaterThanOrEqualTo(48));
    expect(tester.getSize(cyan).height, greaterThanOrEqualTo(48));
    await tester.tap(cyan);
    expect(tapped, const Color(0xFF06B6D4));
    semantics.dispose();
  });

  testWidgets('default accent restores nullable raw seed', (tester) async {
    final semantics = tester.ensureSemantics();
    Color? latest = const Color(0xFF8B5CF6);
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(accentColor: latest),
        home: SettingsPage(
          selectedAccent: latest,
          onSelectAccent: (value) => latest = value,
        ),
      ),
    );
    final reset = find.bySemanticsLabel('Default accent');
    expect(reset, findsOneWidget);
    expect(tester.getSize(reset).width, greaterThanOrEqualTo(48));
    expect(
      tester.getSemantics(reset).flagsCollection.isSelected,
      Tristate.isFalse,
    );
    await tester.tap(reset);
    await tester.pump();
    expect(latest, isNull);
    semantics.dispose();
  });

  testWidgets('accent choices wrap on narrow screens and content is bounded', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: SettingsPage(onSelectAccent: (_) {}),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    final semantics = tester.ensureSemantics();
    final violet = find.bySemanticsLabel('Violet accent');
    final amber = find.bySemanticsLabel('Amber accent');
    expect(violet, findsOneWidget);
    expect(amber, findsOneWidget);
    expect(
      tester.getTopLeft(amber).dy,
      greaterThan(tester.getTopLeft(violet).dy),
    );

    await tester.binding.setSurfaceSize(const Size(1200, 900));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(tester.getSize(find.byType(ListView)).width, lessThanOrEqualTo(640));
    semantics.dispose();
  });

  testWidgets('SettingsPage renders sections and handles clear cache', (
    tester,
  ) async {
    bool? oledToggled;
    var cacheCleared = false;

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: SettingsPage(
          onToggleOled: (val) => oledToggled = val,
          onClearCache: () async => cacheCleared = true,
          loadCacheUsage: () async => const CacheUsage({
            'Source artwork': 1024,
            'Manga pages': 2048,
            'Novel chapters': 0,
            'Novel resources': 0,
          }),
        ),
      ),
    );

    expect(find.text('Settings'), findsOneWidget);
    expect(find.text('Appearance'), findsOneWidget);
    expect(find.text('OLED Pure Black'), findsOneWidget);
    expect(find.text('About Hikari'), findsOneWidget);

    // Toggle OLED
    await tester.tap(find.byType(Switch));
    await tester.pumpAndSettle();
    expect(oledToggled, isTrue);

    // Clear Cache
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Cached temporary data: 3.0 KiB'),
      findsOneWidget,
    );
    expect(find.text('Manga pages'), findsNothing);
    final collapsedHeight = tester.getSize(find.byType(ExpansionTile)).height;
    await tester.tap(find.text('Cache breakdown'));
    await tester.pumpAndSettle();
    expect(find.text('Manga pages'), findsOneWidget);
    expect(
      tester.getSize(find.byType(ExpansionTile)).height,
      greaterThan(collapsedHeight),
    );
    await tester.tap(find.text('Cache breakdown'));
    await tester.pumpAndSettle();
    expect(find.text('Manga pages'), findsNothing);
    await tester.tap(find.text('Clear'));
    await tester.pumpAndSettle();
    expect(cacheCleared, isFalse);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(cacheCleared, isFalse);
    await tester.tap(find.text('Clear'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Clear cache'));
    await tester.pumpAndSettle();
    expect(cacheCleared, isTrue);
    expect(find.text('Temporary cache cleared.'), findsOneWidget);
  });
  testWidgets('cache breakdown stays collapsed and retains state on refresh', (
    tester,
  ) async {
    var bytes = 1024;
    var measurements = 0;
    Future<CacheUsage> load() async {
      measurements++;
      return CacheUsage({'Manga pages': bytes});
    }

    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          onClearCache: () async {
            bytes = 0;
          },
          loadCacheUsage: load,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Cache breakdown'), findsOneWidget);
    expect(find.text('Manga pages'), findsNothing);
    expect(find.textContaining('1.0 KiB'), findsOneWidget);

    await tester.tap(find.text('Cache breakdown'));
    await tester.pumpAndSettle();
    expect(find.text('Manga pages'), findsOneWidget);
    await tester.tap(find.text('Clear'));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Your Library, reading progress, selected sources'),
      findsOneWidget,
    );
    await tester.tap(find.text('Clear cache'));
    await tester.pumpAndSettle();
    expect(measurements, 2);
    expect(find.text('Manga pages'), findsOneWidget);
    expect(find.textContaining('Cached temporary data: 0 B'), findsOneWidget);
  });

  testWidgets('cache sizes include GiB in summary and breakdown', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          onClearCache: () async {},
          loadCacheUsage: () async =>
              const CacheUsage({'Manga pages': 2 * 1024 * 1024 * 1024}),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('2.0 GiB'), findsOneWidget);
    expect(find.text('Manga pages'), findsNothing);
    await tester.tap(find.text('Cache breakdown'));
    await tester.pumpAndSettle();
    expect(find.text('2.0 GiB'), findsOneWidget);
    expect(find.text('Manga pages'), findsOneWidget);
  });

  testWidgets('narrow large text keeps clear action and details reachable', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 660));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2)),
          child: SettingsPage(
            onClearCache: () async {},
            loadCacheUsage: () async => const CacheUsage({'Manga pages': 1024}),
          ),
        ),
      ),
    );
    await tester.scrollUntilVisible(find.text('Cache breakdown'), 100);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('Clear'), findsOneWidget);
    await tester.tap(find.text('Cache breakdown'));
    await tester.pumpAndSettle();
    expect(find.text('Manga pages'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('failed clear is never shown as successful', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          onClearCache: () async => throw StateError('failed disk operation'),
          loadCacheUsage: () async => const CacheUsage({'Manga pages': 20}),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Clear'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Clear cache'));
    await tester.pumpAndSettle();
    expect(
      find.text('Could not clear all temporary cache. Try again.'),
      findsOneWidget,
    );
    expect(find.textContaining('Cached temporary data: 20 B'), findsOneWidget);
  });

  testWidgets('cache size failure can be retried', (tester) async {
    var attempts = 0;
    Future<CacheUsage> load() async {
      if (++attempts == 1) throw StateError('metadata unavailable');
      return const CacheUsage({'Source artwork': 12});
    }

    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(onClearCache: () async {}, loadCacheUsage: load),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Cache size unavailable.'), findsOneWidget);
    expect(find.text('Cache breakdown'), findsNothing);
    await tester.tap(find.text('Retry cache size'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Cached temporary data: 12 B'), findsOneWidget);
    expect(attempts, 2);
  });

  testWidgets('cache stats retry and stale completion protection', (
    tester,
  ) async {
    final old = Completer<CacheUsage>();
    var requests = 0;
    Future<CacheUsage> read() {
      requests++;
      if (requests == 1) return old.future;
      return Future.value(const CacheUsage({'Manga pages': 3}));
    }

    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(onClearCache: () async {}, loadCacheUsage: read),
      ),
    );
    expect(find.text('Measuring cache…'), findsOneWidget);
    // Clearing starts a newer stats request; the original must not overwrite it.
    await tester.tap(find.text('Clear'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Clear cache'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Cached temporary data: 3 B'), findsOneWidget);
    old.complete(const CacheUsage({'Manga pages': 99}));
    await tester.pumpAndSettle();
    expect(find.textContaining('Cached temporary data: 3 B'), findsOneWidget);
  });

  testWidgets('Settings uses the canonical app bar title style', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(theme: HikariTheme.darkTheme(), home: const SettingsPage()),
    );
    expect(find.text('Settings'), findsOneWidget);
    expect(
      Theme.of(tester.element(find.text('Settings')))
          .appBarTheme
          .titleTextStyle
          ?.fontWeight,
      FontWeight.w700,
    );
  });
}
