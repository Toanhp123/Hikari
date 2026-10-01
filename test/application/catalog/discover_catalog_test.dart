import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

void main() {
  test('DiscoverCatalog delegates discovery to provider', () async {
    final expected = CatalogDiscovery(sections: const {});
    final provider = _FakeCatalogProvider(onDiscover: () async => expected);

    final workflow = DiscoverCatalog(provider);
    final result = await workflow.execute();

    expect(result, same(expected));
    expect(provider.discoverCalls, 1);
  });
}

final class _FakeCatalogProvider implements CatalogProvider {
  _FakeCatalogProvider({required this.onDiscover});

  final Future<CatalogDiscovery> Function() onDiscover;
  int discoverCalls = 0;

  @override
  String get id => 'fake';

  @override
  Future<CatalogDiscovery> discover() {
    discoverCalls++;
    return onDiscover();
  }

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      const [];

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async => null;

  @override
  Future<void> close() async {}
}
