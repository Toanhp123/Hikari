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

@DriftDatabase(tables: [ProgressRecords, LibraryRecords])
class UserDatabase extends _$UserDatabase {
  UserDatabase([QueryExecutor? executor])
    : super(executor ?? driftDatabase(name: 'hikari_user_state'));
  @override
  int get schemaVersion => 2;

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
    },
  );
}
