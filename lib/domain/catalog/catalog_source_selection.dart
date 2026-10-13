import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

/// A user-confirmed Catalog-to-source choice, independent of Library and cache.
abstract interface class CatalogSourceSelectionRepository {
  /// Returns the exact selected source media, or null when never selected.
  Future<Media?> load(CatalogEntry entry);

  /// Replaces the choice only for this provider-qualified Catalog entry/type.
  Future<void> save(CatalogEntry entry, Media selected);
}
