import 'dart:convert';

import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/local_media_scan_result.dart';
import 'package:hikari/features/library/library_page.dart';
import 'package:hikari/features/local_media/local_media_page.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';

void main() {
  const media = Media(
    title: 'Saved book',
    type: MediaType.lightNovel,
    source: SourceMediaRef(sourceId: SourceId.local, itemId: 'book'),
  );
  late UserDatabase db;
  late SqliteLibraryRepository library;
  setUp(() {
    db = UserDatabase(NativeDatabase.memory());
    library = SqliteLibraryRepository(db);
  });
  tearDown(() => db.close());
  testWidgets(
    'library empty state and persisted snapshots open without scan then remove',
    (tester) async {
      Media? opened;
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: LibraryPage(
            repository: library,
            openMedia: (_, item) {
              opened = item;
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Your library is empty.'), findsOneWidget);
      await library.upsert(
        LibraryEntry(media: media, addedAt: DateTime.utc(2026)),
      );
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: LibraryPage(
            repository: library,
            openMedia: (_, item) {
              opened = item;
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tapAt(tester.getCenter(find.text('Saved book')));
      expect(opened!.source, media.source);
      await tester.tap(find.byTooltip('Remove from library'));
      await tester.pumpAndSettle();
      await tester.pumpAndSettle();
      expect(await library.contains(media.source), isFalse);
      await tester.pumpAndSettle();
      expect(find.text('Your library is empty.'), findsOneWidget);

      // LibraryPage owns a live Drift watch; dispose it before closing the
      // in-memory database in tearDown.
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
    },
  );
  testWidgets('Library grid and list render persisted artwork', (tester) async {
    const cover = SourceMediaRef(
      sourceId: SourceId('provider'),
      itemId: 'cover',
    );
    final image = base64Decode(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGOQNJvzHwADQgHrKgED3AAAAABJRU5ErkJggg==',
    );
    await library.upsert(
      LibraryEntry(media: media, artwork: cover, addedAt: DateTime.utc(2026)),
    );
    var requested = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: LibraryPage(
          repository: library,
          openMedia: (_, _) {},
          readArtwork: (resource) async {
            expect(resource, cover);
            requested++;
            return image;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(requested, greaterThan(0));
    expect(find.byType(Image), findsWidgets);
    await tester.tap(find.byTooltip('Switch to list view'));
    await tester.pumpAndSettle();
    expect(find.byType(Image), findsWidgets);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('scan result toggles persisted library membership', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: LocalMediaPage(
          library: library,
          scanSelectedRoot: () async =>
              LocalMediaScanResult(rootName: 'Saved', media: [media]),
          chooseRoot: () async => false,
          openMedia: (_, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Add to library'));
    await tester.pumpAndSettle();
    expect(await library.contains(media.source), isTrue);
    await tester.tap(find.byTooltip('Remove from library'));
    await tester.pumpAndSettle();
    expect(await library.contains(media.source), isFalse);
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
  });

  testWidgets('reactive repository updates an already mounted LibraryPage', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: LibraryPage(repository: library, openMedia: (_, _) {}),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Your library is empty.'), findsOneWidget);

    await library.upsert(
      LibraryEntry(media: media, addedAt: DateTime.utc(2026)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Saved book'), findsOneWidget);
    expect(find.text('1 saved title'), findsOneWidget);

    // Dispose the page before the database tearDown closes Drift so the
    // reactive watch subscription is cancelled deterministically.
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
  });
  testWidgets('empty Library offers working catalog navigation', (
    tester,
  ) async {
    var browseCount = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: LibraryPage(
          repository: library,
          openMedia: (_, _) {},
          onNavigateToSearch: () => browseCount++,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Your library is empty.'), findsOneWidget);
    await tester.tap(find.text('Explore titles'));
    expect(browseCount, 1);
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
  });

  testWidgets('Library filters, restores all titles and toggles view mode', (
    tester,
  ) async {
    const manga = Media(
      title: 'Saved manga',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'manga'),
    );
    await library.upsert(
      LibraryEntry(media: media, addedAt: DateTime.utc(2026)),
    );
    await library.upsert(
      LibraryEntry(media: manga, addedAt: DateTime.utc(2026)),
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: LibraryPage(repository: library, openMedia: (_, _) {}),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('2 saved titles'), findsOneWidget);
    expect(find.text('All'), findsOneWidget);
    expect(find.text('All Types'), findsNothing);
    final chips = tester
        .widgetList<FilterChip>(find.byType(FilterChip))
        .toList();
    expect(chips, hasLength(MediaType.values.length + 1));
    for (final chip in chips) {
      expect((chip.label as Row).children, hasLength(1));
    }
    expect(
      tester.getTopLeft(find.byType(FilterChip).at(1)).dx -
          tester.getTopRight(find.byType(FilterChip).first).dx,
      HikariSpacing.sm,
    );
    await tester.tap(find.text('Manga'));
    await tester.pumpAndSettle();
    expect(find.text('1 of 2 titles'), findsOneWidget);
    // Selected chips remain selected; use All to clear a single-select filter.
    await tester.tap(find.text('Manga'));
    await tester.pumpAndSettle();
    expect(find.text('1 of 2 titles'), findsOneWidget);
    await tester.tap(find.text('All'));
    await tester.pumpAndSettle();
    expect(find.text('2 saved titles'), findsOneWidget);
    await tester.tap(find.text('Anime'));
    await tester.pumpAndSettle();
    expect(find.text('No matching titles'), findsOneWidget);
    expect(find.text('0 of 2 titles'), findsOneWidget);
    await tester.tap(find.text('Show all types'));
    await tester.pumpAndSettle();
    expect(find.text('2 saved titles'), findsOneWidget);
    await tester.tap(find.byTooltip('Switch to list view'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Switch to grid view'), findsOneWidget);
    expect(find.text('Saved book'), findsOneWidget);
    expect(find.text('Saved manga'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
  });
  testWidgets('Library actions fit compact screens with enlarged text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 680);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await library.upsert(
      LibraryEntry(media: media, addedAt: DateTime.utc(2026)),
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(1.6)),
          child: LibraryPage(repository: library, openMedia: (_, _) {}),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Library'), findsOneWidget);
    expect(find.byTooltip('Switch to list view'), findsOneWidget);
    await tester.tap(find.byTooltip('Switch to list view'));
    await tester.pumpAndSettle();
    expect(find.text('Saved book'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
  });
}
