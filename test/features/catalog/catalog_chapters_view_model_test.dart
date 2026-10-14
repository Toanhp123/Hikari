import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/read_remote_chapter_list.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/series_continuation.dart';
import 'package:hikari/features/catalog/catalog_chapters_view_model.dart';

const _id = SourceId('test');
const _ref = SourceMediaRef(sourceId: _id, itemId: 'series');
const _one = SourceMediaRef(sourceId: _id, itemId: 'one');
const _two = SourceMediaRef(sourceId: _id, itemId: 'two');
const _three = SourceMediaRef(sourceId: _id, itemId: 'three');
Media _media(MediaType type, [String item = 'series']) => Media(
  title: item,
  source: SourceMediaRef(sourceId: _id, itemId: item),
  type: type,
);

void main() {
  for (final type in [MediaType.manga, MediaType.lightNovel]) {
    test(
      '$type fresh snapshot skips unavailable source; stale revalidates and retains list on failure',
      () async {
        var now = DateTime.utc(2026);
        final source = _source(type);
        final cache = ReadRemoteChapterList(_Cache(), clock: () => now);
        final owner = _owner(source, cache);
        addTearDown(owner.dispose);
        if (type == MediaType.manga) {
          await cache.persistManga(
            _ref,
            source.manga,
            chapterListOrder: ChapterListOrder.reverseReadingOrder,
          );
        } else {
          await cache.persistNovel(
            _ref,
            source.novel,
            chapterListOrder: ChapterListOrder.reverseReadingOrder,
          );
        }
        source.available = false;
        await owner.select(_media(type), null);
        expect(owner.hasChapters, isTrue);
        expect(source.calls, 0);
        expect(source.leases, isEmpty);
        expect(owner.failed, isFalse);
        expect(owner.fromCache, isTrue);
        expect(owner.cacheStale, isFalse);
        expect(owner.fetchedAt, now);
        await owner.load(forceRefresh: true);
        expect(owner.failed, isTrue);
        expect(owner.hasChapters, isTrue);
        now = now.add(const Duration(hours: 1));
        source.available = true;
        final gate = Completer<void>();
        source.gates.add(gate);
        final load = owner.select(_media(type), null);
        await pumpEventQueue();
        expect(owner.hasChapters, isTrue);
        expect(owner.loading, isTrue);
        expect(owner.fromCache, isTrue);
        expect(owner.cacheStale, isTrue);
        expect(owner.fetchedAt, DateTime.utc(2026));
        expect(source.leases.single.released, isFalse);
        gate.completeError(StateError('offline'));
        await load;
        expect(owner.failed, isTrue);
        expect(owner.hasChapters, isTrue);
        expect(source.leases.single.released, isTrue);
        expect(owner.fromCache, isTrue);
        expect(owner.cacheStale, isTrue);
        await owner.load(forceRefresh: true);
        expect(owner.fromCache, isFalse);
        expect(owner.fetchedAt, isNull);
      },
    );

    test(
      '$type display reverse/filter never changes full canonical reader sequence or continuation',
      () async {
        final source = _source(type);
        final owner = _owner(source, ReadRemoteChapterList(_Cache()));
        addTearDown(owner.dispose);
        await owner.select(_media(type), null);
        await owner.refreshReadingTarget();
        expect(owner.primary, _two);
        expect(owner.isContinuation, isTrue);
        owner.toggleSourceOrder();
        owner.setSearchQuery('two');
        final display = type == MediaType.manga
            ? owner.displayManga.map((c) => c.source)
            : owner.displayNovel.map((c) => c.source);
        expect(display, [_two]);
        final sequence = type == MediaType.manga
            ? owner.mangaSequence.map((c) => c.source)
            : owner.novelSequence.map((c) => c.source);
        expect(sequence, [_one, _two, _three]);
        expect(owner.primary, _two);
        expect(source.calls, 1);
        await owner.refreshReadingTarget();
        expect(source.calls, 1);
        expect(owner.reverseSourceOrder, isTrue);
        expect(owner.searchQuery, 'two');
      },
    );

    test(
      '$type A/B/A loads and dispose reject stale completion, release only settled operation',
      () async {
        final source = _source(type);
        final owner = _owner(source, ReadRemoteChapterList(_Cache()));
        final a = Completer<void>();
        final b = Completer<void>();
        final latest = Completer<void>();
        source.gates.addAll([a, b, latest]);
        final first = owner.select(_media(type), null);
        await pumpEventQueue();
        final second = owner.select(_media(type, 'b'), null);
        await pumpEventQueue();
        final third = owner.select(_media(type), null);
        await pumpEventQueue();
        expect(source.leases.every((lease) => !lease.released), isTrue);
        a.complete();
        await first;
        expect(owner.loading, isTrue);
        expect(owner.hasChapters, isFalse);
        expect(source.leases[0].released, isTrue);
        expect(source.leases[1].released, isFalse);
        owner.dispose();
        expect(source.leases[2].released, isFalse);
        b.complete();
        latest.complete();
        await Future.wait([second, third]);
        expect(source.leases.every((lease) => lease.released), isTrue);
        expect(owner.hasChapters, isFalse);
      },
    );

    test(
      '$type delayed reader A/B/A cannot navigate or clear newer opening',
      () async {
        final source = _source(type);
        final owner = _owner(source, ReadRemoteChapterList(_Cache()));
        await owner.select(_media(type), null);
        final oldGate = Completer<void>();
        final newGate = Completer<void>();
        var navigations = 0;
        final old = owner.open((selected, current) async {
          final target = await owner.openMedia.execute(selected);
          try {
            await oldGate.future;
            if (current()) navigations++;
          } finally {
            await target.release();
          }
        });
        await pumpEventQueue();
        await owner.select(_media(type, 'b'), null);
        await owner.select(_media(type), null);
        final active = owner.open((selected, current) async {
          final target = await owner.openMedia.execute(selected);
          try {
            await newGate.future;
            if (current()) navigations++;
          } finally {
            await target.release();
          }
        });
        await pumpEventQueue();
        oldGate.complete();
        await old;
        expect(owner.opening, isTrue);
        expect(navigations, 0);
        expect(source.leases.last.released, isFalse);
        owner.dispose();
        expect(source.leases.last.released, isFalse);
        newGate.complete();
        await active;
        expect(navigations, 0);
        expect(source.leases.every((lease) => lease.released), isTrue);
      },
    );
  }
}

CatalogChaptersViewModel _owner(_Source source, ReadRemoteChapterList cache) =>
    CatalogChaptersViewModel(
      openMedia: OpenMedia(
        SourceRegistry([source]),
        _Progress(),
        chapterLists: cache,
      ),
      chapterLists: cache,
      loadReadingTarget: LoadSeriesReadingTarget(_Continuation()),
    );

final class _Cache implements ByteCache {
  final data = <String, Uint8List>{};
  @override
  Future<Uint8List?> read(String namespace, String key) async =>
      data['$namespace:$key'];
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {
    data['$namespace:$key'] = bytes;
  }

  @override
  Future<void> close() async {}
}

final class _Progress implements ProgressRepository {
  @override
  Future<MediaProgress?> load(SourceMediaRef media) async => null;
  @override
  Future<void> save(MediaProgress progress) async {}
  @override
  Future<void> delete(SourceMediaRef media) async {}
}

final class _Continuation implements SeriesContinuationRepository {
  @override
  Future<SourceMediaRef?> load(SourceMediaRef series) async => _two;
  @override
  Future<void> save(SeriesContinuation continuation) async {}
}

final class _Lease implements MediaOpenLease {
  bool released = false;
  @override
  Future<void> release() async {
    expect(released, isFalse);
    released = true;
  }
}

_Source _source(MediaType type) =>
    type == MediaType.manga ? _MangaSource() : _NovelSource();

abstract class _Source
    implements MediaSource, MediaOpenLeaseSource, MediaSourceAvailability {
  bool available = true;
  int calls = 0;
  final gates = <Completer<void>>[];
  final leases = <_Lease>[];
  final manga = [
    for (final (ref, title) in [
      (_one, 'one'),
      (_two, 'two'),
      (_three, 'three'),
    ])
      MangaChapter(title: title, source: ref),
  ];
  final novel = [
    for (final (ref, title) in [
      (_one, 'one'),
      (_two, 'two'),
      (_three, 'three'),
    ])
      NovelChapter(title: title, source: ref),
  ];
  @override
  SourceId get id => _id;
  @override
  String get name => 'Source';
  @override
  bool get isAvailable => available;
  @override
  MediaOpenLease acquireOpenLease(SourceMediaRef media) {
    final lease = _Lease();
    leases.add(lease);
    return lease;
  }

  Future<void> load() async {
    final index = calls++;
    if (index < gates.length) await gates[index].future;
  }
}

final class _MangaSource extends _Source implements MangaSeriesSource {
  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef series) async {
    await load();
    return MangaSeriesDetails(
      metadata: MediaMetadata(title: series.itemId),
      chapters: manga.reversed.toList(),
      chapterListOrder: ChapterListOrder.reverseReadingOrder,
    );
  }
}

final class _NovelSource extends _Source
    implements NovelSeriesSource, NovelChapterSource {
  @override
  Future<NovelDetails> loadDetails(SourceMediaRef series) async {
    await load();
    return NovelDetails(
      metadata: MediaMetadata(title: series.itemId),
      chapters: novel.reversed.toList(),
      chapterListOrder: ChapterListOrder.reverseReadingOrder,
    );
  }

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async =>
      RichReadingContent(html: 'text');
  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}
