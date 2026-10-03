import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/catalog/search_catalog.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

void main() {
  test('SearchCatalog delegates query and media type to provider', () async {
    final provider = _FakeCatalogProvider();
    final workflow = SearchCatalog(provider);

    final result = await workflow.execute('Frieren', type: MediaType.manga);

    expect(result, same(provider.result));
    expect(provider.query, 'Frieren');
    expect(provider.type, MediaType.manga);
  });
}

final class _FakeCatalogProvider implements CatalogProvider {
  final result = <CatalogEntry>[];
  String? query;
  MediaType? type;

  @override
  String get id => 'fake';

  @override
  Future<CatalogDiscovery> discover() async =>
      CatalogDiscovery(sections: const {});

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async {
    this.query = query;
    this.type = type;
    return result;
  }

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async => null;

  @override
  Future<void> close() async {}
}
