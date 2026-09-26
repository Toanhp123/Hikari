import 'package:drift/drift.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

final class SqliteProgressRepository implements ProgressRepository {
  SqliteProgressRepository(this.db);
  final UserDatabase db;

  @override
  Future<MediaProgress?> load(SourceMediaRef media) async {
    final row =
        await (db.select(db.progressRecords)..where(
              (t) =>
                  t.sourceId.equals(media.sourceId.value) &
                  t.itemId.equals(media.itemId),
            ))
            .getSingleOrNull();
    return row == null ? null : _progress(row);
  }

  @override
  Future<void> save(MediaProgress progress) async {
    switch (progress.position) {
      case VideoPosition(:final position, :final duration):
        await _save(
          progress,
          'video',
          positionMs: position.inMilliseconds,
          durationMs: duration.inMilliseconds,
        );
      case PagePosition(:final pageIndex, :final pageCount):
        await _save(
          progress,
          'page',
          pageIndex: pageIndex,
          pageCount: pageCount,
        );
      case TextPosition(:final progression):
        await _save(progress, 'text', progression: progression);
    }
  }

  Future<void> _save(
    MediaProgress p,
    String kind, {
    int? positionMs,
    int? durationMs,
    int? pageIndex,
    int? pageCount,
    double? progression,
  }) async {
    await db
        .into(db.progressRecords)
        .insertOnConflictUpdate(
          ProgressRecordsCompanion.insert(
            sourceId: p.media.sourceId.value,
            itemId: p.media.itemId,
            kind: kind,
            positionMs: Value(positionMs),
            durationMs: Value(durationMs),
            pageIndex: Value(pageIndex),
            pageCount: Value(pageCount),
            textProgression: Value(progression),
            completed: p.completed ? 1 : 0,
            updatedAt: p.updatedAt.millisecondsSinceEpoch,
          ),
        );
  }

  @override
  Future<void> delete(SourceMediaRef media) async {
    await (db.delete(db.progressRecords)..where(
          (t) =>
              t.sourceId.equals(media.sourceId.value) &
              t.itemId.equals(media.itemId),
        ))
        .go();
  }
}

MediaProgress _progress(ProgressRecord row) {
  try {
    if (row.completed != 0 && row.completed != 1) {
      throw const FormatException('Invalid completion.');
    }
    final ProgressPosition position;
    if (row.kind == 'video' &&
        row.positionMs != null &&
        row.durationMs != null &&
        row.pageIndex == null &&
        row.pageCount == null &&
        row.textProgression == null) {
      position = VideoPosition(
        position: Duration(milliseconds: row.positionMs!),
        duration: Duration(milliseconds: row.durationMs!),
      );
    } else if (row.kind == 'page' &&
        row.pageIndex != null &&
        row.pageCount != null &&
        row.positionMs == null &&
        row.durationMs == null &&
        row.textProgression == null) {
      position = PagePosition(
        pageIndex: row.pageIndex!,
        pageCount: row.pageCount!,
      );
    } else if (row.kind == 'text' &&
        row.textProgression != null &&
        row.positionMs == null &&
        row.durationMs == null &&
        row.pageIndex == null &&
        row.pageCount == null) {
      position = TextPosition(progression: row.textProgression!);
    } else {
      throw const FormatException('Invalid progress discriminator or payload.');
    }
    return MediaProgress(
      media: SourceMediaRef(
        sourceId: SourceId(row.sourceId),
        itemId: row.itemId,
      ),
      position: position,
      completed: row.completed == 1,
      updatedAt: DateTime.fromMillisecondsSinceEpoch(
        row.updatedAt,
        isUtc: true,
      ),
    );
  } on ArgumentError catch (error) {
    throw FormatException('Invalid stored progress: $error');
  }
}

final class SqliteLibraryRepository implements LibraryRepository {
  SqliteLibraryRepository(this.db);
  final UserDatabase db;

  @override
  Future<void> upsert(LibraryEntry entry) async {
    await db
        .into(db.libraryRecords)
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
    await (db.delete(db.libraryRecords)..where(
          (t) =>
              t.sourceId.equals(media.sourceId.value) &
              t.itemId.equals(media.itemId),
        ))
        .go();
  }

  @override
  Future<bool> contains(SourceMediaRef media) async =>
      await (db.select(db.libraryRecords)..where(
            (t) =>
                t.sourceId.equals(media.sourceId.value) &
                t.itemId.equals(media.itemId),
          ))
          .getSingleOrNull() !=
      null;

  @override
  Future<List<LibraryEntry>> loadAll() async {
    final rows = await (db.select(
      db.libraryRecords,
    )..orderBy([(t) => OrderingTerm.desc(t.addedAt)])).get();
    return rows.map((row) {
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
          addedAt: DateTime.fromMillisecondsSinceEpoch(
            row.addedAt,
            isUtc: true,
          ),
        );
      } on ArgumentError catch (error) {
        throw FormatException('Invalid library snapshot: $error');
      }
    }).toList();
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
