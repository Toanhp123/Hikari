import 'package:hikari/domain/catalog/catalog.dart';

final class LoadCatalogDetails {
  const LoadCatalogDetails(this.provider);

  final CatalogProvider provider;

  Future<CatalogDetails?> execute(CatalogMediaId id) {
    if (id.provider != provider.id) return Future.value(null);
    return provider.details(id);
  }
}
