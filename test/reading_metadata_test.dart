import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/source.dart';

void main() {
  test('media metadata defensively copies normalized collections', () {
    final authors = ['Author'];
    final metadata = MediaMetadata(
      title: 'Example',
      authors: authors,
      artists: ['Artist'],
      genres: ['Action'],
      tags: ['Tag'],
    );

    authors.add('Changed');
    expect(metadata.authors, ['Author']);
    expect(() => metadata.authors.add('Other'), throwsUnsupportedError);
    expect(() => metadata.artists.clear(), throwsUnsupportedError);
    expect(() => metadata.genres.clear(), throwsUnsupportedError);
    expect(() => metadata.tags.clear(), throwsUnsupportedError);
  });

  test('manga metadata keeps normalized fields and opaque cover reference', () {
    const cover = SourceMediaRef(
      sourceId: SourceId('mihon:42'),
      itemId: 'mihon-art-v1:cover',
    );
    final metadata = MediaMetadata(
      title: 'Example',
      cover: cover,
      summary: 'Summary',
      authors: ['Author'],
      artists: ['Artist'],
      genres: ['Action'],
      status: PublicationStatus.ongoing,
      rawStatus: 'Publishing',
      rating: 8.5,
    );

    expect(metadata.title, 'Example');
    expect(metadata.cover, cover);
    expect(metadata.status, PublicationStatus.ongoing);
    expect(metadata.rawStatus, 'Publishing');
    expect(metadata.rating, 8.5);
  });

  test(
    'manga pages and series enforce runtime invariants and immutability',
    () {
      const media = Media(
        title: 'Example',
        type: MediaType.manga,
        source: SourceMediaRef(
          sourceId: SourceId('mihon:42'),
          itemId: 'series',
        ),
      );
      const preview = MangaPreview(media: media);
      final page = MangaSearchPage(
        results: [preview],
        hasNextPage: true,
        page: 1,
      );
      final chapter = MangaChapter(
        title: 'Chapter 1',
        source: const SourceMediaRef(
          sourceId: SourceId('mihon:42'),
          itemId: 'chapter',
        ),
        chapterNumber: 1,
        uploadedAt: DateTime.fromMillisecondsSinceEpoch(123456789, isUtc: true),
      );
      final details = MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Example'),
        chapterListOrder: ChapterListOrder.readingOrder,
        chapters: [chapter],
      );

      expect(page.page, 1);
      expect(page.hasNextPage, isTrue);
      expect(page.results.single.media, media);
      expect(chapter.chapterNumber, 1);
      expect(chapter.uploadedAt?.millisecondsSinceEpoch, 123456789);
      expect(() => page.results.clear(), throwsUnsupportedError);
      expect(() => details.chapters.clear(), throwsUnsupportedError);
      expect(
        () => MangaSearchPage(results: const [], hasNextPage: false, page: 0),
        throwsArgumentError,
      );
      expect(
        () => MangaChapter(
          title: 'Invalid',
          source: const SourceMediaRef(
            sourceId: SourceId('mihon:42'),
            itemId: 'invalid',
          ),
          chapterNumber: double.nan,
        ),
        throwsArgumentError,
      );
    },
  );

  test('manga details expose both immutable chapter list orders', () {
    final chapters = [
      for (var index = 0; index < 3; index++)
        MangaChapter(
          title: 'Chapter $index',
          chapterNumber: [3.0, null, 1.5][index],
          source: SourceMediaRef(
            sourceId: const SourceId('test'),
            itemId: '$index',
          ),
        ),
    ];
    final reading = MangaSeriesDetails(
      metadata: MediaMetadata(title: 'Manga'),
      chapterListOrder: ChapterListOrder.readingOrder,
      chapters: chapters,
    );
    final reverse = MangaSeriesDetails(
      metadata: MediaMetadata(title: 'Manga'),
      chapters: chapters,
      chapterListOrder: ChapterListOrder.reverseReadingOrder,
    );
    chapters.clear();

    final sourceOrder = List.of(reverse.chapters);
    reverse.chaptersInReadingOrder;
    expect(reverse.chapters, sourceOrder);
    expect(
      () => reading.chaptersInReadingOrder.clear(),
      throwsUnsupportedError,
    );
    expect(() => reverse.chapters.clear(), throwsUnsupportedError);
    expect(reading.chapterListOrder, ChapterListOrder.readingOrder);
    expect(reading.chaptersInReadingOrder, same(reading.chapters));
    expect(reading.chaptersInReadingOrder.map((chapter) => chapter.title), [
      'Chapter 0',
      'Chapter 1',
      'Chapter 2',
    ]);
    expect(reverse.chaptersInReadingOrder.map((chapter) => chapter.title), [
      'Chapter 2',
      'Chapter 1',
      'Chapter 0',
    ]);
    expect(reverse.chaptersInReadingOrder.first.chapterNumber, 1.5);
    expect(reverse.chaptersInReadingOrder.last.chapterNumber, 3);
    expect(() => reading.chapters.clear(), throwsUnsupportedError);
    expect(
      () => reverse.chaptersInReadingOrder.clear(),
      throwsUnsupportedError,
    );
    for (final order in ChapterListOrder.values) {
      final empty = MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Empty'),
        chapters: const [],
        chapterListOrder: order,
      );
      final single = MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Single'),
        chapters: [reading.chapters.first],
        chapterListOrder: order,
      );
      expect(empty.chaptersInReadingOrder, isEmpty);
      expect(single.chaptersInReadingOrder, [reading.chapters.first]);
    }
  });

  test('novel details expose both immutable chapter list orders', () {
    final chapters = [
      for (var index = 0; index < 3; index++)
        NovelChapter(
          title: 'Chapter $index',
          chapterNumber: [9.0, null, 1.5][index],
          source: SourceMediaRef(
            sourceId: const SourceId('test'),
            itemId: '$index',
          ),
        ),
    ];
    final reading = NovelDetails(
      metadata: MediaMetadata(title: 'Novel'),
      chapterListOrder: ChapterListOrder.readingOrder,
      chapters: chapters,
    );
    final reverse = NovelDetails(
      metadata: MediaMetadata(title: 'Novel'),
      chapters: chapters,
      chapterListOrder: ChapterListOrder.reverseReadingOrder,
    );
    chapters.clear();

    final sourceOrder = List.of(reverse.chapters);
    reverse.chaptersInReadingOrder;
    expect(reverse.chapters, sourceOrder);
    expect(
      () => reading.chaptersInReadingOrder.clear(),
      throwsUnsupportedError,
    );
    expect(() => reverse.chapters.clear(), throwsUnsupportedError);
    expect(reading.chapterListOrder, ChapterListOrder.readingOrder);
    expect(reading.chaptersInReadingOrder, same(reading.chapters));
    expect(reading.chaptersInReadingOrder.map((chapter) => chapter.title), [
      'Chapter 0',
      'Chapter 1',
      'Chapter 2',
    ]);
    expect(reverse.chaptersInReadingOrder.map((chapter) => chapter.title), [
      'Chapter 2',
      'Chapter 1',
      'Chapter 0',
    ]);
    expect(reverse.chaptersInReadingOrder[0].chapterNumber, 1.5);
    expect(reverse.chaptersInReadingOrder[1].chapterNumber, isNull);
    expect(
      () => reading.chapters.add(reading.chapters.first),
      throwsUnsupportedError,
    );
    expect(
      () => reverse.chaptersInReadingOrder.clear(),
      throwsUnsupportedError,
    );
    for (final order in ChapterListOrder.values) {
      final empty = NovelDetails(
        metadata: MediaMetadata(title: 'Empty'),
        chapters: const [],
        chapterListOrder: order,
      );
      final single = NovelDetails(
        metadata: MediaMetadata(title: 'Single'),
        chapters: [reading.chapters.first],
        chapterListOrder: order,
      );
      expect(empty.chaptersInReadingOrder, isEmpty);
      expect(single.chaptersInReadingOrder, [reading.chapters.first]);
    }
  });

  test('artwork source owns cover byte loading', () async {
    final source = _ArtworkSource();
    final bytes = await source.readArtwork(
      const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'cover'),
    );
    expect(bytes, [1, 2, 3]);
  });
}

final class _ArtworkSource implements ArtworkSource {
  @override
  SourceId get id => const SourceId('fake');

  @override
  String get name => 'Fake';

  @override
  Future<Uint8List> readArtwork(SourceMediaRef artwork) async =>
      Uint8List.fromList([1, 2, 3]);
}
