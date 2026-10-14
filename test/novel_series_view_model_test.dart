import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';

void main() {
  test(
    'native detail preserves cached chapters when source is offline',
    () async {
      var calls = 0;
      final model = NovelSeriesViewModel(() async {
        calls++;
        throw StateError('offline');
      }, readCachedDetails: () async => _details('Cached novel'));
      addTearDown(model.dispose);

      await model.load(useCached: true);
      expect(calls, 1);
      expect(model.state.refreshFailed, isTrue);
      expect(model.effectiveMetadata!.title, 'Cached novel');
      await model.load();
      expect(calls, 2);
      expect(model.state.refreshFailed, isTrue);
    },
  );

  test(
    'cache read failure falls through to live source without leaking error',
    () async {
      var calls = 0;
      final model = NovelSeriesViewModel(() async {
        calls++;
        return _details('Live');
      }, readCachedDetails: () async => throw StateError('corrupt cache'));
      addTearDown(model.dispose);
      await model.load(useCached: true);
      expect(calls, 1);
      expect(model.state.details!.metadata.title, 'Live');
    },
  );

  test('late cached snapshot does not replace newer manual refresh', () async {
    final pendingCache = Completer<NovelDetails?>();
    var loads = 0;
    final model = NovelSeriesViewModel(() async {
      loads++;
      return _details('Updated');
    }, readCachedDetails: () => pendingCache.future);
    addTearDown(model.dispose);

    final older = model.load(useCached: true);
    await Future<void>.delayed(Duration.zero);
    await model.load();
    pendingCache.complete(_details('Old cached'));
    await older;
    expect(loads, 1);
    expect(model.state.details!.metadata.title, 'Updated');
  });

  test('NovelSeriesViewModel exposes load failure and retry success', () async {
    var attempts = 0;
    final model = NovelSeriesViewModel(() async {
      attempts++;
      if (attempts == 1) throw StateError('offline');
      return _details('Novel');
    });
    addTearDown(model.dispose);

    await model.load();
    expect(model.state.failed, isTrue);

    await model.load();
    expect(model.state.status, NovelSeriesStatus.ready);
    expect(model.state.details!.metadata.title, 'Novel');
  });

  test('failed refresh preserves the last loaded chapter list', () async {
    var attempts = 0;
    final model = NovelSeriesViewModel(() async {
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

NovelDetails _details(String title) => NovelDetails(
  metadata: MediaMetadata(title: title),
  chapterListOrder: ChapterListOrder.readingOrder,
  chapters: const [],
);
