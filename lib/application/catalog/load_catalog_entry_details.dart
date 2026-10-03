import 'package:hikari/domain/catalog/catalog.dart';

final class LoadCatalogEntryDetails {
  const LoadCatalogEntryDetails(this.provider);

  final CatalogProvider provider;

  Future<CatalogEntryDetails?> execute(CatalogEntryId id) {
    if (id.provider != provider.id) return Future.value(null);
    return provider.loadDetails(id);
  }
}
