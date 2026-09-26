import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';

import 'package:drift/native.dart';
import 'package:hikari/app/app.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/user_state_repositories.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/remote_manga/remote_manga_search_page.dart';
import 'package:hikari/features/remote_manga/manga_chapter_page.dart';

class FakeRemote
    implements MediaSearchSource, MangaChapterSource, MangaPageSource {
  @override
  SourceId get id => const SourceId('fake');
  @override
  String get name => 'Fake remote';
  @override
  Future<List<Media>> search(String query) async => [
    const Media(
      title: 'Series',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId('fake'), itemId: 'series'),
    ),
  ];
  @override
  Future<List<MangaChapter>> chapters(SourceMediaRef manga) async => [
    const MangaChapter(
      title: 'Chapter',
      source: SourceMediaRef(sourceId: SourceId('fake'), itemId: 'chapter'),
      scanlator: 'Group',
    ),
  ];
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

class _CountingRemote extends FakeRemote {
  _CountingRemote({required this.onSearch});
  final void Function() onSearch;

  @override
  Future<List<Media>> search(String query) async {
    onSearch();
    return super.search(query);
  }
}

void main() {
  testWidgets('remote series opens chapters then reader on non Android', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    final db = UserDatabase(NativeDatabase.memory());
    await tester.pumpWidget(
      HikariApp(database: db, remoteSource: FakeRemote()),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Search manga'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'test');
    await tester.tap(find.text('Search'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Series'));
    await tester.pumpAndSettle();
    expect(find.byType(MangaChapterPage), findsOneWidget);
    expect(find.byType(MangaReaderPage), findsNothing);
    await tester.tap(find.text('Chapter'));
    await tester.pumpAndSettle();
    expect(find.byType(MangaReaderPage), findsOneWidget);
    expect(find.textContaining('Fake remote · Group'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
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
      HikariApp(database: db, remoteSource: ResumableRemote()),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Search manga'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'test');
    await tester.tap(find.text('Search'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Series'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chapter'));
    await tester.pumpAndSettle();
    expect(find.text('Page 2 of 2'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
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
    await tester.pumpWidget(HikariApp(database: db, remoteSource: remote));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Series'));
    await tester.pumpAndSettle();
    expect(find.byType(MangaChapterPage), findsOneWidget);
    expect(find.text('Chapter'), findsOneWidget);
    expect(searches, 0);
    await tester.pumpWidget(const SizedBox());
    await db.close();
    debugDefaultTargetPlatformOverride = null;
  });

  testWidgets('chapter loading error retry and empty states are visible', (
    tester,
  ) async {
    final pending = Completer<List<MangaChapter>>();
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: MangaChapterPage(
          title: 'Series',
          sourceName: 'Test source',
          loadChapters: () {
            calls++;
            return calls == 1 ? pending.future : Future.value([]);
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
    expect(find.text('No readable English chapters found.'), findsOneWidget);
    expect(calls, 2);
  });

  testWidgets('search result library toggle persists membership', (
    tester,
  ) async {
    final db = UserDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    final library = SqliteLibraryRepository(db);
    await tester.pumpWidget(
      MaterialApp(
        home: RemoteMangaSearchPage(
          sourceName: 'Test source',
          search: (_) async => [
            const Media(
              title: 'Series',
              type: MediaType.manga,
              source: SourceMediaRef(
                sourceId: SourceId('fake'),
                itemId: 'series',
              ),
            ),
          ],
          openMedia: (_, _) {},
          library: library,
        ),
      ),
    );
    await tester.enterText(find.byType(TextField), 'series');
    await tester.tap(find.text('Search'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Add to library'));
    await tester.pumpAndSettle();
    expect(
      await library.contains(
        const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'series'),
      ),
      isTrue,
    );
  });

  testWidgets('explicit search shows loading then empty and errors', (
    tester,
  ) async {
    final pending = Completer<List<Media>>();
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: RemoteMangaSearchPage(
          sourceName: 'Test source',
          search: (_) {
            calls++;
            return calls == 1
                ? pending.future
                : Future.error(StateError('offline'));
          },
          openMedia: (_, _) {},
        ),
      ),
    );
    expect(calls, 0);
    await tester.enterText(find.byType(TextField), 'test');
    await tester.tap(find.text('Search'));
    await tester.pump();
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    pending.complete([]);
    await tester.pumpAndSettle();
    expect(find.text('No manga found.'), findsOneWidget);
    await tester.tap(find.text('Search'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Could not search'), findsOneWidget);
  });
  testWidgets('chapter list preserves attribution and selected ref', (
    tester,
  ) async {
    const ref = SourceMediaRef(sourceId: SourceId('fake'), itemId: 'chapter');
    MangaChapter? selected;
    await tester.pumpWidget(
      MaterialApp(
        home: MangaChapterPage(
          title: 'Series',
          sourceName: 'Test source',
          loadChapters: () async => [
            const MangaChapter(
              title: 'Chapter 1',
              source: ref,
              scanlator: 'Group',
            ),
          ],
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
