import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/features/remote_manga/manga_series_view_model.dart';

void main() {
  test('MangaSeriesViewModel exposes load failure and retry success', () async {
    var attempts = 0;
    final model = MangaSeriesViewModel(() async {
      attempts++;
      if (attempts == 1) throw StateError('offline');
      return MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Series'),
        chapters: const [],
      );
    });
    addTearDown(model.dispose);

    await model.load();
    expect(model.state, isA<MangaSeriesFailure>());

    await model.load();
    final state = model.state as MangaSeriesReady;
    expect(state.details.metadata.title, 'Series');
  });
}
