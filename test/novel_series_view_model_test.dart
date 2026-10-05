import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';

void main() {
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
