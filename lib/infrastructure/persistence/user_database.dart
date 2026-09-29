import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

part 'user_database.g.dart';

class ProgressRecords extends Table {
  TextColumn get sourceId => text()();
  TextColumn get itemId => text()();
  TextColumn get kind => text()();
  IntColumn get positionMs => integer().nullable()();
  IntColumn get durationMs => integer().nullable()();
  IntColumn get pageIndex => integer().nullable()();
  IntColumn get pageCount => integer().nullable()();
  RealColumn get textProgression => real().nullable()();
  TextColumn get documentResource => text().nullable()();
  RealColumn get documentProgression => real().nullable()();
  RealColumn get documentTotalProgression => real().nullable()();
  TextColumn get documentLocator => text().nullable()();
  IntColumn get completed => integer()();
  IntColumn get updatedAt => integer()();
  @override
  Set<Column<Object>> get primaryKey => {sourceId, itemId};
}

class LibraryRecords extends Table {
  TextColumn get sourceId => text()();
  TextColumn get itemId => text()();
  TextColumn get title => text()();
  TextColumn get mediaType => text()();
  IntColumn get addedAt => integer()();
  @override
  Set<Column<Object>> get primaryKey => {sourceId, itemId};
}

class MihonContinuationRecords extends Table {
  TextColumn get sourceId => text()();
  TextColumn get itemId => text()();
  TextColumn get payload => text()();
  @override
  Set<Column<Object>> get primaryKey => {sourceId, itemId};
}

@DriftDatabase(
  tables: [ProgressRecords, LibraryRecords, MihonContinuationRecords],
)
class UserDatabase extends _$UserDatabase {
  UserDatabase([QueryExecutor? executor])
    : super(executor ?? driftDatabase(name: 'hikari_user_state'));
  @override
  int get schemaVersion => 3;

  Future<void> _migrateMihonReferences() async {
    // Keep latest progress / earliest library membership if old mutable keys
    // describe the same provider URL. Unrecognized payloads remain untouched.
    for (final (table, order) in [
      ('library_records', 'added_at ASC, item_id ASC'),
      ('progress_records', 'updated_at DESC, item_id ASC'),
    ]) {
      final rows = await customSelect(
        "SELECT * FROM $table WHERE source_id LIKE 'mihon:%' ORDER BY $order",
      ).get();
      for (final row in rows) {
        final oldId = row.read<String>('item_id');
        if (!oldId.startsWith('mihon-v1:')) continue;
        Map<String, dynamic> state;
        try {
          final decoded = jsonDecode(
            utf8.decode(base64Url.decode(oldId.substring(9))),
          );
          if (decoded is! Map<String, dynamic> ||
              !['manga', 'chapter'].contains(decoded['kind']) ||
              decoded['url'] is! String ||
              (decoded['url'] as String).trim().isEmpty ||
              [
                'title',
                'memo',
                'scanlator',
              ].any((key) => decoded[key] != null && decoded[key] is! String) ||
              (decoded['chapterNumber'] != null &&
                  (decoded['chapterNumber'] is! num ||
                      !(decoded['chapterNumber'] as num).isFinite)) ||
              (decoded['dateUpload'] != null &&
                  decoded['dateUpload'] is! int)) {
            continue;
          }
          state = decoded;
        } on FormatException {
          continue;
        }
        final stable =
            'mihon-v2:${base64Url.encode(utf8.encode(jsonEncode({'kind': state['kind'], 'url': state['url']})))}';
        final source = row.read<String>('source_id');
        await into(mihonContinuationRecords).insert(
          MihonContinuationRecordsCompanion.insert(
            sourceId: source,
            itemId: stable,
            payload: oldId,
          ),
          mode: InsertMode.insertOrIgnore,
        );
        await customStatement(
          'UPDATE OR IGNORE $table SET item_id = ? WHERE source_id = ? AND item_id = ?',
          [stable, source, oldId],
        );
        await customStatement(
          'DELETE FROM $table WHERE source_id = ? AND item_id = ?',
          [source, oldId],
        );
      }
    }
  }

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (Migrator m) => m.createAll(),
    onUpgrade: (Migrator m, int from, int to) async {
      if (from < 2) {
        await m.addColumn(progressRecords, progressRecords.documentResource);
        await m.addColumn(progressRecords, progressRecords.documentProgression);
        await m.addColumn(
          progressRecords,
          progressRecords.documentTotalProgression,
        );
        await m.addColumn(progressRecords, progressRecords.documentLocator);
      }
      if (from < 3) {
        await m.createTable(mihonContinuationRecords);
        await _migrateMihonReferences();
      }
    },
  );
}
