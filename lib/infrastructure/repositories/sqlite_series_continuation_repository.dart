import 'package:drift/drift.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/series_continuation.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

final class SqliteSeriesContinuationRepository
    implements SeriesContinuationRepository {
  SqliteSeriesContinuationRepository(this._database);

  final UserDatabase _database;

  @override
  Future<SourceMediaRef?> load(SourceMediaRef series) async {
    final row =
        await (_database.select(_database.seriesContinuationRecords)..where(
              (table) =>
                  table.sourceId.equals(series.sourceId.value) &
                  table.seriesItemId.equals(series.itemId),
            ))
            .getSingleOrNull();
    if (row == null) return null;
    try {
      return SeriesContinuation(
        series: series,
        chapter: SourceMediaRef(
          sourceId: SourceId(row.sourceId),
          itemId: row.chapterItemId,
        ),
      ).chapter;
    } on ArgumentError catch (error) {
      throw FormatException('Invalid stored continuation: $error');
    }
  }

  @override
  Future<void> save(SeriesContinuation continuation) async {
    await _database
        .into(_database.seriesContinuationRecords)
        .insertOnConflictUpdate(
          SeriesContinuationRecordsCompanion.insert(
            sourceId: continuation.series.sourceId.value,
            seriesItemId: continuation.series.itemId,
            chapterItemId: continuation.chapter.itemId,
          ),
        );
  }
}
