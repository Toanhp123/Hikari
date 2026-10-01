import 'package:hikari/domain/catalog/catalog.dart';

final class DiscoverCatalog {
  const DiscoverCatalog(this.provider);
  final CatalogProvider provider;
  Future<CatalogDiscovery> execute() {
    if (!provider.capabilities.contains(CatalogCapability.discovery)) {
      throw StateError('Catalog provider does not support discovery.');
    }
    return provider.discover();
  }
}

final class LoadCatalogDetails {
  const LoadCatalogDetails(this.provider);
  final CatalogProvider provider;
  Future<CatalogDetails?> execute(CatalogMediaId id) {
    if (!provider.capabilities.contains(CatalogCapability.details)) {
      throw StateError('Catalog provider does not support details.');
    }
    if (id.provider != provider.id) return Future.value(null);
    return provider.details(id);
  }
}
