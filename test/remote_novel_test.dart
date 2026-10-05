import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/progress.dart' as progress;
import 'package:hikari/features/novel_reader/widgets/novel_content_view.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';
import 'package:hikari/features/remote_novel/novel_series_page.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';

Future<void> _openCatalogSourceSearch(WidgetTester tester) async {
  await tester.tap(find.text('View details'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Read'));
  await tester.pumpAndSettle();
  expect(find.text('Read from'), findsOneWidget);
  await tester.tap(find.text('Search all sources'));
  await tester.pump();
  for (
    var i = 0;
    i < 20 && find.byTooltip('Add to library').evaluate().isEmpty;
    i++
  ) {
    await tester.pump(const Duration(milliseconds: 50));
  }
  expect(find.byTooltip('Add to library'), findsOneWidget);
}

final class _TestCatalogProvider implements CatalogProvider {
  _TestCatalogProvider(MediaType type)
    : _entry = CatalogEntry(
        id: const CatalogEntryId(provider: 'test', value: 'remote'),
        title: 'novel',
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

final class _TestCache implements ByteCache {
  final values = <String, Uint8List>{};
  @override
  Future<Uint8List?> read(String namespace, String key) async =>
      values['$namespace/$key'];
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {
    values['$namespace/$key'] = Uint8List.fromList(bytes);
  }

  @override
  Future<void> close() async {}
}

class FakeNovel
    implements NovelSearchSource, NovelSeriesSource, NovelChapterSource {
  FakeNovel({this.id = const SourceId('fake-novel')});
  int chapterReads = 0;
  int resourceReads = 0;
  @override
  final SourceId id;
  @override
  String get name => 'Novel source';
  int searches = 0;
  Future<NovelSearchPage> Function(String, int)? respond;
  SourceMediaRef ref(String item) => SourceMediaRef(sourceId: id, itemId: item);
  NovelSearchPage page(int page, {bool? next = false}) => NovelSearchPage(
    results: [
      NovelPreview(
        media: Media(
          title: 'Novel $page',
          type: MediaType.lightNovel,
          source: ref('series$page'),
        ),
        metadata: MediaMetadata(title: 'Novel', authors: ['Author']),
      ),
    ],
    page: page,
    hasNextPage: next,
  );
  @override
  Future<NovelSearchPage> search(String query, {int page = 1}) async {
    searches++;
    return respond == null ? this.page(page) : respond!(query, page);
  }

  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: MediaMetadata(
      title: 'Novel',
      summary: 'Real summary',
      authors: ['Author'],
      genres: ['Fantasy'],
    ),
    chapterListOrder: ChapterListOrder.readingOrder,
    chapters: [
      NovelChapter(
        title: 'Chapter rich',
        source: ref('chapter'),
        chapterNumber: 1.5,
        releaseLabel: 'Yesterday',
        scanlators: ['Group A', 'Group B'],
      ),
    ],
  );
  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async {
    chapterReads++;
    return RichReadingContent(
      html:
          '<h2>Rich heading</h2>${List.generate(70, (i) => '<p>Paragraph $i with text for reading progress.</p>').join()}<img src="img">',
      resources: {'img': ref('image')},
    );
  }

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async {
    resourceReads++;
    return Uint8List.fromList([resource.itemId.codeUnits.length]);
  }
}

class _AdjacentNovel extends FakeNovel {
  final chapterRefs = <String>[];
  final resourceRefs = <String>[];

  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: MediaMetadata(title: 'Novel'),
    chapterListOrder: ChapterListOrder.reverseReadingOrder,
    chapters: [
      NovelChapter(title: 'Chapter C', source: ref('c'), chapterNumber: 4),
      NovelChapter(title: 'Chapter B', source: ref('b'), chapterNumber: 4),
      NovelChapter(title: 'Chapter A', source: ref('a'), chapterNumber: 4),
    ],
  );

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async {
    chapterRefs.add(chapter.itemId);
    return RichReadingContent(
      html:
          '<h2>Content ${chapter.itemId}</h2>${List.generate(80, (i) => '<p>${chapter.itemId} paragraph $i with readable content.</p>').join()}<img src="image">',
      resources: {'image': ref('image-${chapter.itemId}')},
    );
  }

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async {
    resourceReads++;
    resourceRefs.add(resource.itemId);
    return Uint8List.fromList([resource.itemId.codeUnits.length]);
  }
}

class _RefreshNovel extends FakeNovel {
  int detailLoads = 0;

  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async {
    if (++detailLoads == 2) throw StateError('offline');
    return NovelDetails(
      metadata: MediaMetadata(title: 'Novel'),
      chapterListOrder: ChapterListOrder.readingOrder,
      chapters: [NovelChapter(title: 'Chapter 1', source: ref('chapter'))],
    );
  }
}

class _ForeignNovel extends FakeNovel {
  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: MediaMetadata(
      title: 'Foreign',
      cover: SourceMediaRef(sourceId: SourceId('foreign'), itemId: 'cover'),
    ),
    chapterListOrder: ChapterListOrder.readingOrder,
    chapters: [],
  );
  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async =>
      RichReadingContent(
        html: '<p>Content</p>',
        resources: {
          'image': const SourceMediaRef(
            sourceId: SourceId('foreign'),
            itemId: 'image',
          ),
        },
      );
}

void main() {
  testWidgets(
    'metadata displays known rating scale without inventing unknown ratings',
    (tester) async {
      Future<void> show(MediaMetadata metadata) => tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: MediaMetadataView(metadata: metadata, sourceName: 'Source'),
          ),
        ),
      );
      await show(MediaMetadata(title: 'Book', rating: 4.5, ratingMax: 5));
      expect(find.text('Rating: 4.5 / 5.0'), findsOneWidget);
      await show(MediaMetadata(title: 'Book', rating: 4.5));
      expect(find.text('Rating: 4.5'), findsOneWidget);
      await show(MediaMetadata(title: 'Book'));
      expect(find.textContaining('Rating:'), findsNothing);
    },
  );
  test('novel details and content reject foreign resources', () async {
    final db = UserDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    final source = _ForeignNovel();
    final dependencies = AppDependencies.create(
      database: db,
      additionalSources: [source],
    );
    addTearDown(dependencies.dispose);
    final target = await dependencies.openMedia.execute(
      source.page(1).results.single.media,
    );
    await expectLater(
      (target as NovelSeriesOpenTarget).loadDetails(),
      throwsStateError,
    );
    await expectLater(
      dependencies.openNovelChapter.execute(
        NovelChapter(title: 'Chapter', source: source.ref('chapter')),
      ),
      throwsStateError,
    );
  });
  testWidgets('novel chapter list preserves source order', (tester) async {
    final source = _ReversedNovel();
    final target = NovelSeriesOpenTarget(
      Media(
        title: 'Novel',
        type: MediaType.lightNovel,
        source: source.ref('series'),
      ),
      source: source,
    );
    await tester.pumpWidget(
      MaterialApp(
        home: NovelSeriesPage(target: target, openChapter: (_, _, _) async {}),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      tester.getTopLeft(find.text('Latest')).dy,
      lessThan(tester.getTopLeft(find.text('Earliest')).dy),
    );
  });

  testWidgets(
    'adjacent novel chapters follow order and bind content resources',
    (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.windows;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      final db = UserDatabase(NativeDatabase.memory());
      final source = _AdjacentNovel();
      final cache = _TestCache();
      final dependencies = AppDependencies.create(
        database: db,
        catalogProvider: _TestCatalogProvider(MediaType.lightNovel),
        cache: cache,
        additionalSources: [source],
      );
      addTearDown(() async {
        await tester.pumpWidget(const SizedBox());
        await tester.pump();
        await dependencies.dispose();
        await db.close();
      });

      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      await _openCatalogSourceSearch(tester);
      await tester.tap(find.byTooltip('Add to library'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Novel 1'));
      await tester.pumpAndSettle();
      expect(
        tester.getTopLeft(find.text('Chapter C')).dy,
        lessThan(tester.getTopLeft(find.text('Chapter A')).dy),
      );
      await tester.tap(find.text('Chapter A'));
      await tester.pumpAndSettle();
      expect(source.chapterRefs, ['a']);
      await tester.ensureVisible(find.byType(NovelContentView));
      await tester.pumpAndSettle();
      expect(source.resourceRefs, ['image-a']);
      final scroll = tester
          .widget<SingleChildScrollView>(find.byType(SingleChildScrollView))
          .controller!;
      scroll.jumpTo(scroll.position.maxScrollExtent * .4);
      await tester.pump(const Duration(milliseconds: 50));
      await tester.tap(find.byTooltip('Next chapter'));
      await tester.pump();
      await tester.pumpAndSettle();
      expect(source.chapterRefs, ['a', 'b']);
      final savedA = await SqliteProgressRepository(db).load(source.ref('a'));
      expect(savedA, isNotNull);
      expect(savedA!.media, source.ref('a'));
      final savedProgression =
          (savedA.position as progress.TextPosition).progression;
      expect(savedProgression, greaterThan(0));
      var reader = tester.widget<NovelReaderPage>(find.byType(NovelReaderPage));
      expect(reader.title, 'Chapter B');
      expect(reader.initialProgress, isNull);
      await tester.ensureVisible(find.byType(NovelContentView));
      await tester.pumpAndSettle();
      expect(source.resourceRefs, ['image-a', 'image-b']);
      await tester.tap(find.byTooltip('Previous chapter'));
      await tester.pump();
      await tester.pumpAndSettle();
      // Reopening A reuses cached content but loads a fresh progress session.
      expect(source.chapterRefs, ['a', 'b']);
      reader = tester.widget<NovelReaderPage>(find.byType(NovelReaderPage));
      expect(reader.title, 'Chapter A');
      expect(reader.initialProgress?.media, source.ref('a'));
      expect(
        (reader.initialProgress!.position as progress.TextPosition).progression,
        savedProgression,
      );
      await tester.ensureVisible(find.byType(NovelContentView));
      await tester.pumpAndSettle();
      expect(source.resourceRefs, ['image-a', 'image-b']);
      expect(source.resourceRefs, everyElement(anyOf('image-a', 'image-b')));
      await tester.pumpWidget(const SizedBox());
      await tester.pump(Duration.zero);
      debugDefaultTargetPlatformOverride = null;
    },
  );

  testWidgets('series refresh failure keeps the last chapter list visible', (
    tester,
  ) async {
    final source = _RefreshNovel();
    final target = NovelSeriesOpenTarget(
      Media(
        title: 'Novel',
        type: MediaType.lightNovel,
        source: source.ref('series'),
      ),
      source: source,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: NovelSeriesPage(target: target, openChapter: (_, _, _) async {}),
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

    expect(source.detailLoads, 2);
    expect(find.text('Chapter 1'), findsOneWidget);
    expect(find.textContaining('Could not refresh chapters.'), findsOneWidget);
  });

  testWidgets('novel search details rich reader library and file restart', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    final directory = Directory.systemTemp.createTempSync('hikari-novel');
    addTearDown(() => directory.deleteSync(recursive: true));
    final file = File('${directory.path}/user.sqlite');
    var db = UserDatabase(NativeDatabase(file));
    final source = FakeNovel();
    final cache = _TestCache();
    var dependencies = AppDependencies.create(
      database: db,
      cache: cache,
      catalogProvider: _TestCatalogProvider(MediaType.lightNovel),
      additionalSources: [source],
    );
    await tester.pumpWidget(HikariApp(dependencies: dependencies));
    await tester.pumpAndSettle();
    await _openCatalogSourceSearch(tester);
    await tester.tap(find.byTooltip('Add to library'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Novel 1'));
    await tester.pumpAndSettle();
    expect(find.text('Real summary'), findsOneWidget);
    expect(find.textContaining('Group A · Group B'), findsOneWidget);
    expect(find.textContaining('1.5'), findsOneWidget);
    await tester.tap(find.text('Chapter rich'));
    await tester.pumpAndSettle();
    expect(source.chapterReads, 1);
    expect(find.byType(NovelContentView), findsOneWidget);
    await tester.ensureVisible(find.byType(NovelContentView));
    await tester.pumpAndSettle();
    expect(source.resourceReads, 1);
    await tester.drag(
      find.byType(SingleChildScrollView).last,
      const Offset(0, -600),
    );
    await tester.pumpAndSettle(const Duration(milliseconds: 600));
    await tester.pageBack();
    await tester.pumpAndSettle();
    final saved = await SqliteProgressRepository(db)
        .load(source.ref('chapter'));
    expect(saved, isNotNull);
    await tester.pumpWidget(const SizedBox());
    // Advance fake time so Drift's deferred stream disposal can finish.
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await db.close();

    db = UserDatabase(NativeDatabase(file));
    final restarted = FakeNovel();
    dependencies = AppDependencies.create(
      database: db,
      cache: cache,
      additionalSources: [restarted],
    );
    await tester.pumpWidget(HikariApp(dependencies: dependencies));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Novel 1'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chapter rich'));
    await tester.pumpAndSettle();
    expect(restarted.searches, 0);
    expect(restarted.chapterReads, 0);
    await tester.ensureVisible(find.byType(NovelContentView));
    await tester.pumpAndSettle();
    expect(restarted.resourceReads, 0);
    final reader = tester.widget<NovelReaderPage>(find.byType(NovelReaderPage));
    expect(
      (reader.initialProgress!.position as progress.TextPosition).progression,
      (saved!.position as progress.TextPosition).progression,
    );
    expect(
      (saved.position as progress.TextPosition).progression,
      greaterThan(0),
    );
    await tester.pumpWidget(const SizedBox());
    // Advance fake time so Drift's deferred stream disposal can finish.
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await db.close();

    db = UserDatabase(NativeDatabase(file));
    dependencies = AppDependencies.create(database: db, cache: cache);
    await tester.pumpWidget(HikariApp(dependencies: dependencies));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Novel 1'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Could not open this item'), findsOneWidget);
    expect(
      await dependencies.libraryRepository.contains(source.ref('series1')),
      isTrue,
    );
    await tester.pumpWidget(const SizedBox());
    // Advance fake time so Drift's deferred stream disposal can finish.
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await db.close();
    debugDefaultTargetPlatformOverride = null;
  });
}

final class _ReversedNovel extends FakeNovel {
  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: MediaMetadata(title: 'Novel'),
    chapterListOrder: ChapterListOrder.reverseReadingOrder,
    chapters: [
      NovelChapter(title: 'Latest', source: ref('latest')),
      NovelChapter(title: 'Earliest', source: ref('earliest')),
    ],
  );
}
