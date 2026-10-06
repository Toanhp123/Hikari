import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/progress/load_continue_reading.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

final class MemoryProgress implements ProgressRepository {
  final rows = <SourceMediaRef, MediaProgress>{};
  @override
  Future<MediaProgress?> load(SourceMediaRef media) async => rows[media];
  @override
  Future<void> save(MediaProgress progress) async =>
      rows[progress.media] = progress;
  @override
  Future<void> delete(SourceMediaRef media) async => rows.remove(media);
}

final class MemoryContinuation implements SeriesContinuationRepository {
  final rows = <SourceMediaRef, SourceMediaRef>{};
  @override
  Future<SourceMediaRef?> load(SourceMediaRef series) async => rows[series];
  @override
  Future<void> save(SeriesContinuation continuation) async =>
      rows[continuation.series] = continuation.chapter;
}

void main() {
  const manga = Media(
    title: 'Series',
    type: MediaType.manga,
    source: SourceMediaRef(sourceId: SourceId('remote'), itemId: 'series'),
  );
  const chapter = SourceMediaRef(
    sourceId: SourceId('remote'),
    itemId: 'chapter',
  );
  final progress = MemoryProgress();
  final continuation = MemoryContinuation();
  final now = DateTime.utc(2026);

  setUp(() {
    progress.rows.clear();
    continuation.rows.clear();
  });

  test('Home loads novel continuation progress', () async {
    const novel = Media(
      title: 'Novel',
      type: MediaType.lightNovel,
      source: SourceMediaRef(sourceId: SourceId('remote'), itemId: 'novel'),
    );
    continuation.rows[novel.source] = chapter;
    progress.rows[chapter] = MediaProgress(
      media: chapter,
      position: TextPosition(progression: .35),
      completed: false,
      updatedAt: now,
    );

    final result = await LoadContinueReading(
      progress,
      continuation,
    ).execute([LibraryEntry(media: novel, addedAt: now)]);

    expect(result.items.single.media, novel);
    expect(result.items.single.chapter, chapter);
    expect(result.items.single.progress, .35);
  });

  test(
    'Home sorts manga and novel continuations by chapter update time',
    () async {
      const novel = Media(
        title: 'Novel',
        type: MediaType.lightNovel,
        source: SourceMediaRef(sourceId: SourceId('remote'), itemId: 'novel'),
      );
      const newerChapter = SourceMediaRef(
        sourceId: SourceId('remote'),
        itemId: 'newer-chapter',
      );
      continuation.rows[manga.source] = chapter;
      continuation.rows[novel.source] = newerChapter;
      progress.rows[chapter] = MediaProgress(
        media: chapter,
        position: PagePosition(pageIndex: 0, pageCount: 4),
        completed: false,
        updatedAt: now,
      );
      progress.rows[newerChapter] = MediaProgress(
        media: newerChapter,
        position: TextPosition(progression: .4),
        completed: false,
        updatedAt: now.add(const Duration(minutes: 1)),
      );

      final result = await LoadContinueReading(progress, continuation).execute([
        LibraryEntry(media: manga, addedAt: now),
        LibraryEntry(media: novel, addedAt: now),
      ]);

      expect(result.items.map((item) => item.media), [novel, manga]);
    },
  );

  test('Home excludes completed direct media progress', () async {
    progress.rows[manga.source] = MediaProgress(
      media: manga.source,
      position: PagePosition(pageIndex: 3, pageCount: 4),
      completed: true,
      updatedAt: now,
    );

    final result = await LoadContinueReading(progress)
        .execute([LibraryEntry(media: manga, addedAt: now)]);

    expect(result.items, isEmpty);
  });

  test(
    'missing continuation chapter does not fall back to series progress',
    () async {
      continuation.rows[manga.source] = chapter;
      progress.rows[manga.source] = MediaProgress(
        media: manga.source,
        position: PagePosition(pageIndex: 0, pageCount: 4),
        completed: false,
        updatedAt: now,
      );

      final result = await LoadContinueReading(
        progress,
        continuation,
      ).execute([LibraryEntry(media: manga, addedAt: now)]);

      expect(result.items, isEmpty);
      expect(result.error, isNull);
    },
  );

  test(
    'malformed and wrong-position continuations isolate bad entry',
    () async {
      const otherManga = Media(
        title: 'Other',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId('remote'), itemId: 'other'),
      );
      const novel = Media(
        title: 'Novel',
        type: MediaType.lightNovel,
        source: SourceMediaRef(sourceId: SourceId('remote'), itemId: 'novel'),
      );
      const otherSourceChapter = SourceMediaRef(
        sourceId: SourceId('other-source'),
        itemId: 'chapter',
      );
      const validChapter = SourceMediaRef(
        sourceId: SourceId('remote'),
        itemId: 'valid-chapter',
      );
      continuation.rows[manga.source] = otherSourceChapter;
      continuation.rows[otherManga.source] = chapter;
      continuation.rows[novel.source] = validChapter;
      progress.rows[chapter] = MediaProgress(
        media: chapter,
        position: PagePosition(pageIndex: 0, pageCount: 2),
        completed: false,
        updatedAt: now,
      );
      progress.rows[validChapter] = MediaProgress(
        media: validChapter,
        position: PagePosition(pageIndex: 0, pageCount: 2),
        completed: false,
        updatedAt: now,
      );

      final result = await LoadContinueReading(progress, continuation).execute([
        LibraryEntry(media: manga, addedAt: now),
        LibraryEntry(
          media: otherManga,
          addedAt: now.add(const Duration(seconds: 1)),
        ),
        LibraryEntry(
          media: novel,
          addedAt: now.add(const Duration(seconds: 2)),
        ),
      ]);

      expect(result.items.map((item) => item.media), [otherManga]);
      expect(result.error, isA<ArgumentError>());
    },
  );

  test('completed serial continuation stays resumable', () async {
    continuation.rows[manga.source] = chapter;
    progress.rows[chapter] = MediaProgress(
      media: chapter,
      position: PagePosition(pageIndex: 1, pageCount: 4),
      completed: true,
      updatedAt: now,
    );

    final result = await LoadContinueReading(
      progress,
      continuation,
    ).execute([LibraryEntry(media: manga, addedAt: now)]);

    expect(result.items.single.chapter, chapter);
    expect(result.items.single.completed, isTrue);
    expect(result.items.single.progress, .5);
  });

  test(
    'bad continuation does not suppress valid continuation entries',
    () async {
      const bad = Media(
        title: 'Bad',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId('remote'), itemId: 'bad'),
      );
      const valid = Media(
        title: 'Valid',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId('remote'), itemId: 'valid'),
      );
      const validChapter = SourceMediaRef(
        sourceId: SourceId('remote'),
        itemId: 'valid-chapter',
      );
      continuation.rows[bad.source] = SourceMediaRef(
        sourceId: SourceId('other-source'),
        itemId: 'orphan',
      );
      continuation.rows[valid.source] = validChapter;
      progress.rows[validChapter] = MediaProgress(
        media: validChapter,
        position: PagePosition(pageIndex: 0, pageCount: 2),
        completed: false,
        updatedAt: now,
      );

      final result = await LoadContinueReading(progress, continuation).execute([
        LibraryEntry(media: bad, addedAt: now),
        LibraryEntry(media: valid, addedAt: now),
      ]);

      expect(result.items.map((item) => item.media), [valid]);
      expect(result.error, isA<ArgumentError>());
    },
  );

  test('wrong continuation position omits only affected series', () async {
    const novel = Media(
      title: 'Novel',
      type: MediaType.lightNovel,
      source: SourceMediaRef(sourceId: SourceId('remote'), itemId: 'novel'),
    );
    const valid = Media(
      title: 'Valid',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId('remote'), itemId: 'valid'),
    );
    const validChapter = SourceMediaRef(
      sourceId: SourceId('remote'),
      itemId: 'valid-chapter',
    );
    continuation.rows[novel.source] = chapter;
    continuation.rows[valid.source] = validChapter;
    progress.rows[chapter] = MediaProgress(
      media: chapter,
      position: PagePosition(pageIndex: 0, pageCount: 2),
      completed: false,
      updatedAt: now,
    );
    progress.rows[validChapter] = MediaProgress(
      media: validChapter,
      position: PagePosition(pageIndex: 1, pageCount: 2),
      completed: false,
      updatedAt: now.add(const Duration(seconds: 1)),
    );

    final result = await LoadContinueReading(progress, continuation).execute([
      LibraryEntry(media: novel, addedAt: now),
      LibraryEntry(media: valid, addedAt: now),
    ]);

    expect(result.items.map((item) => item.media), [valid]);
    expect(result.error, isA<FormatException>());
  });
}
