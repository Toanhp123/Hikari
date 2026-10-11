import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';

import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
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

const _seriesMedia = Media(
  title: 'Series',
  type: MediaType.manga,
  source: SourceMediaRef(sourceId: SourceId('fake'), itemId: 'series'),
);

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

final class _TestByteCache implements ByteCache {
  final values = <String, Uint8List>{};
  final reads = <String>[];
  final writes = <String>[];
  final writeSignals = <String, Completer<void>>{};
  String _key(String namespace, String key) => '$namespace/$key';
  @override
  Future<Uint8List?> read(String namespace, String key) async {
    reads.add(_key(namespace, key));
    return values[_key(namespace, key)];
  }

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {
    final cacheKey = _key(namespace, key);
    writes.add(cacheKey);
    values[cacheKey] = bytes;
    writeSignals[cacheKey]?.complete();
  }

  @override
  Future<void> close() async {}
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
        chapterListOrder: ChapterListOrder.readingOrder,
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

class _CountingPageRemote extends ResumableRemote {
  int pageReads = 0;

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => [
    const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'page-0'),
    const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'page-1'),
  ];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    pageReads++;
    return super.readPage(page);
  }
}

class _GatedPageRemote extends ResumableRemote {
  final reads = <String>[];
  final gate = Completer<Uint8List>();

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async =>
      List.generate(
        4,
        (index) => SourceMediaRef(sourceId: id, itemId: 'page-$index'),
      );

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    reads.add(page.itemId);
    if (page.itemId == 'page-1') return gate.future;
    return super.readPage(page);
  }
}

class _JumpRacePageRemote extends ResumableRemote {
  final reads = <String>[];
  final page1Started = Completer<void>();
  final page5Started = Completer<void>();
  final _page1Gate = Completer<Uint8List>();
  final _page5Gate = Completer<Uint8List>();

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async =>
      List.generate(
        8,
        (index) => SourceMediaRef(sourceId: id, itemId: 'page-$index'),
      );

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    reads.add(page.itemId);
    switch (page.itemId) {
      case 'page-1':
        if (!page1Started.isCompleted) page1Started.complete();
        return _page1Gate.future;
      case 'page-5':
        if (!page5Started.isCompleted) page5Started.complete();
        return _page5Gate.future;
      default:
        return super.readPage(page);
    }
  }

  void releasePage1() => _page1Gate.complete(_png);

  void releasePage5() => _page5Gate.complete(_png);
}

class _AdjacentChapterRemote extends ResumableRemote {
  static const chapterA = SourceMediaRef(
    sourceId: SourceId('fake'),
    itemId: 'a',
  );
  static const chapterB = SourceMediaRef(
    sourceId: SourceId('fake'),
    itemId: 'b',
  );
  static const chapterC = SourceMediaRef(
    sourceId: SourceId('fake'),
    itemId: 'c',
  );
  final reads = <String>[];
  final aPage1 = Completer<void>();
  final cPage1 = Completer<void>();
  final aPage1Gate = Completer<Uint8List>();
  final cPage1Gate = Completer<Uint8List>();

  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef manga) async =>
      MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Series'),
        chapterListOrder: ChapterListOrder.reverseReadingOrder,
        chapters: [
          MangaChapter(title: 'Chapter C', source: chapterC, chapterNumber: 7),
          MangaChapter(
            title: 'Unreadable B',
            source: chapterB,
            chapterNumber: 7,
            canReadPages: false,
          ),
          MangaChapter(title: 'Chapter A', source: chapterA, chapterNumber: 7),
        ],
      );

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async =>
      List.generate(
        3,
        (index) =>
            SourceMediaRef(sourceId: id, itemId: '${readable.itemId}-$index'),
      );

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    reads.add(page.itemId);
    if (page.itemId == 'a-1') {
      if (!aPage1.isCompleted) aPage1.complete();
      return aPage1Gate.future;
    }
    if (page.itemId == 'c-1') {
      if (!cPage1.isCompleted) cPage1.complete();
      return cPage1Gate.future;
    }
    return super.readPage(page);
  }
}

class _RetryingPageRemote extends ResumableRemote {
  int pageReads = 0;
  bool failFirst = false;

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    pageReads++;
    if (failFirst && pageReads == 1) throw StateError('offline');
    return super.readPage(page);
  }
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
      await tester.tapAt(tester.getCenter(find.text('Series')));
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
    await tester.tapAt(tester.getCenter(find.text('Series')));
    await tester.pumpAndSettle();
    expect(find.byType(MangaSeriesPage), findsOneWidget);
    expect(find.byType(MangaReaderPage), findsNothing);
    expect(find.byTooltip('Add to library'), findsOneWidget);
    await tester.tap(find.byTooltip('Add to library'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Remove from library'), findsOneWidget);
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

  testWidgets(
    'remote adjacent chapters follow reading order and stable progress refs',
    (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.windows;
      final db = UserDatabase(NativeDatabase.memory());
      final remote = _AdjacentChapterRemote();
      final dependencies = AppDependencies.create(
        database: db,
        cache: _TestByteCache(),
        additionalSources: [remote],
      );
      const series = SourceMediaRef(
        sourceId: SourceId('fake'),
        itemId: 'series',
      );
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
      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Library'));
      await tester.pumpAndSettle();
      await tester.tapAt(tester.getCenter(find.text('Series')));
      await tester.pumpAndSettle();
      expect(
        tester.getTopLeft(find.text('Chapter C')).dy,
        lessThan(tester.getTopLeft(find.text('Chapter A')).dy),
      );
      await tester.tap(find.text('Chapter A'));
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(find.text('Chapter A'), findsOneWidget);
      expect(find.byTooltip('Previous chapter'), findsNothing);
      await tester.runAsync(() async {
        final image = tester.widget<Image>(find.byType(Image).last);
        final decoded = Completer<void>();
        final stream = image.image.resolve(ImageConfiguration.empty);
        final listener = ImageStreamListener((_, _) => decoded.complete());
        stream.addListener(listener);
        await decoded.future;
        stream.removeListener(listener);
      });
      await tester.pump();
      await tester.tap(find.byTooltip('Next chapter'));
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(find.text('Chapter C'), findsOneWidget);
      expect(find.text('Unreadable B'), findsNothing);
      await tester.tap(find.byTooltip('Previous chapter'));
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(find.text('Chapter A'), findsOneWidget);
      expect(
        remote.reads.map((value) => value.split('-').first),
        everyElement(anyOf('a', 'c')),
      );
      await tester.pumpWidget(const SizedBox());
      await tester.pump(Duration.zero);
      await dependencies.dispose();
      await db.close();
      debugDefaultTargetPlatformOverride = null;
    },
  );

  testWidgets('manga A-C-A handoff flushes latest page progress', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    final db = UserDatabase(NativeDatabase.memory());
    final remote = _AdjacentChapterRemote();
    final dependencies = AppDependencies.create(
      database: db,
      catalogProvider: _TestCatalogProvider(MediaType.manga),
      cache: _TestByteCache(),
      additionalSources: [remote],
    );
    const series = SourceMediaRef(sourceId: SourceId('fake'), itemId: 'series');
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
    await tester.pumpWidget(HikariApp(dependencies: dependencies));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    await tester.tapAt(tester.getCenter(find.text('Series')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chapter A'));
    await tester.pumpAndSettle();
    await tester.runAsync(() async {
      final image = tester.widget<Image>(find.byType(Image).last);
      final decoded = Completer<void>();
      final stream = image.image.resolve(ImageConfiguration.empty);
      final listener = ImageStreamListener((_, _) => decoded.complete());
      stream.addListener(listener);
      await decoded.future;
      stream.removeListener(listener);
    });
    await tester.pump();
    remote.aPage1Gate.complete(remote._png);
    await tester.tap(find.byTooltip('Next page'));
    await tester.pumpAndSettle();
    await tester.runAsync(() async {
      final image = tester.widget<Image>(find.byType(Image).last);
      final decoded = Completer<void>();
      final stream = image.image.resolve(ImageConfiguration.empty);
      final listener = ImageStreamListener((_, _) => decoded.complete());
      stream.addListener(listener);
      await decoded.future;
      stream.removeListener(listener);
    });
    await tester.pumpAndSettle();
    expect(find.text('Page 2 of 3'), findsOneWidget);
    await tester.tap(find.byTooltip('Next chapter'));
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(find.text('Chapter C'), findsOneWidget);
    await tester.tap(find.byTooltip('Previous chapter'));
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(find.text('Chapter A'), findsOneWidget);
    expect(find.text('Page 2 of 3'), findsOneWidget);
    final saved = await SqliteProgressRepository(db)
        .load(_AdjacentChapterRemote.chapterA);
    expect(saved, isNotNull);
    expect((saved!.position as PagePosition).pageIndex, 1);
    if (!remote.aPage1Gate.isCompleted) remote.aPage1Gate.complete(remote._png);
    if (!remote.cPage1Gate.isCompleted) remote.cPage1Gate.complete(remote._png);
    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await db.close();
    debugDefaultTargetPlatformOverride = null;
  });

  testWidgets(
    'adjacent manga target prefetch starts before stale tail drains',
    (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.windows;
      final db = UserDatabase(NativeDatabase.memory());
      final remote = _AdjacentChapterRemote();
      final cache = _TestByteCache();
      final dependencies = AppDependencies.create(
        database: db,
        catalogProvider: _TestCatalogProvider(MediaType.manga),
        cache: cache,
        additionalSources: [remote],
      );
      const series = SourceMediaRef(
        sourceId: SourceId('fake'),
        itemId: 'series',
      );
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
      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Library'));
      await tester.pumpAndSettle();
      await tester.tapAt(tester.getCenter(find.text('Series')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Chapter A'));
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      await tester.runAsync(() async {
        final image = tester.widget<Image>(find.byType(Image).last);
        final decoded = Completer<void>();
        final stream = image.image.resolve(ImageConfiguration.empty);
        final listener = ImageStreamListener((_, _) => decoded.complete());
        stream.addListener(listener);
        await decoded.future;
        stream.removeListener(listener);
      });
      await tester.pump();
      expect(remote.aPage1.isCompleted, isTrue);
      await tester.tap(find.byTooltip('Next chapter'));
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(find.text('Chapter C'), findsOneWidget);
      await tester.runAsync(() async {
        final image = tester.widget<Image>(find.byType(Image).last);
        final decoded = Completer<void>();
        final stream = image.image.resolve(ImageConfiguration.empty);
        final listener = ImageStreamListener((_, _) => decoded.complete());
        stream.addListener(listener);
        await decoded.future;
        stream.removeListener(listener);
      });
      await tester.pump();
      expect(remote.cPage1.isCompleted, isTrue);
      expect(remote.reads, containsAll(['a-1', 'c-1']));
      addTearDown(() {
        if (!remote.aPage1Gate.isCompleted) {
          remote.aPage1Gate.complete(Uint8List(0));
        }
        if (!remote.cPage1Gate.isCompleted) {
          remote.cPage1Gate.complete(Uint8List(0));
        }
      });
      remote.aPage1Gate.complete(
        base64Decode(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==',
        ),
      );
      await tester.runAsync(() async {
        await Future<void>.delayed(Duration.zero);
        await Future<void>.delayed(Duration.zero);
      });
      await tester.pump();
      expect(remote.reads.where((page) => page.startsWith('a-')), [
        'a-0',
        'a-1',
      ]);
      remote.cPage1Gate.complete(
        base64Decode(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==',
        ),
      );
      await tester.pumpWidget(const SizedBox());
      await tester.pump(Duration.zero);
      await dependencies.dispose();
      await db.close();
      debugDefaultTargetPlatformOverride = null;
    },
  );

  testWidgets(
    'remote reader reuses page bytes navigating and reopening chapter',
    (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.windows;
      final db = UserDatabase(NativeDatabase.memory());
      final remote = _CountingPageRemote();
      final cache = _TestByteCache();
      final dependencies = AppDependencies.create(
        database: db,
        catalogProvider: _TestCatalogProvider(MediaType.manga),
        cache: cache,
        additionalSources: [remote],
      );
      const series = SourceMediaRef(
        sourceId: SourceId('fake'),
        itemId: 'series',
      );
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
      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Library'));
      await tester.pumpAndSettle();
      await tester.tapAt(tester.getCenter(find.text('Series')));
      await tester.pumpAndSettle();
      final prefetchedPage = cache.writeSignals.putIfAbsent(
        'manga-page-v1/["fake","page-1"]',
        Completer<void>.new,
      );
      await tester.tap(find.text('Chapter'));
      await tester.pumpAndSettle();
      await tester.runAsync(() async {
        final image = tester.widget<Image>(find.byType(Image).last);
        final decoded = Completer<void>();
        final stream = image.image.resolve(ImageConfiguration.empty);
        final listener = ImageStreamListener((_, _) => decoded.complete());
        stream.addListener(listener);
        await decoded.future;
        stream.removeListener(listener);
      });
      await tester.pumpAndSettle();
      expect(prefetchedPage.isCompleted, isTrue);
      expect(
        cache.values.containsKey('manga-page-v1/["fake","page-1"]'),
        isTrue,
      );
      await tester.tap(find.byTooltip('Next page'));
      await tester.pumpAndSettle();
      expect(remote.pageReads, 2);
      await tester.tap(find.byTooltip('Back'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Chapter'));
      await tester.pumpAndSettle();
      expect(remote.pageReads, 2);
      await tester.pumpWidget(const SizedBox());
      await tester.pump(Duration.zero);
      await dependencies.dispose();
      await db.close();
      debugDefaultTargetPlatformOverride = null;
    },
  );

  testWidgets('remote cached invalid page retries source until valid frame', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    final db = UserDatabase(NativeDatabase.memory());
    final remote = _RetryingPageRemote()..failFirst = true;
    const chapter = SourceMediaRef(
      sourceId: SourceId('fake'),
      itemId: 'chapter',
    );
    final progress = SqliteProgressRepository(db);
    final validPng = base64Decode(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==',
    );
    final cache = _TestByteCache()
      ..values['manga-page-v1/["fake","page-0"]'] = Uint8List.fromList([1]);
    final dependencies = AppDependencies.create(
      database: db,
      catalogProvider: _TestCatalogProvider(MediaType.manga),
      cache: cache,
      additionalSources: [remote],
    );
    await tester.pumpWidget(HikariApp(dependencies: dependencies));
    await tester.pumpAndSettle();
    await _openCatalogSourceSearch(tester);
    await tester.tapAt(tester.getCenter(find.text('Series')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chapter'));
    await tester.pumpAndSettle();
    expect(remote.pageReads, 0);
    expect(cache.writes, isEmpty);
    expect(find.text('Could not decode this page.'), findsOneWidget);
    final baseline = (await progress.load(chapter))!;
    expect((baseline.position as PagePosition).pageIndex, 0);
    expect(baseline.completed, isFalse);
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(remote.pageReads, 1);
    expect(find.text('Could not load this page.'), findsOneWidget);
    final afterFailedRetry = (await progress.load(chapter))!;
    expect((afterFailedRetry.position as PagePosition).pageIndex, 0);
    expect(
      (afterFailedRetry.position as PagePosition).pageCount,
      (baseline.position as PagePosition).pageCount,
    );
    expect(afterFailedRetry.completed, baseline.completed);
    expect(afterFailedRetry.updatedAt, baseline.updatedAt);
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    await tester.runAsync(
      () async => Future<void>.delayed(const Duration(milliseconds: 100)),
    );
    await tester.pumpAndSettle();
    expect(remote.pageReads, 3);
    expect(find.text('Could not decode this page.'), findsNothing);
    expect(cache.values['manga-page-v1/["fake","page-0"]'], validPng);
    expect(cache.writes, [
      'manga-page-v1/["fake","page-0"]',
      'manga-page-v1/["fake","page-1"]',
    ]);
    final saved = await progress.load(chapter);
    expect(saved, isNotNull);
    expect((saved!.position as PagePosition).pageIndex, 0);
    expect(saved.completed, isFalse);
    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await db.close();
    debugDefaultTargetPlatformOverride = null;
  });

  testWidgets(
    'foreground jump invalidates stale prefetch before the new page displays',
    (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.windows;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      final db = UserDatabase(NativeDatabase.memory());
      final remote = _JumpRacePageRemote();
      final cache = _TestByteCache();
      final dependencies = AppDependencies.create(
        database: db,
        catalogProvider: _TestCatalogProvider(MediaType.manga),
        cache: cache,
        additionalSources: [remote],
      );
      addTearDown(dependencies.dispose);
      addTearDown(db.close);

      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      await _openCatalogSourceSearch(tester);
      await tester.tapAt(tester.getCenter(find.text('Series')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Chapter'));
      await tester.pumpAndSettle();

      await tester.runAsync(() async {
        final image = tester.widget<Image>(find.byType(Image).last);
        final decoded = Completer<void>();
        final stream = image.image.resolve(ImageConfiguration.empty);
        final listener = ImageStreamListener((_, _) => decoded.complete());
        stream.addListener(listener);
        await decoded.future;
        stream.removeListener(listener);
      });
      await tester.pump();
      await tester.runAsync(() => remote.page1Started.future);
      expect(remote.reads, ['page-0', 'page-1']);

      final slider = tester.widget<Slider>(find.byType(Slider));
      slider.onChangeEnd!(6);
      await tester.pump();
      await tester.runAsync(() => remote.page5Started.future);
      expect(remote.reads, ['page-0', 'page-1', 'page-5']);

      final page1Cached = cache.writeSignals.putIfAbsent(
        'manga-page-v1/["fake","page-1"]',
        Completer<void>.new,
      );
      remote.releasePage1();
      await tester.runAsync(() async {
        await page1Cached.future;
        await Future<void>.delayed(Duration.zero);
      });
      await tester.pump();
      expect(remote.reads, [
        'page-0',
        'page-1',
        'page-5',
      ], reason: 'the stale page-0 window must not continue with page-2');

      final page5Cached = cache.writeSignals.putIfAbsent(
        'manga-page-v1/["fake","page-5"]',
        Completer<void>.new,
      );
      final page7Cached = cache.writeSignals.putIfAbsent(
        'manga-page-v1/["fake","page-7"]',
        Completer<void>.new,
      );
      remote.releasePage5();
      await tester.runAsync(() => page5Cached.future);
      await tester.pumpAndSettle();
      await tester.runAsync(() async {
        final image = tester.widget<Image>(find.byType(Image).last);
        final decoded = Completer<void>();
        final stream = image.image.resolve(ImageConfiguration.empty);
        final listener = ImageStreamListener((_, _) => decoded.complete());
        stream.addListener(listener);
        await decoded.future;
        stream.removeListener(listener);
      });
      await tester.pump();
      await tester.runAsync(() => page7Cached.future);

      expect(remote.reads, ['page-0', 'page-1', 'page-5', 'page-6', 'page-7']);
      await tester.pumpWidget(const SizedBox());
      await tester.pump(Duration.zero);
      debugDefaultTargetPlatformOverride = null;
    },
  );

  testWidgets('route pop stops active prefetch remainder and late callbacks', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    final db = UserDatabase(NativeDatabase.memory());
    final remote = _GatedPageRemote();
    final cache = _TestByteCache();
    final dependencies = AppDependencies.create(
      database: db,
      catalogProvider: _TestCatalogProvider(MediaType.manga),
      cache: cache,
      additionalSources: [remote],
    );
    await tester.pumpWidget(HikariApp(dependencies: dependencies));
    await tester.pumpAndSettle();
    await _openCatalogSourceSearch(tester);
    await tester.tapAt(tester.getCenter(find.text('Series')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chapter'));
    await tester.pumpAndSettle();
    final reader = tester.widget<MangaReaderPage>(find.byType(MangaReaderPage));
    await tester.runAsync(() async {
      final image = tester.widget<Image>(find.byType(Image).last);
      final decoded = Completer<void>();
      final stream = image.image.resolve(ImageConfiguration.empty);
      final listener = ImageStreamListener((_, _) => decoded.complete());
      stream.addListener(listener);
      await decoded.future;
      stream.removeListener(listener);
    });
    await tester.pumpAndSettle();
    expect(remote.reads, ['page-0', 'page-1']);
    await tester.tap(find.byTooltip('Back'));
    // PopScope invalidates synchronously, before reverse transition completes.
    reader.onPageDisplayed!(1);
    remote.gate.complete(
      await ResumableRemote().readPage(
        const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'page-1'),
      ),
    );
    await tester.pumpAndSettle();
    expect(remote.reads, ['page-0', 'page-1']);
    expect(cache.values.containsKey('manga-page-v1/["fake","page-1"]'), isTrue);
    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await db.close();
    debugDefaultTargetPlatformOverride = null;
  });

  testWidgets('direct local manga reader bypasses remote page cache', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    const channel = MethodChannel('hikari/local_media');
    final messenger = tester.binding.defaultBinaryMessenger;
    var reads = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'children') {
        return [
          {'id': 'page-0', 'name': '1.png', 'isDirectory': false},
          {'id': 'page-1', 'name': '2.png', 'isDirectory': false},
        ];
      }
      if (call.method == 'read') {
        reads++;
        return base64Decode(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==',
        );
      }
      fail('Unexpected ${call.method}');
    });
    final db = UserDatabase(NativeDatabase.memory());
    final cache = _TestByteCache();
    final dependencies = AppDependencies.create(database: db, cache: cache);
    await SqliteLibraryRepository(db).upsert(
      LibraryEntry(
        media: const Media(
          title: 'Local pages',
          type: MediaType.manga,
          source: SourceMediaRef(sourceId: SourceId.local, itemId: 'folder'),
        ),
        addedAt: DateTime.utc(2026),
      ),
    );
    await tester.pumpWidget(HikariApp(dependencies: dependencies));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    await tester.tapAt(tester.getCenter(find.text('Local pages')));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Next page'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    await tester.tapAt(tester.getCenter(find.text('Local pages')));
    await tester.pumpAndSettle();
    expect(reads, 3);
    expect(cache.reads, isEmpty);
    expect(cache.writes, isEmpty);
    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await db.close();
    messenger.setMockMethodCallHandler(channel, null);
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
    await tester.tapAt(tester.getCenter(find.text('Series')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chapter'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
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
    await tester.tap(find.widgetWithText(MediaPoster, 'Series'));
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
          media: _seriesMedia,
          sourceName: 'Test source',
          loadDetails: () {
            calls++;
            return calls == 1
                ? pending.future
                : Future.value(
                    MangaSeriesDetails(
                      metadata: MediaMetadata(title: 'Series'),
                      chapterListOrder: ChapterListOrder.readingOrder,
                      chapters: [],
                    ),
                  );
          },
          openChapter: (_, _, _, _) async {},
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
          media: _seriesMedia,
          sourceName: 'Test source',
          loadDetails: () async {
            if (++calls == 2) throw StateError('offline');
            return MangaSeriesDetails(
              metadata: MediaMetadata(title: 'Series'),
              chapterListOrder: ChapterListOrder.readingOrder,
              chapters: [MangaChapter(title: 'Chapter 1', source: ref)],
            );
          },
          openChapter: (_, _, _, _) async {},
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
          media: _seriesMedia,
          sourceName: 'Test source',
          loadDetails: () async => MangaSeriesDetails(
            metadata: MediaMetadata(title: 'Series'),
            chapterListOrder: ChapterListOrder.readingOrder,
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
          openChapter: (_, _, _, _) async {
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

  testWidgets('chapter list preserves source order, attribution and ref', (
    tester,
  ) async {
    const ref = SourceMediaRef(sourceId: SourceId('fake'), itemId: 'chapter');
    MangaChapter? selected;
    await tester.pumpWidget(
      MaterialApp(
        home: MangaSeriesPage(
          media: _seriesMedia,
          sourceName: 'Test source',
          loadDetails: () async => MangaSeriesDetails(
            metadata: MediaMetadata(title: 'Series'),
            chapterListOrder: ChapterListOrder.reverseReadingOrder,
            chapters: [
              MangaChapter(
                title: 'Latest',
                source: ref,
                chapterNumber: 3,
                scanlator: 'Group',
              ),
              MangaChapter(
                title: 'Earliest',
                source: const SourceMediaRef(
                  sourceId: SourceId('fake'),
                  itemId: 'earliest',
                ),
                chapterNumber: 1,
              ),
            ],
          ),
          openChapter: (_, chapter, _, _) async {
            selected = chapter;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('Group'), findsOneWidget);
    expect(
      tester.getTopLeft(find.text('Latest')).dy,
      lessThan(tester.getTopLeft(find.text('Earliest')).dy),
    );
    await tester.tap(find.text('Latest'));
    await tester.pumpAndSettle();
    expect(selected?.source, ref);
  });
}
