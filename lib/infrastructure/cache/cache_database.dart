import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

part 'cache_database.g.dart';

@TableIndex(
  name: 'cache_entries_namespace_accessed_at',
  columns: {#namespace, #accessedAt},
)
class CacheEntries extends Table {
  TextColumn get namespace => text()();
  TextColumn get keyHash => text()();
  TextColumn get digest => text()();
  IntColumn get size => integer()();
  IntColumn get accessedAt => integer()();

  @override
  Set<Column<Object>> get primaryKey => {namespace, keyHash};
}

@DriftDatabase(tables: [CacheEntries])
class CacheDatabase extends _$CacheDatabase {
  CacheDatabase(super.executor);
  CacheDatabase.at(String path)
    : super(
        driftDatabase(
          name: 'cache_index',
          native: DriftNativeOptions(
            databasePath: () async => path,
            tempDirectoryPath: () async =>
                path.substring(0, path.lastIndexOf(Platform.pathSeparator)),
          ),
        ),
      );

  @override
  int get schemaVersion => 1;
}
