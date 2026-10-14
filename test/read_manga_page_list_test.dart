import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/read_manga_page_list.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/open_series_continuation.dart';
import 'package:hikari/application/media/prefetch_manga_chapter.dart';
import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';
import 'package:hikari/infrastructure/cache/disk_byte_cache.dart';
import 'package:path/path.dart' as p;

const _id = SourceId('remote-manga');
const _chapter = SourceMediaRef(sourceId: _id, itemId: 'chapter');
SourceMediaRef _page(String name) =>
    SourceMediaRef(sourceId: _id, itemId: name);

void main() {
  test('persisted page list opens offline after disk cache restart', () async {
    final root = await Directory.systemTemp.createTemp('hikari-page-list-');
    addTearDown(() => root.delete(recursive: true));
    DiskByteCache makeCache() => DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: const {
        ReadMangaPageList.cacheNamespace: 1024 * 1024,
        ReadMangaPage.cacheNamespace: 1024 * 1024,
      },
      openDatabase: (path) async =>
          CacheDatabase(NativeDatabase(File(p.join(path, 'index.sqlite')))),
    );

    final source = _Source()..result = [_page('one'), _page('two')];
    final first = makeCache();
    expect(
      await ReadMangaPageList(first).execute(source, _chapter),
      source.result,
    );
    expect(await ReadMangaPage(first).execute(source, _page('one')), [1]);
    await first.close();

    source.offline = true;
    final reopened = makeCache();
    addTearDown(reopened.close);
    expect(await ReadMangaPageList(reopened).execute(source, _chapter), [
      _page('one'),
      _page('two'),
    ]);
    expect(source.calls, 1);
    expect(await ReadMangaPage(reopened).execute(source, _page('one')), [1]);

    // Continue opens the persisted chapter directly. Details must not be
    // fetched before the cached page list, even when the source is offline.
    const seriesRef = SourceMediaRef(sourceId: _id, itemId: 'series');
    const media = Media(
      title: 'Series',
      type: MediaType.manga,
      source: seriesRef,
    );
    final registry = SourceRegistry([source]);
    final resume = await OpenSeriesContinuation(
      OpenMedia(registry, _ProgressRepository()),
    ).execute(media, _chapter);
    final reader = await OpenMangaChapter(
      registry,
      _ProgressRepository(),
      ReadMangaPageList(reopened),
    ).execute((resume as MangaContinuationOpenTarget).selected);
    expect(reader.pages, [_page('one'), _page('two')]);
    expect(source.detailsCalls, 0);
    expect(
      (await ReadMangaPage(reopened).execute(source, reader.pages.first)),
      [1],
    );
    await resume.release();

    await reopened.clear(ReadMangaPageList.cacheNamespace);
    await expectLater(
      ReadMangaPageList(reopened).execute(source, _chapter),
      throwsStateError,
    );
  });

  test('refresh does not replace previous snapshot until committed', () async {
    final cache = _MemoryCache();
    final source = _Source()..result = [_page('old')];
    final reader = ReadMangaPageList(cache);
    expect(await reader.execute(source, _chapter), [_page('old')]);

    source.result = [_page('new'), _page('old')];
    final candidate = await reader.fetchFresh(source, _chapter);
    source.offline = true;
    expect(await reader.execute(source, _chapter), [_page('old')]);
    await reader.persist(source, _chapter, candidate);
    expect(await reader.execute(source, _chapter), candidate);
    expect(source.calls, 2);
  });

  test(
    'corrupt snapshot and foreign page list cannot masquerade as valid',
    () async {
      final cache = _MemoryCache();
      final source = _Source()..result = [_page('valid')];
      final reader = ReadMangaPageList(cache);
      await cache.write(
        ReadMangaPageList.cacheNamespace,
        '["remote-manga","chapter"]',
        Uint8List.fromList([1, 2, 3]),
      );
      expect(await reader.execute(source, _chapter), source.result);
      expect(source.calls, 1);

      source.result = [
        const SourceMediaRef(sourceId: SourceId('other'), itemId: 'bad'),
      ];
      await expectLater(reader.fetchFresh(source, _chapter), throwsStateError);
      source.offline = true;
      expect(await reader.execute(source, _chapter), [_page('valid')]);
    },
  );

  test('explicit freshness waits for an older in-flight cache miss', () async {
    final cache = _MemoryCache();
    final source = _Source();
    final gate = Completer<List<SourceMediaRef>>();
    source.pageListOverride = () => gate.future;
    final reader = ReadMangaPageList(cache);
    final original = reader.execute(source, _chapter);
    await source.pageListStarted.future;
    source.pageListOverride = () async => [_page('new')];
    final refreshing = reader.fetchFresh(source, _chapter);
    gate.complete([_page('old')]);
    expect(await original, [_page('old')]);
    expect(await refreshing, [_page('new')]);
    await reader.persist(source, _chapter, [_page('new')]);
    source.offline = true;
    expect(await reader.execute(source, _chapter), [_page('new')]);
  });

  test(
    'chapter-open and lookahead share the persisted list contract',
    () async {
      final cache = _MemoryCache();
      final source = _Source()..result = [_page('first'), _page('second')];
      final reader = ReadMangaPageList(cache);
      final prefetch = PrefetchMangaChapter(
        SourceRegistry([source]),
        ReadMangaPage(cache),
        reader,
      );
      final chapter = MangaChapter(title: 'Chapter', source: _chapter);
      await prefetch.execute(chapter);
      expect(source.calls, 1);
      final opener = OpenMangaChapter(
        SourceRegistry([source]),
        _ProgressRepository(),
        reader,
      );
      source.offline = true;
      final target = await opener.execute(
        chapter,
        pageListLoader: prefetch.loadPages,
      );
      expect(target.pages, [_page('first'), _page('second')]);
      expect(source.calls, 1);
    },
  );

  test('local page lists are never persisted', () async {
    final cache = _MemoryCache();
    final source = _Source(SourceId.local)
      ..result = [
        const SourceMediaRef(sourceId: SourceId.local, itemId: 'file'),
      ];
    final reader = ReadMangaPageList(cache);
    await reader.execute(
      source,
      const SourceMediaRef(sourceId: SourceId.local, itemId: 'folder'),
    );
    expect(cache.values, isEmpty);
  });
}

final class _Source implements MangaPageSource, MangaSeriesSource {
  _Source([this.id = _id]);
  @override
  final SourceId id;
  @override
  String get name => 'Manga';
  List<SourceMediaRef> result = [];
  bool offline = false;
  int calls = 0;
  int detailsCalls = 0;
  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef media) async {
    detailsCalls++;
    if (offline) throw StateError('offline');
    return MangaSeriesDetails(
      metadata: MediaMetadata(title: 'Series'),
      chapters: [MangaChapter(title: 'Chapter', source: _chapter)],
      chapterListOrder: ChapterListOrder.readingOrder,
    );
  }

  Future<List<SourceMediaRef>> Function()? pageListOverride;
  final pageListStarted = Completer<void>();
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef chapter) async {
    calls++;
    if (!pageListStarted.isCompleted) pageListStarted.complete();
    if (offline) throw StateError('offline');
    return pageListOverride?.call() ?? result;
  }

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    if (offline) throw StateError('offline');
    return Uint8List.fromList([1]);
  }
}

final class _MemoryCache implements ByteCache {
  final values = <String, Uint8List>{};
  String _key(String namespace, String key) => '$namespace/$key';
  @override
  Future<Uint8List?> read(String namespace, String key) async =>
      values[_key(namespace, key)];
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {
    values[_key(namespace, key)] = bytes;
  }

  @override
  Future<void> close() async {}
}

final class _ProgressRepository implements ProgressRepository {
  @override
  Future<MediaProgress?> load(SourceMediaRef media) async => null;
  @override
  Future<void> save(MediaProgress progress) async {}
  @override
  Future<void> delete(SourceMediaRef media) async {}
}
