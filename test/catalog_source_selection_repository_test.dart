import 'dart:io';

import 'package:drift/native.dart';
import 'package:drift_dev/api/migrations_native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_catalog_source_selection_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';

CatalogEntry _catalog(
  String value,
  MediaType type, {
  String provider = 'anilist',
}) => CatalogEntry(
  id: CatalogEntryId(provider: provider, value: value),
  title: 'Shared title',
  type: type,
);

Media _source(String sourceId, String itemId, MediaType type) => Media(
  title: 'Source title $itemId',
  type: type,
  source: SourceMediaRef(sourceId: SourceId(sourceId), itemId: itemId),
);

void main() {
  test(
    'saved exact provider/type/source/candidate survives cold restart',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'hikari-mapping-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final file = File('${directory.path}/user.sqlite');
      var database = UserDatabase(NativeDatabase(file));
      var selections = SqliteCatalogSourceSelectionRepository(database);
      final manga = _catalog('17', MediaType.manga);
      final novel = _catalog('17', MediaType.lightNovel);
      final other = _catalog('18', MediaType.manga);
      final otherProvider = _catalog('17', MediaType.manga, provider: 'other');
      final a = _source('mihon:one', 'same-title/candidate/a', MediaType.manga);
      final b = _source('mihon:one', 'same-title/candidate/b', MediaType.manga);
      final c = _source('mihon:two', 'same-title/candidate/a', MediaType.manga);
      final n = _source('lnreader:one', 'novel', MediaType.lightNovel);

      expect(await selections.load(manga), isNull);
      await selections.save(manga, a);
      await selections.save(other, b);
      await selections.save(otherProvider, c);
      await selections.save(novel, n);
      await selections.save(manga, b);
      await database.close();

      database = UserDatabase(NativeDatabase(file));
      selections = SqliteCatalogSourceSelectionRepository(database);
      addTearDown(() => database.close());
      expect((await selections.load(manga))!.source, b.source);
      expect((await selections.load(manga))!.title, b.title);
      expect((await selections.load(other))!.source, b.source);
      expect((await selections.load(otherProvider))!.source, c.source);
      expect((await selections.load(novel))!.source, n.source);
      expect((await selections.load(novel))!.type, MediaType.lightNovel);
      await database.validateDatabaseSchema();
    },
  );

  test(
    'wrong media type/local source cannot replace the saved selection',
    () async {
      final database = UserDatabase(NativeDatabase.memory());
      addTearDown(database.close);
      final selections = SqliteCatalogSourceSelectionRepository(database);
      final catalog = _catalog('17', MediaType.manga);
      final original = _source('mihon:a', 'a', MediaType.manga);
      await selections.save(catalog, original);
      await expectLater(
        selections.save(
          catalog,
          _source('lnreader:a', 'b', MediaType.lightNovel),
        ),
        throwsArgumentError,
      );
      await expectLater(
        selections.save(catalog, _source('local', 'b', MediaType.manga)),
        throwsArgumentError,
      );
      expect((await selections.load(catalog))!.source, original.source);
    },
  );

  test(
    'v5 upgrade preserves Library rows and adds durable selections',
    () async {
      final directory = await Directory.systemTemp.createTemp('hikari-v5-');
      addTearDown(() => directory.delete(recursive: true));
      final file = File('${directory.path}/user.sqlite');
      final catalog = _catalog('17', MediaType.manga);
      final media = _source('mihon:a', 'a', MediaType.manga);
      var db = UserDatabase(NativeDatabase(file));
      await SqliteLibraryRepository(
        db,
      ).upsert(LibraryEntry(media: media, addedAt: DateTime.utc(2026, 10, 13)));
      await db.close();

      // Set the on-disk schema to exactly the v5 subset for this migration.
      db = UserDatabase(
        NativeDatabase(
          file,
          setup: (connection) {
            connection.execute('DROP TABLE catalog_source_selections');
            connection.execute('PRAGMA user_version = 5');
          },
        ),
      );
      final selections = SqliteCatalogSourceSelectionRepository(db);
      await selections.save(catalog, media);
      expect(
        (await SqliteLibraryRepository(db).loadAll()).single.media.source,
        media.source,
      );
      expect((await selections.load(catalog))!.source, media.source);
      await db.validateDatabaseSchema();
      await db.close();
      db = UserDatabase(NativeDatabase(file));
      addTearDown(db.close);
      expect(
        (await SqliteCatalogSourceSelectionRepository(db).load(catalog))!
            .source,
        media.source,
      );
    },
  );
}
