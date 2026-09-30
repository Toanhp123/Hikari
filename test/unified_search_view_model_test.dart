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
