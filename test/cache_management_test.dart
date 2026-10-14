import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/cache/cache_management.dart';
import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';
import 'package:hikari/infrastructure/cache/disk_byte_cache.dart';
import 'package:hikari/infrastructure/local_media/archive_copy_pool.dart';
import 'package:path/path.dart' as p;

void main() {
  test(
    'management uses only the injected allowlist and preserves other bytes',
    () async {
      final root = await Directory.systemTemp.createTemp(
        'hikari-cache-management-',
      );
      addTearDown(() => root.delete(recursive: true));
      const managed = {
        'pages': 'Manga pages',
        'artwork': 'Source artwork',
        'catalog': 'Catalog metadata',
        'novels': 'Novel chapters',
      };
      final cache = DiskByteCache(
        cacheBaseDirectory: () async => root,
        namespaceByteBudgets: {
          for (final namespace in managed.keys) namespace: 64,
          'other': 64,
        },
        openDatabase: (path) async =>
            CacheDatabase(NativeDatabase(File(p.join(path, 'index.sqlite')))),
      );
      addTearDown(cache.close);
      final manager = CacheManagement(cache, namespaces: managed);

      await cache.write('pages', 'page', Uint8List(7));
      await cache.write('artwork', 'cover', Uint8List(3));
      await cache.write('other', 'unrelated', Uint8List(5));
      await cache.write('catalog', 'query', Uint8List(2));

      final usage = await manager.usage();
      expect(usage.totalBytes, 12);
      expect(usage.byCategory, hasLength(managed.length));
      expect(usage.byCategory['Manga pages'], 7);
      expect(usage.byCategory['Catalog metadata'], 2);
      expect(usage.byCategory['Novel chapters'], 0);
      await manager.clear();
      expect((await manager.usage()).totalBytes, 0);
      expect(await cache.read('other', 'unrelated'), Uint8List(5));
    },
  );

  test('rejects duplicate labels and blank namespace registrations', () async {
    final root = await Directory.systemTemp.createTemp('hikari-cache-labels-');
    addTearDown(() => root.delete(recursive: true));
    final cache = DiskByteCache(cacheBaseDirectory: () async => root);
    addTearDown(cache.close);

    expect(
      () => CacheManagement(
        cache,
        namespaces: const {
          'catalog': 'Temporary data',
          'artwork': 'Temporary data',
        },
      ),
      throwsArgumentError,
    );
    expect(
      () => CacheManagement(cache, namespaces: const {'': 'Catalog metadata'}),
      throwsArgumentError,
    );
    expect(
      () => CacheManagement(cache, namespaces: const {'catalog': '  '}),
      throwsArgumentError,
    );
  });

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
      namespaceByteBudgets: const {ReadMangaPage.cacheNamespace: 64},
      openDatabase: (path) async =>
          CacheDatabase(NativeDatabase(File(p.join(path, 'index.sqlite')))),
    );
    addTearDown(cache.close);
    await cache.write(ReadMangaPage.cacheNamespace, 'page', Uint8List(4));

    await CacheManagement(
      cache,
      namespaces: const {ReadMangaPage.cacheNamespace: 'Manga pages'},
    ).clear();
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
