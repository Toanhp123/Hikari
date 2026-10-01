import 'package:hikari/domain/catalog/catalog.dart';

final class DiscoverCatalog {
  const DiscoverCatalog(this.provider);

  final CatalogProvider provider;

  Future<CatalogDiscovery> execute() => provider.discover();
}
