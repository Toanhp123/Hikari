import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/application/catalog/load_catalog_details.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

void main() {
  test(
    'LoadCatalogDetails delegates details for matching provider id',
    () async {
      const id = CatalogMediaId(provider: 'fake', value: '123');
      final details = CatalogDetails(
        media: CatalogMedia(id: id, title: 'Title', type: MediaType.anime),
      );
      final provider = _FakeCatalogProvider(onDetails: (_) async => details);

      final workflow = LoadCatalogDetails(provider);
      final result = await workflow.execute(id);

      expect(result, same(details));
      expect(provider.detailsCalls, 1);
    },
  );

  test('LoadCatalogDetails returns null without calling provider for mismatched provider id', () async {
    const id = CatalogMediaId(provider: 'other', value: '123');
    final provider = _FakeCatalogProvider(onDetails: (_) async => null);

    final workflow = LoadCatalogDetails(provider);
    final result = await workflow.execute(id);

    expect(result, isNull);
    expect(provider.detailsCalls, 0);
  });
}

final class _FakeCatalogProvider implements CatalogProvider {
  _FakeCatalogProvider({required this.onDetails});

  final Future<CatalogDetails?> Function(CatalogMediaId id) onDetails;
  int detailsCalls = 0;

  @override
  String get id => 'fake';

  @override
  Future<CatalogDiscovery> discover() async =>
      CatalogDiscovery(sections: const {});

  @override
  Future<CatalogDetails?> details(CatalogMediaId id) {
    detailsCalls++;
    return onDetails(id);
  }

  @override
  Future<void> close() async {}
}
