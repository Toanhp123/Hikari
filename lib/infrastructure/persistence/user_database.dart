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
  int get schemaVersion => 1;
}
