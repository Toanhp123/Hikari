import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:hikari/features/remote_manga/manga_series_page.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';

Future<void> _openCatalogSourceSearch(WidgetTester tester) async {
  await tester.tap(find.text('View details'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Read'));
  await tester.pumpAndSettle();
  expect(find.text('Read from'), findsOneWidget);
  await tester.tap(find.text('Search all sources'));
  await tester.pump();
  for (var i = 0; i < 20 && find.text('Series').evaluate().isEmpty; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
  expect(find.text('Series'), findsOneWidget);
}

final class _TestCatalogProvider implements CatalogProvider {
  _TestCatalogProvider(MediaType type)
    : _entry = CatalogEntry(
        id: const CatalogEntryId(provider: 'test', value: 'remote'),
        title: 'test',
        type: type,
      );

  final CatalogEntry _entry;

  @override
  String get id => 'test';

  @override
  Future<CatalogDiscovery> discover() async => CatalogDiscovery(
    sections: {
      CatalogSection.featured: [_entry],
    },
  );

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async =>
      CatalogEntryDetails(entry: _entry);

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      const [];

  @override
  Future<void> close() async {}
}

class FakeRemote
    implements MangaSearchSource, MangaSeriesSource, MangaPageSource {
  @override
  SourceId get id => const SourceId('fake');
  @override
  String get name => 'Fake remote';
  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      MangaSearchPage(
        page: page,
        hasNextPage: false,
        results: [
          MangaPreview(
            media: Media(
              title: 'Series',
              type: MediaType.manga,
              source: SourceMediaRef(
                sourceId: SourceId('fake'),
                itemId: 'series',
              ),
            ),
          ),
        ],
      );
  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef manga) async =>
      MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Series'),
        chapters: [
          MangaChapter(
            title: 'Chapter',
            source: SourceMediaRef(
              sourceId: SourceId('fake'),
              itemId: 'chapter',
            ),
            scanlator: 'Group',
          ),
        ],
      );
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async {
    expect(readable.itemId, 'chapter');
    return [];
  }

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

class ResumableRemote extends FakeRemote {
  final _png = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==',
  );

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => [
    const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'page-0'),
    const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'page-1'),
  ];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => _png;
}

class DuplicateIdRemote extends FakeRemote {
  @override
  SourceId get id => SourceId.local;
}

class UnavailableRemote extends FakeRemote {
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async {
    throw StateError('chapter unavailable');
  }
}

class _CountingRemote extends FakeRemote {
  _CountingRemote({required this.onSearch});
  final void Function() onSearch;

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async {
    onSearch();
    return super.search(query, page: page);
  }
}

class _SearchOnlySource implements MangaSearchSource {
  @override
  SourceId get id => const SourceId('search-only');

  @override
  String get name => 'Search only';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      MangaSearchPage(results: [], page: page, hasNextPage: false);
}

class _SecondRemote extends FakeRemote {
  @override
  SourceId get id => const SourceId('second');

  @override
  String get name => 'Second remote';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      MangaSearchPage(
        page: page,
        hasNextPage: false,
        results: [
          MangaPreview(
            media: Media(
              title: 'Second series',
              type: MediaType.manga,
              source: SourceMediaRef(sourceId: id, itemId: 'series'),
            ),
          ),
        ],
      );
}

class _OwnedLocal extends LocalMediaSource {
  int closes = 0;
  bool fail = false;
  @override
  Future<void> close() async {
    closes++;
    if (fail) throw StateError('cleanup failed');
  }
}

void main() {
  test(
    'injected local source ownership and failed disposal retry are explicit',
    () async {
      final db = UserDatabase(NativeDatabase.memory());
      addTearDown(db.close);
      final source = _OwnedLocal();
      final callerOwned = AppDependencies.create(
        database: db,
        localMediaSource: source,
      );
      await callerOwned.dispose();
      expect(source.closes, 0);
      final owned = AppDependencies.create(
        database: db,
        localMediaSource: source,
        ownsLocalMediaSource: true,
      );
      source.fail = true;
      await expectLater(owned.dispose(), throwsStateError);
      source.fail = false;
      await Future.wait([owned.dispose(), owned.dispose()]);
      expect(source.closes, 2);
      await owned.dispose();
      expect(source.closes, 2);
    },
  );
  testWidgets('app boots without remote sources and keeps Library available', (
    tester,
  ) async {
    const channel = MethodChannel('hikari/local_media');
    final messenger = tester.binding.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'selectedTree');
      return null;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
    final db = UserDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    final dependencies = AppDependencies.create(database: db);
    addTearDown(dependencies.dispose);

    expect(dependencies.searchManga.options, isEmpty);
    expect(dependencies.localMediaSource.id, SourceId.local);
    await tester.pumpWidget(HikariApp(dependencies: dependencies));
    await tester.pumpAndSettle();
    expect(find.text('Browse manga'), findsNothing);
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    // Advance fake time so Drift's deferred stream disposal can finish.
    await tester.pump(Duration.zero);
  });

  test('duplicate source ids fail fast during app composition', () async {
    final db = UserDatabase(NativeDatabase.memory());
    addTearDown(db.close);

    expect(
      () => AppDependencies.create(
        database: db,
        additionalSources: [DuplicateIdRemote()],
      ),
      throwsStateError,
    );
  });

  testWidgets(
    'search-only source is not exposed as an openable manga workflow',
    (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.windows;
      final db = UserDatabase(NativeDatabase.memory());
      addTearDown(db.close);

      try {
        await tester.pumpWidget(
          HikariApp(
            dependencies: AppDependencies.create(
              database: db,
              additionalSources: [_SearchOnlySource()],
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Browse manga'), findsNothing);
      } finally {
        await tester.pumpWidget(const SizedBox());
        // Advance fake time so Drift's deferred stream disposal can finish.
        await tester.pump(Duration.zero);
        debugDefaultTargetPlatformOverride = null;
      }
    },
  );

  testWidgets(
    'registered search sources appear without app orchestration changes',
    (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.windows;
      final db = UserDatabase(NativeDatabase.memory());
      addTearDown(db.close);

      try {
        await tester.pumpWidget(
          HikariApp(
            dependencies: AppDependencies.create(
              database: db,
              catalogProvider: _TestCatalogProvider(MediaType.manga),
              additionalSources: [FakeRemote(), _SecondRemote()],
            ),
          ),
        );
        await tester.pumpAndSettle();
        await _openCatalogSourceSearch(tester);

        expect(find.text('Second series'), findsOneWidget);
      } finally {
        await tester.pumpWidget(const SizedBox());
        // Advance fake time so Drift's deferred stream disposal can finish.
        await tester.pump(Duration.zero);
        debugDefaultTargetPlatformOverride = null;
      }
    },
  );

  testWidgets('unavailable remote chapter does not open a broken reader', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    final db = UserDatabase(NativeDatabase.memory());
    addTearDown(db.close);

    try {
      await tester.pumpWidget(
        HikariApp(
          dependencies: AppDependencies.create(
            database: db,
            catalogProvider: _TestCatalogProvider(MediaType.manga),
            additionalSources: [UnavailableRemote()],
          ),
        ),
      );
      await tester.pumpAndSettle();
      await _openCatalogSourceSearch(tester);
      await tester.tap(find.text('Series'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Chapter'));
      await tester.pumpAndSettle();

      expect(find.byType(MangaSeriesPage), findsOneWidget);
      expect(find.byType(MangaReaderPage), findsNothing);
      expect(
        find.text(
          'Could not open this chapter. It may no longer be available from the source.',
        ),
        findsOneWidget,
      );
    } finally {
      await tester.pumpWidget(const SizedBox());
      // Advance fake time so Drift's deferred stream disposal can finish.
      await tester.pump(Duration.zero);
      debugDefaultTargetPlatformOverride = null;
    }
  });

  testWidgets('remote series opens chapters then reader on non Android', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    final db = UserDatabase(NativeDatabase.memory());
    await tester.pumpWidget(
      HikariApp(
        dependencies: AppDependencies.create(
          database: db,
          catalogProvider: _TestCatalogProvider(MediaType.manga),
          additionalSources: [FakeRemote()],
        ),
      ),
    );
    await tester.pumpAndSettle();
    await _openCatalogSourceSearch(tester);
    await tester.tap(find.text('Series'));
    await tester.pumpAndSettle();
    expect(find.byType(MangaSeriesPage), findsOneWidget);
    expect(find.byType(MangaReaderPage), findsNothing);
    await tester.tap(find.text('Chapter'));
    await tester.pumpAndSettle();
    expect(find.byType(MangaReaderPage), findsOneWidget);
    expect(find.textContaining('Fake remote · Group'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    // Advance fake time so Drift's deferred stream disposal can finish.
    await tester.pump(Duration.zero);
    await db.close();
    debugDefaultTargetPlatformOverride = null;
  });
  testWidgets('app resumes remote chapter from chapter-keyed progress', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    final db = UserDatabase(NativeDatabase.memory());
    const chapter = SourceMediaRef(
      sourceId: SourceId('fake'),
      itemId: 'chapter',
    );
    await SqliteProgressRepository(db).save(
      MediaProgress(
        media: chapter,
        position: PagePosition(pageIndex: 1, pageCount: 2),
        completed: false,
        updatedAt: DateTime.utc(2026),
      ),
    );
    await tester.pumpWidget(
      HikariApp(
        dependencies: AppDependencies.create(
          database: db,
          catalogProvider: _TestCatalogProvider(MediaType.manga),
          additionalSources: [ResumableRemote()],
        ),
      ),
    );
    await tester.pumpAndSettle();
    await _openCatalogSourceSearch(tester);
    await tester.tap(find.text('Series'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chapter'));
    await tester.pumpAndSettle();
    expect(find.text('Page 2 of 2'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    // Advance fake time so Drift's deferred stream disposal can finish.
    await tester.pump(Duration.zero);
    await db.close();
    debugDefaultTargetPlatformOverride = null;
  });

  testWidgets('persisted remote series opens chapters without searching', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    const series = SourceMediaRef(sourceId: SourceId('fake'), itemId: 'series');
    final db = UserDatabase(NativeDatabase.memory());
    var searches = 0;
    final remote = _CountingRemote(onSearch: () => searches++);
    await SqliteLibraryRepository(db).upsert(
      LibraryEntry(
        media: const Media(
          title: 'Series',
          type: MediaType.manga,
          source: series,
        ),
        addedAt: DateTime.utc(2026),
      ),
    );
    await tester.pumpWidget(
      HikariApp(
        dependencies: AppDependencies.create(
          database: db,
          additionalSources: [remote],
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Series'));
    await tester.pumpAndSettle();
    expect(find.byType(MangaSeriesPage), findsOneWidget);
    expect(find.text('Chapter'), findsOneWidget);
    expect(searches, 0);
    await tester.pumpWidget(const SizedBox());
    // Advance fake time so Drift's deferred stream disposal can finish.
    await tester.pump(Duration.zero);
    await db.close();
    debugDefaultTargetPlatformOverride = null;
  });

  testWidgets('chapter loading error retry and empty states are visible', (
    tester,
  ) async {
    final pending = Completer<MangaSeriesDetails>();
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: MangaSeriesPage(
          title: 'Series',
          sourceName: 'Test source',
          loadDetails: () {
            calls++;
            return calls == 1
                ? pending.future
                : Future.value(
                    MangaSeriesDetails(
                      metadata: MediaMetadata(title: 'Series'),
                      chapters: [],
                    ),
                  );
          },
          openChapter: (_, _) async {},
        ),
      ),
    );
    await tester.pump();
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    pending.completeError(StateError('offline'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Could not load chapters'), findsOneWidget);
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.text('No readable chapters found.'), findsOneWidget);
    expect(calls, 2);
  });

  testWidgets('series refresh failure keeps the last chapter list visible', (
    tester,
  ) async {
    var calls = 0;
    const ref = SourceMediaRef(sourceId: SourceId('fake'), itemId: 'chapter');
    await tester.pumpWidget(
      MaterialApp(
        home: MangaSeriesPage(
          title: 'Series',
          sourceName: 'Test source',
          loadDetails: () async {
            if (++calls == 2) throw StateError('offline');
            return MangaSeriesDetails(
              metadata: MediaMetadata(title: 'Series'),
              chapters: [MangaChapter(title: 'Chapter 1', source: ref)],
            );
          },
          openChapter: (_, _) async {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Chapter 1'), findsOneWidget);
    expect(find.byType(RefreshIndicator), findsOneWidget);

    final refresh = tester
        .state<RefreshIndicatorState>(find.byType(RefreshIndicator))
        .show();
    await tester.pump();
    await tester.pumpAndSettle();
    await refresh;

    expect(calls, 2);
    expect(find.text('Chapter 1'), findsOneWidget);
    expect(find.textContaining('Could not refresh chapters.'), findsOneWidget);
  });

  testWidgets('non-readable chapters stay visible but cannot open', (
    tester,
  ) async {
    var opens = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: MangaSeriesPage(
          title: 'Series',
          sourceName: 'Test source',
          loadDetails: () async => MangaSeriesDetails(
            metadata: MediaMetadata(title: 'Series'),
            chapters: [
              MangaChapter(
                title: 'External chapter',
                source: SourceMediaRef(
                  sourceId: SourceId('fake'),
                  itemId: 'external',
                ),
                scanlator: 'External group',
                canReadPages: false,
              ),
            ],
          ),
          openChapter: (_, _) async {
            opens++;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('External chapter'), findsOneWidget);
    expect(find.textContaining('Not readable in Hikari'), findsOneWidget);
    await tester.tap(find.text('External chapter'));
    await tester.pumpAndSettle();
    expect(opens, 0);
  });

  testWidgets('chapter list preserves attribution and selected ref', (
    tester,
  ) async {
    const ref = SourceMediaRef(sourceId: SourceId('fake'), itemId: 'chapter');
    MangaChapter? selected;
    await tester.pumpWidget(
      MaterialApp(
        home: MangaSeriesPage(
          title: 'Series',
          sourceName: 'Test source',
          loadDetails: () async => MangaSeriesDetails(
            metadata: MediaMetadata(title: 'Series'),
            chapters: [
              MangaChapter(title: 'Chapter 1', source: ref, scanlator: 'Group'),
            ],
          ),
          openChapter: (_, chapter) async {
            selected = chapter;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('Group'), findsOneWidget);
    await tester.tap(find.text('Chapter 1'));
    await tester.pumpAndSettle();
    expect(selected?.source, ref);
  });
}
