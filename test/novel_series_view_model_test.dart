import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';

void main() {
  test('NovelSeriesViewModel exposes load failure and retry success', () async {
    var attempts = 0;
    final model = NovelSeriesViewModel(() async {
      attempts++;
      if (attempts == 1) throw StateError('offline');
      return NovelDetails(
        metadata: MediaMetadata(title: 'Novel'),
        chapters: const [],
      );
    });
    addTearDown(model.dispose);

    await model.load();
    expect(model.state, isA<NovelSeriesFailure>());

    await model.load();
    final state = model.state as NovelSeriesReady;
    expect(state.details.metadata.title, 'Novel');
  });
}
