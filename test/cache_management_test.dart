import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/cache/cache_management.dart';
import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/application/sources/read_source_artwork.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';
import 'package:hikari/infrastructure/cache/disk_byte_cache.dart';
import 'package:hikari/infrastructure/local_media/archive_copy_pool.dart';
import 'package:path/path.dart' as p;

void main() {
  test(
    'management reports four known groups and leaves unrelated bytes',
    () async {
      final root = await Directory.systemTemp.createTemp(
        'hikari-cache-management-',
      );
      addTearDown(() => root.delete(recursive: true));
      final cache = DiskByteCache(
        cacheBaseDirectory: () async => root,
        namespaceByteBudgets: {
          for (final namespace in CacheManagement.namespaces.keys)
            namespace: 64,
          'other': 64,
        },
        openDatabase: (path) async =>
            CacheDatabase(NativeDatabase(File(p.join(path, 'index.sqlite')))),
      );
      addTearDown(cache.close);
      final manager = CacheManagement(cache);

      await cache.write(ReadMangaPage.cacheNamespace, 'page', Uint8List(7));
      await cache.write(
        ReadSourceArtwork.cacheNamespace,
        'cover',
        Uint8List(3),
      );
      await cache.write('other', 'unrelated', Uint8List(5));

      final usage = await manager.usage();
      expect(usage.totalBytes, 10);
      expect(usage.byCategory, hasLength(4));
      expect(usage.byCategory['Manga pages'], 7);
      expect(usage.byCategory['Novel chapters'], 0);
      await manager.clear();
      expect((await manager.usage()).totalBytes, 0);
      expect(await cache.read('other', 'unrelated'), Uint8List(5));
    },
  );

  test('clear never deletes a local archive with an active lease', () async {
    final root = await Directory.systemTemp.createTemp('hikari-cache-lease-');
    addTearDown(() => root.delete(recursive: true));
    final archive = File(p.join(root.path, 'local_media', 'reading.cbz'));
    await archive.parent.create(recursive: true);
    await archive.writeAsString('original local archive bytes');
    final copies = ArchiveCopyPool((_) async => archive.path, (path) async {
      await File(path).delete();
    });
    copies.retain('content://books/reading');

    final cache = DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: {ReadMangaPage.cacheNamespace: 64},
      openDatabase: (path) async =>
          CacheDatabase(NativeDatabase(File(p.join(path, 'index.sqlite')))),
    );
    addTearDown(cache.close);
    await cache.write(ReadMangaPage.cacheNamespace, 'page', Uint8List(4));

    await CacheManagement(cache).clear();
    final content = await copies.read(
      'content://books/reading',
      (path) => File(path).readAsString(),
    );
    expect(content, 'original local archive bytes');
    expect(await archive.exists(), isTrue);
    await copies.release('content://books/reading');
    expect(await archive.exists(), isFalse);
    await copies.close();
  });
}
