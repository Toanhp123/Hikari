import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/open_series_continuation.dart';
import 'package:hikari/application/media/read_remote_chapter_list.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';
import 'package:hikari/infrastructure/cache/disk_byte_cache.dart';
import 'package:path/path.dart' as p;

const _sourceId = SourceId('remote');
const _series = SourceMediaRef(sourceId: _sourceId, itemId: 'series');
const _otherSeries = SourceMediaRef(sourceId: _sourceId, itemId: 'other');
const _one = SourceMediaRef(sourceId: _sourceId, itemId: 'one');
const _two = SourceMediaRef(sourceId: _sourceId, itemId: 'two');
const _missing = SourceMediaRef(sourceId: _sourceId, itemId: 'missing');
const _manga = Media(title: 'Manga', source: _series, type: MediaType.manga);
const _novel = Media(
  title: 'Novel',
  source: _series,
  type: MediaType.lightNovel,
);

final class _MemoryCache implements ByteCache {
  final data = <String, Uint8List>{};
  String _id(String namespace, String key) => '$namespace:$key';
  @override
  Future<Uint8List?> read(String namespace, String key) async =>
      data[_id(namespace, key)];
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {
    data[_id(namespace, key)] = Uint8List.fromList(bytes);
  }

  @override
  Future<void> close() async {}
}

final class _Progress implements ProgressRepository {
  int saves = 0;
  @override
  Future<MediaProgress?> load(SourceMediaRef ref) async => null;
  @override
  Future<void> save(MediaProgress progress) async {
    saves++;
  }

  @override
  Future<void> delete(SourceMediaRef ref) async {}
}

final class _MangaSource implements MangaSeriesSource {
  bool offline = false;
  int calls = 0;
  Future<void>? firstRequestGate;
  List<MangaChapter> chapters = [
    MangaChapter(title: 'Chapter one', source: _one),
    MangaChapter(title: 'Chapter two', source: _two),
  ];
  @override
  SourceId get id => _sourceId;
  @override
  String get name => 'Remote Manga';
  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef ref) async {
    calls++;
    if (offline) throw StateError('offline');
    final currentChapters = List<MangaChapter>.of(chapters);
    if (calls == 1 && firstRequestGate != null) await firstRequestGate;
    return MangaSeriesDetails(
      metadata: MediaMetadata(title: 'Series'),
      chapters: currentChapters,
      chapterListOrder: ChapterListOrder.reverseReadingOrder,
    );
  }
}

final class _NovelSource implements NovelSeriesSource, NovelChapterSource {
  bool offline = false;
  int calls = 0;
  List<NovelChapter> chapters = [
    NovelChapter(title: 'Part one', source: _one),
    NovelChapter(title: 'Part two', source: _two),
  ];
  @override
  SourceId get id => _sourceId;
  @override
  String get name => 'Remote Novel';
  @override
  Future<NovelDetails> loadDetails(SourceMediaRef ref) async {
    calls++;
    if (offline) throw StateError('offline');
    return NovelDetails(
      metadata: MediaMetadata(title: 'Series'),
      chapters: chapters,
      chapterListOrder: ChapterListOrder.reverseReadingOrder,
    );
  }

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async =>
      RichReadingContent(html: 'text');
  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}

void main() {
  test('snapshot exposes atomic freshness and structural order', () async {
    var now = DateTime.utc(2026, 1, 1);
    final cache = ReadRemoteChapterList(_MemoryCache(), clock: () => now);
    await cache.persistManga(_series, [
      MangaChapter(title: 'First', source: _one),
      MangaChapter(title: 'Last', source: _two),
    ], chapterListOrder: ChapterListOrder.reverseReadingOrder);
    final snapshot = (await cache.readMangaSnapshot(_series))!;
    expect(snapshot.chapters.map((chapter) => chapter.source), [_one, _two]);
    expect(snapshot.chapterListOrder, ChapterListOrder.reverseReadingOrder);
    expect(snapshot.fetchedAt, now);
    expect(cache.isFresh(snapshot.fetchedAt), isTrue);
    now = now.add(const Duration(hours: 1));
    expect(cache.isFresh(snapshot.fetchedAt), isFalse);
    expect((await cache.readManga(_series))!.map((chapter) => chapter.source), [
      _one,
      _two,
    ]);
  });

  test('manga snapshot keeps canonical order and chapter metadata', () async {
    final cache = ReadRemoteChapterList(_MemoryCache());
    final chapters = [
      MangaChapter(
        title: 'Special',
        source: _two,
        scanlator: 'Group',
        chapterNumber: 1.5,
        uploadedAt: DateTime.utc(2025, 2, 3),
        canReadPages: false,
      ),
      MangaChapter(title: 'Chapter', source: _one),
    ];
    await cache.persistManga(_series, chapters);
    final restored = (await cache.readManga(_series))!;
    expect(restored.map((chapter) => chapter.source), [_two, _one]);
    expect(restored.first.title, 'Special');
    expect(restored.first.scanlator, 'Group');
    expect(restored.first.chapterNumber, 1.5);
    expect(restored.first.uploadedAt, DateTime.utc(2025, 2, 3));
    expect(restored.first.canReadPages, isFalse);
    expect(await cache.readNovel(_series), isNull);
    expect(await cache.readManga(_otherSeries), isNull);
    const local = SourceMediaRef(sourceId: SourceId.local, itemId: 'local');
    const localChapter = SourceMediaRef(
      sourceId: SourceId.local,
      itemId: 'chapter',
    );
    await cache.persistManga(local, [
      MangaChapter(
        title: 'Local is not a remote snapshot',
        source: localChapter,
      ),
    ]);
    expect(await cache.readManga(local), isNull);
  });

  test('novel snapshot preserves release and scanlator fields', () async {
    final cache = ReadRemoteChapterList(_MemoryCache());
    await cache.persistNovel(_series, [
      NovelChapter(
        title: 'Prologue',
        source: _one,
        chapterNumber: 0,
        releaseDate: DateTime.utc(2024, 1, 1),
        releaseLabel: 'Jan 2024',
        scanlators: ['A', 'B'],
      ),
    ]);
    final restored = (await cache.readNovel(_series))!.single;
    expect(restored.title, 'Prologue');
    expect(restored.chapterNumber, 0);
    expect(restored.releaseDate, DateTime.utc(2024, 1, 1));
    expect(restored.releaseLabel, 'Jan 2024');
    expect(restored.scanlators, ['A', 'B']);
    expect(await cache.readManga(_series), isNull);
  });

  test(
    'invalid fresh sequences cannot overwrite last valid snapshot',
    () async {
      final cache = ReadRemoteChapterList(_MemoryCache());
      await cache.persistManga(_series, [
        MangaChapter(title: 'Old', source: _one),
      ]);
      await expectLater(
        cache.persistManga(_series, [
          MangaChapter(title: 'Duplicated', source: _one),
          MangaChapter(title: 'Duplicated', source: _one),
        ]),
        throwsStateError,
      );
      await expectLater(
        cache.persistManga(_series, [
          MangaChapter(
            title: 'Foreign',
            source: const SourceMediaRef(
              sourceId: SourceId('foreign'),
              itemId: 'chapter',
            ),
          ),
        ]),
        throwsStateError,
      );
      expect((await cache.readManga(_series))!.single.title, 'Old');
    },
  );

  test(
    'corrupt, mismatched and oversized snapshots are cache misses',
    () async {
      final storage = _MemoryCache();
      final cache = ReadRemoteChapterList(storage);
      await cache.persistManga(_series, [
        MangaChapter(title: 'A', source: _one),
      ]);
      final key = storage.data.keys.single;
      final valid =
          jsonDecode(utf8.decode(storage.data[key]!)) as Map<String, dynamic>;
      for (final changed in [
        {...valid, 'version': 99},
        {...valid, 'source': 'foreign'},
        {
          ...valid,
          'chapters': [
            ['one', 'A', null, null, null, true],
            ['one', 'B', null, null, null, true],
          ],
        },
        {
          ...valid,
          'chapters': [
            ['one', 'A', null, double.nan, null, true],
          ],
        },
        {
          ...valid,
          'chapters': [
            ['', 'A', null, null, null, true],
          ],
        },
      ]) {
        // NaN cannot be JSON encoded; a nonfinite chapter number is represented
        // by an invalid JSON string instead.
        try {
          storage.data[key] = Uint8List.fromList(
            utf8.encode(jsonEncode(changed)),
          );
        } on JsonUnsupportedObjectError {
          storage.data[key] = Uint8List.fromList(utf8.encode('not-json'));
        }
        expect(await cache.readManga(_series), isNull);
      }
      storage.data[key] = Uint8List(2 * 1024 * 1024 + 1);
      expect(await cache.readManga(_series), isNull);
      storage.data[key] = Uint8List.fromList([0xff, 0xff]);
      expect(await cache.readManga(_series), isNull);
    },
  );

  test(
    'restart preserves offline navigation without loading source details',
    () async {
      final root = await Directory.systemTemp.createTemp(
        'hikari-chapter-lists-',
      );
      addTearDown(() => root.delete(recursive: true));
      DiskByteCache openCache() => DiskByteCache(
        cacheBaseDirectory: () async => root,
        namespaceByteBudgets: const {
          ReadRemoteChapterList.cacheNamespace: 1024 * 1024,
        },
        openDatabase: (path) async =>
            CacheDatabase(NativeDatabase(File(p.join(path, 'index.sqlite')))),
      );
      final mangaSource = _MangaSource();
      final progress = _Progress();
      final firstDisk = openCache();
      final first = OpenMedia(
        SourceRegistry([mangaSource]),
        progress,
        chapterLists: ReadRemoteChapterList(firstDisk),
      );
      final detailsTarget =
          await first.execute(_manga) as MangaSeriesOpenTarget;
      await detailsTarget.loadDetails();
      await detailsTarget.release();
      expect(mangaSource.calls, 1);
      await firstDisk.close();

      mangaSource.offline = true;
      final secondDisk = openCache();
      addTearDown(secondDisk.close);
      final reopened = OpenMedia(
        SourceRegistry([mangaSource]),
        progress,
        chapterLists: ReadRemoteChapterList(secondDisk),
      );
      final target = await OpenSeriesContinuation(
        reopened,
      ).execute(_manga, _one) as MangaContinuationOpenTarget;
      final cached = (await target.readCachedSequence())!;
      expect(cached.map((chapter) => chapter.source), [_two, _one]);
      expect(mangaSource.calls, 1);
      await expectLater(target.refreshSequence(), throwsStateError);
      expect((await target.readCachedSequence())!.length, 2);
      final missing = await OpenSeriesContinuation(
        reopened,
      ).execute(_manga, _missing) as MangaContinuationOpenTarget;
      expect(await missing.readCachedSequence(), isNull);
      await missing.release();
      expect(progress.saves, 0);
      await secondDisk.clear(ReadRemoteChapterList.cacheNamespace);
      expect(await target.readCachedSequence(), isNull);
      await target.release();
    },
  );

  test(
    'native Manga and Novel detail reconstruct structural order offline',
    () async {
      final disk = _MemoryCache();
      final lists = ReadRemoteChapterList(disk);
      final mangaSource = _MangaSource();
      final novelSource = _NovelSource();
      final mangaOpen = OpenMedia(
        SourceRegistry([mangaSource]),
        _Progress(),
        chapterLists: lists,
      );
      final novelOpen = OpenMedia(
        SourceRegistry([novelSource]),
        _Progress(),
        chapterLists: lists,
      );
      final manga = await mangaOpen.execute(_manga) as MangaSeriesOpenTarget;
      final novel = await novelOpen.execute(_novel) as NovelSeriesOpenTarget;
      await manga.loadDetails();
      await novel.loadDetails();
      mangaSource.offline = true;
      novelSource.offline = true;
      // Recreating the reader of persisted bytes models a cold process start.
      final restarted = ReadRemoteChapterList(disk);
      final coldManga = await OpenMedia(
        SourceRegistry([mangaSource]),
        _Progress(),
        chapterLists: restarted,
      ).execute(_manga) as MangaSeriesOpenTarget;
      final coldNovel = await OpenMedia(
        SourceRegistry([novelSource]),
        _Progress(),
        chapterLists: restarted,
      ).execute(_novel) as NovelSeriesOpenTarget;
      final mangaCached = (await coldManga.readCachedDetails())!;
      final novelCached = (await coldNovel.readCachedDetails())!;
      expect(mangaCached.chaptersInReadingOrder.map((c) => c.source), [
        _two,
        _one,
      ]);
      expect(novelCached.chaptersInReadingOrder.map((c) => c.source), [
        _two,
        _one,
      ]);
      expect(mangaCached.metadata.title, _manga.title);
      expect(novelCached.metadata.title, _novel.title);
      expect(mangaSource.calls, 1);
      expect(novelSource.calls, 1);
      await coldManga.release();
      await coldNovel.release();
      await manga.release();
      await novel.release();
    },
  );

  test(
    'a delayed older request cannot replace the newer chapter order',
    () async {
      final source = _MangaSource();
      final gate = Completer<void>();
      source.firstRequestGate = gate.future;
      final cache = ReadRemoteChapterList(_MemoryCache());
      final target = await OpenMedia(
        SourceRegistry([source]),
        _Progress(),
        chapterLists: cache,
      ).execute(_manga) as MangaSeriesOpenTarget;

      final older = target.loadDetails();
      source.chapters = [MangaChapter(title: 'New', source: _two)];
      await target.loadDetails();
      gate.complete();
      await older;
      expect(
        (await cache.readManga(_series))!.map((chapter) => chapter.source),
        [_two],
      );
      await target.release();
    },
  );

  test('failed detail update leaves the last valid snapshot intact', () async {
    final source = _MangaSource();
    final cache = ReadRemoteChapterList(_MemoryCache());
    final target = await OpenMedia(
      SourceRegistry([source]),
      _Progress(),
      chapterLists: cache,
    ).execute(_manga) as MangaSeriesOpenTarget;
    await target.loadDetails();
    source.chapters = [
      MangaChapter(title: 'Duplicate A', source: _one),
      MangaChapter(title: 'Duplicate B', source: _one),
    ];
    await expectLater(target.loadDetails(), throwsStateError);
    expect((await cache.readManga(_series))!.map((item) => item.source), [
      _two,
      _one,
    ]);
    await target.release();
  });

  test('novel cached sequence survives offline fresh failure', () async {
    final source = _NovelSource();
    final cache = ReadRemoteChapterList(_MemoryCache());
    final progress = _Progress();
    final open = OpenMedia(
      SourceRegistry([source]),
      progress,
      chapterLists: cache,
    );
    final detail = await open.execute(_novel) as NovelSeriesOpenTarget;
    await detail.loadDetails();
    await detail.release();
    source.offline = true;
    final target = await OpenSeriesContinuation(
      open,
    ).execute(_novel, _one) as NovelContinuationOpenTarget;
    expect(
      (await target.readCachedSequence())!.map((chapter) => chapter.source),
      [_two, _one],
    );
    await expectLater(target.refreshSequence(), throwsStateError);
    expect((await target.readCachedSequence())!.length, 2);
    expect(progress.saves, 0);
    await target.release();
  });
}
