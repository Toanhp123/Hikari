import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/cache/cache_management.dart';
import 'package:hikari/application/sources/read_source_artwork.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';
import 'package:hikari/infrastructure/cache/disk_byte_cache.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';
import 'package:path/path.dart' as p;

void main() {
  test('clearing cache does not change durable user state', () async {
    final root = await Directory.systemTemp.createTemp('hikari-cache-state-');
    addTearDown(() => root.delete(recursive: true));
    final userDatabase = UserDatabase(
      NativeDatabase(File(p.join(root.path, 'user.sqlite'))),
    );
    addTearDown(userDatabase.close);
    final library = SqliteLibraryRepository(userDatabase);
    final progress = SqliteProgressRepository(userDatabase);
    const mediaRef = SourceMediaRef(
      sourceId: SourceId('test:source'),
      itemId: 'series-1',
    );

    await library.upsert(
      LibraryEntry(
        media: const Media(
          title: 'Stored series',
          type: MediaType.manga,
          source: mediaRef,
        ),
        addedAt: DateTime.utc(2026, 1, 1),
      ),
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
    await cache.write(
      ReadSourceArtwork.cacheNamespace,
      'cover',
      Uint8List.fromList([1, 2, 3]),
    );
    await CacheManagement(cache).clear();

    expect(await cache.read(ReadSourceArtwork.cacheNamespace, 'cover'), isNull);
    expect(await library.contains(mediaRef), isTrue);
    final storedProgress = await progress.load(mediaRef);
    expect(storedProgress, isNotNull);
    expect(storedProgress!.completed, isFalse);
    expect(
      (storedProgress.position as TextPosition).progression,
      closeTo(0.4, 0.000001),
    );
  });
}
