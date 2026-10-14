import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/cache/cache_management.dart';
import 'package:hikari/application/sources/read_source_artwork.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';
import 'package:hikari/infrastructure/cache/disk_byte_cache.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_catalog_source_selection_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';
import 'package:path/path.dart' as p;

void main() {
  test('clearing cache preserves durable user state after restart', () async {
    final root = await Directory.systemTemp.createTemp('hikari-cache-state-');
    addTearDown(() => root.delete(recursive: true));
    final userFile = File(p.join(root.path, 'user.sqlite'));
    var userDatabase = UserDatabase(NativeDatabase(userFile));
    addTearDown(() => userDatabase.close());
    final library = SqliteLibraryRepository(userDatabase);
    final progress = SqliteProgressRepository(userDatabase);
    const mediaRef = SourceMediaRef(
      sourceId: SourceId('test:source'),
      itemId: 'series-1',
    );

    const media = Media(
      title: 'Stored series',
      type: MediaType.manga,
      source: mediaRef,
    );
    final catalog = CatalogEntry(
      id: const CatalogEntryId(provider: 'anilist', value: '17'),
      title: 'Catalog title',
      type: MediaType.manga,
    );
    await SqliteCatalogSourceSelectionRepository(userDatabase)
        .save(catalog, media);
    await library.upsert(
      LibraryEntry(media: media, addedAt: DateTime.utc(2026, 1, 1)),
    );
    await progress.save(
      MediaProgress(
        media: mediaRef,
        position: TextPosition(progression: 0.4),
        completed: false,
        updatedAt: DateTime.utc(2026, 1, 2),
      ),
    );

    final cache = DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: const {ReadSourceArtwork.cacheNamespace: 64},
      openDatabase: (path) async =>
          CacheDatabase(NativeDatabase(File('$path/index.sqlite'))),
    );
    addTearDown(cache.close);
    final bytes = Uint8List.fromList([1, 2, 3]);
    await cache.write(ReadSourceArtwork.cacheNamespace, 'cover', bytes);
    expect(await cache.read(ReadSourceArtwork.cacheNamespace, 'cover'), bytes);
    await CacheManagement(
      cache,
      namespaces: const {ReadSourceArtwork.cacheNamespace: 'Source artwork'},
    ).clear();

    expect(await cache.read(ReadSourceArtwork.cacheNamespace, 'cover'), isNull);
    expect(await library.contains(mediaRef), isTrue);

    await userDatabase.close();
    userDatabase = UserDatabase(NativeDatabase(userFile));
    final selections = SqliteCatalogSourceSelectionRepository(userDatabase);
    final selected = await selections.load(catalog);
    expect(selected, isNotNull);
    expect(selected!.source, media.source);
    expect(selected.title, media.title);
    expect(selected.type, media.type);
    for (final (provider, value, type) in [
      ('other', '17', MediaType.manga),
      ('anilist', '18', MediaType.manga),
      ('anilist', '17', MediaType.lightNovel),
    ]) {
      expect(
        await selections.load(
          CatalogEntry(
            id: CatalogEntryId(provider: provider, value: value),
            title: catalog.title,
            type: type,
          ),
        ),
        isNull,
      );
    }
    final storedLibrary = (await SqliteLibraryRepository(
      userDatabase,
    ).loadAll()).single;
    expect(storedLibrary.media.source, media.source);
    expect(storedLibrary.media.title, media.title);
    expect(storedLibrary.media.type, media.type);
    expect(storedLibrary.addedAt, DateTime.utc(2026, 1, 1));
    final storedProgress = await SqliteProgressRepository(userDatabase)
        .load(mediaRef);
    expect(storedProgress, isNotNull);
    expect(storedProgress!.media, mediaRef);
    expect(storedProgress.completed, isFalse);
    expect(storedProgress.updatedAt, DateTime.utc(2026, 1, 2));
    expect(
      (storedProgress.position as TextPosition).progression,
      closeTo(0.4, 0.000001),
    );
  });
}
