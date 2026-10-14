import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/features/remote_manga/manga_series_view_model.dart';

void main() {
  test('offline snapshot is usable while metadata refresh fails', () async {
    const source = SourceId('s');
    final chapter = MangaChapter(
      title: 'Cached chapter',
      source: const SourceMediaRef(sourceId: source, itemId: 'c'),
    );
    var calls = 0;
    final model = MangaSeriesViewModel(
      () async {
        calls++;
        throw StateError('offline');
      },
      readCachedDetails: () async => MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Offline'),
        chapters: [chapter],
        chapterListOrder: ChapterListOrder.readingOrder,
      ),
    );
    addTearDown(model.dispose);

    await model.load(useCached: true);
    expect(calls, 1);
    expect(model.state.refreshFailed, isTrue);
    expect(model.readingSequence.single.source, chapter.source);
    await model.load();
    expect(calls, 2);
    expect(model.state.refreshFailed, isTrue);
    expect(model.readingSequence.single.source, chapter.source);
  });

  test('cached chapters render before failing source revalidation', () async {
    final gate = Completer<void>();
    var calls = 0;
    final model = MangaSeriesViewModel(() async {
      calls++;
      await gate.future;
      throw StateError('offline');
    }, readCachedDetails: () async => _details('Stale'));
    addTearDown(model.dispose);

    final pending = model.load(useCached: true);
    await Future<void>.delayed(Duration.zero);
    expect(model.state.refreshing, isTrue);
    expect(model.state.details!.metadata.title, 'Stale');
    expect(calls, 1);
    gate.complete();
    await pending;
    expect(model.state.refreshFailed, isTrue);
    expect(model.state.details!.metadata.title, 'Stale');
  });

  test('MangaSeriesViewModel exposes load failure and retry success', () async {
    var attempts = 0;
    final model = MangaSeriesViewModel(() async {
      attempts++;
      if (attempts == 1) throw StateError('offline');
      return _details('Series');
    });
    addTearDown(model.dispose);

    await model.load();
    expect(model.state.failed, isTrue);

    await model.load();
    expect(model.state.status, MangaSeriesStatus.ready);
    expect(model.state.details!.metadata.title, 'Series');
  });

  test('failed refresh preserves the last loaded chapter list', () async {
    var attempts = 0;
    final model = MangaSeriesViewModel(() async {
      attempts++;
      if (attempts == 2) throw StateError('offline');
      return _details('Loaded');
    });
    addTearDown(model.dispose);

    await model.load();
    await model.load();

    expect(model.state.refreshFailed, isTrue);
    expect(model.state.details!.metadata.title, 'Loaded');
  });
}

MangaSeriesDetails _details(String title) => MangaSeriesDetails(
  metadata: MediaMetadata(title: title),
  chapterListOrder: ChapterListOrder.readingOrder,
  chapters: const [],
);
