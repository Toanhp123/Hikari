import 'package:hikari/domain/catalog/catalog.dart';

final class DiscoverCatalog {
  const DiscoverCatalog(this.provider);

  final CatalogProvider provider;

  Future<CatalogDiscovery> execute() => provider.discover();

  Future<CatalogSnapshot<CatalogDiscovery>?> cached() =>
      provider is CatalogSnapshotProvider
      ? (provider as CatalogSnapshotProvider).cachedDiscovery()
      : Future.value(null);

  Future<CatalogDiscovery> refresh() => provider is CatalogSnapshotProvider
      ? (provider as CatalogSnapshotProvider).refreshDiscovery()
      : provider.discover();
}
