import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/prefetch_manga_chapter.dart';
import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';

const _sourceId = SourceId('manga');

void main() {
  test(
    'warms the next chapter page list and first two page bytes serially',
    () async {
      final source = _Source();
      final prefetch = _prefetch(source, _Cache());

      await prefetch.execute(_chapter('next'));

      expect(source.pageLists, ['next']);
      expect(source.pageReads, ['next-0', 'next-1']);
      expect(source.maxActivePageReads, 1);
    },
  );

  test('foreground joins an active speculative page-list request', () async {
    final source = _Source()..holdPageList = 'next';
    final prefetch = _prefetch(source, _Cache());

    final speculative = prefetch.execute(_chapter('next'));
    await source.pageListStarted.future;
    final foreground = prefetch.loadPages(source, _ref('next'));

    expect(source.pageLists, ['next']);
    source.pageListGate.complete([_ref('next-0'), _ref('next-1')]);
    expect(await foreground, [_ref('next-0'), _ref('next-1')]);
    await speculative;
    expect(source.pageLists, ['next']);
  });

  test('warmed page bytes are reused by foreground reads', () async {
    final cache = _Cache();
    final source = _Source();
    final reader = ReadMangaPage(cache);
    final prefetch = PrefetchMangaChapter(SourceRegistry([source]), reader);

    await prefetch.execute(_chapter('next'));
    source.pageReads.clear();
    final pages = await prefetch.loadPages(source, _ref('next'));
    await reader.execute(source, pages.first);

    expect(source.pageLists, ['next']);
    expect(source.pageReads, isEmpty);
  });

  test(
    'empty speculative page list is not handed off to foreground open',
    () async {
      var call = 0;
      final source = _Source()
        ..pagesFor = (_) async {
          call++;
          return call == 1 ? <SourceMediaRef>[] : [_ref('foreground-page')];
        };
      final prefetch = _prefetch(source, _Cache());

      await prefetch.execute(_chapter('next'));
      final pages = await prefetch.loadPages(source, _ref('next'));

      expect(source.pageLists, ['next', 'next']);
      expect(pages, [_ref('foreground-page')]);
    },
  );

  test(
    'invalid speculative page ownership is retried by foreground open',
    () async {
      var call = 0;
      final source = _Source()
        ..pagesFor = (_) async {
          call++;
          if (call == 1) {
            return const [
              SourceMediaRef(sourceId: SourceId('foreign'), itemId: 'bad'),
            ];
          }
          return [_ref('foreground-page')];
        };
      final prefetch = _prefetch(source, _Cache());

      await prefetch.execute(_chapter('next'));
      final pages = await prefetch.loadPages(source, _ref('next'));

      expect(source.pageLists, ['next', 'next']);
      expect(pages, [_ref('foreground-page')]);
      expect(source.pageReads, isEmpty);
    },
  );

  test('empty request does not cancel active valid chapter', () async {
    final source = _Source()..holdPageRead = 'next-0';
    final prefetch = _prefetch(source, _Cache());

    final pending = prefetch.execute(_chapter('next'));
    await source.pageReadStarted.future;
    await prefetch.execute(_chapter(''));
    source.pageReadGate.complete(Uint8List.fromList([1]));
    await pending;

    expect(source.pageReads, ['next-0', 'next-1']);
  });

  test('new chapter supersedes stale byte remainder', () async {
    final source = _Source()..holdPageRead = 'old-0';
    final prefetch = _prefetch(source, _Cache());

    final old = prefetch.execute(_chapter('old'));
    await source.pageReadStarted.future;
    prefetch.discard();
    final newest = prefetch.execute(_chapter('new'));
    source.pageReadGate.complete(Uint8List.fromList([1]));
    await Future.wait([old, newest]);

    expect(source.pageReads, ['old-0', 'new-0', 'new-1']);
    expect(source.pageLists, ['old', 'new']);
  });

  test('cancel stops byte remainder but preserves page-list handoff', () async {
    final source = _Source()..holdPageRead = 'next-0';
    final prefetch = _prefetch(source, _Cache());

    final pending = prefetch.execute(_chapter('next'));
    await source.pageReadStarted.future;
    prefetch.cancelPending();
    source.pageReadGate.complete(Uint8List.fromList([1]));
    await pending;

    expect(source.pageReads, ['next-0']);
    expect(await prefetch.loadPages(source, _ref('next')), [
      _ref('next-0'),
      _ref('next-1'),
      _ref('next-2'),
    ]);
    expect(source.pageLists, ['next']);
  });

  test(
    'discard removes stale handoff without retrying speculative bytes',
    () async {
      final source = _Source();
      final prefetch = _prefetch(source, _Cache());

      await prefetch.execute(_chapter('next'));
      prefetch.discard();
      final pages = await prefetch.loadPages(source, _ref('next'));

      expect(source.pageLists, ['next', 'next']);
      expect(pages, [_ref('next-0'), _ref('next-1'), _ref('next-2')]);
    },
  );
}

PrefetchMangaChapter _prefetch(_Source source, _Cache cache) =>
    PrefetchMangaChapter(SourceRegistry([source]), ReadMangaPage(cache));

MangaChapter _chapter(String itemId) =>
    MangaChapter(title: itemId, source: _ref(itemId));

SourceMediaRef _ref(String itemId) =>
    SourceMediaRef(sourceId: _sourceId, itemId: itemId);

final class _Source implements MangaPageSource {
  final List<String> pageLists = [];
  final List<String> pageReads = [];
  Future<List<SourceMediaRef>> Function(SourceMediaRef chapter)? pagesFor;
  String? holdPageList;
  String? holdPageRead;
  int activePageReads = 0;
  int maxActivePageReads = 0;
  final pageListStarted = Completer<void>();
  final pageListGate = Completer<List<SourceMediaRef>>();
  final pageReadStarted = Completer<void>();
  final pageReadGate = Completer<Uint8List>();

  @override
  SourceId get id => _sourceId;

  @override
  String get name => 'Manga';

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef chapter) async {
    pageLists.add(chapter.itemId);
    if (chapter.itemId == holdPageList) {
      if (!pageListStarted.isCompleted) pageListStarted.complete();
      return pageListGate.future;
    }
    final custom = pagesFor;
    if (custom != null) return custom(chapter);
    return [
      _ref('${chapter.itemId}-0'),
      _ref('${chapter.itemId}-1'),
      _ref('${chapter.itemId}-2'),
    ];
  }

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    pageReads.add(page.itemId);
    activePageReads++;
    if (activePageReads > maxActivePageReads) {
      maxActivePageReads = activePageReads;
    }
    try {
      if (page.itemId == holdPageRead) {
        if (!pageReadStarted.isCompleted) pageReadStarted.complete();
        return await pageReadGate.future;
      }
      return Uint8List.fromList([1]);
    } finally {
      activePageReads--;
    }
  }
}

final class _Cache implements ByteCache {
  final values = <String, Uint8List>{};

  @override
  Future<Uint8List?> read(String namespace, String key) async => values[key];

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {
    values[key] = bytes;
  }

  @override
  Future<void> close() async {}
}
