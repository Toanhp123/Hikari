import 'package:drift/drift.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

final class SqliteProgressRepository implements ProgressRepository {
  SqliteProgressRepository(this._database);

  final UserDatabase _database;

  @override
  Future<MediaProgress?> load(SourceMediaRef media) async {
    final row =
        await (_database.select(_database.progressRecords)..where(
              (table) =>
                  table.sourceId.equals(media.sourceId.value) &
                  table.itemId.equals(media.itemId),
            ))
            .getSingleOrNull();
    return row == null ? null : _mapProgress(row);
  }

  @override
  Future<void> save(MediaProgress progress) async {
    switch (progress.position) {
      case VideoPosition(:final position, :final duration):
        await _upsert(
          progress,
          kind: 'video',
          positionMs: position.inMilliseconds,
          durationMs: duration.inMilliseconds,
        );
      case PagePosition(:final pageIndex, :final pageCount):
        await _upsert(
          progress,
          kind: 'page',
          pageIndex: pageIndex,
          pageCount: pageCount,
        );
      case TextPosition(:final progression):
        await _upsert(progress, kind: 'text', progression: progression);
      case DocumentPosition(
        :final resource,
        :final progression,
        :final totalProgression,
        :final locator,
      ):
        await _upsert(
          progress,
          kind: 'document',
          documentResource: resource,
          documentProgression: progression,
          documentTotalProgression: totalProgression,
          documentLocator: locator,
        );
    }
  }

  Future<void> _upsert(
    MediaProgress progress, {
    required String kind,
    int? positionMs,
    int? durationMs,
    int? pageIndex,
    int? pageCount,
    double? progression,
    String? documentResource,
    double? documentProgression,
    double? documentTotalProgression,
    String? documentLocator,
  }) async {
    await _database
        .into(_database.progressRecords)
        .insertOnConflictUpdate(
          ProgressRecordsCompanion.insert(
            sourceId: progress.media.sourceId.value,
            itemId: progress.media.itemId,
            kind: kind,
            positionMs: Value(positionMs),
            durationMs: Value(durationMs),
            pageIndex: Value(pageIndex),
            pageCount: Value(pageCount),
            textProgression: Value(progression),
            documentResource: Value(documentResource),
            documentProgression: Value(documentProgression),
            documentTotalProgression: Value(documentTotalProgression),
            documentLocator: Value(documentLocator),
            completed: progress.completed ? 1 : 0,
            updatedAt: progress.updatedAt.millisecondsSinceEpoch,
          ),
        );
  }

  @override
  Future<void> delete(SourceMediaRef media) async {
    await (_database.delete(_database.progressRecords)..where(
          (table) =>
              table.sourceId.equals(media.sourceId.value) &
              table.itemId.equals(media.itemId),
        ))
        .go();
  }
}

MediaProgress _mapProgress(ProgressRecord row) {
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
        row.textProgression == null &&
        row.documentResource == null &&
        row.documentProgression == null &&
        row.documentTotalProgression == null &&
        row.documentLocator == null) {
      position = VideoPosition(
        position: Duration(milliseconds: row.positionMs!),
        duration: Duration(milliseconds: row.durationMs!),
      );
    } else if (row.kind == 'page' &&
        row.pageIndex != null &&
        row.pageCount != null &&
        row.positionMs == null &&
        row.durationMs == null &&
        row.textProgression == null &&
        row.documentResource == null &&
        row.documentProgression == null &&
        row.documentTotalProgression == null &&
        row.documentLocator == null) {
      position = PagePosition(
        pageIndex: row.pageIndex!,
        pageCount: row.pageCount!,
      );
    } else if (row.kind == 'text' &&
        row.textProgression != null &&
        row.positionMs == null &&
        row.durationMs == null &&
        row.pageIndex == null &&
        row.pageCount == null &&
        row.documentResource == null &&
        row.documentProgression == null &&
        row.documentTotalProgression == null &&
        row.documentLocator == null) {
      position = TextPosition(progression: row.textProgression!);
    } else if (row.kind == 'document' &&
        row.documentResource != null &&
        row.positionMs == null &&
        row.durationMs == null &&
        row.pageIndex == null &&
        row.pageCount == null &&
        row.textProgression == null) {
      position = DocumentPosition(
        resource: row.documentResource!,
        progression: row.documentProgression,
        totalProgression: row.documentTotalProgression,
        locator: row.documentLocator,
      );
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
