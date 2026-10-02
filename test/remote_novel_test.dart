import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/progress.dart' as progress;
import 'package:hikari/features/novel_reader/widgets/novel_content_view.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';

Future<void> _openCatalogSourceSearch(WidgetTester tester) async {
  await tester.tap(find.text('View details'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Read'));
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

class FakeNovel
    implements NovelSearchSource, NovelSeriesSource, NovelChapterSource {
  FakeNovel({this.id = const SourceId('fake-novel')});
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
  Future<RichReadingContent> chapterContent(
    SourceMediaRef chapter,
  ) async => RichReadingContent(
    html:
        '<h2>Rich heading</h2>${List.generate(70, (i) => '<p>Paragraph $i with text for reading progress.</p>').join()}',
  );
  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}

class _ForeignNovel extends FakeNovel {
  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: MediaMetadata(
      title: 'Foreign',
      cover: SourceMediaRef(sourceId: SourceId('foreign'), itemId: 'cover'),
    ),
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
    var dependencies = AppDependencies.create(
      database: db,
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
    expect(find.byType(NovelContentView), findsOneWidget);
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
    dependencies = AppDependencies.create(database: db);
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
