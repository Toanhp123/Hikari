import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/prefetch_manga_pages.dart';
import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';

void main() {
  final pages = List.generate(8, (i) => _page('$i'));

  test('reads only next two pages in order', () async {
    final source = _PageSource();
    await PrefetchMangaPages(ReadMangaPage(_Cache())).execute(source, pages, 0);
    expect(source.reads, ['1', '2']);
  });

  test('beginning, end, single, empty and invalid bounds', () async {
    for (final (length, index, expected) in [
      (3, 0, ['1', '2']),
      (3, 1, ['2']),
      (3, 2, <String>[]),
      (1, 0, <String>[]),
      (0, 0, <String>[]),
      (3, -1, <String>[]),
      (3, 3, <String>[]),
    ]) {
      final source = _PageSource();
      await PrefetchMangaPages(ReadMangaPage(_Cache()))
          .execute(source, pages.take(length).toList(), index);
      expect(source.reads, expected);
    }
  });

  test('speculative reads never overlap while first read is blocked', () async {
    final source = _PageSource()..holdFirst = true;
    final prefetch = PrefetchMangaPages(ReadMangaPage(_Cache()));
    final pending = prefetch.execute(source, pages, 0);
    await source.started.future;
    expect(source.reads, ['1']);
    expect(source.active, 1);
    source.gate.complete(Uint8List.fromList([1]));
    await pending;
    expect(source.reads, ['1', '2']);
    expect(source.maxActive, 1);
  });

  test(
    'latest display supersedes active remainder and queued windows',
    () async {
      final source = _PageSource()..holdFirst = true;
      final prefetch = PrefetchMangaPages(ReadMangaPage(_Cache()));
      final first = prefetch.execute(source, pages, 0);
      await source.started.future;
      final stale = prefetch.execute(source, pages, 2);
      final newest = prefetch.execute(source, pages, 5);
      expect(source.reads, ['1']);
      source.gate.complete(Uint8List.fromList([1]));
      await Future.wait([first, stale, newest]);
      expect(source.reads, ['1', '6', '7']);
      expect(source.maxActive, 1);
    },
  );

  test(
    'cancel skips queued work, lets active finish, permits later request',
    () async {
      final source = _PageSource()..holdFirst = true;
      final prefetch = PrefetchMangaPages(ReadMangaPage(_Cache()));
      final first = prefetch.execute(source, pages, 0);
      await source.started.future;
      final queued = prefetch.execute(source, pages, 3);
      prefetch.cancelPending();
      expect(source.active, 1);
      source.gate.complete(Uint8List.fromList([1]));
      await Future.wait([first, queued]);
      expect(source.reads, ['1']);
      await prefetch.execute(source, pages, 5);
      expect(source.reads, ['1', '6', '7']);
    },
  );

  test(
    'foreground coalesces same page and bypasses speculative queue',
    () async {
      final source = _PageSource()..holdFirst = true;
      final reader = ReadMangaPage(_Cache());
      final prefetch = PrefetchMangaPages(reader);
      final pending = prefetch.execute(source, pages, 0);
      await source.started.future;
      final samePage = reader.execute(source, pages[1]);
      await reader.execute(source, pages[5]);
      expect(source.reads, ['1', '5']);
      source.gate.complete(Uint8List.fromList([1]));
      await samePage;
      await pending;
      expect(source.reads, ['1', '5', '2']);
    },
  );

  test(
    'failures are swallowed without retries and later requests work',
    () async {
      final source = _PageSource()..failAt = '1';
      final prefetch = PrefetchMangaPages(ReadMangaPage(_Cache()));
      await prefetch.execute(source, pages, 0);
      expect(source.reads, ['1', '2']);
      source.failAt = null;
      await prefetch.execute(source, pages, 0);
      expect(source.reads, ['1', '2', '1']);
    },
  );

  test(
    'cached next page needs no source read and foreground reuses warmed bytes',
    () async {
      final source = _PageSource();
      final cache = _Cache();
      final reader = ReadMangaPage(cache);
      await reader.execute(source, pages[1]);
      source.reads.clear();
      await PrefetchMangaPages(reader).execute(source, pages, 0);
      expect(source.reads, ['2']);
      await reader.execute(source, pages[2]);
      expect(source.reads, ['2']);
    },
  );
}

SourceMediaRef _page(String item) =>
    SourceMediaRef(sourceId: const SourceId('remote'), itemId: item);

final class _PageSource implements MangaPageSource {
  final reads = <String>[];
  bool holdFirst = false;
  String? failAt;
  int active = 0;
  int maxActive = 0;
  final started = Completer<void>();
  final gate = Completer<Uint8List>();
  @override
  SourceId get id => const SourceId('remote');
  @override
  String get name => 'remote';
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => [];
  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    reads.add(page.itemId);
    active++;
    if (active > maxActive) maxActive = active;
    try {
      if (holdFirst && reads.length == 1) {
        started.complete();
        return await gate.future;
      }
      if (page.itemId == failAt) throw StateError('offline');
      return Uint8List.fromList([1]);
    } finally {
      active--;
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
