import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_novel_chapter.dart';
import 'package:hikari/application/media/open_series_continuation.dart';
import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/progress/progress_session.dart';
import 'package:hikari/application/progress/save_series_chapter_progress.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

const _sourceId = SourceId('remote');
const _series = SourceMediaRef(sourceId: _sourceId, itemId: 'series');
const _chapterA = SourceMediaRef(sourceId: _sourceId, itemId: 'chapter-a');
const _chapterB = SourceMediaRef(sourceId: _sourceId, itemId: 'chapter-b');

final class _Progress implements ProgressRepository {
  final Map<SourceMediaRef, MediaProgress> rows = {};
  int saves = 0;
  @override
  Future<MediaProgress?> load(SourceMediaRef media) async => rows[media];
  @override
  Future<void> save(MediaProgress progress) async {
    saves++;
    rows[progress.media] = progress;
  }

  @override
  Future<void> delete(SourceMediaRef media) async => rows.remove(media);
}

final class _Continuations implements SeriesContinuationRepository {
  SourceMediaRef? chapter;
  Completer<void>? delay;
  bool fail = false;
  int saves = 0;
  @override
  Future<SourceMediaRef?> load(SourceMediaRef series) async => chapter;
  @override
  Future<void> save(SeriesContinuation continuation) async {
    saves++;
    await delay?.future;
    if (fail) throw StateError('continuation write failed');
    chapter = continuation.chapter;
  }
}

final class _MangaRemote implements MangaPageSource, MangaSeriesSource {
  List<SourceMediaRef> refs = [_chapterA, _chapterB];
  ChapterListOrder order = ChapterListOrder.readingOrder;
  bool failLoad = false;
  @override
  SourceId get id => _sourceId;
  @override
  String get name => 'Remote';
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => [
    SourceMediaRef(sourceId: _sourceId, itemId: '${readable.itemId}-page'),
  ];
  @override
  Future<Uint8List> readPage(SourceMediaRef page) async =>
      Uint8List.fromList([1]);
  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef manga) async => failLoad
      ? throw StateError('source failed')
      : MangaSeriesDetails(
          metadata: MediaMetadata(title: 'Series'),
          chapters: refs
              .map((ref) => MangaChapter(title: ref.itemId, source: ref))
              .toList(),
          chapterListOrder: order,
        );
}

final class _NovelSeries implements NovelSeriesSource, NovelChapterSource {
  List<SourceMediaRef> refs = [_chapterA, _chapterB];
  ChapterListOrder order = ChapterListOrder.readingOrder;
  bool failLoad = false;
  @override
  SourceId get id => _sourceId;
  @override
  String get name => 'Remote';
  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => failLoad
      ? throw StateError('source failed')
      : NovelDetails(
          metadata: MediaMetadata(title: 'Series'),
          chapters: refs
              .map((ref) => NovelChapter(title: ref.itemId, source: ref))
              .toList(),
          chapterListOrder: order,
        );
  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async =>
      RichReadingContent(html: '<p>${chapter.itemId}</p>');
  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}

void main() {
  final progress = _Progress();
  final continuation = _Continuations();
  final mangaRemote = _MangaRemote();
  final novelSeriesSource = _NovelSeries();
  final mangaRegistry = SourceRegistry([mangaRemote]);
  final novelRegistry = SourceRegistry([novelSeriesSource]);
  final reader = ReadNovelChapterContent(_NoCache());
  final mangaOpen = OpenMangaChapter(mangaRegistry, progress);
  final novelOpen = OpenNovelChapter(novelRegistry, progress, reader);
  const mangaSeries = Media(
    title: 'Series',
    type: MediaType.manga,
    source: _series,
  );
  const novelSeries = Media(
    title: 'Novel',
    type: MediaType.lightNovel,
    source: _series,
  );

  setUp(() {
    mangaRemote.refs = [_chapterA, _chapterB];
    novelSeriesSource.refs = [_chapterA, _chapterB];
    mangaRemote.order = ChapterListOrder.readingOrder;
    novelSeriesSource.order = ChapterListOrder.readingOrder;
    mangaRemote.failLoad = false;
    novelSeriesSource.failLoad = false;
    progress.rows.clear();
    continuation.chapter = _chapterA;
    continuation.delay = null;
    continuation.fail = false;
    continuation.saves = 0;
    progress.saves = 0;
  });

  for (final manga in [true, false]) {
    test(
      '${manga ? 'manga' : 'novel'} Home Detail parity and invalid sequences',
      () async {
        final workflow = OpenSeriesContinuation(
          OpenMedia(manga ? mangaRegistry : novelRegistry, progress),
        );
        final media = manga ? mangaSeries : novelSeries;
        final detail = LoadSeriesReadingTarget(continuation);
        mangaRemote.order = ChapterListOrder.reverseReadingOrder;
        novelSeriesSource.order = ChapterListOrder.reverseReadingOrder;
        for (final saved in [
          _chapterA,
          const SourceMediaRef(sourceId: _sourceId, itemId: 'missing'),
        ]) {
          continuation.chapter = saved;
          final target = await workflow.execute(media, saved);
          final resolved = await detail.execute(_series, [
            _chapterB,
            _chapterA,
          ]);
          expect(target.chapter, resolved.chapter);
          final sequence = switch (target) {
            MangaContinuationOpenTarget() => target.sequence.map(
              (c) => c.source,
            ),
            NovelContinuationOpenTarget() => target.sequence.map(
              (c) => c.source,
            ),
          };
          expect(sequence, [_chapterB, _chapterA]);
          await target.release();
        }
        continuation.chapter = null;
        expect(
          (await detail.execute(_series, [
            _chapterB,
            _chapterA,
          ])).isContinuation,
          isFalse,
        );
        for (final refs in <List<SourceMediaRef>>[
          [],
          [_chapterA, _chapterA],
          [_series],
          [const SourceMediaRef(sourceId: _sourceId, itemId: '')],
          [
            const SourceMediaRef(
              sourceId: SourceId('foreign'),
              itemId: 'chapter',
            ),
          ],
        ]) {
          mangaRemote.refs = refs;
          novelSeriesSource.refs = refs;
          await expectLater(
            workflow.execute(media, _chapterA),
            throwsStateError,
          );
          if (refs.isEmpty) {
            expect((await detail.execute(_series, refs)).chapter, isNull);
          } else {
            await expectLater(detail.execute(_series, refs), throwsStateError);
          }
        }
        mangaRemote.failLoad = true;
        novelSeriesSource.failLoad = true;
        await expectLater(
          workflow.execute(media, _chapterA),
          throwsA(
            isA<StateError>().having(
              (e) => e.message,
              'message',
              'source failed',
            ),
          ),
        );
      },
    );
  }

  test(
    'resume validates exact tapped chapter against fresh sequence',
    () async {
      final workflow = OpenSeriesContinuation(
        OpenMedia(mangaRegistry, progress),
      );
      final result = await workflow.execute(mangaSeries, _chapterB);
      expect(
        (result as MangaContinuationOpenTarget).selected.source,
        _chapterB,
      );
      expect(result.sequence.map((chapter) => chapter.source), [
        _chapterA,
        _chapterB,
      ]);
      await result.release();
      final fallback = await workflow.execute(
        mangaSeries,
        const SourceMediaRef(sourceId: _sourceId, itemId: 'same-title-number'),
      );
      expect(fallback.chapter, _chapterA);
      await fallback.release();
      final novelFallback =
          await OpenSeriesContinuation(OpenMedia(novelRegistry, progress))
              .execute(
                novelSeries,
                const SourceMediaRef(sourceId: _sourceId, itemId: 'missing'),
              );
      expect(novelFallback.chapter, _chapterA);
      await novelFallback.release();
    },
  );

  test(
    'manga and novel progress writes persist position and relationship',
    () async {
      final mangaTarget = await mangaOpen.execute(
        MangaChapter(title: 'A', source: _chapterA),
      );
      final saveManga = SaveSeriesChapterProgress(continuation, _series);
      await saveManga.execute(
        mangaTarget.progress,
        _chapterA,
        PagePosition(pageIndex: 1, pageCount: 3),
        false,
      );
      expect((progress.rows[_chapterA]!.position as PagePosition).pageIndex, 1);
      expect(continuation.chapter, _chapterA);

      final novelTarget = await novelOpen.execute(
        NovelChapter(title: 'B', source: _chapterB),
      );
      final saveNovel = SaveSeriesChapterProgress(continuation, _series);
      await saveNovel.execute(
        novelTarget.progress,
        _chapterB,
        TextPosition(progression: .4),
        false,
      );
      expect(
        (progress.rows[_chapterB]!.position as TextPosition).progression,
        .4,
      );
      expect(continuation.chapter, _chapterB);

      final novelWorkflow = OpenSeriesContinuation(
        OpenMedia(novelRegistry, progress),
      );
      final resumed = await novelWorkflow.execute(novelSeries, _chapterB);
      expect(
        (resumed as NovelContinuationOpenTarget).selected.source,
        _chapterB,
      );
      await resumed.release();
    },
  );

  test(
    'chapter activation seeds baseline progress and advances continuation',
    () async {
      final save = SaveSeriesChapterProgress(continuation, _series);

      await save.activate(
        await _progressSessionForTest(progress, _chapterB),
        _chapterB,
        TextPosition(progression: 0),
      );

      expect(continuation.chapter, _chapterB);
      final baseline = progress.rows[_chapterB]!;
      expect(baseline.completed, isFalse);
      expect((baseline.position as TextPosition).progression, 0);
    },
  );

  test(
    'chapter activation preserves existing position and completion',
    () async {
      final oldTimestamp = DateTime.utc(2025);
      progress.rows[_chapterB] = MediaProgress(
        media: _chapterB,
        position: TextPosition(progression: .7),
        completed: true,
        updatedAt: oldTimestamp,
      );
      final save = SaveSeriesChapterProgress(continuation, _series);

      await save.activate(
        await _progressSessionForTest(progress, _chapterB),
        _chapterB,
        TextPosition(progression: 0),
      );

      final refreshed = progress.rows[_chapterB]!;
      expect((refreshed.position as TextPosition).progression, .7);
      expect(refreshed.completed, isTrue);
      expect(refreshed.updatedAt.isAfter(oldTimestamp), isTrue);
      expect(continuation.chapter, _chapterB);
    },
  );

  test(
    'duplicate in-flight activation of the same chapter is coalesced',
    () async {
      final save = SaveSeriesChapterProgress(continuation, _series);
      final gate = Completer<void>();
      continuation.delay = gate;

      final session = await _progressSessionForTest(progress, _chapterB);
      final first = save.activate(
        session,
        _chapterB,
        TextPosition(progression: 0),
      );
      final second = save.activate(
        session,
        _chapterB,
        TextPosition(progression: 0),
      );
      await Future<void>.delayed(Duration.zero);
      expect(continuation.saves, 1);

      gate.complete();
      await Future.wait([first, second]);
      expect(continuation.chapter, _chapterB);
      expect(continuation.saves, 1);
    },
  );

  test('progress failure prevents relationship write; continuation failure retries', () async {
    final target = await mangaOpen.execute(
      MangaChapter(title: 'A', source: _chapterA),
    );
    final save = SaveSeriesChapterProgress(continuation, _series);
    await expectLater(
      SaveSeriesChapterProgress(continuation, _series).execute(
        await _progressSessionForTest(_FailingProgress(), _chapterA),
        _chapterA,
        PagePosition(pageIndex: 0, pageCount: 2),
        false,
      ),
      throwsStateError,
    );
    expect(continuation.saves, 0);
    continuation.fail = true;
    await expectLater(
      save.execute(
        target.progress,
        _chapterA,
        PagePosition(pageIndex: 0, pageCount: 2),
        false,
      ),
      throwsStateError,
    );
    continuation.fail = false;
    await save.execute(
      target.progress,
      _chapterA,
      PagePosition(pageIndex: 0, pageCount: 2),
      false,
    );
    expect(continuation.chapter, _chapterA);
  });

  test('delayed continuation write is awaited', () async {
    final target = await mangaOpen.execute(
      MangaChapter(title: 'A', source: _chapterA),
    );
    final save = SaveSeriesChapterProgress(continuation, _series);
    final gate = Completer<void>();
    continuation.delay = gate;
    var completed = false;
    final pending = save
        .execute(
          target.progress,
          _chapterA,
          PagePosition(pageIndex: 1, pageCount: 2),
          false,
        )
        .then((_) => completed = true);
    await Future<void>.delayed(Duration.zero);
    expect(completed, isFalse);
    gate.complete();
    await pending;
    expect(completed, isTrue);
    expect(continuation.chapter, _chapterA);
  });

  test('completed chapter retains exact progress for resume', () async {
    progress.rows[_chapterA] = MediaProgress(
      media: _chapterA,
      position: TextPosition(progression: .75),
      completed: true,
      updatedAt: DateTime.utc(2026),
    );
    final target = await novelOpen.execute(
      NovelChapter(title: 'A', source: _chapterA),
    );
    expect(target.progress.initialProgress!.completed, isTrue);
    expect(
      (target.progress.initialProgress!.position as TextPosition).progression,
      .75,
    );
  });
}

final class _FailingProgress implements ProgressRepository {
  @override
  Future<MediaProgress?> load(SourceMediaRef media) async => null;
  @override
  Future<void> save(MediaProgress progress) async =>
      throw StateError('progress failed');
  @override
  Future<void> delete(SourceMediaRef media) async {}
}

Future<ProgressSession> _progressSessionForTest(
  ProgressRepository repository,
  SourceMediaRef media,
) => ProgressSession.load(repository: repository, media: media);

final class _NoCache implements ByteCache {
  @override
  Future<Uint8List?> read(String namespace, String key) async => null;
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}
  @override
  Future<void> close() async {}
}
