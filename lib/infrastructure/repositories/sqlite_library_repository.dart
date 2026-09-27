import 'package:drift/drift.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

final class SqliteLibraryRepository implements LibraryRepository {
  SqliteLibraryRepository(this._database);

  final UserDatabase _database;

  @override
  Future<void> upsert(LibraryEntry entry) async {
    await _database
        .into(_database.libraryRecords)
        .insertOnConflictUpdate(
          LibraryRecord(
            sourceId: entry.media.source.sourceId.value,
            itemId: entry.media.source.itemId,
            title: entry.media.title,
            mediaType: _mediaTypeStorageValue(entry.media.type),
            addedAt: entry.addedAt.millisecondsSinceEpoch,
          ),
        );
  }

  @override
  Future<void> remove(SourceMediaRef media) async {
    await (_database.delete(_database.libraryRecords)..where(
          (table) =>
              table.sourceId.equals(media.sourceId.value) &
              table.itemId.equals(media.itemId),
        ))
        .go();
  }

  @override
  Future<bool> contains(SourceMediaRef media) async =>
      await (_database.select(_database.libraryRecords)..where(
            (table) =>
                table.sourceId.equals(media.sourceId.value) &
                table.itemId.equals(media.itemId),
          ))
          .getSingleOrNull() !=
      null;

  @override
  Future<List<LibraryEntry>> loadAll() async {
    final rows = await (_database.select(
      _database.libraryRecords,
    )..orderBy([(table) => OrderingTerm.desc(table.addedAt)])).get();

    return rows.map(_mapLibraryEntry).toList();
  }
}

LibraryEntry _mapLibraryEntry(LibraryRecord row) {
  try {
    return LibraryEntry(
      media: Media(
        title: row.title,
        type: _mediaTypeFromStorageValue(row.mediaType),
        source: SourceMediaRef(
          sourceId: SourceId(row.sourceId),
          itemId: row.itemId,
        ),
      ),
      addedAt: DateTime.fromMillisecondsSinceEpoch(row.addedAt, isUtc: true),
    );
  } on ArgumentError catch (error) {
    throw FormatException('Invalid library snapshot: $error');
  }
}

String _mediaTypeStorageValue(MediaType type) => switch (type) {
  MediaType.anime => 'anime',
  MediaType.manga => 'manga',
  MediaType.lightNovel => 'light_novel',
};

MediaType _mediaTypeFromStorageValue(String value) => switch (value) {
  'anime' => MediaType.anime,
  'manga' => MediaType.manga,
  'light_novel' => MediaType.lightNovel,
  _ => throw FormatException('Unknown stored media type: $value'),
};
