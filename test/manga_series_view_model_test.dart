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
  chapters: const [],
);
