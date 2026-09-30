import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/search/unified_search_view_model.dart';

void main() {
  const media = Media(
    title: 'Solo Leveling',
    type: MediaType.anime,
    source: SourceMediaRef(sourceId: SourceId.local, itemId: 'solo'),
  );

  test(
    'local catalog scans once across queries in one view-model session',
    () async {
      var scans = 0;
      final model = UnifiedSearchViewModel(
        scanLocalMedia: () async {
          scans++;
          return const [media];
        },
      );
      addTearDown(model.dispose);

      await model.search('Solo');
      expect(model.state.visibleResults.single.media, media);

      await model.search('Leveling');
      expect(model.state.visibleResults.single.media, media);
      expect(scans, 1);
    },
  );

  test('refreshLocalCatalog invalidates the cached SAF scan', () async {
    var scans = 0;
    final model = UnifiedSearchViewModel(
      scanLocalMedia: () async {
        scans++;
        return const [media];
      },
    );
    addTearDown(model.dispose);

    await model.search('Solo');
    await model.refreshLocalCatalog();

    expect(scans, 2);
  });

  test('old successful scan cannot publish after root refresh', () async {
    final old = Completer<List<Media>?>();
    var scans = 0;
    final model = UnifiedSearchViewModel(
      scanLocalMedia: () {
        return ++scans == 1 ? old.future : Future.value(<Media>[]);
      },
    );
    addTearDown(model.dispose);
    final pending = model.search('Solo');
    await model.refreshLocalCatalog();
    old.complete([media]);
    await pending;
    expect(model.state.status, UnifiedSearchStatus.empty);
    expect(model.state.results, isEmpty);
  });

  test('old failing scan cannot evict replacement root cache', () async {
    final old = Completer<List<Media>?>();
    var scans = 0;
    final model = UnifiedSearchViewModel(
      scanLocalMedia: () {
        scans++;
        return scans == 1 ? old.future : Future.value([media]);
      },
    );
    addTearDown(model.dispose);
    final pending = model.search('Solo');
    await model.refreshLocalCatalog();
    old.completeError(StateError('old root lost permission'));
    await pending;
    await model.search('Leveling');
    expect(scans, 2);
    expect(model.state.results.single.media, media);
  });

  test(
    'media type filters are presentation-only over fetched results',
    () async {
      final model = UnifiedSearchViewModel(
        scanLocalMedia: () async => const [media],
      );
      addTearDown(model.dispose);

      await model.search('Solo');
      model.selectFilter(SearchMediaTypeFilter.manga);

      expect(model.state.results, hasLength(1));
      expect(model.state.visibleResults, isEmpty);
    },
  );
}
