import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';
import 'package:hikari/infrastructure/cache/disk_byte_cache.dart';

void main() {
  test('caches page bytes under independent manga namespace', () async {
    final cache = _MemoryCache();
    final source = _PageSource();
    final reader = ReadMangaPage(cache);
    const page = SourceMediaRef(
      sourceId: SourceId('remote'),
      itemId: 'opaque/page',
    );
    expect(await reader.execute(source, page), [1, 2, 3]);
    expect(await reader.execute(source, page), [1, 2, 3]);
    expect(source.reads, 1);
    expect(cache.reads, 2);
    expect(cache.values.keys.single, 'manga-page-v1/["remote","opaque/page"]');
  });

  test('keys isolate source and opaque item tuples and namespaces', () async {
    final cache = _MemoryCache();
    final sourceA = _PageSource(id: const SourceId('a:b'));
    final sourceB = _PageSource(id: const SourceId('a'));
    final reader = ReadMangaPage(cache);
    await reader.execute(sourceA, _page('a:b', 'c'));
    await reader.execute(sourceB, _page('a', 'b:c'));
    await cache.write(
      'source-artwork-v1',
      '["a:b","c"]',
      Uint8List.fromList([9]),
    );
    expect(cache.values.length, 3);
    expect(ReadMangaPage.cacheKey(_page('a:b', 'c')), '["a:b","c"]');
  });

  test(
    'coalesces same-page reads and lets different pages run independently',
    () async {
      final cache = _MemoryCache();
      final source = _PageSource()..holdPages = true;
      final reader = ReadMangaPage(cache);
      final one = reader.execute(source, _page('remote', 'one'));
      final same = reader.execute(source, _page('remote', 'one'));
      final other = reader.execute(source, _page('remote', 'two'));
      await source.started.future;
      expect(source.reads, 2);
      source.gates['one']!.complete(Uint8List.fromList([1]));
      source.gates['two']!.complete(Uint8List.fromList([2]));
      expect(await one, [1]);
      expect(await same, [1]);
      expect(await other, [2]);
    },
  );

  test('provider failure cleans flight so next read retries', () async {
    final source = _PageSource()..failNext = true;
    final reader = ReadMangaPage(_MemoryCache());
    final page = _page('remote', 'retry');
    await expectLater(reader.execute(source, page), throwsStateError);
    expect(await reader.execute(source, page), [1, 2, 3]);
    expect(source.reads, 2);
  });

  test('cache read and write failures fail open', () async {
    final cache = _MemoryCache()..failRead = true;
    final source = _PageSource();
    final reader = ReadMangaPage(cache);
    expect(await reader.execute(source, _page('remote', 'read')), [1, 2, 3]);
    cache.failRead = false;
    cache.failWrite = true;
    expect(await reader.execute(source, _page('remote', 'write')), [1, 2, 3]);
    expect(source.reads, 2);
  });

  test('empty page bytes are returned without caching', () async {
    final source = _PageSource()..bytes = Uint8List(0);
    final cache = _MemoryCache();
    final reader = ReadMangaPage(cache);
    final page = _page('remote', 'empty');
    expect(await reader.execute(source, page), isEmpty);
    expect(await reader.execute(source, page), isEmpty);
    expect(cache.writes, 0);
    expect(source.reads, 2);
  });

  test('rejects page references owned by another source', () async {
    final source = _PageSource(id: const SourceId('remote'));
    final reader = ReadMangaPage(_MemoryCache());
    await expectLater(
      reader.execute(source, _page('other', 'page')),
      throwsStateError,
    );
    expect(source.reads, 0);
  });

  test('fresh reload skips stale cache and replaces cached bytes', () async {
    final cache = _MemoryCache()
      ..values['manga-page-v1/["remote","page"]'] = Uint8List.fromList([1]);
    final source = _PageSource()..bytes = Uint8List.fromList([2]);
    final reader = ReadMangaPage(cache);
    final page = _page('remote', 'page');
    expect(await reader.reload(source, page), [2]);
    expect(await reader.execute(source, page), [2]);
    expect(source.reads, 1);
  });

  test(
    'fresh reload waits for earlier cached read before replacing it',
    () async {
      final cache = _MemoryCache();
      final source = _PageSource()..holdPages = true;
      final reader = ReadMangaPage(cache);
      final page = _page('remote', 'race');
      final stale = reader.execute(source, page);
      await source.started.future;
      final fresh = reader.reload(source, page);
      source.gates['race']!.complete(Uint8List.fromList([1]));
      expect(await stale, [1]);
      await Future<void>.delayed(Duration.zero);
      source.gates['race']!.complete(Uint8List.fromList([1, 1]));
      expect(await fresh, [1, 1]);
      expect(await reader.execute(source, page), [1, 1]);
      expect(source.reads, 2);
    },
  );

  test(
    'fresh reload proceeds after failed predecessor and owns next retry',
    () async {
      final source = _PageSource()..holdPages = true;
      final reader = ReadMangaPage(_MemoryCache());
      final page = _page('remote', 'failed-refresh');
      final old = reader.execute(source, page);
      await source.started.future;
      final fresh = reader.reload(source, page);
      source.gates['failed-refresh']!.completeError(StateError('offline'));
      await expectLater(old, throwsStateError);
      await Future<void>.delayed(Duration.zero);
      source.gates['failed-refresh']!.completeError(StateError('offline'));
      await expectLater(fresh, throwsStateError);
      source.holdPages = false;
      expect(await reader.reload(source, page), [1, 2, 3]);
      expect(source.reads, 3);
    },
  );

  test(
    'late source completion after cache close skips failed cache write',
    () async {
      final root = await Directory.systemTemp.createTemp('hikari-page-late-');
      addTearDown(() => root.delete(recursive: true));
      final cache = DiskByteCache(
        cacheBaseDirectory: () async => root,
        namespaceByteBudgets: const {ReadMangaPage.cacheNamespace: 64},
        openDatabase: (path) async =>
            CacheDatabase(NativeDatabase(File('$path/index.sqlite'))),
      );
      final source = _PageSource()..holdPages = true;
      final reader = ReadMangaPage(cache);
      final pending = reader.execute(source, _page('remote', 'late'));
      await source.started.future;
      await cache.close();
      source.gates['late']!.complete(Uint8List.fromList([8]));
      expect(await pending, [8]);
      expect(
        await root
            .list(recursive: true)
            .where((entry) => entry.path.endsWith('.blob'))
            .length,
        0,
      );
    },
  );

  test('production disk cache reuses bytes after reopen', () async {
    final root = await Directory.systemTemp.createTemp('hikari-page-cache-');
    addTearDown(() => root.delete(recursive: true));
    DiskByteCache cache() => DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: const {ReadMangaPage.cacheNamespace: 1024},
      openDatabase: (path) async =>
          CacheDatabase(NativeDatabase(File('$path/index.sqlite'))),
    );
    final first = cache();
    await ReadMangaPage(first).execute(_PageSource(), _page('remote', 'disk'));
    await first.close();
    final source = _PageSource()..bytes = Uint8List.fromList([9]);
    final second = cache();
    addTearDown(second.close);
    expect(
      await ReadMangaPage(second).execute(source, _page('remote', 'disk')),
      [1, 2, 3],
    );
    expect(source.reads, 0);
  });

  test('page budget evicts independently from artwork budget', () async {
    final root = await Directory.systemTemp.createTemp('hikari-page-budget-');
    addTearDown(() => root.delete(recursive: true));
    final cache = DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: const {
        ReadMangaPage.cacheNamespace: 3,
        'source-artwork-v1': 3,
      },
      openDatabase: (path) async =>
          CacheDatabase(NativeDatabase(File('$path/index.sqlite'))),
    );
    addTearDown(cache.close);
    final reader = ReadMangaPage(cache);
    await reader.execute(_PageSource(), _page('remote', 'one'));
    await cache.write(
      'source-artwork-v1',
      'cover',
      Uint8List.fromList([9, 9, 9]),
    );
    await reader.execute(
      _PageSource(bytes: Uint8List.fromList([4, 5, 6])),
      _page('remote', 'two'),
    );
    expect(await cache.read('source-artwork-v1', 'cover'), [9, 9, 9]);
    expect(await cache.sizeBytes(ReadMangaPage.cacheNamespace), 3);
  });
}

SourceMediaRef _page(String source, String item) =>
    SourceMediaRef(sourceId: SourceId(source), itemId: item);

final class _PageSource implements MangaPageSource {
  _PageSource({SourceId? id, Uint8List? bytes})
    : id = id ?? const SourceId('remote'),
      bytes = bytes ?? Uint8List.fromList([1, 2, 3]);

  @override
  final SourceId id;
  @override
  String get name => 'Page source';
  Uint8List bytes;
  int reads = 0;
  bool failNext = false;
  bool holdPages = false;
  final started = Completer<void>();
  final gates = <String, Completer<Uint8List>>{};

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    reads++;
    if (!started.isCompleted) started.complete();
    if (failNext) {
      failNext = false;
      throw StateError('source read failed');
    }
    if (holdPages) {
      final wait = Completer<Uint8List>();
      gates[page.itemId] = wait;
      return wait.future;
    }
    return bytes;
  }
}

final class _MemoryCache implements ByteCache {
  final values = <String, Uint8List>{};
  int reads = 0;
  int writes = 0;
  bool failRead = false;
  bool failWrite = false;
  @override
  Future<Uint8List?> read(String namespace, String key) async {
    reads++;
    if (failRead) throw StateError('cache read failed');
    return values['$namespace/$key'];
  }

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {
    writes++;
    if (failWrite) throw StateError('cache write failed');
    values['$namespace/$key'] = bytes;
  }

  @override
  Future<void> close() async {}
}
