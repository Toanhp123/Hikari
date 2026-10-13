import 'package:drift/drift.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/catalog/catalog_source_selection.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

final class SqliteCatalogSourceSelectionRepository
    implements CatalogSourceSelectionRepository {
  SqliteCatalogSourceSelectionRepository(this._database);

  final UserDatabase _database;

  @override
  Future<Media?> load(CatalogEntry entry) async {
    final row =
        await (_database.select(_database.catalogSourceSelections)..where(
              (table) =>
                  table.catalogProvider.equals(entry.id.provider) &
                  table.catalogItemId.equals(entry.id.value) &
                  table.mediaType.equals(_typeValue(entry.type)),
            ))
            .getSingleOrNull();
    if (row == null) return null;
    if (row.sourceId.isEmpty || row.itemId.isEmpty || row.sourceTitle.isEmpty) {
      throw const FormatException('Invalid saved Catalog source selection.');
    }
    return Media(
      title: row.sourceTitle,
      type: entry.type,
      source: SourceMediaRef(
        sourceId: SourceId(row.sourceId),
        itemId: row.itemId,
      ),
    );
  }

  @override
  Future<void> save(CatalogEntry entry, Media selected) async {
    if (entry.type != selected.type ||
        entry.type == MediaType.anime ||
        entry.id.provider.isEmpty ||
        entry.id.value.isEmpty ||
        selected.source.sourceId.value.isEmpty ||
        selected.source.sourceId == SourceId.local ||
        selected.source.itemId.isEmpty ||
        selected.title.isEmpty) {
      throw ArgumentError('Invalid Catalog source selection.');
    }
    await _database
        .into(_database.catalogSourceSelections)
        .insertOnConflictUpdate(
          CatalogSourceSelectionRecord(
            catalogProvider: entry.id.provider,
            catalogItemId: entry.id.value,
            mediaType: _typeValue(entry.type),
            sourceId: selected.source.sourceId.value,
            itemId: selected.source.itemId,
            sourceTitle: selected.title,
          ),
        );
  }
}

String _typeValue(MediaType type) => switch (type) {
  MediaType.anime => 'anime',
  MediaType.manga => 'manga',
  MediaType.lightNovel => 'light_novel',
};
