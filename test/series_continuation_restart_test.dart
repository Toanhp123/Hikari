import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_novel_chapter.dart';
import 'package:hikari/application/media/open_series_continuation.dart';
import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/progress/load_continue_reading.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/series_continuation.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_series_continuation_repository.dart';

const _id = SourceId('remote-manga');
const _novelId = SourceId('remote-novel');
const _mangaSeriesRef = SourceMediaRef(sourceId: _id, itemId: 'manga-series');
const _mangaChapterRef = SourceMediaRef(sourceId: _id, itemId: 'manga-chapter');
const _novelSeriesRef = SourceMediaRef(
  sourceId: _novelId,
  itemId: 'novel-series',
);
const _novelChapterRef = SourceMediaRef(
  sourceId: _novelId,
  itemId: 'novel-chapter',
);

class _MangaSource
    implements MangaPageSource, MangaSeriesSource, MediaSourceAvailability {
  final bool available;
  _MangaSource({this.available = true});
  @override
  SourceId get id => _id;
  @override
  String get name => 'Manga';
  @override
  bool get isAvailable => available;
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef chapter) async => [
    SourceMediaRef(sourceId: _id, itemId: '${chapter.itemId}-page'),
  ];
  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef manga) async =>
      MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Manga'),
        chapters: [MangaChapter(title: 'Chapter', source: _mangaChapterRef)],
        chapterListOrder: ChapterListOrder.readingOrder,
      );
}

final class _NovelSource implements NovelSeriesSource, NovelChapterSource {
  @override
  SourceId get id => _novelId;
  @override
  String get name => 'Novel';
  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: MediaMetadata(title: 'Novel'),
    chapters: [NovelChapter(title: 'Chapter', source: _novelChapterRef)],
    chapterListOrder: ChapterListOrder.readingOrder,
  );
  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async =>
      RichReadingContent(html: '<p>Chapter</p>');
  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}

final class _NoCache implements ByteCache {
  @override
  Future<Uint8List?> read(String namespace, String key) async => null;
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}
  @override
  Future<void> close() async {}
}

void main() {
  late UserDatabase db;
  late SqliteProgressRepository progress;
  late SqliteLibraryRepository library;
  late SqliteSeriesContinuationRepository continuations;
  late _MangaSource mangaSource;
  late _NovelSource novelSource;
  late SourceRegistry registry;

  late Directory directory;
  late File file;

  setUp(() async {
    directory = await Directory.systemTemp.createTemp('hikari-series-restart-');
    file = File('${directory.path}/state.sqlite');
    db = UserDatabase(NativeDatabase.createInBackground(file));
    progress = SqliteProgressRepository(db);
    library = SqliteLibraryRepository(db);
    continuations = SqliteSeriesContinuationRepository(db);
    mangaSource = _MangaSource();
    novelSource = _NovelSource();
    registry = SourceRegistry([mangaSource, novelSource]);
  });
  tearDown(() async {
    await db.close();
    await directory.delete(recursive: true);
  });

  test('reopen resolves saved manga and novel continuations through fresh workflows', () async {
    const manga = Media(
      title: 'Manga',
      type: MediaType.manga,
      source: _mangaSeriesRef,
    );
    const novel = Media(
      title: 'Novel',
      type: MediaType.lightNovel,
      source: _novelSeriesRef,
    );
    await library.upsert(
      LibraryEntry(media: manga, addedAt: DateTime.utc(2026)),
    );
    await library.upsert(
      LibraryEntry(media: novel, addedAt: DateTime.utc(2026)),
    );
    await progress.save(
      MediaProgress(
        media: _mangaChapterRef,
        position: PagePosition(pageIndex: 2, pageCount: 8),
        completed: false,
        updatedAt: DateTime.utc(2026, 1, 2),
      ),
    );
    await progress.save(
      MediaProgress(
        media: _novelChapterRef,
        position: TextPosition(progression: .42),
        completed: false,
        updatedAt: DateTime.utc(2026, 1, 3),
      ),
    );
    await continuations.save(
      SeriesContinuation(series: _mangaSeriesRef, chapter: _mangaChapterRef),
    );
    await continuations.save(
      SeriesContinuation(series: _novelSeriesRef, chapter: _novelChapterRef),
    );

    await db.close();
    db = UserDatabase(NativeDatabase.createInBackground(file));
    progress = SqliteProgressRepository(db);
    library = SqliteLibraryRepository(db);
    continuations = SqliteSeriesContinuationRepository(db);

    final detailResolver = LoadSeriesReadingTarget(continuations);
    final mangaDetails = await mangaSource.loadDetails(_mangaSeriesRef);
    final mangaCta = await detailResolver.execute(
      _mangaSeriesRef,
      mangaDetails.chaptersInReadingOrder
          .map((chapter) => chapter.source)
          .toList(),
    );
    expect(mangaCta.isContinuation, isTrue);
    final detailMangaOpen = await OpenMangaChapter(registry, progress).execute(
      mangaDetails.chapters.singleWhere(
        (chapter) => chapter.source == mangaCta.chapter,
      ),
    );
    expect(
      (detailMangaOpen.progress.initialProgress!.position as PagePosition)
          .pageIndex,
      2,
    );
    final novelDetails = await novelSource.loadDetails(_novelSeriesRef);
    final novelCta = await detailResolver.execute(
      _novelSeriesRef,
      novelDetails.chaptersInReadingOrder
          .map((chapter) => chapter.source)
          .toList(),
    );
    expect(novelCta.isContinuation, isTrue);
    final detailNovelOpen =
        await OpenNovelChapter(
          registry,
          progress,
          ReadNovelChapterContent(_NoCache()),
        ).execute(
          novelDetails.chapters.singleWhere(
            (chapter) => chapter.source == novelCta.chapter,
          ),
        );
    expect(
      (detailNovelOpen.progress.initialProgress!.position as TextPosition)
          .progression,
      .42,
    );

    final home = await LoadContinueReading(
      progress,
      continuations,
    ).execute(await library.loadAll());
    expect(home.items.map((item) => item.media.type), [
      MediaType.lightNovel,
      MediaType.manga,
    ]);

    final mangaContinuation = home.items.singleWhere(
      (item) => item.media.type == MediaType.manga,
    );
    final mangaOpen = OpenSeriesContinuation(OpenMedia(registry, progress));
    final openedManga = await mangaOpen.execute(
      mangaContinuation.media,
      mangaContinuation.chapter!,
    );
    final openedMangaTarget = openedManga as MangaContinuationOpenTarget;
    final mangaTarget = await OpenMangaChapter(
      registry,
      progress,
    ).execute(openedMangaTarget.selected);
    expect(openedMangaTarget.sequence, hasLength(1));
    expect(openedMangaTarget.sequence.single.source, _mangaChapterRef);
    expect(
      () => openedMangaTarget.sequence.add(openedMangaTarget.selected),
      throwsUnsupportedError,
    );
    expect(openedMangaTarget.selected.source, _mangaChapterRef);
    final mangaPosition =
        mangaTarget.progress.initialProgress!.position as PagePosition;
    expect(mangaPosition.pageIndex, 2);
    expect(mangaPosition.pageCount, 8);
    await openedManga.release();

    final novelContinuation = home.items.singleWhere(
      (item) => item.media.type == MediaType.lightNovel,
    );
    final novelOpen = OpenSeriesContinuation(OpenMedia(registry, progress));
    final openedNovel = await novelOpen.execute(
      novelContinuation.media,
      novelContinuation.chapter!,
    );
    final openedNovelTarget = openedNovel as NovelContinuationOpenTarget;
    final novelTarget = await OpenNovelChapter(
      registry,
      progress,
      ReadNovelChapterContent(_NoCache()),
    ).execute(openedNovelTarget.selected);
    expect(openedNovelTarget.sequence, hasLength(1));
    expect(openedNovelTarget.sequence.single.source, _novelChapterRef);
    expect(
      () => openedNovelTarget.sequence.add(openedNovelTarget.selected),
      throwsUnsupportedError,
    );
    expect(openedNovelTarget.selected.source, _novelChapterRef);
    final novelPosition =
        novelTarget.progress.initialProgress!.position as TextPosition;
    expect(novelPosition.progression, .42);
    await openedNovel.release();
  });

  test('resume rejects missing chapter and unavailable source', () async {
    const manga = Media(
      title: 'Manga',
      type: MediaType.manga,
      source: _mangaSeriesRef,
    );
    const missing = SourceMediaRef(sourceId: _id, itemId: 'missing');
    final openSeries = OpenSeriesContinuation(OpenMedia(registry, progress));
    await expectLater(openSeries.execute(manga, missing), throwsStateError);

    final unavailableRegistry = SourceRegistry([
      _MangaSource(available: false),
      novelSource,
    ]);
    await expectLater(
      OpenSeriesContinuation(OpenMedia(unavailableRegistry, progress))
          .execute(manga, _mangaChapterRef),
      throwsStateError,
    );
  });

  test(
    'resume rejects duplicate chapter sequence and releases acquired lease',
    () async {
      final leased = _LeasedMangaSource();
      final leasedRegistry = SourceRegistry([leased]);
      const manga = Media(
        title: 'Manga',
        type: MediaType.manga,
        source: _mangaSeriesRef,
      );

      await expectLater(
        OpenSeriesContinuation(OpenMedia(leasedRegistry, progress))
            .execute(manga, _mangaChapterRef),
        throwsStateError,
      );
      expect(leased.released, 1);
    },
  );
}

final class _LeasedMangaSource extends _MangaSource
    implements MediaOpenLeaseSource {
  int released = 0;
  _LeasedMangaSource() : super();
  @override
  MediaOpenLease acquireOpenLease(SourceMediaRef media) =>
      _TestLease(() => released++);
  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef manga) async =>
      MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Manga'),
        chapters: [
          MangaChapter(title: 'First', source: _mangaChapterRef),
          MangaChapter(title: 'Duplicate', source: _mangaChapterRef),
        ],
        chapterListOrder: ChapterListOrder.readingOrder,
      );
}

final class _TestLease implements MediaOpenLease {
  _TestLease(this.onRelease);
  final void Function() onRelease;
  bool _released = false;
  @override
  Future<void> release() async {
    if (_released) return;
    _released = true;
    onRelease();
  }
}
