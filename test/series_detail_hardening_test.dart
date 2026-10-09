import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/series_continuation.dart';
import 'package:hikari/features/remote_manga/manga_series_view_model.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';

SourceMediaRef ref(String id) =>
    SourceMediaRef(sourceId: const SourceId('fake'), itemId: id);

class _Continuations implements SeriesContinuationRepository {
  SourceMediaRef? saved;
  @override
  Future<SourceMediaRef?> load(SourceMediaRef series) async => saved;
  @override
  Future<void> save(SeriesContinuation continuation) async {
    saved = continuation.chapter;
  }
}

void main() {
  test('continuation resolves persisted chapter and falls back for missing chapter', () async {
    final repository = _Continuations();
    final resolve = LoadSeriesReadingTarget(repository);
    final chapters = [ref('first'), ref('second')];
    expect(
      (await resolve.execute(ref('series'), chapters)).chapter,
      chapters.first,
    );
    await repository.save(
      SeriesContinuation(series: ref('series'), chapter: chapters.last),
    );
    final resumed = await resolve.execute(ref('series'), chapters);
    expect(resumed.chapter, chapters.last);
    expect(resumed.isContinuation, isTrue);
    repository.saved = ref('removed');
    final fallback = await resolve.execute(ref('series'), chapters);
    expect(fallback.chapter, chapters.first);
    expect(fallback.isContinuation, isFalse);
    expect((await resolve.execute(ref('series'), [])).chapter, isNull);
  });

  for (final manga in [true, false]) {
    test(
      '${manga ? 'manga' : 'novel'} invalidates continuation when refresh starts',
      () async {
        final resolvers = <Completer<SeriesReadingTarget>>[];
        final refresh = Completer<void>();
        var loads = 0;
        Future<void> loading() async {
          if (++loads > 1) await refresh.future;
        }

        Future<SeriesReadingTarget> resolve(List<SourceMediaRef> _) {
          final pending = Completer<SeriesReadingTarget>();
          resolvers.add(pending);
          return pending.future;
        }

        final dynamic model = manga
            ? MangaSeriesViewModel(
                () async {
                  await loading();
                  return MangaSeriesDetails(
                    metadata: MediaMetadata(title: 'Series'),
                    chapterListOrder: ChapterListOrder.readingOrder,
                    chapters: [MangaChapter(title: 'One', source: ref('one'))],
                  );
                },
                series: ref('series'),
                loadReadingTarget: resolve,
              )
            : NovelSeriesViewModel(
                () async {
                  await loading();
                  return NovelDetails(
                    metadata: MediaMetadata(title: 'Series'),
                    chapterListOrder: ChapterListOrder.readingOrder,
                    chapters: [NovelChapter(title: 'One', source: ref('one'))],
                  );
                },
                series: ref('series'),
                loadReadingTarget: resolve,
              );
        addTearDown(() => model.dispose());
        final Future<void> first = model.load() as Future<void>;
        await Future<void>.delayed(Duration.zero);
        final Future<void> second = model.load() as Future<void>;
        resolvers.first.complete(
          SeriesReadingTarget(ref('one'), isContinuation: true),
        );
        await first;
        expect(model.primaryChapter, isNull);
        refresh.completeError(StateError('refresh failed'));
        await Future<void>.delayed(Duration.zero);
        expect(resolvers.length, 2);
        resolvers.last.complete(SeriesReadingTarget(ref('one')));
        await second;
        expect(model.primaryChapter.source, ref('one'));
        expect(model.state.refreshFailed, isTrue);
      },
    );
  }

  for (final size in [100, 1000, 10000, 50000]) {
    test(
      'derived manga/novel lists remain stable and preserve sequence at $size',
      () async {
        final manga = MangaSeriesViewModel(
          () async => MangaSeriesDetails(
            metadata: MediaMetadata(title: 'Manga'),
            chapterListOrder: ChapterListOrder.reverseReadingOrder,
            chapters: List.generate(
              size,
              (i) => MangaChapter(
                title: 'Chapter $i',
                source: ref('$i'),
                chapterNumber: i.toDouble(),
              ),
            ),
          ),
        );
        final novel = NovelSeriesViewModel(
          () async => NovelDetails(
            metadata: MediaMetadata(title: 'Novel'),
            chapterListOrder: ChapterListOrder.reverseReadingOrder,
            chapters: List.generate(
              size,
              (i) => NovelChapter(
                title: 'Chapter $i',
                source: ref('$i'),
                chapterNumber: i.toDouble(),
              ),
            ),
          ),
        );
        addTearDown(manga.dispose);
        addTearDown(novel.dispose);
        await manga.load();
        await novel.load();
        final mangaSequence = manga.readingSequence;
        final novelSequence = novel.readingSequence;
        expect(mangaSequence.first.source, ref('${size - 1}'));
        expect(novelSequence.first.source, ref('${size - 1}'));
        manga.setSearchQuery('chapter 9');
        novel.setSearchQuery('chapter 9');
        final mangaFiltered = manga.displayChapters;
        final novelFiltered = novel.displayChapters;
        manga.setSearchQuery(' CHAPTER 9 ');
        novel.setSearchQuery(' CHAPTER 9 ');
        expect(identical(mangaFiltered, manga.displayChapters), isTrue);
        expect(identical(novelFiltered, novel.displayChapters), isTrue);
        manga.toggleSourceOrder();
        novel.toggleSourceOrder();
        expect(manga.displayChapters, mangaFiltered.reversed.toList());
        expect(novel.displayChapters, novelFiltered.reversed.toList());
        expect(identical(mangaSequence, manga.readingSequence), isTrue);
        expect(identical(novelSequence, novel.readingSequence), isTrue);
        await manga.refreshReadingTarget();
        await novel.refreshReadingTarget();
        final cached = manga.displayChapters;
        await manga.refreshReadingTarget();
        expect(identical(cached, manga.displayChapters), isTrue);
      },
    );
  }

  test(
    'stale continuation result cannot replace refreshed novel target',
    () async {
      final pending = <Completer<SeriesReadingTarget>>[];
      final model = NovelSeriesViewModel(
        () async => NovelDetails(
          metadata: MediaMetadata(title: 'Novel'),
          chapterListOrder: ChapterListOrder.readingOrder,
          chapters: [
            NovelChapter(title: 'One', source: ref('one')),
            NovelChapter(title: 'Two', source: ref('two')),
          ],
        ),
        loadReadingTarget: (_) {
          final result = Completer<SeriesReadingTarget>();
          pending.add(result);
          return result.future;
        },
      );
      addTearDown(model.dispose);
      final first = model.load();
      await Future<void>.delayed(Duration.zero);
      final second = model.load();
      await Future<void>.delayed(Duration.zero);
      pending.last.complete(
        SeriesReadingTarget(ref('two'), isContinuation: true),
      );
      await second;
      pending.first.complete(SeriesReadingTarget(ref('one')));
      await first;
      expect(model.primaryChapter?.source, ref('two'));
      expect(model.isContinuation, isTrue);
    },
  );
}
