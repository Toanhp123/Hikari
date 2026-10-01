import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

void main() {
  test(
    'LoadCatalogEntryDetails delegates detail loading for matching provider id',
    () async {
      const id = CatalogEntryId(provider: 'fake', value: '123');
      final details = CatalogEntryDetails(
        entry: CatalogEntry(id: id, title: 'Title', type: MediaType.anime),
      );
      final provider = _FakeCatalogProvider(
        onLoadDetails: (_) async => details,
      );

      final workflow = LoadCatalogEntryDetails(provider);
      final result = await workflow.execute(id);

      expect(result, same(details));
      expect(provider.loadDetailsCalls, 1);
    },
  );

  test('LoadCatalogEntryDetails returns null without calling provider for mismatched provider id', () async {
    const id = CatalogEntryId(provider: 'other', value: '123');
    final provider = _FakeCatalogProvider(onLoadDetails: (_) async => null);

    final workflow = LoadCatalogEntryDetails(provider);
    final result = await workflow.execute(id);

    expect(result, isNull);
    expect(provider.loadDetailsCalls, 0);
  });
}

final class _FakeCatalogProvider implements CatalogProvider {
  _FakeCatalogProvider({required this.onLoadDetails});

  final Future<CatalogEntryDetails?> Function(CatalogEntryId id) onLoadDetails;
  int loadDetailsCalls = 0;

  @override
  String get id => 'fake';

  @override
  Future<CatalogDiscovery> discover() async =>
      CatalogDiscovery(sections: const {});

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      const [];

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) {
    loadDetailsCalls++;
    return onLoadDetails(id);
  }

  @override
  Future<void> close() async {}
}
