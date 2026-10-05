import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/media/read_novel_resource.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';
import 'package:hikari/infrastructure/cache/disk_byte_cache.dart';

void main() {
  test('chapter and resource cache persist across disk-cache reopen', () async {
    final directory = await Directory.systemTemp.createTemp(
      'hikari-novel-cache',
    );
    addTearDown(() => directory.delete(recursive: true));
    DiskByteCache open() => DiskByteCache(
      cacheBaseDirectory: () async => directory,
      namespaceByteBudgets: const {
        ReadNovelChapterContent.cacheNamespace: 1024,
        ReadNovelResource.cacheNamespace: 1024,
      },
      openDatabase: (path) async =>
          CacheDatabase(NativeDatabase(File('$path/index.sqlite'))),
    );
    final first = open();
    final source = _Source()..resourceBytes = Uint8List.fromList([4, 5]);
    await ReadNovelChapterContent(first).execute(source, _ref('disk-chapter'));
    await ReadNovelResource(first).execute(source, _ref('disk-resource'));
    await first.close();
    final second = open();
    addTearDown(second.close);
    final reopenedSource = _Source();
    expect(
      (await ReadNovelChapterContent(
        second,
      ).execute(reopenedSource, _ref('disk-chapter'))).html,
      contains('chapter 1'),
    );
    expect(
      await ReadNovelResource(second)
          .execute(reopenedSource, _ref('disk-resource')),
      [4, 5],
    );
    expect(reopenedSource.chapterReads, 0);
    expect(reopenedSource.resourceReads, 0);
  });

  test('chapter rejects malformed fields, ownership, and version as misses', () async {
    final cache = _Cache();
    final source = _Source();
    final reader = ReadNovelChapterContent(cache);
    final chapter = _ref('bad-json');
    final key =
        '${ReadNovelChapterContent.cacheNamespace}/${ReadNovelChapterContent.cacheKey(chapter)}';
    final malformed = <Object>[
      '{',
      jsonEncode({
        'version': 2,
        'sourceId': 'novel',
        'itemId': 'bad-json',
        'html': '<p>cached</p>',
        'resources': <String, dynamic>{},
      }),
      jsonEncode({
        'version': 1,
        'sourceId': 'novel',
        'itemId': 'bad-json',
        'html': 3,
        'resources': <String, dynamic>{},
      }),
      jsonEncode({
        'version': 1,
        'sourceId': 'novel',
        'itemId': 'bad-json',
        'html': '<p>cached</p>',
        'resources': {
          'img': {'sourceId': 'foreign', 'itemId': 'x'},
        },
      }),
      jsonEncode({
        'version': 1,
        'sourceId': 'novel',
        'itemId': 'bad-json',
        'html': '<p>cached</p>',
        'resources': {
          'img': {'sourceId': 42, 'itemId': 'x'},
        },
      }),
    ];
    for (final payload in malformed) {
      cache.values[key] = Uint8List.fromList(utf8.encode(payload.toString()));
      final reads = source.chapterReads;
      await reader.execute(source, chapter);
      expect(source.chapterReads, reads + 1);
    }
  });

  test('reload failure preserves cached values and ordinary reads join reload', () async {
    final cache = _Cache();
    final source = _Source()..holdChapter = true;
    final reader = ReadNovelChapterContent(cache);
    final chapter = _ref('reload-race');
    final reload = reader.reload(source, chapter);
    await source.waitForChapterReads(1);
    final ordinary = reader.execute(source, chapter);
    source.chapterGates
        .removeAt(0)
        .complete(RichReadingContent(html: '<p>fresh</p>'));
    expect((await reload).html, contains('fresh'));
    expect((await ordinary).html, contains('fresh'));
    expect(source.chapterReads, 1);

    final failureSource = _Source()..failChapter = true;
    final cachedReader = ReadNovelChapterContent(cache);
    await cachedReader.execute(source, chapter);
    final before = cache
        .values['${ReadNovelChapterContent.cacheNamespace}/${ReadNovelChapterContent.cacheKey(chapter)}']!;
    await expectLater(
      cachedReader.reload(failureSource, chapter),
      throwsStateError,
    );
    expect(
      cache
          .values['${ReadNovelChapterContent.cacheNamespace}/${ReadNovelChapterContent.cacheKey(chapter)}'],
      before,
    );
  });

  test('resource reload failure preserves old bytes; source errors retry', () async {
    final cache = _Cache();
    final source = _Source()..resourceBytes = Uint8List.fromList([7]);
    final reader = ReadNovelResource(cache);
    final resource = _ref('resource-reload');
    await reader.execute(source, resource);
    final previous = cache
        .values['${ReadNovelResource.cacheNamespace}/${ReadNovelResource.cacheKey(resource)}']!;
    final broken = _Source()..failResource = true;
    await expectLater(reader.reload(broken, resource), throwsStateError);
    expect(
      cache
          .values['${ReadNovelResource.cacheNamespace}/${ReadNovelResource.cacheKey(resource)}'],
      previous,
    );
    expect(await reader.reload(source, resource), [7]);
    expect(
      cache.values.keys
          .where((key) => key.startsWith(ReadNovelResource.cacheNamespace))
          .length,
      1,
    );
  });

  test('independent novel content and resource namespaces', () async {
    final cache = _Cache();
    final source = _Source();
    await ReadNovelChapterContent(cache).execute(source, _ref('same'));
    await ReadNovelResource(cache).execute(source, _ref('same'));
    expect(cache.values.keys.toSet(), {
      '${ReadNovelChapterContent.cacheNamespace}/${ReadNovelChapterContent.cacheKey(_ref('same'))}',
      '${ReadNovelResource.cacheNamespace}/${ReadNovelResource.cacheKey(_ref('same'))}',
    });
  });

  test(
    'chapter coalesces same ref while different refs progress independently',
    () async {
      final cache = _Cache();
      final source = _Source()..holdChapter = true;
      final reader = ReadNovelChapterContent(cache);
      final one = reader.execute(source, _ref('one'));
      final same = reader.execute(source, _ref('one'));
      final other = reader.execute(source, _ref('two'));
      await source.waitForChapterReads(2);
      expect(source.chapterReads, 2);
      source.chapterGates
          .removeAt(1)
          .complete(RichReadingContent(html: '<p>two</p>'));
      source.chapterGates
          .removeAt(0)
          .complete(RichReadingContent(html: '<p>one</p>'));
      expect((await one).html, contains('one'));
      expect((await same).html, contains('one'));
      expect((await other).html, contains('two'));
    },
  );

  test('chapter cache read/write failures and resource source errors fail open/retry', () async {
    final cache = _Cache()..failRead = true;
    final source = _Source()..failResource = true;
    final chapterReader = ReadNovelChapterContent(cache);
    await chapterReader.execute(source, _ref('cache-fail'));
    cache.failRead = false;
    cache.failWrite = true;
    await chapterReader.execute(source, _ref('write-fail'));
    final resourceReader = ReadNovelResource(cache);
    await expectLater(
      resourceReader.execute(source, _ref('resource-retry')),
      throwsStateError,
    );
    cache.failWrite = false;
    expect(await resourceReader.execute(source, _ref('resource-retry')), [0]);
  });

  test(
    'resource reload waits for old read and replaces cache after success',
    () async {
      final cache = _Cache();
      final source = _Source()..holdResource = true;
      final reader = ReadNovelResource(cache);
      final resource = _ref('resource-race');
      final old = reader.execute(source, resource);
      await source.waitForResourceReads(1);
      final fresh = reader.reload(source, resource);
      source.holdResource = false;
      source.resourceBytes = Uint8List.fromList([2]);
      source.resourceGates.removeAt(0).complete(Uint8List.fromList([1]));
      expect(await old, [1]);
      await source.waitForResourceReads(2);
      source.resourceBytes = Uint8List.fromList([2]);
      expect(await fresh, [2]);
      expect(await reader.execute(source, resource), [2]);
      expect(source.resourceReads, 2);
    },
  );

  test('chapter caches normalized payload, hit and malformed data are misses', () async {
    final cache = _Cache();
    final source = _Source();
    final reader = ReadNovelChapterContent(cache);
    final chapter = _ref('chapter');
    final first = await reader.execute(source, chapter);
    expect(first.html, contains('chapter 1'));
    expect(
      (jsonDecode(utf8.decode(cache.values.values.single)) as Map)['version'],
      1,
    );
    await reader.execute(source, chapter);
    expect(source.chapterReads, 1);
    cache.values['${ReadNovelChapterContent.cacheNamespace}/${ReadNovelChapterContent.cacheKey(chapter)}'] =
        Uint8List.fromList(utf8.encode('{'));
    await reader.execute(source, chapter);
    expect(source.chapterReads, 2);
  });

  test('blank cached entities are misses and blank fresh HTML is not cached', () async {
    final cache = _Cache();
    final source = _Source();
    final reader = ReadNovelChapterContent(cache);
    final chapter = _ref('blank-cache');
    final key =
        '${ReadNovelChapterContent.cacheNamespace}/${ReadNovelChapterContent.cacheKey(chapter)}';
    for (final blank in ['<p>&nbsp;</p>', '<p>&#0160;</p>']) {
      cache.values[key] = Uint8List.fromList(
        utf8.encode(
          jsonEncode({
            'version': 1,
            'sourceId': chapter.sourceId.value,
            'itemId': chapter.itemId,
            'html': blank,
            'resources': <String, dynamic>{},
          }),
        ),
      );
      final reads = source.chapterReads;
      await reader.execute(source, chapter);
      expect(source.chapterReads, reads + 1);
    }
    expect(cache.writes, 2);
    source.result = RichReadingContent(html: '<p> \n <b></b> </p>');
    await reader.execute(source, _ref('empty'));
    expect(cache.writes, 2);
  });

  test('image-only rich chapter is cacheable', () async {
    final cache = _Cache();
    final source = _Source();
    final reader = ReadNovelChapterContent(cache);
    final chapter = _ref('illustration-only');
    final image = _ref('illustration');
    source.result = RichReadingContent(
      html: '<figure><img src="image"></figure>',
      resources: {'image': image},
    );

    final first = await reader.execute(source, chapter);
    expect(first.resources, {'image': image});
    expect(cache.writes, 1);

    source.result = RichReadingContent(html: '<p>network should not run</p>');
    final cached = await reader.execute(source, chapter);
    expect(cached.resources, {'image': image});
    expect(source.chapterReads, 1);
  });

  test('chapter rejects foreign refs and resources', () async {
    final cache = _Cache();
    final source = _Source();
    final reader = ReadNovelChapterContent(cache);
    await expectLater(
      reader.execute(source, _ref('chapter', 'other')),
      throwsStateError,
    );
    source.result = RichReadingContent(
      html: '<p>ok</p>',
      resources: {'img': _ref('foreign', 'elsewhere')},
    );
    await expectLater(
      reader.execute(source, _ref('foreign-resource')),
      throwsStateError,
    );
    expect(cache.writes, 0);
  });

  test('chapter reload waits for prior read, then replaces cache', () async {
    final cache = _Cache();
    final source = _Source()..holdChapter = true;
    final reader = ReadNovelChapterContent(cache);
    final chapter = _ref('race');
    final first = reader.execute(source, chapter);
    await source.waitForChapterReads(1);
    final reload = reader.reload(source, chapter);
    final sameReload = reader.reload(source, chapter);
    source.chapterGates
        .removeAt(0)
        .complete(RichReadingContent(html: '<p>old</p>'));
    await first;
    await source.waitForChapterReads(2);
    source.chapterGates
        .removeAt(0)
        .complete(RichReadingContent(html: '<p>new</p>'));
    expect((await reload).html, contains('new'));
    expect((await sameReload).html, contains('new'));
    expect((await reader.execute(source, chapter)).html, contains('new'));
    expect(source.chapterReads, 2);
  });

  test('chapter source errors retry and cache failures fail open', () async {
    final cache = _Cache()..failRead = true;
    final source = _Source()..failChapter = true;
    final reader = ReadNovelChapterContent(cache);
    await expectLater(reader.execute(source, _ref('retry')), throwsStateError);
    cache.failRead = false;
    cache.failWrite = true;
    expect(
      (await reader.execute(source, _ref('retry'))).html,
      contains('chapter 1'),
    );
    expect(source.chapterReads, 2);
  });

  test(
    'resource hit, empty rejection, coalescing and reload fail-open',
    () async {
      final cache = _Cache();
      final source = _Source()..holdResource = true;
      final reader = ReadNovelResource(cache);
      final resource = _ref('image');
      final first = reader.execute(source, resource);
      final same = reader.execute(source, resource);
      await source.waitForResourceReads(1);
      source.resourceGates.removeAt(0).complete(Uint8List.fromList([1]));
      source.holdResource = false;
      expect(await first, [1]);
      expect(await same, [1]);
      expect(await reader.execute(source, resource), [1]);
      expect(source.resourceReads, 1);
      source.resourceBytes = Uint8List.fromList([2]);
      expect(await reader.reload(source, resource), [2]);
      source.resourceBytes = Uint8List(0);
      await reader.reload(source, _ref('blank'));
      expect(cache.writes, 2);
      await expectLater(
        reader.execute(source, _ref('foreign', 'other')),
        throwsStateError,
      );
      cache.failRead = true;
      expect(await reader.execute(source, _ref('read-fail')), isEmpty);
    },
  );
}

SourceMediaRef _ref(String item, [String source = 'novel']) =>
    SourceMediaRef(sourceId: SourceId(source), itemId: item);

final class _Source implements NovelChapterSource {
  @override
  final SourceId id = const SourceId('novel');
  @override
  String get name => 'test';
  int chapterReads = 0;
  int resourceReads = 0;
  bool failChapter = false;
  bool failResource = false;
  bool holdChapter = false;
  bool holdResource = false;
  RichReadingContent result = RichReadingContent(html: '<p>chapter 1</p>');
  Uint8List resourceBytes = Uint8List.fromList([0]);
  final chapterGates = <Completer<RichReadingContent>>[];
  final resourceGates = <Completer<Uint8List>>[];
  final _chapterWaiters = <int, Completer<void>>{};
  final _resourceWaiters = <int, Completer<void>>{};
  Future<void> waitForChapterReads(int count) =>
      _wait(chapterReads, count, _chapterWaiters);
  Future<void> waitForResourceReads(int count) =>
      _wait(resourceReads, count, _resourceWaiters);

  Future<void> _wait(
    int count,
    int wanted,
    Map<int, Completer<void>> waiters,
  ) => count >= wanted
      ? Future.value()
      : (waiters[wanted] ??= Completer<void>()).future;

  void _signal(int count, Map<int, Completer<void>> waiters) {
    for (final key in waiters.keys.toList()) {
      if (count >= key) waiters.remove(key)!.complete();
    }
  }

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async {
    chapterReads++;
    _signal(chapterReads, _chapterWaiters);
    if (failChapter) {
      failChapter = false;
      throw StateError('offline');
    }
    if (!holdChapter) return result;
    final gate = Completer<RichReadingContent>();
    chapterGates.add(gate);
    return gate.future;
  }

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async {
    resourceReads++;
    _signal(resourceReads, _resourceWaiters);
    if (failResource) {
      failResource = false;
      throw StateError('offline');
    }
    if (!holdResource) return resourceBytes;
    final gate = Completer<Uint8List>();
    resourceGates.add(gate);
    return gate.future;
  }
}

final class _Cache implements ByteCache {
  final values = <String, Uint8List>{};
  int writes = 0;
  bool failRead = false;
  bool failWrite = false;
  @override
  Future<Uint8List?> read(String namespace, String key) async {
    if (failRead) throw StateError('read');
    return values['$namespace/$key'];
  }

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {
    writes++;
    if (failWrite) throw StateError('write');
    values['$namespace/$key'] = Uint8List.fromList(bytes);
  }

  @override
  Future<void> close() async {}
}
