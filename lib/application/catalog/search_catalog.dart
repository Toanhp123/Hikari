import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

final class SearchCatalog {
  const SearchCatalog(this._provider);

  final CatalogProvider _provider;

  Future<List<CatalogEntry>> execute(String query, {MediaType? type}) =>
      _provider.search(query, type: type);
}
